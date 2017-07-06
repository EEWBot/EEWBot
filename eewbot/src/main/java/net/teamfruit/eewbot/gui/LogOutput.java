package net.teamfruit.eewbot.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.JTextArea;

public class LogOutput extends OutputStream {

	private final JTextArea area;
	private final ByteArrayOutputStream baos;

	public LogOutput(final JTextArea area) {
		this.area = area;
		this.baos = new ByteArrayOutputStream();
	}

	@Override
	public void write(final int b) throws IOException {
		this.baos.write(b);
	}

	@Override
	public void flush() throws IOException {
		this.area.append(this.baos.toString("UTF-8"));
		this.baos.reset();
	}

}
