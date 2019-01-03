package net.teamfruit.eewbot.event;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.Event;

public abstract class EEWBotEvent<E> extends Event {

	protected E element;

	public EEWBotEvent(final IDiscordClient client, final E element) {
		this.element = element;
	}

	public E getElement() {
		return this.element;
	}
}
