package net.teamfruit.eewbot.node;

import java.text.ParseException;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.util.EmbedBuilder;

public class EEW implements Embeddable {
	public static final FastDateFormat FORMAT = FastDateFormat.getInstance("yyyy/MM/dd HH:mm:ss");

	private String alertflg;
	private String calcintensity;
	private String depth;
	private boolean is_cancel;
	private boolean is_final;
	private boolean is_training;
	private String latitude;
	private String longitude;
	private String magunitude;
	private String origin_time;
	private String region_code;
	private String region_name;
	private String report_id;
	private String report_num;
	private String report_time;
	private String request_hypo_type;
	private String request_time;
	private Result result;
	private Security security;

	public static class Result {
		private boolean is_auth;
		private String message;
		private String status;

		public boolean isAuth() {
			return this.is_auth;
		}

		public String getMessage() {
			return this.message;
		}

		public boolean getStatus() {
			return "success".equals(this.status);
		}

		@Override
		public String toString() {
			return "Result [is_auth="+this.is_auth+", message="+this.message+", status="+this.status+"]";
		}

	}

	public static class Security {
		private String hash;
		private String realm;

		public String getHash() {
			return this.hash;
		}

		public String getReam() {
			return this.realm;
		}

		@Override
		public String toString() {
			return "Security [hash="+this.hash+", realm="+this.realm+"]";
		}
	}

	public boolean isEEW() {
		return getAlertFlg()!=null;
	}

	public String getAlertFlg() {
		return this.alertflg;
	}

	public boolean isAlert() {
		return "警報".equals(this.alertflg);
	}

	public SeismicIntensity getIntensity() {
		return SeismicIntensity.get(this.calcintensity);
	}

	public int getDepth() {
		if (StringUtils.isEmpty(this.depth))
			return -1;
		return Integer.parseInt(this.depth.substring(0, this.depth.length()-2));
	}

	public boolean isCancel() {
		return this.is_cancel;
	}

	public boolean isFinal() {
		return this.is_final;
	}

	public boolean isTraining() {
		return this.is_training;
	}

	public float getLat() {
		if (StringUtils.isEmpty(this.latitude))
			return -1;
		return Float.parseFloat(this.latitude);
	}

	public float getLon() {
		if (StringUtils.isEmpty(this.longitude))
			return -1;
		return Float.parseFloat(this.longitude);
	}

	public float getMagnitude() {
		if (StringUtils.isEmpty(this.magunitude))
			return -1;
		return Float.parseFloat(this.magunitude);
	}

	public Date getOriginTime() {
		if (StringUtils.isEmpty(this.origin_time))
			return null;
		try {
			return EEWDispatcher.FORMAT.parse(this.origin_time);
		} catch (final ParseException e) {
			return null;
		}
	}

	@Deprecated
	public int getRegionCode() {
		if (StringUtils.isEmpty(this.region_code))
			return -1;
		return Integer.parseInt(this.region_code);
	}

	public String getRegionName() {
		return this.region_name;
	}

	public long getReportId() {
		if (StringUtils.isEmpty(this.report_id))
			return -1;
		return Long.parseLong(this.report_id);
	}

	public int getReportNum() {
		if (StringUtils.isEmpty(this.report_num))
			return -1;
		return Integer.parseInt(this.report_num);
	}

	public Date getReportTime() {
		if (StringUtils.isEmpty(this.report_time))
			return null;
		try {
			return FORMAT.parse(this.report_time);
		} catch (final ParseException e) {
			return null;
		}
	}

	public String getRequestHypoType() {
		return this.request_hypo_type;
	}

	public Date getRequestTime() {
		if (StringUtils.isEmpty(this.request_time))
			return null;
		try {
			return EEWDispatcher.FORMAT.parse(this.request_time);
		} catch (final ParseException e) {
			return null;
		}
	}

	@Override
	public String toString() {
		return "EEW [alertflg="+this.alertflg+", calcintensity="+this.calcintensity+", depth="+this.depth+", is_cancel="+this.is_cancel+", is_final="+this.is_final+", is_training="+this.is_training+", latitude="+this.latitude+", longitude="+this.longitude+", magunitude="+this.magunitude+", origin_time="+this.origin_time+", region_code="+this.region_code+", region_name="+this.region_name+", report_id="+this.report_id+", report_num="+this.report_num+", report_time="+this.report_time+", request_hypo_type="
				+this.request_hypo_type+", request_time="+this.request_time+", result="+this.result
				+", security="+this.security+"]";
	}

	@Override
	public EmbedObject buildEmbed() {
		final EmbedBuilder builder = new EmbedBuilder();

		builder.appendField("震央", getRegionName(), true);
		builder.appendField("深さ", getDepth()+"km", true);
		builder.appendField("マグニチュード", String.valueOf(getMagnitude()), true);
		builder.appendField("予想震度", getIntensity().getSimple(), false);

		if (isAlert())
			builder.withColor(255, 0, 0);
		else
			builder.withColor(0, 0, 255);
		builder.withTitle("緊急地震速報 ("+getAlertFlg()+") "+(isFinal() ? "最終報" : "第"+getReportNum()+"報"));
		builder.withTimestamp(getReportTime().getTime());

		builder.withFooterText("新強震モニタ");
		return builder.build();
	}
}
