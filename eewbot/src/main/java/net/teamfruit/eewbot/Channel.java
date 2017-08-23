package net.teamfruit.eewbot;

import java.io.IOException;
import java.util.Arrays;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class Channel {
	public static final Channel DEFAULT = new Channel().setId(0);

	private long id;
	public final ChannelElement eewAlert = new ChannelElement("EEW警報", true);
	public final ChannelElement eewPrediction = new ChannelElement("EEW予報", false);
	public final ChannelElement quakeInfo = new ChannelElement("地震情報", true);
	public final ChannelElement quakeInfoDetail = new ChannelElement("詳細地震情報", false);
	public final ChannelElement monitor = new ChannelElement("強震モニタ", true);

	public Channel() {
	}

	@Deprecated
	public Channel(final long id) {
		this.id = id;
	}

	public Channel setId(final long id) {
		this.id = id;
		return this;
	}

	public long getId() {
		return this.id;
	}

	public ChannelElement getElement(final String str) {
		return Arrays.stream(Channel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).filter(f -> {
			try {
				return f.getName().equalsIgnoreCase(str)||((ChannelElement) f.get(this)).name.equals(str);
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}).findAny().map(f -> {
			try {
				return ((ChannelElement) f.get(this));
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}).orElse(null);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("EEW警報: ").append(this.eewAlert).append("\n");
		sb.append("EEW予報: ").append(this.eewPrediction).append("\n");
		sb.append("地震情報: ").append(this.quakeInfo).append("\n");
		sb.append("詳細地震情報: ").append(this.quakeInfoDetail).append("\n");
		sb.append("強震モニタ: ").append(this.monitor);
		return sb.toString();
	}

	public static class ChannelElement {

		public final String name;
		private boolean bool;

		public ChannelElement(final String name, final boolean bool) {
			this.name = name;
			this.bool = bool;
		}

		public void set(final boolean bool) {
			this.bool = bool;
		}

		public boolean get() {
			return this.bool;
		}
	}

	public static class ChannelTypeAdapter extends TypeAdapter<Channel> {

		@Override
		public void write(final JsonWriter out, final Channel value) throws IOException {
			out.beginObject();
			out.name("eewAlert").value(value.eewAlert.get());
			out.name("eewPrediction").value(value.eewPrediction.get());
			out.name("quakeInfo").value(value.quakeInfo.get());
			out.name("quakeInfoDetail").value(value.quakeInfoDetail.get());
			out.name("monitor").value(value.monitor.get());
			out.endObject();
		}

		@Override
		public Channel read(final JsonReader in) throws IOException {
			final Channel channel = new Channel();
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
					case "eewAlert":
						channel.eewAlert.set(Boolean.valueOf(in.nextString()));
						break;
					case "eewPrediction":
						channel.eewPrediction.set(Boolean.valueOf(in.nextString()));
						break;
					case "quakeInfo":
						channel.quakeInfo.set(Boolean.valueOf(in.nextString()));
						break;
					case "quakeInfoDetail":
						channel.quakeInfoDetail.set(Boolean.valueOf(in.nextString()));
						break;
					case "monitor":
						channel.monitor.set(Boolean.valueOf(in.nextString()));
						break;
				}
			}
			in.endObject();
			return channel;
		}

	}
}
