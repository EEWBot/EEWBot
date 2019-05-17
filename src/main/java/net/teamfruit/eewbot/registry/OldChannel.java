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

@Deprecated
public class OldChannel {

	public final long id;
	public final ChannelElement eewAlert = new ChannelElement("EEW警報", true);
	public final ChannelElement eewPrediction = new ChannelElement("EEW予報", false);
	public final ChannelElement eewDecimation = new ChannelElement("EEW間引きモード", false);
	public final ChannelElement quakeInfo = new ChannelElement("地震情報", true);
	public final ChannelElement quakeInfoDetail = new ChannelElement("詳細地震情報", false);
	public final ChannelElement monitor = new ChannelElement("強震モニタ", true);

	public OldChannel(final long id) {
		this.id = id;
	}

	public ChannelElement getElement(final String str) {
		return Arrays.stream(OldChannel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).filter(f -> {
			try {
				return f.getName().equalsIgnoreCase(str)||((ChannelElement) f.get(this)).name.equals(str);
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}).findAny().map(f -> {
			try {
				return (ChannelElement) f.get(this);
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}).orElse(null);
	}

	@Override
	public String toString() {
		return Arrays.stream(OldChannel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).map(f -> {
			try {
				return f.get(this).toString();
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}).collect(Collectors.joining("\n"));
	}

	@Deprecated
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

	@Deprecated
	public static class ChannelTypeAdapter extends TypeAdapter<OldChannel> {

		@Override
		public void write(final JsonWriter out, final OldChannel value) throws IOException {
			out.beginObject();
			out.name("id").value(value.id);
			Arrays.stream(OldChannel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).forEach(f -> {
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
		public OldChannel read(final JsonReader in) throws IOException {
			final OldChannel channel = new OldChannel(-1);
			final Map<String, ChannelElement> map = Arrays.stream(OldChannel.class.getFields()).filter(f -> f.getType()==ChannelElement.class).collect(Collectors.toMap(f -> f.getName(), f -> {
				try {
					return (ChannelElement) f.get(channel);
				} catch (final IllegalAccessException e) {
					throw new JsonParseException(e);
				}
			}));
			in.beginObject();
			while (in.hasNext()) {
				final String line = in.nextName();
				if (line.equals("id"))
					try {
						final Field field = OldChannel.class.getField("id");
						field.setAccessible(true);
						field.setLong(channel, in.nextLong());
					} catch (NoSuchFieldException|SecurityException|IllegalAccessException e) {
						throw new RuntimeException(e);
					}
				else {
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
