package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.jooq.Condition;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jooq.impl.DSL.*;

public class ChannelFilter {

    private Boolean isGuild;
    private boolean isGuildPresent;
    private long guildId;
    private boolean guildIdPresent;
    private long channelId;
    private boolean channelIdPresent;
    private Long threadId;
    private boolean threadIdPresent;
    private Boolean isThread;
    private boolean isThreadPresent;
    private boolean eewAlert;
    private boolean eewAlertPresent;
    private boolean eewPrediction;
    private boolean eewPredictionPresent;
    private boolean eewDecimation;
    private boolean eewDecimationPresent;
    private boolean quakeInfo;
    private boolean quakeInfoPresent;
    private SeismicIntensity intensity;
    private boolean intensityPresent;
    private long webhookId;
    private boolean webhookIdPresent;

    public boolean isGuildPresent() {
        return this.isGuildPresent;
    }

    public boolean isGuildIdPresent() {
        return this.guildIdPresent;
    }

    public boolean isChannelIdPresent() {
        return this.channelIdPresent;
    }

    public boolean isThreadIdPresent() {
        return this.threadIdPresent;
    }

    public boolean isIsThreadPresent() {
        return this.isThreadPresent;
    }

    public boolean isEewAlertPresent() {
        return this.eewAlertPresent;
    }

    public boolean isEewPredictionPresent() {
        return this.eewPredictionPresent;
    }

    public boolean isEewDecimationPresent() {
        return this.eewDecimationPresent;
    }

    public boolean isQuakeInfoPresent() {
        return this.quakeInfoPresent;
    }

    public boolean isIntensityPresent() {
        return this.intensityPresent;
    }

    public boolean isWebhookIdPresent() {
        return this.webhookIdPresent;
    }

    public Boolean getIsGuild() {
        return this.isGuild;
    }

    public long getGuildId() {
        return this.guildId;
    }

    public long getChannelId() {
        return this.channelId;
    }

    public Long getThreadId() {
        return this.threadId;
    }

    public Boolean getIsThread() {
        return this.isThread;
    }

    public boolean getEewAlert() {
        return this.eewAlert;
    }

    public boolean getEewPrediction() {
        return this.eewPrediction;
    }

    public boolean getEewDecimation() {
        return this.eewDecimation;
    }

    public boolean getQuakeInfo() {
        return this.quakeInfo;
    }

    public SeismicIntensity getIntensity() {
        return this.intensity;
    }

    public long getWebhookId() {
        return this.webhookId;
    }

    /**
     * Converts this filter into a jOOQ Condition for SQL queries.
     * Boolean fields are stored as INTEGER (0/1) in the database for both SQLite and PostgreSQL.
     *
     * @return jOOQ Condition representing the filter criteria
     */
    public Condition toCondition() {
        List<Condition> conditions = new ArrayList<>();

        if (this.isGuildPresent) {
            Field<Integer> f = field(name("is_guild"), Integer.class);
            if (this.isGuild != null) {
                conditions.add(f.eq(this.isGuild ? 1 : 0));
            } else {
                conditions.add(f.isNull());
            }
        }
        if (this.guildIdPresent) {
            conditions.add(field(name("guild_id"), Long.class).eq(this.guildId));
        }
        if (this.channelIdPresent) {
            conditions.add(field(name("channel_id"), Long.class).eq(this.channelId));
        }
        if (this.threadIdPresent) {
            Field<Long> f = field(name("thread_id"), Long.class);
            if (this.threadId != null) {
                conditions.add(f.eq(this.threadId));
            } else {
                conditions.add(f.isNull());
            }
        }
        if (this.isThreadPresent) {
            Field<Long> f = field(name("thread_id"), Long.class);
            if (this.isThread != null && this.isThread) {
                conditions.add(f.isNotNull());
            } else if (this.isThread != null) {
                conditions.add(f.isNull());
            }
        }
        if (this.eewAlertPresent) {
            conditions.add(field(name("eew_alert"), Integer.class).eq(this.eewAlert ? 1 : 0));
        }
        if (this.eewPredictionPresent) {
            conditions.add(field(name("eew_prediction"), Integer.class).eq(this.eewPrediction ? 1 : 0));
        }
        if (this.eewDecimationPresent) {
            conditions.add(field(name("eew_decimation"), Integer.class).eq(this.eewDecimation ? 1 : 0));
        }
        if (this.quakeInfoPresent) {
            conditions.add(field(name("quake_info"), Integer.class).eq(this.quakeInfo ? 1 : 0));
        }
        if (this.intensityPresent) {
            conditions.add(field(name("min_intensity"), Integer.class).le(this.intensity.ordinal()));
        }

        if (conditions.isEmpty()) {
            return noCondition();
        }
        return conditions.stream().reduce(Condition::and).orElse(noCondition());
    }

    public boolean test(Channel channel) {
        if (this.isGuildPresent && !Objects.equals(channel.isGuild(), this.isGuild))
            return false;
        if (this.guildIdPresent && !Objects.equals(channel.getGuildId(), this.guildId))
            return false;
        if (this.channelIdPresent && !Objects.equals(channel.getChannelId(), this.channelId))
            return false;
        if (this.threadIdPresent && !Objects.equals(channel.getThreadId(), this.threadId))
            return false;
        if (this.isThreadPresent) {
            boolean channelIsThread = channel.getThreadId() != null;
            if (this.isThread != null && this.isThread != channelIsThread)
                return false;
        }
        if (this.eewAlertPresent && channel.isEewAlert() != this.eewAlert)
            return false;
        if (this.eewPredictionPresent && channel.isEewPrediction() != this.eewPrediction)
            return false;
        if (this.eewDecimationPresent && channel.isEewDecimation() != this.eewDecimation)
            return false;
        if (this.quakeInfoPresent && channel.isQuakeInfo() != this.quakeInfo)
            return false;
        if (this.intensityPresent && channel.getMinIntensity().ordinal() > this.intensity.ordinal())
            return false;
        if (this.webhookIdPresent && channel.getWebhook() == null)
            return false;
        return !this.webhookIdPresent || channel.getWebhook().getId() == this.webhookId;
    }

    public String toQueryString() {
        StringBuilder builder = new StringBuilder();
        if (this.isGuildPresent) {
            if (this.isGuild != null)
                builder.append("@isGuild:{").append(this.isGuild).append("} ");
            else
                builder.append("-@isGuild:{true | false}");
        }
        if (this.guildIdPresent)
            builder.append("@guildId:[").append(this.guildId).append(" ").append(this.guildId).append("] ");
        if (this.channelIdPresent)
            builder.append("@channelId:[").append(this.channelId).append(" ").append(this.channelId).append("] ");
        if (this.threadIdPresent) {
            if (this.threadId != null)
                builder.append("@threadId:[").append(this.threadId).append(" ").append(this.threadId).append("] ");
            else
                builder.append("-@threadId:[0 inf] ");
        }
        if (this.isThreadPresent) {
            if (this.isThread != null && this.isThread)
                builder.append("@threadId:[0 inf] ");
            else if (this.isThread != null)
                builder.append("-@threadId:[0 inf] ");
        }
        if (this.eewAlertPresent)
            builder.append("@eewAlert:{").append(this.eewAlert).append("} ");
        if (this.eewPredictionPresent)
            builder.append("@eewPrediction:{").append(this.eewPrediction).append("} ");
        if (this.eewDecimationPresent)
            builder.append("@eewDecimation:{").append(this.eewDecimation).append("} ");
        if (this.quakeInfoPresent)
            builder.append("@quakeInfo:{").append(this.quakeInfo).append("} ");
        if (this.intensityPresent)
            builder.append("@minIntensity:[0 ").append(this.intensity.ordinal()).append("] ");
        if (this.webhookIdPresent)
            builder.append("@webhookId:[").append(this.webhookId).append(" ").append(this.webhookId).append("]");
        return builder.toString();
    }

    public static ChannelFilter.Builder builder() {
        return new ChannelFilter.Builder();
    }

    public static class Builder {

        private final ChannelFilter filter = new ChannelFilter();

        public Builder isGuild(Boolean isGuild) {
            this.filter.isGuild = isGuild;
            this.filter.isGuildPresent = true;
            return this;
        }

        public Builder guildId(long guildId) {
            this.filter.guildId = guildId;
            this.filter.guildIdPresent = true;
            return this;
        }

        public Builder channelId(long channelId) {
            this.filter.channelId = channelId;
            this.filter.channelIdPresent = true;
            return this;
        }

        public Builder threadId(Long threadId) {
            this.filter.threadId = threadId;
            this.filter.threadIdPresent = true;
            return this;
        }

        public Builder isThread(boolean isThread) {
            this.filter.isThread = isThread;
            this.filter.isThreadPresent = true;
            return this;
        }

        public Builder eewAlert(boolean eewAlert) {
            this.filter.eewAlert = eewAlert;
            this.filter.eewAlertPresent = true;
            return this;
        }

        public Builder eewPrediction(boolean eewPrediction) {
            this.filter.eewPrediction = eewPrediction;
            this.filter.eewPredictionPresent = true;
            return this;
        }

        public Builder eewDecimation(boolean eewDecimation) {
            this.filter.eewDecimation = eewDecimation;
            this.filter.eewDecimationPresent = true;
            return this;
        }

        public Builder quakeInfo(boolean quakeInfo) {
            this.filter.quakeInfo = quakeInfo;
            this.filter.quakeInfoPresent = true;
            return this;
        }

        public Builder intensity(SeismicIntensity minIntensity) {
            this.filter.intensity = minIntensity;
            this.filter.intensityPresent = true;
            return this;
        }

        public Builder webhookId(long webhookId) {
            this.filter.webhookId = webhookId;
            this.filter.webhookIdPresent = true;
            return this;
        }

        public ChannelFilter build() {
            return this.filter;
        }
    }

}
