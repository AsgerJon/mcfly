package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.block.BlockPistonBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Stage (d), suite `rules`: 1.7.10's other fixed bounds at 0 and 255/256, moved to the floor and the top as the other
 * side's answers say (spec §11). Each test builds at its own place at z 120..150, south of spawn; the portal at the top
 * is made at x 400, z 400.
 */
public class SweepTests extends McFlyTestCase {

	public void testSandFallsBelowZero() {
		place(40, -40, 120, Blocks.stone);
		place(40, -30, 120, Blocks.sand);
		// and sand at the floor, with nothing under it: it falls out of the world
		place(42, FLOOR, 120, Blocks.sand);
		waitUntil("the sand from -30 to land on the stone at -40", () -> world().getBlock(40, -39, 120) == Blocks.sand, 80);
		assertEqual(Arrays.asList("minecraft:air", "minecraft:air"), Arrays.asList(blockAt(40, -30, 120), blockAt(42, FLOOR, 120)),
			"where the two sands were, at -30 and at -64");
	}

	public void testFallingSandDropsBelowTheFloor() {
		// a falling block's first update removes its block, or the entity itself when the block isn't there: past that
		EntityFallingBlock below = fallingSand(44.5, -70.0, 120.5);
		EntityFallingBlock above = fallingSand(46.5, -50.0, 120.5);
		// held until the one at -70 goes, and its item looked for at once (it falls away into the void after)
		List<Boolean> dropped = new ArrayList<>();
		for (int tick = 0; tick < 150 && dropped.isEmpty(); tick++) {
			onServer(() -> {
				if (below.isDead) {
					dropped.add(!world().getEntitiesWithinAABB(EntityItem.class, below.boundingBox.expand(2.0, 2.0, 2.0)).isEmpty());
				} else {
					hold(below, 44.5, -70.0, 120.5);
				}
				hold(above, 46.5, -50.0, 120.5);
			});
			serverTicks(1);
		}
		assertEqual(Collections.singletonList(true), dropped, "falling sand held at -70 for over 100 ticks: gone, with its item there");
		assertTrue(onServer(() -> above.field_145812_b > 100 && !above.isDead), "falling sand held at -50 as long, past 100 ticks of falling, still falling");
	}

	public void testPistonPushesIntoTheFloorLayer() {
		place(50, FLOOR + 1, 120, Blocks.stone);
		place(50, FLOOR + 2, 120, Blocks.piston, 0);
		place(51, FLOOR + 2, 120, Blocks.redstone_block);
		waitUntil("the piston at -62 to push the stone at -63 down into the floor layer", () -> world().getBlock(50, FLOOR, 120) == Blocks.stone, 20);
	}

	/** Guard: passes before and after (1.7.10 already stops pistons at y <= 0 and, with (a), at 320). */
	public void testPistonStopsAtTheLimits() {
		// pushing a block below the floor, pushing one above the top, and a head above the top
		place(54, FLOOR, 120, Blocks.stone);
		place(54, FLOOR + 1, 120, Blocks.piston, 0);
		place(55, FLOOR + 1, 120, Blocks.redstone_block);
		place(58, TOP, 120, Blocks.stone);
		place(58, TOP - 1, 120, Blocks.piston, 1);
		place(59, TOP - 1, 120, Blocks.redstone_block);
		place(62, TOP, 120, Blocks.piston, 1);
		place(63, TOP, 120, Blocks.redstone_block);
		serverTicks(10);
		assertEqual(Arrays.asList(false, false, false), onServer(() -> Arrays.asList(BlockPistonBase.isExtended(world().getBlockMetadata(54, FLOOR + 1, 120)),
			BlockPistonBase.isExtended(world().getBlockMetadata(58, TOP - 1, 120)), BlockPistonBase.isExtended(world().getBlockMetadata(62, TOP, 120)))),
			"pistons extended: at -63 down onto a block at -64, at 318 up onto a block at 319, at 319 up");
		assertEqual(Arrays.asList("minecraft:stone", "minecraft:stone"), Arrays.asList(blockAt(54, FLOOR, 120), blockAt(58, TOP, 120)), "the blocks they would push");
	}

	public void testDragonEggStaysInTheRange() {
		// the egg at -63 in solid stone: the only air its teleport can find within 15 across and 7 up and down lies
		// below the floor, outside the world, which it must not take
		fill(70, FLOOR, 120, 100, FLOOR + 8, 150, Blocks.stone);
		place(85, FLOOR + 1, 135, Blocks.dragon_egg);
		onServer(() -> {
			for (int click = 0; click < 20; click++) {
				Blocks.dragon_egg.onBlockActivated(world(), 85, FLOOR + 1, 135, player(), 0, 0.0F, 0.0F, 0.0F);
			}
		});
		assertEqual("minecraft:dragon_egg", blockAt(85, FLOOR + 1, 135), "the egg at -63 after 20 clicks, with air only below the floor");
	}

	public void testEndermanTeleportsBelowZero() throws Exception {
		// inside x 111..115, y -42..-38, z 121..125, on a floor at -43
		shell(110, -43, 120, 116, -37, 126, Blocks.stone);
		place(120, 300, 130, Blocks.stone);
		EntityEnderman enderman = spawn(EntityEnderman::new, 111.5, -42.0, 121.5);
		Method teleportTo = EntityEnderman.class.getDeclaredMethod("teleportTo", double.class, double.class, double.class);
		teleportTo.setAccessible(true);
		List<Object> found = onServer(() -> {
			boolean inRoom = (Boolean) teleportTo.invoke(enderman, 113.5, -39.0, 123.5);
			double roomY = enderman.posY;
			// a start above the top looks down from there
			boolean onBlock = (Boolean) teleportTo.invoke(enderman, 120.5, 330.0, 130.5);
			return Arrays.asList(inRoom, roomY, onBlock, enderman.posY);
		});
		assertEqual(Arrays.asList(true, -42.0, true, 301.0), found,
			"teleports into the room at -39 (lands on its floor, y -42), and from 330 above a block at 300 (lands on it, 301)");
	}

	public void testSpreadplayersLandsAboveOldTop() {
		// a 3x3 hole to the void around 140, 140, with a block at 300 over each column
		for (int x = 139; x <= 141; x++) {
			for (int z = 139; z <= 141; z++) {
				clearColumn(x, z);
				place(x, 300, z, Blocks.stone);
			}
		}
		assertNoError(command("/spreadplayers 140 140 0 1 false " + playerName()));
		assertEqual(301.0, onServer(() -> player().boundingBox.minY), "the player's feet after /spreadplayers: on a block at 300");
	}

	public void testNoPathIntoTheVoid() {
		// a stone walk at -62 from x 0 to 10, with x 5 missing: under it, the void. The path finder reads the world
		// through a ChunkCache, whose bounds move to floor..top with this stage
		fill(0, FLOOR + 2, 130, 10, FLOOR + 2, 132, Blocks.stone);
		for (int z = 130; z <= 132; z++) {
			int at = z;
			onServer(() -> world().setBlockToAir(5, FLOOR + 2, at));
		}
		List<String> overTheGap = onServer(() -> {
			EntityPig walker = new EntityPig(world());
			walker.setPosition(1.5, FLOOR + 3, 131.5);
			EntityPig goal = new EntityPig(world());
			goal.setPosition(9.5, FLOOR + 3, 131.5);
			PathEntity path = world().getPathEntityToEntity(walker, goal, 16.0F, false, false, false, true);
			List<String> points = new ArrayList<>();
			for (int i = 0; path != null && i < path.getCurrentPathLength(); i++) {
				if (path.getPathPointFromIndex(i).xCoord == 5) {
					points.add(path.getPathPointFromIndex(i).toString());
				}
			}
			return points;
		});
		assertEqual(Collections.emptyList(), overTheGap, "points of a pig's path across a gap with the void under it (the drop has no ground)");
	}

	public void testPortalBelowZeroIsFound() {
		// a portal at -40, 5 blocks from where a pig arrives at 40, 4, 140
		for (int x = 45; x <= 46; x++) {
			for (int y = -40; y <= -38; y++) {
				int px = x;
				int py = y;
				onServer(() -> world().setBlock(px, py, 140, Blocks.portal, 0, 2));
			}
		}
		List<Object> found = onServer(() -> {
			EntityPig pig = new EntityPig(world());
			pig.setLocationAndAngles(40.5, 4.0, 140.5, 0.0F, 0.0F);
			boolean placed = new Teleporter(world()).placeInExistingPortal(pig, 40.5, 4.0, 140.5, 0.0F);
			return Arrays.asList(placed, pig.posY < 0.0);
		});
		assertEqual(Arrays.asList(true, true), found, "an existing portal at -40 found, and the pig put there");
	}

	public void testPortalAtTheTopFits() {
		// a stone roof at 318 over 33x33 around 400, 400: the first spots the portal maker tries are on it, at 319
		fill(384, 318, 384, 416, 318, 416, Blocks.stone);
		onServer(() -> {
			EntityPig pig = new EntityPig(world());
			pig.setLocationAndAngles(400.5, 319.0, 400.5, 0.0F, 0.0F);
			new Teleporter(world()).makePortal(pig);
		});
		int portals = onServer(() -> {
			int count = 0;
			for (int x = 380; x <= 420; x++) {
				for (int z = 380; z <= 420; z++) {
					for (int y = FLOOR; y <= TOP; y++) {
						if (world().getBlock(x, y, z) == Blocks.portal) {
							count++;
						}
					}
				}
			}
			return count;
		});
		assertEqual(6, portals, "portal blocks of the new portal (a whole one has 6)");
	}

	public void testPortalFrameBelowZeroLights() {
		// an obsidian frame at x 50..53, y -45..-41, z 145: inside x 51..52, y -44..-42
		for (int x = 50; x <= 53; x++) {
			place(x, -45, 145, Blocks.obsidian);
			place(x, -41, 145, Blocks.obsidian);
		}
		for (int y = -44; y <= -42; y++) {
			place(50, y, 145, Blocks.obsidian);
			place(53, y, 145, Blocks.obsidian);
		}
		// lit at the top of the inside, so the frame's bottom is searched for downwards (1.7.10: only above 0)
		assertTrue(onServer(() -> Blocks.portal.func_150000_e(world(), 51, -42, 145)), "the portal lit in a frame at -45..-41");
		assertEqual("minecraft:portal", blockAt(52, -42, 145), "inside the frame");
	}

	public void testLilyPadBelowZero() {
		// water in a basin at -31, the lily pad on it at -30
		place(60, -32, 140, Blocks.stone);
		for (int[] side : new int[][] {{59, 140}, {61, 140}, {60, 139}, {60, 141}}) {
			place(side[0], -31, side[1], Blocks.stone);
		}
		place(60, -31, 140, Blocks.water);
		place(60, -30, 140, Blocks.waterlily);
		onServer(() -> world().notifyBlockOfNeighborChange(60, -30, 140, Blocks.stone));
		assertEqual("minecraft:waterlily", blockAt(60, -30, 140), "a lily pad on water at -31, after a neighbour changed");
	}

	public void testMushroomBelowZero() {
		// on stone at -31, in the dark under the flat world's bedrock
		place(64, -31, 140, Blocks.stone);
		place(64, -30, 140, Blocks.brown_mushroom);
		onServer(() -> world().notifyBlockOfNeighborChange(64, -30, 140, Blocks.stone));
		assertEqual("minecraft:brown_mushroom", blockAt(64, -30, 140), "a mushroom at -30, after a neighbour changed");
	}

	public void testVineGrowsIntoTheTopLayer() {
		// a vine at 318 on a stone wall south of it, which goes up to 319
		place(68, 318, 141, Blocks.stone);
		place(68, TOP, 141, Blocks.stone);
		place(68, 318, 140, Blocks.vine, 1);
		// 1.7.10 picks the vine's way to grow at random; this one always picks up (1 of nextInt(6))
		Random up = new Random() {
			@Override
			public int nextInt(int bound) {
				return bound == 6 ? 1 : super.nextInt(bound);
			}
		};
		onServer(() -> {
			for (int tick = 0; tick < 400 && world().getBlock(68, TOP, 140) != Blocks.vine; tick++) {
				Blocks.vine.updateTick(world(), 68, 318, 140, up);
			}
		});
		assertEqual("minecraft:vine", blockAt(68, TOP, 140), "the vine at 318 grown up into 319");
	}

	public void testSpawnHeightsReachBelowZero() throws Exception {
		// chunk 9, 8 with a block at -40 (its lowest section, -3, starts at -48); chunk 9, 9 plain
		place(150, -40, 135, Blocks.stone);
		Method spawnPlace = SpawnerAnimals.class.getDeclaredMethod("func_151350_a", World.class, int.class, int.class);
		spawnPlace.setAccessible(true);
		List<Integer> lowest = onServer(() -> {
			int tall = Integer.MAX_VALUE;
			int plain = Integer.MAX_VALUE;
			for (int i = 0; i < 300; i++) {
				tall = Math.min(tall, ((ChunkPosition) spawnPlace.invoke(null, world(), 9, 8)).chunkPosY);
				plain = Math.min(plain, ((ChunkPosition) spawnPlace.invoke(null, world(), 9, 9)).chunkPosY);
			}
			return Arrays.asList(tall, plain);
		});
		assertTrue(lowest.get(0) < 0 && lowest.get(0) >= -48, "the lowest of 300 spawn heights tried in a chunk with a block at -40, " + lowest.get(0)
			+ ": below 0, but not below its lowest section (-48)");
		assertTrue(lowest.get(1) >= 0, "the lowest of 300 tried in a chunk with nothing below 0, " + lowest.get(1) + ": not below 0");
	}

	/** A falling sand entity there, already past its first update (which would remove it, with no sand block there). */
	private EntityFallingBlock fallingSand(double x, double y, double z) {
		return onServer(() -> {
			EntityFallingBlock sand = new EntityFallingBlock(world(), x, y, z, Blocks.sand);
			sand.field_145812_b = 1;
			world().spawnEntityInWorld(sand);
			return sand;
		});
	}

	private static void hold(Entity entity, double x, double y, double z) {
		entity.setPosition(x, y, z);
		entity.motionX = 0.0;
		entity.motionY = 0.0;
		entity.motionZ = 0.0;
	}
}
