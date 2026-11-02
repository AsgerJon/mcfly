package io.github.asgerjon.mcfly.unittas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The test thread, as Python's unittest runs a suite: every public no-argument method whose name starts with `test`,
 * in name order, each on a fresh instance with setUp before and tearDown after. Between tests the player and the world
 * are put back (creative, empty inventory, no effects, at the spawn point, no other entities). A failure or error takes
 * a screenshot. At the end: the report, and the game quits.
 */
final class TasRunner implements Runnable {

	/** The spawn point every test starts from, on the flat world's grass. */
	static final double START_X = 0.5;
	static final double START_Y = 4.0;
	static final double START_Z = 0.5;

	private enum Outcome {
		OK("ok"), FAIL("FAIL"), ERROR("ERROR"), SKIP("skipped");

		final String word;

		Outcome(String word) {
			this.word = word;
		}
	}

	private static final class Result {
		final String name;
		Outcome outcome;
		String detail = "";
		double seconds;
		List<TasGame.ChatLine> chat = new ArrayList<>();
		String screenshot;

		Result(String name) {
			this.name = name;
		}
	}

	private final TasGame game;
	private final UnitTas.Request request;
	private final List<Result> results = new ArrayList<>();

	TasRunner(TasGame game) {
		this.game = game;
		this.request = game.request();
	}

	@Override
	public void run() {
		long start = System.currentTimeMillis();
		try {
			prepareWorld();
			List<Method> tests = new ArrayList<>();
			for (String suite : request.suites) {
				List<Class<? extends TasTestCase>> classes = TasSuites.get(suite);
				if (classes == null) {
					request.progress("no suite called '" + suite + "' (there are: " + String.join(", ", TasSuites.names()) + ")");
					continue;
				}
				for (Class<? extends TasTestCase> testClass : classes) {
					tests.addAll(testsOf(testClass));
				}
			}
			int number = 0;
			for (Method test : tests) {
				number++;
				Result result = runOne(test);
				results.add(result);
				request.progress(String.format(Locale.ROOT, "test %d/%d %s ... %s", number, tests.size(), result.name, result.outcome.word));
			}
		} catch (Throwable t) {
			Result broken = new Result("unitTAS");
			broken.outcome = Outcome.ERROR;
			broken.detail = stackTrace(t);
			results.add(broken);
			request.progress("unitTAS itself failed: " + t);
		}
		writeReport((System.currentTimeMillis() - start) / 1000.0);
		try {
			game.client(() -> {
				game.finish(Minecraft.getMinecraft());
				return null;
			});
		} catch (RuntimeException e) {
			UnitTas.LOG.error("Couldn't quit the game", e);
		}
	}

	/** Steady conditions: no day and night, no weather, no mobs spawning on their own, no fire spread. */
	private void prepareWorld() {
		game.server(() -> {
			for (WorldServer world : MinecraftServer.getServer().worldServers) {
				GameRules rules = world.getGameRules();
				rules.setOrCreateGameRule("doDaylightCycle", "false");
				rules.setOrCreateGameRule("doMobSpawning", "false");
				rules.setOrCreateGameRule("doFireTick", "false");
				rules.setOrCreateGameRule("mobGriefing", "false");
				world.setWorldTime(6000);
				world.getWorldInfo().setRaining(false);
				world.getWorldInfo().setThundering(false);
			}
			return null;
		});
	}

	private List<Method> testsOf(Class<? extends TasTestCase> testClass) {
		List<Method> tests = new ArrayList<>();
		for (Method method : testClass.getMethods()) {
			if (method.getName().startsWith("test") && method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers())
				&& (request.filter == null || nameOf(method).contains(request.filter))) {
				tests.add(method);
			}
		}
		tests.sort(Comparator.comparing(Method::getName));
		return tests;
	}

	private static String nameOf(Method test) {
		return test.getDeclaringClass().getSimpleName() + "." + test.getName();
	}

	private Result runOne(Method test) {
		Result result = new Result(nameOf(test));
		int chatMark = game.chatMark();
		long start = System.currentTimeMillis();
		TasTestCase instance = null;
		try {
			instance = (TasTestCase) test.getDeclaringClass().getConstructor().newInstance();
			instance.attach(game);
			reset();
			chatMark = game.chatMark();
			game.startClock(instance.timeoutSeconds());
			instance.setUp();
			try {
				test.invoke(instance);
			} finally {
				instance.tearDown();
			}
			// a sound the game doesn't have plays nothing, without a word: one the test caused fails it
			synchronized (UnitTasMode.SOUNDS_UNKNOWN) {
				if (!UnitTasMode.SOUNDS_UNKNOWN.isEmpty()) {
					throw new AssertionError("played sounds the game doesn't have (silent): " + new ArrayList<>(UnitTasMode.SOUNDS_UNKNOWN_FROM));
				}
			}
			result.outcome = Outcome.OK;
		} catch (Throwable t) {
			Throwable cause = t instanceof InvocationTargetException ? t.getCause() : t;
			if (cause instanceof TasTestCase.Skip) {
				result.outcome = Outcome.SKIP;
				result.detail = cause.getMessage();
			} else {
				result.outcome = cause instanceof AssertionError ? Outcome.FAIL : Outcome.ERROR;
				result.detail = cause instanceof AssertionError ? "AssertionError: " + cause.getMessage() : stackTrace(cause);
			}
		} finally {
			game.stopClock();
			Thread.interrupted();
		}
		if (result.outcome == Outcome.FAIL || result.outcome == Outcome.ERROR) {
			try {
				result.screenshot = TasTestCase.screenshot(game, result.name);
			} catch (RuntimeException e) {
				result.detail += "\n(no screenshot: " + e.getMessage() + ")";
			}
		}
		result.seconds = (System.currentTimeMillis() - start) / 1000.0;
		result.chat = game.chatSince(chatMark);
		return result;
	}

	/**
	 * Back to the start: no screen, no keys held, the player as new at the spawn point, the world empty of entities, no
	 * sounds heard yet.
	 */
	private void reset() {
		game.client(() -> {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc.currentScreen != null) {
				mc.displayGuiScreen((GuiScreen) null);
			}
			KeyBinding.unPressAllKeys();
			// the first hotbar slot, so a given item is the held one
			mc.thePlayer.inventory.currentItem = 0;
			mc.gameSettings.difficulty = EnumDifficulty.EASY;
			mc.gameSettings.thirdPersonView = 0;
			mc.gameSettings.hideGUI = false;
			return null;
		});
		// a test that left the player in another dimension: back to the overworld as the End's exit does (the only way
		// out of the End), which makes a new player entity
		boolean respawned = game.server(() -> {
			EntityPlayerMP player = TasGame.serverPlayer();
			if (player.dimension == 0) {
				return false;
			}
			// the connection must take the new entity, as vanilla's respawn request does; otherwise the client's packets
			// keep going to the removed one
			player.playerNetServerHandler.playerEntity = MinecraftServer.getServer().getConfigurationManager().respawnPlayer(player, 0, true);
			return true;
		});
		for (int tick = 0; respawned && tick < 200; tick++) {
			if (game.client(() -> {
				Minecraft mc = Minecraft.getMinecraft();
				return mc.thePlayer != null && mc.thePlayer.dimension == 0 && !(mc.currentScreen instanceof GuiDownloadTerrain);
			})) {
				break;
			}
			game.clientTicks(1);
		}
		game.server(() -> {
			EntityPlayerMP player = TasGame.serverPlayer();
			// no bed of an earlier test's to respawn at
			player.setSpawnChunk(null, false, 0);
			player.inventory.clearInventory(null, -1);
			player.clearItemInUse();
			player.inventoryContainer.detectAndSendChanges();
			player.clearActivePotions();
			// arrows from earlier tests stay drawn stuck in the player, over what the next test's shots show
			player.setArrowCountInEntity(0);
			player.setGameType(WorldSettings.GameType.CREATIVE);
			player.extinguish();
			player.setHealth(player.getMaxHealth());
			player.getFoodStats().addStats(20, 20.0F);
			player.fallDistance = 0.0F;
			if (player.ridingEntity != null) {
				player.mountEntity(null);
			}
			player.playerNetServerHandler.setPlayerLocation(START_X, START_Y, START_Z, 0.0F, 0.0F);
			for (WorldServer world : MinecraftServer.getServer().worldServers) {
				world.difficultySetting = EnumDifficulty.EASY;
				world.setAllowedSpawnTypes(true, true);
				for (Object loaded : new ArrayList<Object>(world.loadedEntityList)) {
					if (!(loaded instanceof EntityPlayer)) {
						((Entity) loaded).setDead();
					}
				}
				world.weatherEffects.clear();
			}
			return null;
		});
		game.clientTicks(3);
		UnitTasMode.SOUNDS_HEARD.clear();
		UnitTasMode.SOUNDS_UNKNOWN.clear();
		UnitTasMode.SOUNDS_UNKNOWN_FROM.clear();
	}

	private void writeReport(double seconds) {
		int failures = 0;
		int errors = 0;
		int skipped = 0;
		StringBuilder report = new StringBuilder();
		report.append("unitTAS report, ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()))
			.append(", suites: ").append(String.join(", ", request.suites));
		if (request.filter != null) {
			report.append(", only '").append(request.filter).append("'");
		}
		report.append("\n\n");
		for (Result result : results) {
			report.append(String.format(Locale.ROOT, "%s ... %s (%.1f s)%n", result.name, result.outcome.word, result.seconds));
			switch (result.outcome) {
				case FAIL:
					failures++;
					break;
				case ERROR:
					errors++;
					break;
				case SKIP:
					skipped++;
					break;
				default:
					break;
			}
			if (result.outcome != Outcome.OK) {
				for (String line : result.detail.split("\n")) {
					report.append("    ").append(line).append('\n');
				}
				if (result.screenshot != null) {
					report.append("    screenshot: ").append(result.screenshot).append('\n');
				}
				if (result.outcome != Outcome.SKIP) {
					report.append("    chat during the test:\n");
					for (TasGame.ChatLine line : result.chat) {
						report.append("      | ").append(line.plain).append('\n');
					}
				}
			}
		}
		report.append("----------------------------------------------------------------------\n");
		report.append(String.format(Locale.ROOT, "Ran %d tests in %.1f s%n%n", results.size(), seconds));
		if (failures + errors == 0) {
			report.append("OK").append(skipped > 0 ? " (skipped=" + skipped + ")" : "").append('\n');
		} else {
			List<String> counts = new ArrayList<>();
			if (failures > 0) {
				counts.add("failures=" + failures);
			}
			if (errors > 0) {
				counts.add("errors=" + errors);
			}
			if (skipped > 0) {
				counts.add("skipped=" + skipped);
			}
			report.append("FAILED (").append(String.join(", ", counts)).append(")\n");
		}
		File results = request.results;
		if (!results.isDirectory() && !results.mkdirs()) {
			UnitTas.LOG.error("Can't make {}", results);
		}
		try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(results, "report.txt")), StandardCharsets.UTF_8)) {
			out.write(report.toString());
		} catch (IOException e) {
			UnitTas.LOG.error("Can't write the report", e);
		}
		request.progress(failures + errors == 0 ? "done: OK" : "done: FAILED");
	}

	private static String stackTrace(Throwable t) {
		StringWriter text = new StringWriter();
		t.printStackTrace(new PrintWriter(text));
		// the frames below the test's own are unitTAS and reflection
		List<String> kept = new ArrayList<>();
		for (String line : text.toString().split("\n")) {
			if (line.contains("sun.reflect.") || line.contains("jdk.internal.reflect.") || line.contains("java.lang.reflect.Method.invoke")) {
				break;
			}
			kept.add(line.replace("\t", "  "));
		}
		return String.join("\n", kept);
	}
}
