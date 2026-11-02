package io.github.asgerjon.mcfly.unittas;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * unitTAS: tests that play the real game, in the manner of Python's unittest (see UNITTAS.md in the project root).
 * Asleep unless ./unittas.sh left a request in the client's folder (`unittas/request.properties`). Then it makes a
 * fresh test world from the main menu, runs the asked-for suites on their own thread, writes the report and
 * screenshots to the results folder and quits the game. The request is used up at once, so a crash never leaves the
 * next ordinary run in test mode.
 */
public final class UnitTas {

	static final Logger LOG = LogManager.getLogger("unitTAS");

	/** What ./unittas.sh asked for. */
	static final class Request {
		final List<String> suites;
		/** Only tests whose `Class.method` name contains this, or null for all. */
		final String filter;
		final File results;

		Request(Properties properties) {
			String suiteList = properties.getProperty("suites", "").trim();
			suites = suiteList.isEmpty() ? new ArrayList<>(TasSuites.names()) : Arrays.asList(suiteList.split("[,\\s]+"));
			String only = properties.getProperty("filter", "").trim();
			filter = only.isEmpty() ? null : only;
			results = new File(properties.getProperty("results", "unittas-results"));
		}

		/** One line for the script to show, also logged. */
		void progress(String line) {
			LOG.info(line);
			if (!results.isDirectory() && !results.mkdirs()) {
				return;
			}
			try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(results, "progress.txt"), true), StandardCharsets.UTF_8)) {
				out.write(line + "\n");
			} catch (IOException e) {
				LOG.warn("Can't write progress", e);
			}
		}
	}

	private UnitTas() {
	}

	/**
	 * Call once from the client proxy (init, client only): starts a run when ./unittas.sh left a request, and otherwise
	 * does nothing.
	 */
	public static void startIfRequested() {
		File request = UnitTasMode.request(Minecraft.getMinecraft().mcDataDir);
		if (request.isFile()) {
			start(request);
		}
	}

	private static void start(File requestFile) {
		Properties properties = new Properties();
		try (InputStream in = new FileInputStream(requestFile)) {
			properties.load(in);
		} catch (IOException e) {
			LOG.error("Can't read {}", requestFile, e);
			return;
		} finally {
			if (!requestFile.delete()) {
				LOG.warn("Can't remove {}; delete it before the next ordinary run", requestFile);
			}
		}
		UnitTasMode.running = true;
		Request request = new Request(properties);
		request.progress("unitTAS started: suites " + String.join(", ", request.suites)
			+ (request.filter == null ? "" : ", only tests matching '" + request.filter + "'"));
		TasGame game = new TasGame(request);
		FMLCommonHandler.instance().bus().register(game);
		MinecraftForge.EVENT_BUS.register(game);
	}
}
