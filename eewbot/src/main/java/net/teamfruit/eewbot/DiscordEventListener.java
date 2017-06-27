package net.teamfruit.eewbot;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher.EEW;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.ChannelDeleteEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

public class DiscordEventListener {

	@EventSubscriber
	public void onMessageReceived(final MessageReceivedEvent e) {
		final String msg = e.getMessage().getContent();
		if (msg.startsWith("!eew")) {
			final String[] args = msg.split(" ");
			if (args.length<=1)
				BotUtils.reply(e, "引数が不足しています！");
			else {
				final Command command = EnumUtils.getEnum(Command.class, args[1]);
				if (command!=null)
					command.onCommand(e, ArrayUtils.subarray(args, 2, args.length+1));
			}
		}
	}

	@EventSubscriber
	public void onChannelDelete(final ChannelDeleteEvent e) {
		final CopyOnWriteArrayList<Channel> channels = EEWBot.channels.get(e.getGuild().getLongID());
		if (channels!=null) {
			final long id = e.getChannel().getLongID();
			for (final Iterator<Channel> it = channels.iterator(); it.hasNext();)
				if (it.next().getId()==id)
					it.remove();
			try {
				EEWBot.saveConfigs();
			} catch (final ConfigException ex) {
				EEWBot.LOGGER.error("Error on channel delete", ex);
			}
		}
	}

	public static enum Command {
		register {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				final long serverid = e.getGuild().getLongID();
				final long channelid = e.getChannel().getLongID();
				CopyOnWriteArrayList<Channel> channels = EEWBot.channels.get(serverid);
				Channel channel = BotUtils.getChannel(serverid, channelid);
				if (channels==null)
					channels = new CopyOnWriteArrayList<>();
				if (channel==null)
					channel = new Channel(channelid);

				if (args.length<=0)
					channel.eewAlert = true;
				else if (args.length%2!=0)
					BotUtils.reply(e, "引数が不足しています");
				else {
					final Field[] fields = Channel.class.getFields();
					for (int i = 0; i<args.length; i += 2) {
						for (final Field line : fields) {
							if (line.getName().equalsIgnoreCase(args[i])||args[i].equals("*"))
								try {
									line.setBoolean(channel, BooleanUtils.toBoolean(args[i+1]));
								} catch (IllegalArgumentException|IllegalAccessException ex) {
									BotUtils.reply(e, "エラが発生しました");
									EEWBot.LOGGER.error("Reflection error", ex);
								}
						}
					}
				}

				channels.add(channel);
				EEWBot.channels.put(serverid, channels);
				try {
					EEWBot.saveConfigs();
				} catch (final ConfigException ex) {
					BotUtils.reply(e, "ConfigException");
					EEWBot.LOGGER.error("Save error", ex);
				}
				BotUtils.reply(e, "チャンネルを設定しました");
			}
		},
		unregister {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				final CopyOnWriteArrayList<Channel> channels = EEWBot.channels.get(e.getGuild().getLongID());
				if (channels!=null) {
					final long id = e.getChannel().getLongID();
					for (final Iterator<Channel> it = channels.iterator(); it.hasNext();)
						if (it.next().getId()==id)
							it.remove();
					try {
						EEWBot.saveConfigs();
					} catch (final ConfigException ex) {
						EEWBot.LOGGER.error("Error on channel delete", ex);
					}
				} else
					BotUtils.reply(e, "このチャンネルには設定がありません");
			}
		},
		reload {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				try {
					EEWBot.loadConfigs();
					BotUtils.reply(e, ":ok:");
				} catch (final ConfigException ex) {
					BotUtils.reply(e, "エラーが発生しました");
					EEWBot.LOGGER.error("Load error", ex);
				}
			}
		},
		bsc24 {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				BotUtils.reply(e, "http://ch.nicovideo.jp/bousai-share");
			}
		},
		joinserver {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				BotUtils.reply(e, "https://discordapp.com/oauth2/authorize?client_id="+EEWBot.client.getApplicationClientID()+"&scope=bot");
			}
		},
		test {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				if (args.length<=0) {
					BotUtils.reply(e, "引数が不足しています");
				} else {
					EEW eew = null;
					try {
						if (args[0].startsWith("http://"))
							try (InputStreamReader isr = new InputStreamReader(new URL(args[0]).openStream(), StandardCharsets.UTF_8)) {
								eew = EEWBot.GSON.fromJson(isr, EEW.class);
							}
						else
							eew = EEWBot.GSON.fromJson(StringUtils.remove(e.getMessage().getContent(), "!eew test "), EEW.class);
						BotUtils.reply(e, "**これは訓練です！**", EEWEventListener.buildEmbed(eew));
					} catch (final Exception ex) {
						EEWBot.LOGGER.info(ExceptionUtils.getStackTrace(ex));
						BotUtils.reply(e, "```"+ex.getClass().getSimpleName()+"```");
					}
				}
			}
		},
		help {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				BotUtils.reply(e, "```"+Arrays.stream(Command.values()).filter(command -> command!=Command.help).map(command -> command.name()).collect(Collectors.joining(" "))+"```");
			}
		};

		public abstract void onCommand(MessageReceivedEvent e, String[] args);
	}

}
