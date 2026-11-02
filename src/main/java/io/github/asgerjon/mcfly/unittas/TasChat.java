package io.github.asgerjon.mcfly.unittas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

/** The chat screen as a test typed into it: what is in the input, more keys, and Enter. */
public final class TasChat {

	/** Ticks after a key before the screen is read: a Tab completion comes back from the server. */
	private static final int SETTLE_TICKS = 4;

	private static Field inputField;

	private final TasGame game;

	TasChat(TasGame game) {
		this.game = game;
	}

	/** Types more, key by key. */
	public TasChat typeMore(String text) {
		game.client(() -> {
			GuiScreen screen = openChat();
			for (char character : text.toCharArray()) {
				TasTestCase.keyTyped(screen, character, 0);
			}
			return null;
		});
		game.clientTicks(SETTLE_TICKS);
		return this;
	}

	/** Presses a key such as Keyboard.KEY_TAB, KEY_UP, KEY_BACK. */
	public TasChat key(int keyCode) {
		game.client(() -> {
			TasTestCase.keyTyped(openChat(), '\0', keyCode);
			return null;
		});
		game.clientTicks(SETTLE_TICKS);
		return this;
	}

	/** The text in the input. */
	public String text() {
		return game.client(() -> input(openChat()).getText());
	}

	/** Presses Enter: the chat sends the text and closes; returns what came back. */
	public TasCommand enter() {
		String text = text();
		int mark = game.chatMark();
		game.client(() -> {
			TasTestCase.keyTyped(openChat(), '\r', Keyboard.KEY_RETURN);
			return null;
		});
		game.clientTicks(SETTLE_TICKS);
		return new TasCommand(text, -1, game.chatSince(mark));
	}

	public void close() {
		game.client(() -> {
			if (Minecraft.getMinecraft().currentScreen instanceof GuiChat) {
				Minecraft.getMinecraft().displayGuiScreen(null);
			}
			return null;
		});
	}

	private static GuiScreen openChat() {
		GuiScreen screen = Minecraft.getMinecraft().currentScreen;
		if (!(screen instanceof GuiChat)) {
			throw new AssertionError("the chat isn't open (the screen is " + (screen == null ? "the game" : screen.getClass().getSimpleName()) + ")");
		}
		return screen;
	}

	/** GuiChat's input field (dev name first, then the obfuscated one). */
	private static GuiTextField input(GuiScreen chat) throws ReflectiveOperationException {
		if (inputField == null) {
			Field field;
			try {
				field = GuiChat.class.getDeclaredField("inputField");
			} catch (NoSuchFieldException e) {
				field = GuiChat.class.getDeclaredField("field_146415_a");
			}
			field.setAccessible(true);
			inputField = field;
		}
		return (GuiTextField) inputField.get(chat);
	}
}
