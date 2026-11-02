package io.github.asgerjon.mcfly.unittas.tests;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.asgerjon.mcfly.world.TallChunk;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.lwjgl.opengl.Display;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stage (e), suite `bench`: the renderer's cost, measured, not judged (PLAN-mcfly.md §2 (e)). Each test builds a scene of
 * towers (stone, glass, glowstone) every 48 blocks within 144 of its centre, flies the same 30 s path over it at
 * y 60 looking ahead and down, with the frame rate unlimited, and adds one line of numbers to `bench.txt` in the run's
 * results folder. The scenes: towers in 0..255 only, and the same towers also below 0 and above 255. The line also says
 * what the code doesn't decide but the numbers depend on: the window's size, how much of the flight it was in focus,
 * and the Java garbage collector's pauses. Then it flies the path again with 1.7.10's profiler on (the one behind
 * Shift+F3's pie chart) and writes the time each of its sections took to `profile.txt`: apart from the timed flight,
 * because the profiler's own work would change the times. Last, one rebuild's cost on its own, to `rebuild.txt`.
 */
public class RenderBench extends McFlyTestCase {

	private static final int FLIGHT_TICKS = 600;
	private static final int WARM_UP = 50;
	private static final int REBUILDS = 300;
	private static final double EYE_Y = 60.0;

	@Override
	protected int timeoutSeconds() {
		return 300;
	}

	public void testFlightOverClassicScene() throws Exception {
		fly("towers in 0..255", 2000, 2000, 4);
	}

	public void testFlightOverTallScene() throws Exception {
		fly("towers in 0..255, below 0, above 255", 2000, 3000, 4, -60, 280);
	}

	/**
	 * What a rebuild costs apart from the flight: one tower's three render sections, its foot (with the ground), its top,
	 * and the air above it (next to blocks, so 1.7.10 looks through it), each rebuilt again and again in a row.
	 */
	public void testRebuildCost() throws Exception {
		int x = 2000;
		int z = 4000;
		onServer(() -> tower(x, 4, z));
		onServer(() -> {
			player().capabilities.isFlying = true;
			player().sendPlayerAbilities();
			player().playerNetServerHandler.setPlayerLocation(x + 0.5, EYE_Y - 1.62, z - 20.5, 0.0F, 60.0F);
		});
		try {
			ticks(200);
			String line = onClient(() -> String.format(Locale.ROOT, "render column %d sections: one tower's sections, rebuilt %d times each "
					+ "after %d; ms a rebuild: foot %s; top %s; air above %s", Flight.field("renderChunksTall").getInt(mc().renderGlobal), REBUILDS,
				WARM_UP, rebuildCost(x, 0, z), rebuildCost(x, 16, z), rebuildCost(x, 32, z)));
			append("rebuild.txt", line + "\n");
		} finally {
			onServer(() -> {
				player().capabilities.isFlying = false;
				player().sendPlayerAbilities();
			});
		}
	}

	/** The render section holding the block, rebuilt as the queue would: marked, then updated. On the client. */
	private static String rebuildCost(int x, int y, int z) throws ReflectiveOperationException {
		WorldRenderer section = null;
		for (WorldRenderer renderer : (WorldRenderer[]) Flight.field("worldRenderers").get(mc().renderGlobal)) {
			if (renderer.posX == (x & ~15) && renderer.posY == (y & ~15) && renderer.posZ == (z & ~15)) {
				section = renderer;
			}
		}
		assertIsNotNone(section, "no render section at " + x + " " + y + " " + z);
		EntityLivingBase viewer = mc().renderViewEntity;
		long[] nanos = new long[REBUILDS];
		for (int i = -WARM_UP; i < REBUILDS; i++) {
			long start = System.nanoTime();
			section.markDirty();
			section.updateRenderer(viewer);
			if (i >= 0) {
				nanos[i] = System.nanoTime() - start;
			}
		}
		Arrays.sort(nanos);
		return String.format(Locale.ROOT, "median %.3f, 95th %.3f", nanos[REBUILDS / 2] / 1.0e6, nanos[(int) (REBUILDS * 0.95)] / 1.0e6);
	}

	private void fly(String scene, int centreX, int centreZ, int... bands) throws Exception {
		for (int tx = centreX - 144; tx <= centreX + 144; tx += 48) {
			for (int tz = centreZ - 144; tz <= centreZ + 144; tz += 48) {
				for (int band : bands) {
					int x = tx;
					int z = tz;
					onServer(() -> tower(x, band, z));
				}
			}
		}
		double startX = centreX - 150.0;
		int[] settings = onClient(() -> new int[] {mc().gameSettings.limitFramerate, mc().gameSettings.enableVsync ? 1 : 0});
		Flight timed = new Flight(startX, centreZ + 0.5, false);
		Flight profiled = new Flight(startX, centreZ + 0.5, true);
		try {
			onClient(() -> {
				// 260 is "unlimited"; vsync is the display's, set when the game starts
				mc().gameSettings.limitFramerate = 260;
				mc().gameSettings.enableVsync = false;
				Display.setVSyncEnabled(false);
			});
			pass(timed);
			// the game turns its profiler on between two frames while the pie chart is shown, so its sections stay paired
			onClient(() -> {
				mc().gameSettings.showDebugInfo = true;
				mc().gameSettings.showDebugProfilerChart = true;
			});
			pass(profiled);
		} finally {
			onClient(() -> {
				mc().gameSettings.showDebugInfo = false;
				mc().gameSettings.showDebugProfilerChart = false;
				mc().gameSettings.limitFramerate = settings[0];
				mc().gameSettings.enableVsync = settings[1] == 1;
				Display.setVSyncEnabled(settings[1] == 1);
			});
			onServer(() -> {
				player().capabilities.isFlying = false;
				player().sendPlayerAbilities();
			});
		}
		append("bench.txt", onClient(() -> timed.summary(scene)) + "\n");
		append("profile.txt", onClient(() -> profiled.profile(scene)));
		append("queue.txt", onClient(() -> profiled.reentries(scene)));
	}

	/** One flight along the path: to its start, the scene's chunks there come and are drawn a first time, then 600 ticks. */
	private void pass(Flight flight) {
		onServer(() -> {
			player().capabilities.isFlying = true;
			player().sendPlayerAbilities();
			player().playerNetServerHandler.setPlayerLocation(flight.startX, EYE_Y - 1.62, flight.z, -90.0F, 25.0F);
		});
		ticks(200);
		onClient(() -> FMLCommonHandler.instance().bus().register(flight));
		try {
			waitUntilOnClient("the flight to end", () -> flight.done, FLIGHT_TICKS + 100);
		} finally {
			onClient(() -> {
				FMLCommonHandler.instance().bus().unregister(flight);
				flight.endQueue();
			});
		}
	}

	private void append(String file, String text) throws Exception {
		try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(resultsFolder(), file), true), StandardCharsets.UTF_8)) {
			out.write(text);
		}
	}

	/** A 3x3 tower 16 high from y: stone corners, glass sides, a core of stone with glowstone every fourth layer. */
	private static void tower(int x, int y, int z) {
		for (int dy = 0; dy < 16; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					Block block = dx != 0 && dz != 0 ? Blocks.stone : dx == 0 && dz == 0 ? (dy % 4 == 0 ? Blocks.glowstone : Blocks.stone) : Blocks.glass;
					world().setBlock(x + dx, y + dy, z + dz, block, 0, 2);
				}
			}
		}
	}

	/** Moves the player along the path each client tick, and keeps each frame's numbers: its time, and its tick's and drawing's. */
	public static final class Flight {

		private final double startX;
		private final double z;
		private int tick;
		volatile boolean done;
		private long lastFrame;
		private final List<Long> frameNanos = new ArrayList<>();
		private final List<Long> rebuildFrameNanos = new ArrayList<>();
		private final List<Long> plainFrameNanos = new ArrayList<>();
		private long tickStart;
		private final List<Long> tickNanos = new ArrayList<>();
		private long renderStart;
		private final List<Long> renderNanos = new ArrayList<>();
		private int width;
		private int height;
		private boolean resized;
		private int focusedFrames;
		private long[] collectedBefore;
		private long[] collectedAfter;
		private Map<String, Long> profiledLast;
		private final Map<String, List<int[]>> queuedWithBlocks = new HashMap<>();
		private int queuedAir;
		private final boolean traced;
		private final Map<String, Integer> reentries = new HashMap<>();
		private final Map<String, Integer> firstEntries = new HashMap<>();
		private final Map<String, List<String>> queuedWhy = new HashMap<>();
		private final int[] behindBy = new int[3];
		private final Map<String, Long> profiledTotal = new HashMap<>();
		private int lastUpdated;
		private long rebuilds;
		private long loaded;
		private long drawn;
		private final Field renderersLoaded;
		private final Field renderersBeingRendered;
		private final Field renderChunksTall;
		private final Field profilingMap;
		private final Field renderersToUpdate;

		/** Traced: each time a section with blocks goes back in the queue, it notes what put it there (the profiled flight). */
		Flight(double startX, double z, boolean traced) throws ReflectiveOperationException {
			this.traced = traced;
			this.startX = startX;
			this.z = z;
			renderersLoaded = field("renderersLoaded");
			renderersBeingRendered = field("renderersBeingRendered");
			renderChunksTall = field("renderChunksTall");
			profilingMap = Profiler.class.getDeclaredField("profilingMap");
			profilingMap.setAccessible(true);
			renderersToUpdate = field("worldRenderersToUpdate");
		}

		@SubscribeEvent
		public void onClientTick(TickEvent.ClientTickEvent event) {
			EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
			if (done || player == null) {
				return;
			}
			// the tick's own time: the game's update and the packets it handles (chunks among them), not the drawing
			if (event.phase == TickEvent.Phase.END) {
				if (tickStart != 0) {
					tickNanos.add(System.nanoTime() - tickStart);
				}
				return;
			}
			if (tick == 0) {
				collectedBefore = collections();
				lastUpdated = WorldRenderer.chunksUpdated;
				setQueue(new Queue((List<?>) getQueue()));
			}
			tickStart = System.nanoTime();
			player.setPosition(startX + 0.5 * tick, EYE_Y, z);
			player.motionX = 0.0;
			player.motionY = 0.0;
			player.motionZ = 0.0;
			player.rotationYaw = -90.0F;
			player.rotationPitch = 25.0F;
			if (++tick > FLIGHT_TICKS) {
				collectedAfter = collections();
				endQueue();
				done = true;
			}
		}

		/** The rebuild queue as 1.7.10 had it, once the flight is over. */
		void endQueue() {
			Object queue = getQueue();
			if (queue instanceof Queue) {
				setQueue(new ArrayList<>((Queue) queue));
			}
		}

		/**
		 * A render section put in the queue: with blocks of its own, the flight's tick and whether it's ahead of the
		 * player (1) or behind (0), by section; or one more of air.
		 */
		private void queued(WorldRenderer section) {
			ExtendedBlockStorage blocks = null;
			if (section.worldObj != null) {
				Chunk chunk = section.worldObj.getChunkFromChunkCoords(section.posX >> 4, section.posZ >> 4);
				blocks = ((TallChunk) chunk).mcfly$section(section.posY >> 4);
			}
			if (blocks == null || blocks.isEmpty()) {
				queuedAir++;
				return;
			}
			String key = section.posX + " " + section.posY + " " + section.posZ;
			List<int[]> entries = queuedWithBlocks.get(key);
			if (entries == null) {
				queuedWithBlocks.put(key, entries = new ArrayList<>());
			}
			double playerX = Minecraft.getMinecraft().thePlayer.posX;
			int ahead = section.posX + 8 > playerX ? 1 : 0;
			if (traced) {
				// what put it in, at which chunk from the player's along the flight, with how many of the 8 chunks around its own missing
				int at = (section.posX >> 4) - ((int) Math.floor(playerX) >> 4);
				String why = why(markedBy()) + " at " + (at > 0 ? "+" : "") + at + ", " + missingAround(section) + " around missing";
				List<String> whys = queuedWhy.get(key);
				if (whys == null) {
					queuedWhy.put(key, whys = new ArrayList<>());
				}
				if (entries.isEmpty()) {
					String first = why.substring(0, why.indexOf(" at "));
					Integer times = firstEntries.get(first);
					firstEntries.put(first, times == null ? 1 : times + 1);
				} else {
					int gap = tick - entries.get(entries.size() - 1)[0];
					String cause = (ahead == 1 ? "ahead" : "behind") + (gap < 10 ? ", within 9 ticks" : gap < 40 ? ", after 10-39" : ", after 40 or more") + ": "
						+ whys.get(whys.size() - 1) + ", then " + why;
					Integer times = reentries.get(cause);
					reentries.put(cause, times == null ? 1 : times + 1);
					if (ahead == 0) {
						behindBy[-at < 4 ? 0 : -at < 10 ? 1 : 2]++;
					}
				}
				whys.add(why);
			}
			entries.add(new int[] {tick, ahead});
		}

		/** The calls that put a section in the queue, nearest first: 1.7.10's and the mixins', not the bench's. */
		private static String markedBy() {
			StringBuilder out = new StringBuilder();
			int taken = 0;
			for (StackTraceElement frame : new Throwable().getStackTrace()) {
				if (frame.getClassName().startsWith(RenderBench.class.getName())) {
					continue;
				}
				String owner = frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1);
				out.append(taken == 0 ? "" : " < ").append(owner).append('.').append(frame.getMethodName());
				if (++taken == 9) {
					break;
				}
			}
			return out.toString();
		}

		/** What a stack of calls into the queue was, in a word, as far as it's one of the known ones. */
		private static String why(String calls) {
			return calls.contains("doPreChunk") ? "an unload" : calls.contains("handleMapChunkBulk") ? "a bulk arrival"
				: calls.contains("handleChunkData") ? "a chunk arrival" : calls.contains("markRenderersForNewPosition") ? "the grid's move"
				: calls.contains("sortAndRender") ? "the round robin" : calls.contains("setBlock") ? "a block change"
				: calls.contains("LightValue") || calls.contains("updateLightByType") ? "a light change" : calls;
		}

		private static int missingAround(WorldRenderer section) {
			int missing = 0;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && section.worldObj.getChunkFromChunkCoords((section.posX >> 4) + dx, (section.posZ >> 4) + dz).isEmpty()) {
						missing++;
					}
				}
			}
			return missing;
		}

		/**
		 * The times a section with blocks went back in the queue during the (traced) flight, by what put it there the time
		 * before and this time, most first; how far behind the player the ones behind were; and the first times in, by what.
		 */
		String reentries(String scene) throws IllegalAccessException {
			int tall = renderChunksTall.getInt(Minecraft.getMinecraft().renderGlobal);
			int total = 0;
			for (int times : reentries.values()) {
				total += times;
			}
			StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%s, render column %d sections, the profiled flight: a section with blocks back "
				+ "in the queue %d times; of those behind the player, by 0-3 chunks %d, 4-9 %d, 10 or more %d; by what put it back:%n", scene, tall, total,
				behindBy[0], behindBy[1], behindBy[2]));
			out.append("  first times in: ").append(firstEntries).append(String.format(Locale.ROOT, "%n"));
			List<Map.Entry<String, Integer>> causes = new ArrayList<>(reentries.entrySet());
			causes.sort((a, b) -> b.getValue() - a.getValue());
			for (Map.Entry<String, Integer> cause : causes.subList(0, Math.min(15, causes.size()))) {
				out.append(String.format(Locale.ROOT, "  %5d  %s%n", cause.getValue(), cause.getKey()));
			}
			return out.toString();
		}

		private Object getQueue() {
			try {
				return renderersToUpdate.get(Minecraft.getMinecraft().renderGlobal);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		private void setQueue(List<?> queue) {
			try {
				renderersToUpdate.set(Minecraft.getMinecraft().renderGlobal, queue);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		/**
		 * The renderer's queue of render sections to rebuild, for the flight: 1.7.10's list, noting each section put in.
		 * A section already marked isn't put in again, so each time in is one rebuild.
		 */
		private final class Queue extends ArrayList<Object> {

			Queue(Collection<?> entries) {
				super(entries);
			}

			@Override
			public boolean add(Object section) {
				queued((WorldRenderer) section);
				return super.add(section);
			}
		}

		@SubscribeEvent
		public void onRenderTick(TickEvent.RenderTickEvent event) {
			if (done || tick == 0) {
				return;
			}
			long now = System.nanoTime();
			// the drawing's own time, as the CPU sees it: the frame's rest is ticks and the buffer swap
			if (event.phase == TickEvent.Phase.START) {
				renderStart = now;
				return;
			}
			if (renderStart != 0) {
				renderNanos.add(now - renderStart);
			}
			// the game sets the counter back to 0 every second
			int updated = WorldRenderer.chunksUpdated;
			int rebuilt = updated >= lastUpdated ? updated - lastUpdated : updated;
			rebuilds += rebuilt;
			lastUpdated = updated;
			if (lastFrame != 0) {
				frameNanos.add(now - lastFrame);
				(rebuilt > 0 ? rebuildFrameNanos : plainFrameNanos).add(now - lastFrame);
			}
			lastFrame = now;
			Minecraft mc = Minecraft.getMinecraft();
			if (width == 0) {
				width = mc.displayWidth;
				height = mc.displayHeight;
			} else if (width != mc.displayWidth || height != mc.displayHeight) {
				resized = true;
			}
			if (Display.isActive()) {
				focusedFrames++;
			}
			if (mc.mcProfiler.profilingEnabled) {
				profile();
			}
			try {
				RenderGlobal renderer = Minecraft.getMinecraft().renderGlobal;
				loaded += renderersLoaded.getInt(renderer);
				drawn += renderersBeingRendered.getInt(renderer);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		String summary(String scene) throws IllegalAccessException {
			List<Long> sorted = sorted(frameNanos);
			List<Long> renders = sorted(renderNanos);
			List<Long> ticks = sorted(tickNanos);
			List<Long> rebuildFrames = sorted(rebuildFrameNanos);
			List<Long> plainFrames = sorted(plainFrameNanos);
			int frames = sorted.size();
			// how soon a section with blocks went back in after its last time, ahead of the player and behind
			int times = 0;
			int most = 0;
			int[] ahead = new int[2];
			int[][] again = new int[2][3];
			for (List<int[]> entries : queuedWithBlocks.values()) {
				times += entries.size();
				most = Math.max(most, entries.size());
				for (int i = 0; i < entries.size(); i++) {
					int side = entries.get(i)[1];
					ahead[side]++;
					if (i > 0) {
						int gap = entries.get(i)[0] - entries.get(i - 1)[0];
						again[side][gap < 10 ? 0 : gap < 40 ? 1 : 2]++;
					}
				}
			}
			int sections = queuedWithBlocks.size();
			int tall = renderChunksTall.getInt(Minecraft.getMinecraft().renderGlobal);
			return String.format(Locale.ROOT, "%s: %d frames in %d ticks; frame ms median %.2f, 95th %.2f, 99th %.2f; render ms median %.2f, 95th %.2f, "
					+ "99th %.2f; tick ms median %.2f, 95th %.2f, max %.2f (%d ticks); chunk rebuilds %d (%.2f a frame); "
					+ "renderers loaded %.0f, drawn %.0f (a frame); render column %d sections; render distance %d; window %dx%d%s, in focus %.0f%% of "
				+ "frames; garbage collections %d (%d ms); frames with a rebuild %d, ms median %.2f, 95th %.2f; without %d, median %.2f, 95th %.2f; "
				+ "queued to rebuild: with blocks %d times (ahead of the player %d, behind %d), %d sections (%.1f a section, most %d), back in "
				+ "(ahead / behind) within 9 ticks %d / %d, 10-39 %d / %d, 40 or more %d / %d; air %d times",
				scene, frames, FLIGHT_TICKS, percentile(sorted, 0.50), percentile(sorted, 0.95), percentile(sorted, 0.99), percentile(renders, 0.50),
				percentile(renders, 0.95), percentile(renders, 0.99), percentile(ticks, 0.50), percentile(ticks, 0.95), percentile(ticks, 1.0), ticks.size(),
				rebuilds, frames == 0 ? 0.0 : (double) rebuilds / frames, frames == 0 ? 0.0 : (double) loaded / frames,
				frames == 0 ? 0.0 : (double) drawn / frames, tall, Minecraft.getMinecraft().gameSettings.renderDistanceChunks, width, height,
				resized ? " (resized during the flight)" : "", frames == 0 ? 0.0 : 100.0 * focusedFrames / frames, collectedAfter[0] - collectedBefore[0],
				collectedAfter[1] - collectedBefore[1], rebuildFrames.size(), percentile(rebuildFrames, 0.50), percentile(rebuildFrames, 0.95),
				plainFrames.size(), percentile(plainFrames, 0.50), percentile(plainFrames, 0.95), times, ahead[1], ahead[0], sections,
				sections == 0 ? 0.0 : (double) times / sections, most, again[1][0], again[0][0], again[1][1], again[0][1], again[1][2], again[0][2],
				queuedAir);
		}

		/**
		 * Adds what each of the profiler's sections took since the last frame. Its totals aren't sums: once a frame, while
		 * the pie chart is shown, the game shrinks every one by a thousandth, after the frame's first `root` has ended. So
		 * what a section took is its total now less its last total shrunk. The first `root`'s own end comes after this, so
		 * its time is counted a frame late and a thousandth short.
		 */
		private void profile() {
			Map<String, Long> totals = profiled();
			if (profiledLast != null) {
				for (Map.Entry<String, Long> section : totals.entrySet()) {
					Long last = profiledLast.get(section.getKey());
					long took = section.getValue() - (last == null ? 0L : last * 999L / 1000L);
					Long sum = profiledTotal.get(section.getKey());
					profiledTotal.put(section.getKey(), sum == null ? took : sum + took);
				}
			}
			profiledLast = totals;
		}

		/**
		 * The profiler's sections and the time each took during the flight, inner ones included in outer ones (`root` is
		 * the whole frame, in two parts: the ticks and the drawing, then the buffer swap): in ms, and in µs a frame. Only
		 * the heading when the profiler was off.
		 */
		String profile(String scene) throws IllegalAccessException {
			int frames = frameNanos.size();
			int tall = renderChunksTall.getInt(Minecraft.getMinecraft().renderGlobal);
			StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%s, render column %d sections: %d frames in %d ticks, chunk rebuilds %d, "
				+ "profiler on%n", scene, tall, frames, FLIGHT_TICKS, rebuilds));
			List<String> sections = new ArrayList<>(profiledTotal.keySet());
			Collections.sort(sections);
			for (String section : sections) {
				long nanos = profiledTotal.get(section);
				out.append(String.format(Locale.ROOT, "  %-56s %10.1f ms %9.1f \u00b5s a frame%n", section, nanos / 1.0e6, frames == 0 ? 0.0 : nanos / 1.0e3 / frames));
			}
			return out.toString();
		}

		@SuppressWarnings("unchecked")
		private Map<String, Long> profiled() {
			try {
				return new HashMap<>((Map<String, Long>) profilingMap.get(Minecraft.getMinecraft().mcProfiler));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		/** Every collector's collections so far and their time in ms, summed. */
		private static long[] collections() {
			long count = 0;
			long millis = 0;
			for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
				count += Math.max(0, collector.getCollectionCount());
				millis += Math.max(0, collector.getCollectionTime());
			}
			return new long[] {count, millis};
		}

		private static List<Long> sorted(List<Long> nanos) {
			List<Long> sorted = new ArrayList<>(nanos);
			Collections.sort(sorted);
			return sorted;
		}

		private static double percentile(List<Long> sorted, double share) {
			return sorted.isEmpty() ? 0.0 : sorted.get(Math.min(sorted.size() - 1, (int) (share * sorted.size()))) / 1.0e6;
		}

		static Field field(String name) throws NoSuchFieldException {
			Field field = RenderGlobal.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		}
	}
}
