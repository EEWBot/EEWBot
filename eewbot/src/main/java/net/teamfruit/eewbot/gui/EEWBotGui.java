package net.teamfruit.eewbot.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

import net.teamfruit.eewbot.EEWBot;
import sx.blah.discord.Discord4J.Discord4JLogger;

public class EEWBotGui extends JFrame {

	public EEWBotGui() {
		super("EEWBot");

		setSize(900, 580);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		final JTextArea area = new JTextArea();
		area.setEditable(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		final DefaultCaret caret = (DefaultCaret) area.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		final JScrollPane scrollpane = new JScrollPane(area,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollpane.setBorder(new EmptyBorder(0, 0, 0, 0));

		final LogOutput out = new LogOutput(area);
		final PrintStream stream = new PrintStream(out, true);
		System.setOut(stream);
		final Discord4JLogger logger = ((Discord4JLogger) EEWBot.LOGGER);
		logger.setStandardStream(stream);
		logger.setErrorStream(stream);

		getContentPane().add(area, BorderLayout.CENTER);
	}
}
