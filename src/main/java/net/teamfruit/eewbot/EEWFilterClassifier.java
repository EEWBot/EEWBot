package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate.DeliveryHistory;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EEWFilterClassifier {

    private EEWFilterClassifier() {
    }

    public static ChannelFilter classifyEEW(boolean isWarning, boolean isImportant, SeismicIntensity maxIntensity) {
        ChannelFilter.Builder builder = ChannelFilter.builder();
        if (isWarning)
            builder.eewAlert(true);
        else
            builder.eewPrediction(true);
        if (!isImportant)
            builder.eewDecimation(false);
        builder.intensity(maxIntensity);
        return builder.build();
    }

    public static boolean isDmdataWarning(EEW current, @Nullable EEW prev) {
        return current.isCancelReport() ? prev != null && prev.isEEWWarning() : current.isEEWWarning();
    }

    public static List<ChannelFilter> classifyDmdataCancellation(DeliveryHistory history) {
        if (history.isEmpty()) {
            // Lost history: accept either EEW subscription, without intensity or decimation filters.
            return List.of(ChannelFilter.builder().eewAlert(true).build(),
                    ChannelFilter.builder().eewPrediction(true).build());
        }
        List<ChannelFilter> filters = new ArrayList<>();
        if (history.warningMaxIntensity() != null)
            filters.add(classifyEEW(true, true, history.warningMaxIntensity()));
        if (history.forecastMaxIntensity() != null)
            filters.add(classifyEEW(false, true, history.forecastMaxIntensity()));
        return List.copyOf(filters);
    }

    public static boolean isDmdataImportant(EEW current, @Nullable EEW prev) {
        if (prev == null || prev.isCancelReport())
            return true;
        // Only distinct telegram IDs reach this classifier. Non-advancing serials and corrections
        // may contain important edits outside the fields compared below, so never decimate them.
        if (current.getInfoType() == JMAInfoType.訂正 || Integer.parseInt(current.getSerial()) <= Integer.parseInt(prev.getSerial()))
            return true;
        EEW.ForecastMaxInt currentMaxInt = current.getForecastMaxInt();
        EEW.ForecastMaxInt prevMaxInt = prev.getForecastMaxInt();
        return current.isLastInfo() ||
                current.isEEWWarning() != prev.isEEWWarning() ||
                (currentMaxInt == null) != (prevMaxInt == null) ||
                currentMaxInt != null && !currentMaxInt.from().equals(prevMaxInt.from()) ||
                !Objects.equals(current.getHypocenterName(), prev.getHypocenterName());
    }

    public static ChannelFilter classifyQuakeInfo(SeismicIntensity maxIntensity) {
        return ChannelFilter.builder()
                .quakeInfo(true)
                .intensity(maxIntensity)
                .build();
    }

    public static ChannelFilter classifyTsunami() {
        return ChannelFilter.builder()
                .tsunami(true)
                .build();
    }
}
