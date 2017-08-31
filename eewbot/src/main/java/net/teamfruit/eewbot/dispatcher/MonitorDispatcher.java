package net.teamfruit.eewbot.dispatcher;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.event.MonitorEvent;

public class MonitorDispatcher implements Runnable {

	public static final MonitorDispatcher INSTANCE = new MonitorDispatcher();

	public static final String REMOTE = "http://www.kmoni.bosai.go.jp/new/data/map_img/";
	public static final FastDateFormat FORMAT = FastDateFormat.getInstance("yyyyMMddHHmmss");

	private MonitorDispatcher() {
	}

	@Override
	public void run() {
		try {
			EEWBot.instance.getClient().getDispatcher().dispatch(new MonitorEvent(EEWBot.instance.getClient(), get()));
		} catch (final IOException e) {
			EEWBot.LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static InputStream get() throws IOException {
		final Date date = new Date(System.currentTimeMillis()+NTPDispatcher.INSTANCE.getOffset()-TimeUnit.SECONDS.toMillis(1));
		final List<BufferedImage> images = new ArrayList<>();

		final String dateStr = FORMAT.format(date);
		final String dayStr = StringUtils.substring(dateStr, 0, 8);
		Stream.of(/*REMOTE+"EstShindoImg/eew/"+dayStr+"/"+dateStr+".eew.gif",*/
				REMOTE+"RealTimeImg/acmap_s/"+dayStr+"/"+dateStr+".acmap_s.gif",
				REMOTE+"PSWaveImg/eew/"+dayStr+"/"+dateStr+".eew.gif").forEach(str -> {
					final RequestConfig config = RequestConfig.custom()
							.setConnectTimeout(10000)
							.setSocketTimeout(10000)
							.build();
					final HttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
					final HttpGet get = new HttpGet(str);
					try {
						final HttpResponse response = client.execute(get);
						final StatusLine statusLine = response.getStatusLine();
						if (statusLine.getStatusCode()==HttpStatus.SC_OK)
							images.add(ImageIO.read(response.getEntity().getContent()));
						else if (statusLine.getStatusCode()==HttpStatus.SC_NOT_FOUND) {
							final TimeInfo info = NTPDispatcher.get();
							info.computeDetails();
							final long offset = NTPDispatcher.getOffset(info);
							NTPDispatcher.INSTANCE.setOffset(offset);
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
		g.drawImage(ImageIO.read(MonitorDispatcher.class.getResource("/nied_acmap_s_w_scale.gif")), 305, 99, null);
		g.dispose();
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ImageIO.write(base, "png", baos);
			return new ByteArrayInputStream(baos.toByteArray());
		}
	}
}
