package net.teamfruit.eewbot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.commons.net.ntp.TimeStamp;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher;
import net.teamfruit.eewbot.dispatcher.EEWDispatcher.EEW;
import net.teamfruit.eewbot.dispatcher.MonitorDispatcher;
import net.teamfruit.eewbot.dispatcher.NTPDispatcher;
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

			@Override
			public String getHelp() {
				return super.getHelp();
			}
		},
		add {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				if (args.length<=0)
					BotUtils.reply(e, "引数が不足しています");
				else {
					final Channel channel = BotUtils.getChannel(e.getGuild().getLongID(), e.getChannel().getLongID());
					final Field[] fields = Channel.class.getFields();
					Arrays.stream(args).forEach(str -> {
						Arrays.stream(Channel.class.getFields()).forEach(field -> {
							if (field.getName().equalsIgnoreCase(str)||str.equals("*"))
								try {
									field.setBoolean(channel, true);
								} catch (IllegalArgumentException|IllegalAccessException ex) {
									BotUtils.reply(e, "エラが発生しました");
									EEWBot.LOGGER.error("Reflection error", ex);
								}

						});
					});
				}
			}
		},
		remove {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				if (args.length<=0)
					BotUtils.reply(e, "引数が不足しています");
				else {
					final Channel channel = BotUtils.getChannel(e.getGuild().getLongID(), e.getChannel().getLongID());
					final Field[] fields = Channel.class.getFields();
					Arrays.stream(args).forEach(str -> {
						Arrays.stream(Channel.class.getFields()).forEach(field -> {
							if (field.getName().equalsIgnoreCase(str)||str.equals("*"))
								try {
									field.setBoolean(channel, false);
								} catch (IllegalArgumentException|IllegalAccessException ex) {
									BotUtils.reply(e, "エラが発生しました");
									EEWBot.LOGGER.error("Reflection error", ex);
								}

						});
					});
				}
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
		reload {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				try {
					EEWBot.instance.loadConfigs();
					BotUtils.reply(e, ":ok:");
				} catch (final ConfigException ex) {
					EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(ex));
					BotUtils.reply(e, ":warning: エラーが発生しました");
				}
			}
		},
		//		bsc24 {
		//			@Override
		//			public void onCommand(final MessageReceivedEvent e, final String[] args) {
		//				BotUtils.reply(e, "http://ch.nicovideo.jp/bousai-share");
		//			}
		//		},
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
		monitor {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				EEWBot.instance.getExecutor().execute(() -> {
					try {
						e.getChannel().sendFile("", MonitorDispatcher.get(), "kyoshinmonitor.png");
					} catch (final Exception ex) {
						EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(ex));
						BotUtils.reply(e, ":warning: エラーが発生しました");
					}
				});
			}
		},
		timefix {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				EEWBot.instance.getExecutor().execute(() -> {
					try {
						final StringBuilder sb = new StringBuilder();
						final TimeInfo info = NTPDispatcher.get();
						info.computeDetails();
						final NtpV3Packet message = info.getMessage();
						final TimeStamp origNtpTime = message.getOriginateTimeStamp();
						sb.append("コンピューターの時刻: `").append(origNtpTime.toDateString()).append("`\n");
						final TimeStamp refNtpTime = message.getReferenceTimeStamp();
						sb.append("サーバーからの時刻: `").append(refNtpTime.toDateString()).append("`\n");
						final long offset = NTPDispatcher.getOffset(info);
						sb.append("オフセット: `").append(offset).append("ms`");
						BotUtils.reply(e, sb.toString());
						NTPDispatcher.INSTANCE.setOffset(offset);
					} catch (final IOException ex) {
						EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(ex));
						BotUtils.reply(e, ":warning: エラーが発生しました");
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

		public static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

		public abstract void onCommand(MessageReceivedEvent e, String[] args);

		public String getHelp() {
			return null;
		}
	}

}
