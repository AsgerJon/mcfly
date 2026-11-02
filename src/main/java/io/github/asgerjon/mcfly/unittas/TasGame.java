package io.github.asgerjon.mcfly.unittas;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The game side of a unitTAS run: makes the test world from the main menu, starts the runner once the player stands in
 * it, runs the test thread's steps on the client and server threads, counts their ticks, keeps the chat the client
 * receives, and stops a test that runs out of time.
 */
public final class TasGame {

	static final String WORLD_FOLDER = "unittas";
	/** Bedrock, two dirt, grass: the surface is y 3, a player stands at y 4. */
	static final String FLAT_LAYERS = "2;7,2x3,2;1;";
	static final long SEED = 20261029L;
	private static final int SETTLE_TICKS = 40;
	private static final int WORLD_TIMEOUT_TICKS = 20 * 180;
	private static final int LOGIN_TIMEOUT_TICKS = 20 * 30;
	private static final int STEP_TIMEOUT_SECONDS = 15;

	/** A step that didn't come back, or a test that ran out of time. */
	static final class Timeout extends RuntimeException {
		Timeout(String message) {
			super(message);
		}
	}

	/** One line of chat as the client got it: with formatting codes, and without. */
	static final class ChatLine {
		final String formatted;
		final String plain;

		ChatLine(String formatted, String plain) {
			this.formatted = formatted;
			this.plain = plain;
		}
	}

	private enum Stage {MENU, LOADING, RUNNING, DONE}

	private final UnitTas.Request request;
	private final Queue<Runnable> clientSteps = new ConcurrentLinkedQueue<>();
	private final Queue<Runnable> serverSteps = new ConcurrentLinkedQueue<>();
	private final Object tickLock = new Object();
	private final List<ChatLine> chat = new ArrayList<>();
	private volatile long clientTicks;
	private volatile long serverTicks;
	private Stage stage = Stage.MENU;
	private int stageTicks;
	private int settledTicks;
	private Thread runnerThread;
	/** When the running test must be done by, in client ticks; 0 between tests. */
	private volatile long deadline;
	private boolean pauseOnLostFocus;
	private EnumDifficulty difficulty;

	TasGame(UnitTas.Request request) {
		this.request = request;
	}

	UnitTas.Request request() {
		return request;
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.phase == TickEvent.Phase.START) {
			ignoreRealInput(mc);
			return;
		}
		stageTicks++;
		switch (stage) {
			case MENU:
				if (mc.currentScreen instanceof GuiMainMenu) {
					stage = Stage.LOADING;
					stageTicks = 0;
					request.progress("making the test world");
					createWorld(mc);
				} else if (stageTicks > WORLD_TIMEOUT_TICKS) {
					// another screen stands in front of the main menu (a mod's notice, say)
					request.progress("the main menu never showed (the screen is " + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName()) + ")");
					finish(mc);
				}
				break;
			case LOADING:
				if (mc.thePlayer == null || mc.theWorld == null || serverPlayerOrNull() == null) {
					settledTicks = 0;
					if (mc.theWorld == null && stageTicks > LOGIN_TIMEOUT_TICKS) {
						// the client ticks again once the server is up, and the login takes a second; 1.7.10's stalls
						// now and then, without a word, and ./unittas.sh starts the game again
						request.progress("the test world didn't load (the player never logged in)");
						finish(mc);
					} else if (stageTicks > WORLD_TIMEOUT_TICKS) {
						request.progress("the test world didn't load");
						finish(mc);
					}
				} else if (++settledTicks >= SETTLE_TICKS) {
					// the chunks around the player are in by now
					stage = Stage.RUNNING;
					runnerThread = new Thread(new TasRunner(this), "unitTAS");
					runnerThread.setDaemon(true);
					runnerThread.start();
				}
				break;
			case RUNNING:
				if (deadline > 0 && clientTicks > deadline && runnerThread != null) {
					deadline = 0;
					runnerThread.interrupt();
				}
				break;
			default:
				break;
		}
		runAll(clientSteps);
		synchronized (tickLock) {
			clientTicks++;
			tickLock.notifyAll();
		}
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		runAll(serverSteps);
		synchronized (tickLock) {
			serverTicks++;
			tickLock.notifyAll();
		}
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		if (event.message != null) {
			synchronized (chat) {
				chat.add(new ChatLine(event.message.getFormattedText(), event.message.getUnformattedText()));
			}
		}
	}

	/**
	 * The tests are the only input: the real mouse and keyboard events go before the game reads them (this runs at the
	 * start of the tick, ahead of the screens and the game), so the window works without focus and a stray click
	 * doesn't disturb a test. The game counts as focused while no screen is open, as holding a key to mine needs.
	 */
	private void ignoreRealInput(Minecraft mc) {
		if (stage == Stage.DONE) {
			return;
		}
		while (Mouse.next()) {
			// dropped
		}
		while (Keyboard.next()) {
			// dropped
		}
		if (mc.theWorld != null && mc.currentScreen == null) {
			mc.inGameHasFocus = true;
		}
	}

	private static void runAll(Queue<Runnable> steps) {
		Runnable step;
		while ((step = steps.poll()) != null) {
			step.run();
		}
	}

	/** A fresh world each run: the old test world goes first (only that folder), then creative, cheats, flat, a fixed seed. */
	private void createWorld(Minecraft mc) {
		pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
		difficulty = mc.gameSettings.difficulty;
		mc.gameSettings.pauseOnLostFocus = false;
		mc.gameSettings.difficulty = EnumDifficulty.EASY;
		ISaveFormat saves = mc.getSaveLoader();
		saves.flushCache();
		saves.deleteWorldDirectory(WORLD_FOLDER);
		WorldSettings settings = new WorldSettings(SEED, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT).enableCommands();
		settings.func_82750_a(FLAT_LAYERS);
		mc.launchIntegratedServer(WORLD_FOLDER, "unitTAS", settings);
	}

	/** Puts the player's settings back and quits the game (on the client thread). */
	void finish(Minecraft mc) {
		stage = Stage.DONE;
		UnitTasMode.running = false;
		mc.gameSettings.pauseOnLostFocus = pauseOnLostFocus;
		if (difficulty != null) {
			mc.gameSettings.difficulty = difficulty;
		}
		mc.shutdown();
	}

	/** The test's time limit starts now. */
	void startClock(int seconds) {
		deadline = clientTicks + seconds * 20L;
	}

	void stopClock() {
		deadline = 0;
	}

	/** Runs a step on the client thread at its next tick and returns its result. */
	<T> T client(Callable<T> step) {
		return await(clientSteps, step, "the client");
	}

	/** Runs a step on the server thread at its next tick and returns its result. */
	<T> T server(Callable<T> step) {
		return await(serverSteps, step, "the server");
	}

	private static <T> T await(Queue<Runnable> steps, Callable<T> step, String side) {
		FutureTask<T> task = new FutureTask<>(step);
		steps.add(task);
		try {
			return task.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new RuntimeException(cause);
		} catch (TimeoutException e) {
			task.cancel(false);
			throw new Timeout(side + " didn't run a step within " + STEP_TIMEOUT_SECONDS + " s (paused or stuck?)");
		} catch (InterruptedException e) {
			task.cancel(false);
			throw new Timeout("the test ran out of time");
		}
	}

	void clientTicks(int ticks) {
		waitTicks(ticks, true);
	}

	void serverTicks(int ticks) {
		waitTicks(ticks, false);
	}

	private void waitTicks(int ticks, boolean client) {
		long target = (client ? clientTicks : serverTicks) + ticks;
		long giveUp = System.currentTimeMillis() + ticks * 50L + STEP_TIMEOUT_SECONDS * 1000L;
		synchronized (tickLock) {
			while ((client ? clientTicks : serverTicks) < target) {
				long left = giveUp - System.currentTimeMillis();
				if (left <= 0) {
					throw new Timeout((client ? "the client" : "the server") + " stopped ticking (paused or stuck?)");
				}
				try {
					tickLock.wait(left);
				} catch (InterruptedException e) {
					throw new Timeout("the test ran out of time");
				}
			}
		}
	}

	long clientTickCount() {
		return clientTicks;
	}

	/** Where the chat stands now; {@link #chatSince} gives what came after. */
	int chatMark() {
		synchronized (chat) {
			return chat.size();
		}
	}

	List<ChatLine> chatSince(int mark) {
		synchronized (chat) {
			return new ArrayList<>(chat.subList(Math.min(mark, chat.size()), chat.size()));
		}
	}

	/** The one player, on the server side; call on the server thread. */
	static EntityPlayerMP serverPlayer() {
		EntityPlayerMP player = serverPlayerOrNull();
		if (player == null) {
			throw new IllegalStateException("No player on the server");
		}
		return player;
	}

	private static EntityPlayerMP serverPlayerOrNull() {
		MinecraftServer server = MinecraftServer.getServer();
		if (server == null || server.getConfigurationManager() == null || server.getConfigurationManager().playerEntityList.isEmpty()) {
			return null;
		}
		return (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
	}
}
