package io.github.asgerjon.mcfly.unittas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A command a test ran, its result and what came back in the chat. */
public final class TasCommand {

	private static final String RED = "§c";

	private final String text;
	private final int result;
	private final List<TasGame.ChatLine> chat;

	TasCommand(String text, int result, List<TasGame.ChatLine> chat) {
		this.text = text;
		this.result = result;
		this.chat = chat;
	}

	public String text() {
		return text;
	}

	/**
	 * What the command returned: how many times it ran (once per player a selector picked), 0 when it failed; -1 for
	 * one typed into the chat.
	 */
	public int result() {
		return result;
	}

	/** The chat lines, without formatting. */
	public List<String> lines() {
		List<String> lines = new ArrayList<>();
		for (TasGame.ChatLine line : chat) {
			lines.add(line.plain);
		}
		return Collections.unmodifiableList(lines);
	}

	/** The red lines: the errors. */
	public List<String> errors() {
		List<String> errors = new ArrayList<>();
		for (TasGame.ChatLine line : chat) {
			if (line.formatted.startsWith(RED)) {
				errors.add(line.plain);
			}
		}
		return errors;
	}

	@Override
	public String toString() {
		return text + " -> " + lines();
	}
}
