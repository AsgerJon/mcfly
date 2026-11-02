package io.github.asgerjon.mcfly.unittas.tests;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stage (a), suite `storage`: the Overworld holds blocks from y -64 to 319, refuses them outside, and keeps its height
 * map, block entities, random ticks and entities working over that range; the Nether and the End stay 0..255. Each test
 * builds at its own x near spawn (z 8); the random-tick tests use whole chunks at z 16..31.
 */
public class StorageTests extends McFlyTestCase {

	public void testWriteAndReadBelowZero() {
		place(32, -30, 8, Blocks.stone);
		place(33, -30, 8, Blocks.wool, 14);
		assertEqual("minecraft:stone", blockAt(32, -30, 8));
		assertEqual(14, onServer(() -> world().getBlockMetadata(33, -30, 8)), "the wool's colour");
		// the column itself, not only the world
		assertEqual("minecraft:stone", onServer(() -> name(world().getChunkFromBlockCoords(32, 8).getBlock(0, -30, 8))), "read through the chunk");
	}

	public void testWriteAndReadAboveOldTop() {
		place(34, 300, 8, Blocks.stone);
		place(35, 300, 8, Blocks.wool, 14);
		assertEqual("minecraft:stone", blockAt(34, 300, 8));
		assertEqual(14, onServer(() -> world().getBlockMetadata(35, 300, 8)), "the wool's colour");
		assertEqual("minecraft:stone", onServer(() -> name(world().getChunkFromBlockCoords(34, 8).getBlock(2, 300, 8))), "read through the chunk");
	}

	public void testFloorAndTopAreTheLimits() {
		place(36, FLOOR, 8, Blocks.stone);
		place(36, TOP, 8, Blocks.stone);
		assertFalse(onServer(() -> world().setBlock(37, FLOOR - 1, 8, Blocks.stone)), "the world took stone at y -65, below the floor");
		assertFalse(onServer(() -> world().setBlock(37, TOP + 1, 8, Blocks.stone)), "the world took stone at y 320, above the top");
		assertEqual("minecraft:air", blockAt(37, FLOOR - 1, 8), "below the floor");
		assertEqual("minecraft:air", blockAt(37, TOP + 1, 8), "above the top");
	}

	public void testLoadedOnlyInsideTheRange() {
		assertTrue(onServer(() -> world().blockExists(38, FLOOR, 8)), "y -64 of a loaded chunk counts as loaded");
		assertTrue(onServer(() -> world().blockExists(38, TOP, 8)), "y 319 of a loaded chunk counts as loaded");
		assertFalse(onServer(() -> world().blockExists(38, FLOOR - 1, 8)), "y -65 counts as loaded");
		assertFalse(onServer(() -> world().blockExists(38, TOP + 1, 8)), "y 320 counts as loaded");
	}

	public void testSetblockCommandRange() {
		assertNoError(command("/setblock 40 -64 8 stone"));
		assertNoError(command("/setblock 40 319 8 stone"));
		assertEqual("minecraft:stone", blockAt(40, FLOOR, 8), "after /setblock at y -64");
		assertEqual("minecraft:stone", blockAt(40, TOP, 8), "after /setblock at y 319");
		assertError(command("/setblock 41 -65 8 stone"), "Cannot place block outside of the world");
		assertError(command("/setblock 41 320 8 stone"), "Cannot place block outside of the world");
	}

	public void testHeightMapOfAnEmptyColumnIsTheFloor() {
		clearColumn(42, 8);
		assertEqual(FLOOR, height(42, 8), "the height map of a column with nothing in it");
		assertEqual(FLOOR, onServer(() -> world().getPrecipitationHeight(42, 8)), "the precipitation height of a column with nothing in it");
	}

	public void testHeightMapFollowsTallBlocks() {
		place(44, 300, 8, Blocks.stone);
		assertEqual(301, height(44, 8), "a block at 300 over the flat ground");
		onServer(() -> world().setBlockToAir(44, 300, 8));
		assertEqual(4, height(44, 8), "the block at 300 taken away again (the grass is at 3)");
		clearColumn(46, 8);
		place(46, -40, 8, Blocks.stone);
		assertEqual(-39, height(46, 8), "a column cleared to the void, with one block at -40");
	}

	/**
	 * A bug, found by `LightTests.testRemovingATopBlockRelightsTheColumn`. 1.7.10's full height map pass
	 * (`generateSkylightMap`, run when a column gets a new section above its height) starts one block below the top of
	 * the highest section, so a block in that layer goes unseen: y 255 in 1.7.10, 319 here.
	 */
	public void testHeightMapSeesTheTopLayer() {
		// chunk 4, 0
		place(70, TOP, 8, Blocks.stone);
		assertEqual(320, height(70, 8), "a block at 319 over the flat ground");
		// a block in a new section of the same chunk, above the height map there: the chunk's full pass runs
		place(74, 100, 8, Blocks.stone);
		assertEqual(320, height(70, 8), "the block at 319, after the chunk's full height map pass");
	}

	public void testBlockEntityBelowZero() {
		place(48, -20, 8, Blocks.chest);
		String stored = onServer(() -> {
			TileEntity tile = world().getTileEntity(48, -20, 8);
			if (!(tile instanceof TileEntityChest)) {
				return "no chest block entity (the world gave " + tile + ")";
			}
			((TileEntityChest) tile).setInventorySlotContents(0, new ItemStack(Items.diamond, 3));
			ItemStack back = ((TileEntityChest) world().getTileEntity(48, -20, 8)).getStackInSlot(0);
			return id(back) + " x" + (back == null ? 0 : back.stackSize);
		});
		assertEqual("minecraft:diamond:0 x3", stored, "the chest at y -20");
	}

	public void testRandomTicksBelowZero() {
		driesOut(4, 1, -40);
	}

	public void testRandomTicksAboveOldTop() {
		driesOut(5, 1, 300);
	}

	public void testEntityBelowZeroKeepsItsChunkSlot() {
		fill(51, -41, 7, 53, -41, 9, Blocks.stone);
		EntityItem item = onServer(() -> {
			// an item's y is the middle of its box, 0.25 high: this drops it the last 1/8 block onto the platform (a box
			// that starts inside the platform would fall through it, as it would at any height)
			EntityItem made = new EntityItem(world(), 52.5, -39.75, 8.5, new ItemStack(Blocks.stone));
			made.motionX = 0.0;
			made.motionY = 0.0;
			made.motionZ = 0.0;
			world().spawnEntityInWorld(made);
			return made;
		});
		ChunkEntries entries = new ChunkEntries(item);
		onServer(() -> MinecraftForge.EVENT_BUS.register(entries));
		try {
			serverTicks(40);
		} finally {
			onServer(() -> MinecraftForge.EVENT_BUS.unregister(entries));
		}
		assertFalse(onServer(() -> item.isDead), "the item on the platform at y -41 is gone");
		assertAlmostEqual(onServer(() -> item.posY), -39.875, 0.01);
		// 1.7.10 compares the entity's y >> 4 with its clamped list index, and moves it every tick
		assertTrue(entries.count <= 1, "the item at y -40 was put into its chunk " + entries.count + " times in 40 ticks");
	}

	public void testOverworldHeightIs320() {
		assertEqual(320, onServer(() -> world().provider.getHeight()), "the Overworld provider's height");
		assertEqual(320, onServer(() -> world().provider.getActualHeight()), "the Overworld provider's actual height");
		assertEqual(320, onServer(() -> world().getHeight()), "the Overworld's height");
	}

	/** Guard: passes before and after; the Nether and the End keep 1.7.10's range. */
	public void testNetherAndEndKeep0To255() {
		for (int id : new int[] {-1, 1}) {
			List<String> wrong = onServer(() -> {
				WorldServer other = dimension(id);
				List<String> found = new ArrayList<>();
				if (other.provider.getHeight() != 256) {
					found.add("height " + other.provider.getHeight());
				}
				if (!takes(other, 8, 0, 8)) {
					found.add("refused y 0");
				}
				if (!takes(other, 8, 255, 8)) {
					found.add("refused y 255");
				}
				if (takes(other, 8, -1, 8)) {
					found.add("took y -1");
				}
				if (takes(other, 8, 256, 8)) {
					found.add("took y 256");
				}
				return found;
			});
			assertEqual(Collections.emptyList(), wrong, id == -1 ? "the Nether" : "the End");
		}
	}

	/**
	 * Fills the chunk's layer at y with dry farmland, which turns to dirt at its first random tick, and waits for some to
	 * turn. The chunk must lie within 7 chunks of the player, where the server ticks blocks at random.
	 */
	private void driesOut(int chunkX, int chunkZ, int y) {
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		fill(x0, y, z0, x0 + 15, y, z0 + 15, Blocks.farmland);
		waitUntil("some of the dry farmland at y " + y + " to turn to dirt at a random tick", () -> {
			for (int x = x0; x < x0 + 16; x++) {
				for (int z = z0; z < z0 + 16; z++) {
					if (world().getBlock(x, y, z) == Blocks.dirt) {
						return true;
					}
				}
			}
			return false;
		}, 600);
	}

	/** Counts how often one entity is put into a chunk (Forge's EnteringChunk, posted on every add). */
	public static final class ChunkEntries {

		private final Entity entity;
		volatile int count;

		ChunkEntries(Entity entity) {
			this.entity = entity;
		}

		@SubscribeEvent
		public void onEntering(EntityEvent.EnteringChunk event) {
			if (event.entity == entity) {
				count++;
			}
		}
	}
}
