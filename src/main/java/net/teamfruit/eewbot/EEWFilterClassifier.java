package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEW;
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

    public static boolean isDmdataWarning(DmdataEEW current, @Nullable DmdataEEW prev) {
        DmdataEEW.Body currentBody = current.getBody();
        DmdataEEW.Body prevBody = prev != null ? prev.getBody() : null;
        return currentBody.isCanceled() ? prevBody != null && prevBody.isWarning() : currentBody.isWarning();
    }

    public static boolean isDmdataImportant(DmdataEEW current, @Nullable DmdataEEW prev) {
        DmdataEEW.Body currentBody = current.getBody();
        DmdataEEW.Body prevBody = prev != null ? prev.getBody() : null;
        if (prevBody == null)
            return true;
        DmdataEEW.Body.Intensity currentIntensity = currentBody.getIntensity();
        DmdataEEW.Body.Intensity prevIntensity = prevBody.getIntensity();
        return currentBody.isLastInfo() ||
                currentBody.isWarning() != prevBody.isWarning() ||
                (currentIntensity == null) != (prevIntensity == null) ||
                currentIntensity != null && !currentIntensity.getForecastMaxInt().getFrom().equals(prevIntensity.getForecastMaxInt().getFrom()) ||
                !currentBody.getEarthquake().getHypocenter().getName().equals(prevBody.getEarthquake().getHypocenter().getName());
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
