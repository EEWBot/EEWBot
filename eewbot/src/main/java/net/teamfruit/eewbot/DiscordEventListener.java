package net.teamfruit.eewbot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.commons.net.ntp.TimeStamp;

import net.teamfruit.eewbot.dispatcher.EEWDispatcher;
import net.teamfruit.eewbot.dispatcher.MonitorDispatcher;
import net.teamfruit.eewbot.dispatcher.NTPDispatcher;
import net.teamfruit.eewbot.dispatcher.QuakeInfoDispather;
import net.teamfruit.eewbot.node.EEW;
import net.teamfruit.eewbot.node.Embeddable;
import net.teamfruit.eewbot.node.QuakeInfo;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.GuildLeaveEvent;
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
					if (!EEWBot.instance.getConfig().isEnablePermission()||BotUtils.userHasPermission(e.getAuthor().getLongID(), command))
						command.onCommand(e, ArrayUtils.subarray(args, 2, args.length+1));
					else
						BotUtils.reply(e, "権限がありません！");
				else
					BotUtils.reply(e, "コマンドが存在しません\nコマンド一覧は`help`コマンドで確認出来ます");
			}
		}
	}

	@EventSubscriber
	public void onChannelDelete(final ChannelDeleteEvent e) {
		final CopyOnWriteArrayList<Channel> channels = EEWBot.instance.getChannels().get(e.getGuild().getLongID());
		if (channels!=null) {
			final long id = e.getChannel().getLongID();
			if (channels.removeIf(channel -> channel.getId()==id))
				try {
					EEWBot.instance.saveConfigs();
				} catch (final ConfigException ex) {
					EEWBot.LOGGER.error("Error on channel delete", ex);
				}
		}
	}

	@EventSubscriber
	public void onServerDelete(final GuildLeaveEvent e) {
		if (EEWBot.instance.getChannels().remove(e.getGuild().getLongID())!=null)
			try {
				EEWBot.instance.saveConfigs();
			} catch (final ConfigException ex) {
				EEWBot.LOGGER.error("Error on guild delete", ex);
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
				if (channel==null) {
					channel = new Channel(channelid);
					channels.add(channel);
					EEWBot.instance.getChannels().put(serverid, channels);
					try {
						EEWBot.instance.saveConfigs();
					} catch (final ConfigException ex) {
						BotUtils.reply(e, "ConfigException");
						EEWBot.LOGGER.error("Save error", ex);
					}
					BotUtils.reply(e, "チャンネルを設定しました！");
					BotUtils.reply(e, getHelp());
				} else
					BotUtils.reply(e, "このチャンネルには既に設定が存在します！");
			}

			@Override
			public String getHelp() {
				return "コマンドを実行したチャンネルをBotのメッセージ送信先に設定します。\n"+"初期状態では以下の設定になります。```"+Channel.DEFAULT+"```送信するイベントを追加するには`add`, 消去するには`remove`, チャンネルの設定を消去するには`unregister`を使用してください。";
			}
		},
		add {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				if (args.length<=0)
					BotUtils.reply(e, "引数が不足しています");
				else {
					final Channel channel = BotUtils.getChannel(e.getGuild().getLongID(), e.getChannel().getLongID());
					if (channel!=null) {
						final Field field = Arrays.stream(Channel.class.getFields()).filter(f -> !Modifier.isStatic(f.getModifiers())&&(f.getName().equalsIgnoreCase(args[0])||args[0].equals("*"))).findAny().orElse(null);
						if (field!=null) {
							try {
								field.setBoolean(channel, true);
								EEWBot.instance.saveConfigs();
								BotUtils.reply(e, ":ok:");
							} catch (IllegalArgumentException|IllegalAccessException ex) {
								BotUtils.reply(e, "エラが発生しました");
								EEWBot.LOGGER.error("Reflection error", ex);
							} catch (final ConfigException ex) {
								BotUtils.reply(e, "ConfigException");
								EEWBot.LOGGER.error("Save error", ex);
							}
						} else {
							BotUtils.reply(e, args[0]+"という項目は存在しません！");
							return;
						}
					} else
						BotUtils.reply(e, "このチャンネルには設定が存在しません！");
				}
			}

			@Override
			public String getHelp() {
				return "以下の項目が利用できます```"+Arrays.stream(Channel.class.getFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getName).collect(Collectors.joining(" ")).toString()+"```";
			}
		},
		remove {
			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				if (args.length<=0)
					BotUtils.reply(e, "引数が不足しています");
				else {
					final Channel channel = BotUtils.getChannel(e.getGuild().getLongID(), e.getChannel().getLongID());
					if (channel!=null) {
						final Field field = Arrays.stream(Channel.class.getFields()).filter(f -> !Modifier.isStatic(f.getModifiers())&&(f.getName().equalsIgnoreCase(args[0])||args[0].equals("*"))).findAny().orElse(null);
						if (field!=null) {
							try {
								field.setBoolean(channel, false);
								EEWBot.instance.saveConfigs();
								BotUtils.reply(e, ":ok:");
							} catch (IllegalArgumentException|IllegalAccessException ex) {
								BotUtils.reply(e, "エラが発生しました");
								EEWBot.LOGGER.error("Reflection error", ex);
							} catch (final ConfigException ex) {
								BotUtils.reply(e, "ConfigException");
								EEWBot.LOGGER.error("Save error", ex);
							}
						} else {
							BotUtils.reply(e, args[0]+"という項目は存在しません！");
							return;
						}
					} else
						BotUtils.reply(e, "このチャンネルには設定が存在しません！");
				}
			}

			@Override
			public String getHelp() {
				return "以下の項目が利用できます```"+Arrays.stream(Channel.class.getFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getName).collect(Collectors.joining(" ")).toString()+"```";
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
					BotUtils.reply(e, "このチャンネルには設定がありません！");
			}

			@Override
			public String getHelp() {
				return "コマンドを実行したチャンネルをBotのメッセージ送信先から除外します。\n"+"送信するイベントを消去するには`remove`を使用してください。";
			}

		},
		details {

			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				final Channel channel = BotUtils.getChannel(e.getGuild().getLongID(), e.getChannel().getLongID());
				if (channel!=null)
					BotUtils.reply(e, channel.toString());
				else
					BotUtils.reply(e, "このチャンネルには設定がありません！");
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
		joinserver {

			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				BotUtils.reply(e, "https://discordapp.com/oauth2/authorize?client_id="+EEWBot.instance.getClient().getApplicationClientID()+"&scope=bot");
			}

		},
		quakeinfo {

			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				EEWBot.instance.getExecutor().execute(() -> {
					String remote = null;
					if (args.length<=0)
						remote = QuakeInfoDispather.REMOTE;
					else if (args[0].startsWith("http://")||args[0].startsWith("https://"))
						remote = args[0];
					else
						BotUtils.reply(e, "URLが不正です");

					try {
						final QuakeInfo info = QuakeInfoDispather.get(remote);
						e.getChannel().sendMessage(info.buildEmbed());
						info.getDetails().forEach(detail -> BotUtils.reply(e, detail.buildEmbed()));
					} catch (final Exception ex) {
						EEWBot.LOGGER.info(ExceptionUtils.getStackTrace(ex));
						BotUtils.reply(e, "```"+ex.getClass().getSimpleName()+"```");
					}
				});
			}

		},
		test {

			@Override
			public void onCommand(final MessageReceivedEvent e, final String[] args) {
				if (args.length<=0)
					BotUtils.reply(e, "引数が不足しています");
				else {
					EEWBot.instance.getExecutor().execute(() -> {
						Embeddable embeddable = null;
						try {
							if (args[0].startsWith("http://")||args[0].startsWith("https://"))
								embeddable = EEWDispatcher.get(args[0]);
							else
								embeddable = EEWBot.GSON.fromJson(String.join(" ", args), EEW.class);
							BotUtils.reply(e, "**これはテストです！**", embeddable.buildEmbed());
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
				if (args.length<=0)
					BotUtils.reply(e, "```"+Arrays.stream(Command.values()).filter(command -> command!=Command.help).map(Command::name).collect(Collectors.joining(" "))+"```"+"EEWを通知したいチャンネルで`register`コマンドを使用してチャンネルを設定出来ます。");
				else {
					final Command command = EnumUtils.getEnum(Command.class, args[0]);
					if (command!=null) {
						final String help = command.getHelp();
						if (help!=null)
							BotUtils.reply(e, help);
						else
							BotUtils.reply(e, "ヘルプが存在しません");
					} else
						BotUtils.reply(e, "コマンドが存在しません");
				}
			}
		};

		public static final FastDateFormat FORMAT = FastDateFormat.getInstance("yyyy/MM/dd HH:mm:ss.SSS");

		public abstract void onCommand(MessageReceivedEvent e, String[] args);

		public String getHelp() {
			return null;
		}
	}

}
