package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Stage (e), suite `render`: the client draws the whole column, -64..319 (spec §9), and the sky and fog of the camera
 * near the floor (§4 answer 8): the horizon, the fog colour's darkening measured from the floor, no void particles; and
 * when a render section is rebuilt (decisions 37 to 39). The test world is flat; the tests that need a normal world's
 * darkening set the client's world type to default while they look. Each builds at its own x at z -80..-60, north of
 * spawn.
 */
public class RenderTests extends McFlyTestCase {

	/** The block tests wait for the world around the player to load and be drawn first (a check of the client and a tick each round). */
	@Override
	protected int timeoutSeconds() {
		return 120;
	}

	@Override
	protected void tearDown() {
		// back on the grass first: out of a shaft, a player who stops flying falls into the void (creative doesn't save them)
		onServer(() -> {
			player().playerNetServerHandler.setPlayerLocation(30.5, 4.0, -60.5, 0.0F, 0.0F);
			player().capabilities.isFlying = false;
			player().sendPlayerAbilities();
		});
	}

	public void testRenderersCoverTheColumn() throws Exception {
		Field all = field(RenderGlobal.class, "worldRenderers");
		List<Integer> heights = onClient(() -> {
			int chunkX = (int) Math.floor(mc().thePlayer.posX) >> 4 << 4;
			int chunkZ = (int) Math.floor(mc().thePlayer.posZ) >> 4 << 4;
			List<Integer> found = new ArrayList<>();
			for (WorldRenderer renderer : (WorldRenderer[]) all.get(mc().renderGlobal)) {
				if (renderer.posX == chunkX && renderer.posZ == chunkZ) {
					found.add(renderer.posY);
				}
			}
			Collections.sort(found);
			return found;
		});
		List<Integer> expected = new ArrayList<>();
		for (int y = FLOOR; y <= TOP; y += 16) {
			expected.add(y);
		}
		assertEqual(expected, heights, "the render sections' bottoms in the player's column");
	}

	public void testABlockBelowZeroIsDrawn() throws Exception {
		assertDrawn(8, -30, -72);
	}

	public void testABlockAtTheTopIsDrawn() throws Exception {
		assertDrawn(24, TOP, -72);
	}

	public void testHorizonInAFlatOverworldIsTheFloor() {
		assertEqual((double) FLOOR, onClient(() -> mc().theWorld.getHorizon()), "the client's horizon in the flat test world");
	}

	public void testNoVoidFogBelowZero() throws Exception {
		openShaft(40, -71);
		float[] above = fogAt(41.5, 10.0, -70.5);
		float[] below = fogAt(41.5, -30.0, -70.5);
		for (int i = 0; i < 3; i++) {
			assertAlmostEqual(below[i], above[i], 0.01);
		}
	}

	public void testFogDarkensNearTheFloor() throws Exception {
		openShaft(46, -71);
		WorldProvider provider = onClient(() -> mc().theWorld.provider);
		WorldType flat = onClient(() -> provider.terrainType);
		try {
			// a normal world's darkening: from 32 above the floor down to black at it
			onClient(() -> provider.terrainType = WorldType.DEFAULT);
			float[] high = fogAt(47.5, 100.0, -70.5);
			float[] quarter = fogAt(47.5, FLOOR + 16, -70.5);
			float[] black = fogAt(47.5, FLOOR, -70.5);
			for (int i = 0; i < 3; i++) {
				assertAlmostEqual(quarter[i], high[i] * 0.25, 0.02);
				assertAlmostEqual(black[i], 0.0, 0.01);
			}
		} finally {
			onClient(() -> provider.terrainType = flat);
		}
	}

	public void testNoVoidParticlesInTheOverworld() {
		boolean particles = onClient(() -> {
			WorldProvider provider = mc().theWorld.provider;
			WorldType flat = provider.terrainType;
			provider.terrainType = WorldType.DEFAULT;
			try {
				return provider.getWorldHasVoidParticles();
			} finally {
				provider.terrainType = flat;
			}
		});
		assertFalse(particles, "the Overworld has void particles, with a default world type");
	}

	/** Decision 37: a mark one grid's width east of a built render section doesn't wrap round to it. */
	public void testAMarkPastTheGridDoesNotWrap() throws Exception {
		// the middle of the ground's render section at 64..79, 0..15, -80..-65: the mark's extra block on each side stays in it
		int x = 72;
		int z = -72;
		waitForTheWorldDrawn();
		waitUntilBuilt(x, 8, z, 40);
		Field wide = field(RenderGlobal.class, "renderChunksWide");
		boolean[] marked = onClient(() -> {
			WorldRenderer ground = sectionAt(x, 8, z);
			mc().renderGlobal.markBlockForRenderUpdate(x + wide.getInt(mc().renderGlobal) * 16, 8, z);
			boolean wrapped = ground.needsUpdate;
			mc().renderGlobal.markBlockForRenderUpdate(x, 8, z);
			return new boolean[] {wrapped, ground.needsUpdate};
		});
		assertFalse(marked[0], "a mark one grid's width east marked the render section at 64, 0, -80");
		assertTrue(marked[1], "a mark at its place didn't mark the render section at 64, 0, -80");
	}

	/** Decision 37: a mark in the top layer doesn't wrap round to the floor's render section, nor one in the floor layer to the top's. */
	public void testAMarkInTheTopOrFloorLayerDoesNotWrap() throws Exception {
		int x = 88;
		int z = -72;
		place(x, FLOOR + 4, z, Blocks.stone);
		place(x, TOP - 4, z, Blocks.stone);
		waitForTheWorldDrawn();
		waitUntilBuilt(x, FLOOR, z, 40);
		waitUntilBuilt(x, TOP, z, 40);
		// the top layer's mark covers TOP + 1 too, a section past the column: in 1.7.10's way that is the floor's
		boolean bottomMarked = onClient(() -> {
			mc().renderGlobal.markBlockForRenderUpdate(x, TOP, z);
			return sectionAt(x, FLOOR, z).needsUpdate;
		});
		assertFalse(bottomMarked, "a mark at y " + TOP + " marked the floor's render section");
		waitUntilBuilt(x, FLOOR, z, 40);
		waitUntilBuilt(x, TOP, z, 40);
		boolean topMarked = onClient(() -> {
			mc().renderGlobal.markBlockForRenderUpdate(x, FLOOR, z);
			return sectionAt(x, TOP, z).needsUpdate;
		});
		assertFalse(topMarked, "a mark at y " + FLOOR + " marked the top's render section");
	}

	/** Decision 38: while a chunk diagonal to its own is missing, a render section's first build waits, and comes when the chunk does. */
	public void testAFirstBuildWaitsForTheChunksAround() throws Exception {
		// the ground's render section in chunk 6, -5; the chunk north-east of it, 7, -6, goes
		int x = 104;
		int z = -72;
		waitForTheWorldDrawn();
		goneFromTheClient(7, -6);
		try {
			waitUntilBuilt(x, 8, z, 40);
			unbuild(x, 8, z);
			ticks(20);
			assertFalse(built(x, 8, z), "the render section at 96, 0, -80 was built while chunk 7, -6 was missing");
			backOnTheClient(7, -6);
			waitUntilBuilt(x, 8, z, 10);
		} finally {
			backOnTheClient(7, -6);
		}
	}

	/** Decision 38: a first build waits for a missing chunk beside its own for 40 ticks, then is built anyway. */
	public void testAFirstBuildWaitsAtMostFortyTicks() throws Exception {
		// the ground's render section in chunk 8, -5; the chunk east of it, 9, -5, goes
		int x = 136;
		int z = -72;
		waitForTheWorldDrawn();
		goneFromTheClient(9, -5);
		try {
			waitUntilBuilt(x, 8, z, 40);
			unbuild(x, 8, z);
			ticks(20);
			assertFalse(built(x, 8, z), "the render section at 128, 0, -80 was built while chunk 9, -5 was missing");
			waitUntilBuilt(x, 8, z, 60);
		} finally {
			backOnTheClient(9, -5);
		}
	}

	/** Decision 38 keeps 1.7.10's way for a render section built before: marked, it's rebuilt at once, a neighbour missing or not. */
	public void testASectionBuiltBeforeIsRebuiltAtOnce() throws Exception {
		// the ground's render section in chunk 10, -5; the chunk east of it, 11, -5, goes; ice draws in the second pass
		int x = 168;
		int z = -72;
		waitForTheWorldDrawn();
		goneFromTheClient(11, -5);
		try {
			waitUntilBuilt(x, 8, z, 40);
			place(x, 5, z, Blocks.ice);
			waitUntilOnClient("the ice drawn in the render section at 160, 0, -80", () -> {
				WorldRenderer section = sectionAt(x, 8, z);
				return !section.needsUpdate && !section.skipRenderPass[1];
			}, 10);
		} finally {
			backOnTheClient(11, -5);
		}
	}

	/** Decision 39: a chunk going from the client marks no render section in the columns around it; its own stop drawing. */
	public void testAnUnloadMarksOnlyItsOwnColumn() throws Exception {
		// chunk 6, -4 goes: the ground's render section in it at 96, 0, -64, and the one beside it at 80, 0, -64
		int gone = 104;
		int beside = 88;
		int z = -56;
		waitForTheWorldDrawn();
		waitUntilBuilt(gone, 8, z, 40);
		waitUntilBuilt(beside, 8, z, 40);
		try {
			boolean besideMarked = onClient(() -> {
				mc().theWorld.doPreChunk(6, -4, false);
				return sectionAt(beside, 8, z).needsUpdate;
			});
			assertFalse(besideMarked, "the unload of chunk 6, -4 marked the render section beside it at 80, 0, -64");
			waitUntilOnClient("the render section at 96, 0, -64 drawing nothing, its chunk gone", () -> sectionAt(gone, 8, z).skipRenderPass[0], 10);
		} finally {
			backOnTheClient(6, -4);
		}
	}

	/** Not a check: two views for the owner, below 0 and at the top, as screenshots in the results. */
	public void testViewsForTheOwner() throws Exception {
		// a lit room at -45..-38 with a window of glass, and a platform with glowstone at 312..315
		shell(52, -45, -76, 58, -38, -70, Blocks.stone);
		place(55, -44, -73, Blocks.glowstone);
		place(55, -41, -70, Blocks.glass);
		fill(52, 312, -76, 58, 312, -70, Blocks.stone);
		place(55, 313, -73, Blocks.glowstone);
		place(55, 315, -73, Blocks.glowstone);
		// fogAt flies the player there facing south: towards the window
		fogAt(55.5, -42.0, -73.5);
		screenshot("view inside a room at -45..-38");
		fogAt(55.5, 318.0, -66.5);
		look(180.0F, 30.0F);
		screenshot("view of a platform at 312, from 318");
	}

	/**
	 * Places stone there and waits for the render section holding it to be rebuilt with something to draw. First the
	 * world around the player is loaded and drawn: 1.7.10 rebuilds one far section a frame, those in view first, then
	 * the nearest, so while chunks are still coming (nearest first, a few a tick, each marking its neighbours' sections
	 * too) a far section out of view, like the stone 300 above the player, is rebuilt last. The queue alone is no sign:
	 * it empties between two ticks' chunks.
	 */
	private void assertDrawn(int x, int y, int z) throws Exception {
		Field all = field(RenderGlobal.class, "worldRenderers");
		Field queue = field(RenderGlobal.class, "worldRenderersToUpdate");
		waitForTheWorldDrawn();
		place(x, y, z, Blocks.stone);
		waitUntilOnClient("the stone at y " + y + " on the client", () -> mc().theWorld.getBlock(x, y, z) == Blocks.stone, 40);
		int sectionX = x >> 4 << 4;
		int sectionY = y >> 4 << 4;
		int sectionZ = z >> 4 << 4;
		Callable<WorldRenderer> find = () -> {
			for (WorldRenderer renderer : (WorldRenderer[]) all.get(mc().renderGlobal)) {
				if (renderer.posX == sectionX && renderer.posY == sectionY && renderer.posZ == sectionZ) {
					return renderer;
				}
			}
			return null;
		};
		// each tick: built yet? and which render sections are waiting in the queue meanwhile
		Set<String> passed = new HashSet<>();
		for (int tick = 0; tick < 100; tick++) {
			boolean built = onClient(() -> {
				for (Object waiting : (List<?>) queue.get(mc().renderGlobal)) {
					if (waiting != null) {
						passed.add(where((WorldRenderer) waiting));
					}
				}
				WorldRenderer renderer = find.call();
				return renderer != null && !renderer.needsUpdate && !renderer.skipRenderPass[0];
			});
			if (built) {
				return;
			}
			ticks(1);
		}
		// what the renderer was doing instead, and what went before it
		String state = onClient(() -> {
			WorldRenderer renderer = find.call();
			List<WorldRenderer> waiting = new ArrayList<>();
			for (Object entry : (List<?>) queue.get(mc().renderGlobal)) {
				if (entry != null) {
					waiting.add((WorldRenderer) entry);
				}
			}
			String self = renderer == null ? "no render section there" : "marked " + renderer.needsUpdate + ", draws " + !renderer.skipRenderPass[0]
				+ ", in view " + renderer.isInFrustum + ", queued " + waiting.contains(renderer) + " (queue " + waiting.size() + ")";
			waiting.sort(Comparator.comparingDouble(entry -> entry.distanceToEntitySquared(mc().thePlayer)));
			StringBuilder nearest = new StringBuilder();
			for (WorldRenderer entry : waiting.subList(0, Math.min(8, waiting.size()))) {
				nearest.append(nearest.length() == 0 ? "" : "; ").append(where(entry)).append(" (marked ").append(entry.needsUpdate).append(", in view ")
					.append(entry.isInFrustum).append(", ").append((int) Math.sqrt(entry.distanceToEntitySquared(mc().thePlayer))).append(" away)");
			}
			return self + "; " + passed.size() + " render sections were queued during the wait; nearest queued now: " + nearest;
		});
		fail("waited 100 ticks for the render section at " + sectionX + ", " + sectionY + ", " + sectionZ + " to be built with the stone in it: " + state);
	}

	/** The world around the player loaded on the client and drawn: every chunk but the outermost ring there, and the renderer's queue empty. */
	private void waitForTheWorldDrawn() throws Exception {
		Field queue = field(RenderGlobal.class, "worldRenderersToUpdate");
		// not the outermost ring: the server sends a chunk once its light is done, which needs all its neighbours loaded
		waitUntilOnClient("every chunk within the render distance less one on the client", () -> {
			int radius = mc().gameSettings.renderDistanceChunks - 1;
			int chunkX = MathHelper.floor_double(mc().thePlayer.posX) >> 4;
			int chunkZ = MathHelper.floor_double(mc().thePlayer.posZ) >> 4;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (mc().theWorld.getChunkFromChunkCoords(chunkX + dx, chunkZ + dz).isEmpty()) {
						return false;
					}
				}
			}
			return true;
		}, 300);
		waitUntilOnClient("the renderer's queue to empty (the world around the player drawn)", () -> ((List<?>) queue.get(mc().renderGlobal)).isEmpty(), 200);
	}

	/** The render section holding the block, on the client: there's always one within the render distance. */
	private static WorldRenderer sectionAt(int x, int y, int z) throws Exception {
		for (WorldRenderer renderer : (WorldRenderer[]) field(RenderGlobal.class, "worldRenderers").get(mc().renderGlobal)) {
			if (renderer.posX == x >> 4 << 4 && renderer.posY == y >> 4 << 4 && renderer.posZ == z >> 4 << 4) {
				return renderer;
			}
		}
		throw new AssertionError("no render section at " + (x >> 4 << 4) + ", " + (y >> 4 << 4) + ", " + (z >> 4 << 4));
	}

	/** Whether the render section holding the block is built and draws its first pass, not marked. */
	private boolean built(int x, int y, int z) {
		return onClient(() -> {
			WorldRenderer section = sectionAt(x, y, z);
			return !section.needsUpdate && !section.skipRenderPass[0];
		});
	}

	private void waitUntilBuilt(int x, int y, int z, int maxTicks) {
		waitUntilOnClient("the render section at " + (x >> 4 << 4) + ", " + (y >> 4 << 4) + ", " + (z >> 4 << 4) + " built", () -> {
			WorldRenderer section = sectionAt(x, y, z);
			return !section.needsUpdate && !section.skipRenderPass[0];
		}, maxTicks);
	}

	/** Puts the render section holding the block back to not built at its place, as a move does, and marks it. */
	private void unbuild(int x, int y, int z) {
		onClient(() -> {
			sectionAt(x, y, z).setDontDraw();
			mc().renderGlobal.markBlockForRenderUpdate(x, y, z);
			return null;
		});
	}

	/** The client drops the chunk, as when the server unloads it there (the server still counts it as sent). */
	private void goneFromTheClient(int chunkX, int chunkZ) {
		onClient(() -> mc().theWorld.doPreChunk(chunkX, chunkZ, false));
		waitUntilOnClient("chunk " + chunkX + ", " + chunkZ + " gone from the client", () -> mc().theWorld.getChunkFromChunkCoords(chunkX, chunkZ).isEmpty(), 10);
	}

	/** The server sends the chunk again, as it sends chunks: in a bulk packet. Nothing when the client has it. */
	private void backOnTheClient(int chunkX, int chunkZ) {
		if (!onClient(() -> mc().theWorld.getChunkFromChunkCoords(chunkX, chunkZ).isEmpty())) {
			return;
		}
		onServer(() -> player().playerNetServerHandler.sendPacket(new S26PacketMapChunkBulk(Collections.singletonList(world().getChunkFromChunkCoords(chunkX, chunkZ)))));
		waitUntilOnClient("chunk " + chunkX + ", " + chunkZ + " back on the client", () -> !mc().theWorld.getChunkFromChunkCoords(chunkX, chunkZ).isEmpty(), 20);
	}

	private static String where(WorldRenderer renderer) {
		return renderer.posX + "," + renderer.posY + "," + renderer.posZ;
	}

	/** Takes the flat layers out of a 3x3 around x+1, z+1: open to the sky down into the void. */
	private void openShaft(int x, int z) {
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				clearColumn(x + dx, z + dz);
			}
		}
		waitUntilOnClient("the shaft on the client", () -> mc().theWorld.isAirBlock(x + 1, 0, z + 1), 40);
	}

	/** Flies the player with their eyes at that height, lets some frames draw, and returns the fog colour (red, green, blue). */
	private float[] fogAt(double x, double eyeY, double z) throws Exception {
		double feet = eyeY - 1.62;
		onServer(() -> {
			player().capabilities.isFlying = true;
			player().sendPlayerAbilities();
			player().playerNetServerHandler.setPlayerLocation(x, feet, z, 0.0F, 0.0F);
		});
		waitUntilOnClient("the player's eyes at y " + eyeY, () -> mc().thePlayer.capabilities.isFlying && Math.abs(mc().thePlayer.posY - eyeY) < 0.01, 40);
		ticks(10);
		Field red = field(EntityRenderer.class, "fogColorRed");
		Field green = field(EntityRenderer.class, "fogColorGreen");
		Field blue = field(EntityRenderer.class, "fogColorBlue");
		return onClient(() -> new float[] {red.getFloat(mc().entityRenderer), green.getFloat(mc().entityRenderer), blue.getFloat(mc().entityRenderer)});
	}

	private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
