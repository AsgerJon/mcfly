package io.github.asgerjon.mcfly.unittas.tests;

import io.github.asgerjon.mcfly.unittas.TasTestCase;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.ThreadedFileIOBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;

/**
 * McFly's tests: what they share. The heights of the taller Overworld, and helpers for building in the flat test world
 * (bedrock at y 0, two dirt, grass at y 3) and for saving chunks and reading them back from disk. Built on 1.7.10 alone,
 * so the tests run on the game as it is before each stage's code.
 */
public abstract class McFlyTestCase extends TasTestCase {

	/** The Overworld's lowest and highest y (spec §1). */
	protected static final int FLOOR = -64;
	protected static final int TOP = 319;

	/** The block's registry name (`minecraft:stone`), for messages that read. */
	protected static String name(Block block) {
		return Block.blockRegistry.getNameForObject(block);
	}

	/** The name of the block the server's Overworld has there. */
	protected final String blockAt(int x, int y, int z) {
		return onServer(() -> name(world().getBlock(x, y, z)));
	}

	/** Puts the block there in the server's Overworld; fails the test when the world refuses it. */
	protected final void place(int x, int y, int z, Block block) {
		place(x, y, z, block, 0);
	}

	protected final void place(int x, int y, int z, Block block, int meta) {
		boolean placed = onServer(() -> world().setBlock(x, y, z, block, meta, 3));
		assertTrue(placed, "the world refused " + name(block) + " at y " + y + " (x " + x + ", z " + z + ")");
	}

	/** Fills the box (both corners included); fails the test when the world refuses any of it. */
	protected final void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		int refused = onServer(() -> {
			int count = 0;
			for (int x = x1; x <= x2; x++) {
				for (int y = y1; y <= y2; y++) {
					for (int z = z1; z <= z2; z++) {
						if (!world().setBlock(x, y, z, block, 0, 2)) {
							count++;
						}
					}
				}
			}
			return count;
		});
		assertEqual(0, refused, "blocks of " + name(block) + " the world refused between y " + y1 + " and " + y2);
	}

	/** The box's outside layer (both corners included), hollow; fails the test when the world refuses any of it. */
	protected final void shell(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		int refused = onServer(() -> {
			int count = 0;
			for (int x = x1; x <= x2; x++) {
				for (int y = y1; y <= y2; y++) {
					for (int z = z1; z <= z2; z++) {
						boolean outside = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
						if (outside && !world().setBlock(x, y, z, block, 0, 3)) {
							count++;
						}
					}
				}
			}
			return count;
		});
		assertEqual(0, refused, "blocks of " + name(block) + " the world refused for the box between y " + y1 + " and " + y2);
	}

	/** The light of that kind the server's Overworld has stored there. */
	protected final int light(EnumSkyBlock type, int x, int y, int z) {
		return onServer(() -> world().getSavedLightValue(type, x, y, z));
	}

	/** Takes the flat layers (y 0..3) out of the column, leaving it empty: open to the void. */
	protected final void clearColumn(int x, int z) {
		onServer(() -> {
			for (int y = 0; y <= 3; y++) {
				world().setBlock(x, y, z, Blocks.air, 0, 2);
			}
		});
	}

	/** The server's height map at the column: the first y above the highest block that stops light. */
	protected final int height(int x, int z) {
		return onServer(() -> world().getHeightValue(x, z));
	}

	/** The folder the Overworld's region files are in. */
	protected static File regionFolder(WorldServer world) {
		return ((AnvilChunkLoader) world.theChunkProviderServer.currentChunkLoader).chunkSaveLocation;
	}

	/** Saves the Overworld's chunk as the game does, and waits until the file holds it. */
	protected final void saveChunk(int chunkX, int chunkZ) {
		onServer(() -> {
			Chunk chunk = world().getChunkFromChunkCoords(chunkX, chunkZ);
			world().theChunkProviderServer.currentChunkLoader.saveChunk(world(), chunk);
			return null;
		});
		try {
			ThreadedFileIOBase.threadedIOInstance.waitForFinish();
		} catch (InterruptedException e) {
			throw new AssertionError("interrupted while the chunk was being written");
		}
	}

	/**
	 * The chunk as the file has it: read by a loader of its own, which has nothing waiting to be written, so it reads
	 * the disk. The chunk it returns isn't part of the world; read it only inside a server step.
	 */
	protected final Chunk readBackFromDisk(int chunkX, int chunkZ) {
		Chunk chunk = onServer(() -> new AnvilChunkLoader(regionFolder(world())).loadChunk(world(), chunkX, chunkZ));
		assertIsNotNone(chunk, "chunk " + chunkX + ", " + chunkZ + " in its region file");
		return chunk;
	}

	/** The chunk's saved tag (its `Level` compound), as the file has it. */
	protected final NBTTagCompound savedTag(int chunkX, int chunkZ) {
		NBTTagCompound root = onServer(() -> {
			DataInputStream in = RegionFileCache.getChunkInputStream(regionFolder(world()), chunkX, chunkZ);
			if (in == null) {
				return null;
			}
			try {
				return CompressedStreamTools.read(in);
			} finally {
				in.close();
			}
		});
		assertIsNotNone(root, "chunk " + chunkX + ", " + chunkZ + " in its region file");
		return root.getCompoundTag("Level");
	}

	/** Writes the tag (with its `Level` compound) as the chunk's entry in the region file, as the game writes one. */
	protected final void writeChunkTag(int chunkX, int chunkZ, NBTTagCompound root) {
		onServer(() -> {
			DataOutputStream out = RegionFileCache.getChunkOutputStream(regionFolder(world()), chunkX, chunkZ);
			try {
				CompressedStreamTools.write(root, out);
			} finally {
				out.close();
			}
			return null;
		});
	}

	/** A height map's index for the column inside its chunk, as 1.7.10 keeps it. */
	protected static int column(int x, int z) {
		return (z & 15) << 4 | (x & 15);
	}

	/** The block's place in a section's arrays, as 1.7.10 keeps it. */
	protected static int inSection(int x, int y, int z) {
		return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
	}

	/** The world of the dimension (-1 the Nether, 1 the End), loaded if it isn't; call inside a server step. */
	protected static WorldServer dimension(int id) {
		return MinecraftServer.getServer().worldServerForDimension(id);
	}

	/** True when the world takes stone at the y (and puts the old block back); call inside a server step. */
	protected static boolean takes(World world, int x, int y, int z) {
		Block before = world.getBlock(x, y, z);
		int meta = world.getBlockMetadata(x, y, z);
		if (!world.setBlock(x, y, z, before == Blocks.stone ? Blocks.cobblestone : Blocks.stone, 0, 2)) {
			return false;
		}
		world.setBlock(x, y, z, before, meta, 2);
		return true;
	}
}
