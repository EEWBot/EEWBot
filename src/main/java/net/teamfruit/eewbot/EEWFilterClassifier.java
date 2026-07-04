package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import net.teamfruit.eewbot.entity.other.KmoniEEW;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.jetbrains.annotations.Nullable;

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

    public static boolean isKmoniWarning(KmoniEEW eew) {
        KmoniEEW prev = eew.getPrev();
        return eew.isCancel() ? prev != null && prev.isAlert() : eew.isAlert();
    }

    public static boolean isKmoniImportant(KmoniEEW eew) {
        KmoniEEW prev = eew.getPrev();
        return prev == null ||
                eew.isInitial() ||
                eew.isFinal() ||
                eew.isAlert() != prev.isAlert() ||
                !eew.getIntensity().equals(prev.getIntensity()) ||
                !eew.getRegionName().equals(prev.getRegionName());
    }

    public static boolean isDmdataWarning(EEW current, @Nullable EEW prev) {
        return current.isCanceled() ? prev != null && prev.isEEWWarning() : current.isEEWWarning();
    }

    public static boolean isDmdataImportant(EEW current, @Nullable EEW prev) {
        if (prev == null)
            return true;
        EEW.ForecastMaxInt currentMaxInt = current.getForecastMaxInt();
        EEW.ForecastMaxInt prevMaxInt = prev.getForecastMaxInt();
        return current.isLastInfo() ||
                current.isEEWWarning() != prev.isEEWWarning() ||
                (currentMaxInt == null) != (prevMaxInt == null) ||
                currentMaxInt != null && !currentMaxInt.from().equals(prevMaxInt.from()) ||
                !current.getHypocenterName().equals(prev.getHypocenterName());
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
