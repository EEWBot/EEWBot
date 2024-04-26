package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE53;
import reactor.util.annotation.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class QuakeInfoStore {

    private final Map<Long, Map<JMAXmlType, QuakeInfo>> reports = new HashMap<>();
    private @Nullable QuakeInfo lastReport;

    public void putReport(QuakeInfo report) {
        this.lastReport = report;

        if (report instanceof VXSE53) {
            this.reports.remove(report.getEventId());
        } else {
            this.reports.computeIfAbsent(report.getEventId(), k -> new EnumMap<>(JMAXmlType.class))
                    .put(JMAXmlType.from(((JMAReport) report).getClass()), report);
        }
    }

    public Optional<QuakeInfo> getReport(long eventId, JMAXmlType type) {
        return Optional.ofNullable(this.reports.get(eventId)).flatMap(m -> Optional.ofNullable(m.get(type)));
    }

    public Optional<QuakeInfo> getLatestReport() {
        return Optional.ofNullable(this.lastReport);
    }

}
