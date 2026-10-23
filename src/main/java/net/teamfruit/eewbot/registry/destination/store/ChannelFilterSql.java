package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.jooq.Condition;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.*;

/** SQL translation of the backend-independent delivery filter. */
public final class ChannelFilterSql {

    private ChannelFilterSql() {
    }

    /**
     * Converts this filter into a jOOQ Condition for SQL queries.
     * Boolean fields are stored as INTEGER (0/1) in the database for both SQLite and PostgreSQL.
     *
     * @return jOOQ Condition representing the filter criteria
     */
    public static Condition toCondition(ChannelFilter filter) {
        if (filter == null) {
            return noCondition();
        }
        List<Condition> conditions = new ArrayList<>();

        if (filter.isHasGuildPresent()) {
            Field<Long> f = field(name("guild_id"), Long.class);
            if (filter.getHasGuild() != null && filter.getHasGuild()) {
                conditions.add(f.isNotNull());
            } else if (filter.getHasGuild() != null) {
                conditions.add(f.isNull());
            }
        }
        if (filter.isGuildIdPresent()) {
            conditions.add(field(name("guild_id"), Long.class).eq(filter.getGuildId()));
        }
        if (filter.isChannelIdPresent()) {
            conditions.add(field(name("channel_id"), Long.class).eq(filter.getChannelId()));
        }
        if (filter.isThreadIdPresent()) {
            Field<Long> f = field(name("thread_id"), Long.class);
            if (filter.getThreadId() != null) {
                conditions.add(f.eq(filter.getThreadId()));
            } else {
                conditions.add(f.isNull());
            }
        }
        if (filter.isIsThreadPresent()) {
            Field<Long> f = field(name("thread_id"), Long.class);
            if (filter.getIsThread() != null && filter.getIsThread()) {
                conditions.add(f.isNotNull());
            } else if (filter.getIsThread() != null) {
                conditions.add(f.isNull());
            }
        }
        if (filter.isEewAlertPresent()) {
            conditions.add(field(name("eew_alert"), Integer.class).eq(filter.getEewAlert() ? 1 : 0));
        }
        if (filter.isEewPredictionPresent()) {
            conditions.add(field(name("eew_prediction"), Integer.class).eq(filter.getEewPrediction() ? 1 : 0));
        }
        if (filter.isEewDecimationPresent()) {
            conditions.add(field(name("eew_decimation"), Integer.class).eq(filter.getEewDecimation() ? 1 : 0));
        }
        if (filter.isQuakeInfoPresent()) {
            conditions.add(field(name("quake_info"), Integer.class).eq(filter.getQuakeInfo() ? 1 : 0));
        }
        if (filter.isTsunamiPresent()) {
            conditions.add(field(name("tsunami"), Integer.class).eq(filter.getTsunami() ? 1 : 0));
        }
        if (filter.isIntensityPresent()) {
            conditions.add(field(name("min_intensity"), Integer.class).le(filter.getIntensity().getCode()));
        }
        if (filter.isWebhookIdPresent()) {
            conditions.add(field(name("webhook_id"), Long.class).eq(filter.getWebhookId()));
        }

        if (conditions.isEmpty()) {
            return noCondition();
        }
        return conditions.stream().reduce(Condition::and).orElse(noCondition());
    }

}
