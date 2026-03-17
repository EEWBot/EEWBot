package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE53;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class QuakeInfoStore {

    private final Map<String, Map<JMAXmlType, QuakeInfo>> reports = new ConcurrentHashMap<>();
    private volatile @Nullable QuakeInfo lastReport;

    public void putReport(QuakeInfo report) {
        report.initQuakeInfoStore(this);
        this.lastReport = report;

        if (report instanceof VXSE53) {
            this.reports.remove(report.getEventId());
        } else {
            this.reports.computeIfAbsent(report.getEventId(), k -> new ConcurrentHashMap<>())
                    .put(JMAXmlType.from(((JMAReport) report).getClass()), report);
        }
    }

    public Optional<QuakeInfo> getReport(String eventId, JMAXmlType type) {
        return Optional.ofNullable(this.reports.get(eventId)).flatMap(m -> Optional.ofNullable(m.get(type)));
    }

    public Optional<QuakeInfo> getLatestReport() {
        return Optional.ofNullable(this.lastReport);
    }

}
