package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Earthquake;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Hypocenter;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class VXSE61Impl extends JmxSeis implements VXSE61 {

    private QuakeInfoStore quakeInfoStore;

    @Override
    public <T> T createEmbed(String lang, EmbedContext ctx, IEmbedBuilder<T> builder) {
        this.quakeInfoStore = ctx.store();
        return VXSE61.super.createEmbed(lang, ctx, builder);
    }

    private Optional<QuakeInfo> getVXSE53() {
        return this.quakeInfoStore.getReport(getHead().getEventID(), JMAXmlType.VXSE53);
    }

    @Override
    public Instant getOriginTime() {
        return getEarthquake().getOriginTime();
    }

    @Override
    public String getHypocenterName() {
        return getHypocenter().getArea().getName();
    }

    @Override
    public Optional<String> getDepth() {
        return Optional.ofNullable(getHypocenter().getArea().getCoordinate().getLast().getDepth());
    }

    @Override
    public String getMagnitude() {
        return Objects.requireNonNull(getEarthquake().getMagnitude()).getMagnitude();
    }

    @Override
    public Optional<SeismicIntensity> getQuakeInfoMaxInt() {
        return this.quakeInfoStore.getReport(getHead().getEventID(), JMAXmlType.VXSE53)
                .flatMap(QuakeInfo::getQuakeInfoMaxInt);
    }

    @Override
    public Intensity.IntensityDetail getIntensityDetail() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return getVXSE53().map(QuakeInfo::getIntensityDetail).orElse(null);
    }

    private Earthquake getEarthquake() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return Objects.requireNonNull(getBody().getEarthquakes().get(0));
    }

    private Hypocenter getHypocenter() {
        return Objects.requireNonNull(getEarthquake().getHypocenter());
    }

    @Override
    public Coordinate getCoordinate() {
        return getHypocenter().getArea().getCoordinate().getFirst();
    }

    @Override
    public Optional<String> getFreeFormComment() {
        Comment comments = getBody().getComments();
        if (comments == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(comments.getFreeFormComment());
    }
}
