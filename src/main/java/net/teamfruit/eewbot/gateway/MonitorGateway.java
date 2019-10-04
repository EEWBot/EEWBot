package net.teamfruit.eewbot.gateway;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.xml.ws.http.HTTPException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.entity.EEW;
import net.teamfruit.eewbot.entity.Monitor;

public abstract class MonitorGateway implements Gateway<Monitor> {

	public static final String REMOTE = "http://www.kmoni.bosai.go.jp/new/data/map_img/";
	public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final TimeProvider time;
	private final EEW relationEEW;

	public MonitorGateway(final TimeProvider time, final EEW eew) {
		this.time = time;
		this.relationEEW = eew;
	}

	public MonitorGateway(final TimeProvider time) {
		this(time, null);
	}

	@Override
	public void run() {
		try {
			final ZonedDateTime date = this.time.offset(-1000);
			final List<BufferedImage> images = new ArrayList<>();

			final String dateStr = FORMAT.format(date);
			final String dayStr = StringUtils.substring(dateStr, 0, 8);
			Stream.of(REMOTE+"RealTimeImg/acmap_s/"+dayStr+"/"+dateStr+".acmap_s.gif",
					REMOTE+"PSWaveImg/eew/"+dayStr+"/"+dateStr+".eew.gif").forEach(str -> {
						for (int i = 0; i<3; i++) {
							final HttpGet get = new HttpGet(str);
							try {
								try (CloseableHttpResponse response = EEWBot.instance.getHttpClient().execute(get)) {
									final StatusLine statusLine = response.getStatusLine();
									if (statusLine.getStatusCode()==HttpStatus.SC_OK) {
										images.add(ImageIO.read(response.getEntity().getContent()));
										break;
									} else
										throw new HTTPException(response.getStatusLine().getStatusCode());
								}
							} catch (final Exception e) {
								onError(e);
							}
						}
					});

			final BufferedImage base = ImageIO.read(MonitorGateway.class.getResource("/base_map_w.png"));
			final Graphics2D graphics = base.createGraphics();
			images.stream().forEach(image -> graphics.drawImage(image, 0, 0, null));
			graphics.drawImage(ImageIO.read(MonitorGateway.class.getResource("/nied_acmap_s_w_scale.gif")), 305, 99, null);
			graphics.dispose();
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				ImageIO.write(base, "png", baos);
				onNewData(new Monitor(baos.toByteArray(), this.relationEEW));
			}
		} catch (final Exception e) {
			onError(e);
		}
	}
}
