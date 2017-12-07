package net.teamfruit.eewbot.registry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class Channel {

	public final long id;
	public final ChannelElement eewAlert = new ChannelElement("EEW警報", true);
	public final ChannelElement eewPrediction = new ChannelElement("EEW予報", false);
	public final ChannelElement quakeInfo = new ChannelElement("地震情報", true);
	public final ChannelElement quakeInfoDetail = new ChannelElement("詳細地震情報", false);
	public final ChannelElement monitor = new ChannelElement("強震モニタ", true);

	public Channel(final long id) {
		this.id = id;
	}

	public ChannelElement getElement(final String str) {
		return Arrays.stream(Channel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).filter(f -> {
			try {
				return f.getName().equalsIgnoreCase(str)||((ChannelElement) f.get(this)).name.equals(str);
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}).findAny().map(f -> {
			try {
				return ((ChannelElement) f.get(this));
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}).orElse(null);
	}

	@Override
	public String toString() {
		return Arrays.stream(Channel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).map(f -> {
			try {
				return f.get(this).toString();
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}).collect(Collectors.joining("\n"));
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

		@Override
		public String toString() {
			return this.name+": "+this.bool;
		}
	}

	public static class ChannelTypeAdapter extends TypeAdapter<Channel> {

		@Override
		public void write(final JsonWriter out, final Channel value) throws IOException {
			out.beginObject();
			out.name("id").value(value.id);
			Arrays.stream(Channel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).forEach(f -> {
				try {
					out.name(f.getName()).value(((ChannelElement) f.get(value)).get());
				} catch (final IllegalAccessException e) {
					throw new JsonParseException(e);
				} catch (final IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			out.endObject();
		}

		@Override
		public Channel read(final JsonReader in) throws IOException {
			final Channel channel = new Channel(-1);
			final Map<String, ChannelElement> map = Arrays.stream(Channel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).collect(Collectors.toMap(f -> f.getName(), f -> {
				try {
					return (ChannelElement) f.get(channel);
				} catch (final IllegalAccessException e) {
					throw new JsonParseException(e);
				}
			}));
			in.beginObject();
			while (in.hasNext()) {
				final String line = in.nextName();
				if (line.equals("id")) {
					try {
						final Field field = Channel.class.getField("id");
						field.setAccessible(true);
						field.setLong(channel, in.nextLong());
					} catch (NoSuchFieldException|SecurityException|IllegalAccessException e) {
						throw new RuntimeException(e);
					}
				} else {
					final ChannelElement element = map.get(line);
					if (element!=null)
						element.set(Boolean.valueOf(in.nextBoolean()));
				}
			}
			in.endObject();
			return channel;
		}

	}
}
