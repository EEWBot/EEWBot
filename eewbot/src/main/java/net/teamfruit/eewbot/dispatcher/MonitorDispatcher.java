package net.teamfruit.eewbot.dispatcher;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import net.teamfruit.eewbot.EEWBot;

public class MonitorDispatcher implements Runnable {

	public static final String REMOTE = "http://www.kmoni.bosai.go.jp/new/data/map_img/";
	public static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

	@Override
	public void run() {
		try {
			EEWBot.client.getDispatcher().dispatch(new MonitorEvent(EEWBot.client, get()));
		} catch (final IOException e) {
			EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static InputStream get() throws IOException {
		final Date date = new Date(System.currentTimeMillis()+EEWBot.ntp.getOffset()-TimeUnit.SECONDS.toMillis(1));
		final List<BufferedImage> images = new ArrayList<>();

		final String dateStr = FORMAT.format(date);
		final String dayStr = StringUtils.substring(dateStr, 0, 8);
		Stream.of(REMOTE+"EstShindoImg/eew/"+dayStr+"/"+dateStr+".eew.gif",
				REMOTE+"RealTimeImg/jma_s/"+dayStr+"/"+dateStr+".jma_s.gif",
				REMOTE+"PSWaveImg/eew/"+dayStr+"/"+dateStr+".eew.gif").forEach(str -> {
					try {
						final URL url = new URL(str);
						try (InputStream is = new BufferedInputStream(url.openStream())) {
							images.add(ImageIO.read(is));
						}
					} catch (final IOException e) {
						EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(e));
					}
				});

		final BufferedImage base = ImageIO.read(MonitorDispatcher.class.getResource("/base_map_w.gif"));
		images.stream().forEach(image -> {
			final Graphics2D g = base.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
		});
		final Graphics2D g = base.createGraphics();
		g.drawImage(ImageIO.read(MonitorDispatcher.class.getResource("/nied_jma_s_w_scale.gif")), 305, 99, null);
		g.dispose();
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ImageIO.write(base, "png", baos);
			return new ByteArrayInputStream(baos.toByteArray());
		}
	}
}
