package net.teamfruit.eewbot.registry;

import java.util.Arrays;

public class Channel {

	@CommandName("EEW警報")
	public boolean eewAlert = true;

	@CommandName("EEW予報")
	public boolean eewPrediction = true;

	@CommandName("EEW間引き")
	public boolean eewDecimation = false;

	@CommandName("地震情報")
	public boolean quakeInfo = true;

	@CommandName("詳細地震情報")
	public boolean quakeInfoDetail = false;

	@CommandName("強震モニタ")
	public boolean monitor = true;

	public Channel() {
	}

	public Channel(final boolean eewAlert, final boolean eewPrediction, final boolean eewDecimation, final boolean quakeInfo, final boolean quakeInfoDetail, final boolean monitor) {
		this.eewAlert = eewAlert;
		this.eewPrediction = eewPrediction;
		this.eewDecimation = eewDecimation;
		this.quakeInfo = quakeInfo;
		this.quakeInfoDetail = quakeInfoDetail;
		this.monitor = monitor;
	}

	public boolean value(final String name) {
		return Arrays.stream(getClass().getFields())
				.filter(field -> field.isAnnotationPresent(CommandName.class)&&field.getAnnotation(CommandName.class).value().equals(name))
				.map(field -> {
					try {
						return field.getBoolean(this);
					} catch (IllegalArgumentException|IllegalAccessException e) {
						throw new RuntimeException(e);
					}
				})
				.findAny().orElseThrow(() -> new IllegalArgumentException());
	}

	public static Channel fromOldChannel(final OldChannel old) {
		return new Channel(old.eewAlert.get(),
				old.eewPrediction.get(),
				old.eewDecimation.get(),
				old.quakeInfo.get(),
				old.quakeInfoDetail.get(),
				old.monitor.get());
	}
}
