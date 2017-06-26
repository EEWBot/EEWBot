package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import net.teamfruit.eewbot.EEWBot;

public class EEWDispatcher implements Runnable {

	public static final String REMOTE = "http://www.kmoni.bosai.go.jp/new/webservice/hypo/eew/";
	public static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

	@Override
	public void run() {
		try {
			final Date date = new Date(System.currentTimeMillis()+EEWBot.ntp.getOffset()-1000);
			final URL url = new java.net.URL(REMOTE+FORMAT.format(date)+".json");
			final EEW res = EEWBot.GSON.fromJson(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8), EEW.class);
			if (res.alertflg!=null) { //EEW
				final EEWEvent event = new EEWEvent(EEWBot.client, res);
				EEWBot.client.getDispatcher().dispatch(event);
			}
		} catch (final JsonSyntaxException|JsonIOException|IOException e) {
			EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static class EEW {
		public String alertflg;
		public String calcintensity;
		public String depth;
		public boolean is_cancel;
		public boolean is_final;
		public boolean is_training;
		public String latitude;
		public String longitude;
		public String magunitude;
		public String origin_time;
		public String region_code;
		public String region_name;
		public String report_id;
		public String report_num;
		public String report_time;
		public String request_hypo_type;
		public String request_time;
		public Result result;
		public Security security;

		public static class Result {
			public boolean is_auth;
			public String message;
			public String status;
		}

		public static class Security {
			public String hash;
			public String realm;
		}
	}
}
