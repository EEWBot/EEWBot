package net.teamfruit.eewbot;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher;
import net.teamfruit.eewbot.dispatcher.EEWDispatcher.EEW;
import net.teamfruit.eewbot.dispatcher.MonitorDispatcher;
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
		final CopyOnWriteArrayList<Channel> channels = EEWBot.instance.getChannels().get(e.getGuild().getLongID());
		if (channels!=null) {
			final long id = e.getChannel().getLongID();
			channels.removeIf(channel -> channel.getId()==id);
			try {
				EEWBot.instance.saveConfigs();
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
				CopyOnWriteArrayList<Channel> channels = EEWBot.instance.getChannels().get(serverid);
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
				EEWBot.instance.getChannels().put(serverid, channels);
				try {
					EEWBot.instance.saveConfigs();
				} catch (final ConfigException ex) {
					BotUtils.reply(e, "ConfigException");
					EEWBot.LOGGER.error("Save error", ex);
				}
				BotUtils.reply(e, ":ok:");
			}
		},
		unregister {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				final long serverid = e.getGuild().getLongID();
				final long channelid = e.getChannel().getLongID();
				final CopyOnWriteArrayList<Channel> channels = EEWBot.instance.getChannels().get(serverid);
				if (channels.removeIf(channel -> channel.getId()==channelid)) {
					try {
						EEWBot.instance.saveConfigs();
						BotUtils.reply(e, ":ok:");
					} catch (final ConfigException ex) {
						EEWBot.LOGGER.error("Error on channel delete", ex);
						BotUtils.reply(e, "設定のセーブに失敗しました");
					}
				} else
					BotUtils.reply(e, "このチャンネルには設定がありません");
			}
		},
		restart {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				EEWBot.instance.getExecutor().shutdown();
				EEWBot.instance.getClient().logout();
				try {
					EEWBot.instance = new EEWBot();
					System.gc();
					BotUtils.reply(e, ":ok:");
				} catch (final Throwable t) {
					EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(t));
					System.exit(1);
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
				BotUtils.reply(e, "https://discordapp.com/oauth2/authorize?client_id="+EEWBot.instance.getClient().getApplicationClientID()+"&scope=bot");
			}
		},
		test {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				if (args.length<=0) {
					BotUtils.reply(e, "引数が不足しています");
				} else {
					EEWBot.instance.getExecutor().execute(() -> {
						EEW eew = null;
						try {
							if (args[0].startsWith("http://"))
								eew = EEWDispatcher.get(args[0]);
							else
								eew = EEWBot.GSON.fromJson(String.join(" ", args), EEW.class);
							BotUtils.reply(e, "**これは訓練です！**", EEWEventListener.buildEmbed(eew));
						} catch (final Exception ex) {
							EEWBot.LOGGER.info(ExceptionUtils.getStackTrace(ex));
							BotUtils.reply(e, "```"+ex.getClass().getSimpleName()+"```");
						}
					});
				}
			}
		},
		getmonitor {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				EEWBot.instance.getExecutor().execute(() -> {
					try {
						e.getChannel().sendFile("", MonitorDispatcher.get(), "kyoshinmonitor.png");
					} catch (final Exception ex) {
						EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(ex));
					}
				});
			}
		},
		help {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				BotUtils.reply(e, "```"+Arrays.stream(Command.values()).filter(command -> command!=Command.help).map(command -> command.name()).collect(Collectors.joining(" "))+"```"+"EEWを通知したいチャンネルでregisterコマンドを使用してチャンネルを設定出来ます。");
			}
		};

		public abstract void onCommand(MessageReceivedEvent e, String[] args);
	}

}
