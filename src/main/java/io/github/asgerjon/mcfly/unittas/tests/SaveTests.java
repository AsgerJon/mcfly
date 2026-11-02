package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stage (a), suite `storage`: chunks with blocks outside 0..255 are saved and read back from disk; what an unmodded
 * 1.7.10 reads of them stays as it expects; and a chunk saved by plain 1.7.10 loads as it was. Each test uses its own
 * chunk, north of spawn.
 */
public class SaveTests extends McFlyTestCase {

	public void testTallSectionsSurviveSaveAndLoad() {
		// chunk 3, -2
		int x = 53;
		int z = -27;
		place(x, FLOOR, z, Blocks.stone);
		place(x, -1, z, Blocks.wool, 3);
		place(x, 0, z, Blocks.gold_block);
		place(x, 255, z, Blocks.diamond_block);
		place(x, 256, z, Blocks.wool, 14);
		place(x, TOP, z, Blocks.glowstone);
		place(x + 2, -20, z, Blocks.chest);
		onServer(() -> ((TileEntityChest) world().getTileEntity(x + 2, -20, z)).setInventorySlotContents(0, new ItemStack(Items.diamond, 3)));
		saveChunk(3, -2);
		Chunk read = readBackFromDisk(3, -2);
		List<String> column = onServer(() -> {
			List<String> blocks = new ArrayList<>();
			for (int y : new int[] {FLOOR, -1, 0, 255, 256, TOP}) {
				blocks.add(y + " " + name(read.getBlock(x & 15, y, z & 15)) + ":" + read.getBlockMetadata(x & 15, y, z & 15));
			}
			return blocks;
		});
		assertEqual(Arrays.asList("-64 minecraft:stone:0", "-1 minecraft:wool:3", "0 minecraft:gold_block:0", "255 minecraft:diamond_block:0",
			"256 minecraft:wool:14", "319 minecraft:glowstone:0"), column, "the column read back from disk");
		String chest = onServer(() -> {
			TileEntity tile = read.chunkTileEntityMap.get(new ChunkPosition((x + 2) & 15, -20, z & 15));
			if (!(tile instanceof TileEntityChest)) {
				return "no chest block entity (found " + tile + ")";
			}
			ItemStack stack = ((TileEntityChest) tile).getStackInSlot(0);
			return id(stack) + " x" + (stack == null ? 0 : stack.stackSize);
		});
		assertEqual("minecraft:diamond:0 x3", chest, "the chest at y -20, read back from disk");
	}

	public void testVanillaTagsStayReadable() {
		// chunk 4, -2; three columns side by side at z -27
		int z = -27;
		place(64, 300, z, Blocks.stone);
		clearColumn(65, z);
		place(65, -30, z, Blocks.stone);
		// x 66: the flat ground alone
		saveChunk(4, -2);
		NBTTagCompound level = savedTag(4, -2);

		// an unmodded 1.7.10 fails the whole chunk on a section numbered outside 0..15 (spec §0.4)
		List<Integer> outside = new ArrayList<>();
		for (int number : sectionNumbers(level, "Sections")) {
			if (number < 0 || number > 15) {
				outside.add(number);
			}
		}
		assertEqual(Collections.emptyList(), outside, "Sections numbered outside 0..15");

		int[] heightMap = level.getIntArray("HeightMap");
		assertEqual(256, heightMap.length, "HeightMap's length");
		List<Integer> wrong = new ArrayList<>();
		for (int value : heightMap) {
			if (value < 0 || value > 256) {
				wrong.add(value);
			}
		}
		assertEqual(Collections.emptyList(), wrong, "HeightMap values outside 0..256");
		assertEqual(Arrays.asList(4, 0, 4), Arrays.asList(heightMap[column(64, z)], heightMap[column(65, z)], heightMap[column(66, z)]),
			"HeightMap as plain 1.7.10 works it out from y 0..255 (a block at 300 over the ground; a cleared column with a block at -30; the ground)");

		assertTrue(level.hasKey("TallHeightMap", 11), "the saved chunk has a TallHeightMap");
		int[] tall = level.getIntArray("TallHeightMap");
		assertEqual(Arrays.asList(301, -29, 4), Arrays.asList(tall[column(64, z)], tall[column(65, z)], tall[column(66, z)]), "TallHeightMap");

		assertTrue(level.hasKey("TallSections", 9), "the saved chunk has a TallSections list");
		List<Integer> tallNumbers = sectionNumbers(level, "TallSections");
		assertIn(18, tallNumbers, "TallSections (the block at 300)");
		assertIn(-2, tallNumbers, "TallSections (the block at -30)");
		for (int number : tallNumbers) {
			assertFalse(number >= 0 && number <= 15, "TallSections has section " + number + ", which belongs in Sections");
		}
	}

	public void testPlain1710ChunkLoads() {
		int chunkX = -40;
		int chunkZ = -40;
		if (onServer(() -> world().theChunkProviderServer.chunkExists(chunkX, chunkZ))) {
			skipTest("chunk -40, -40 is loaded already; the test needs one the world hasn't made");
		}
		writeChunkTag(chunkX, chunkZ, plainChunk(chunkX, chunkZ));
		onServer(() -> world().theChunkProviderServer.loadChunk(chunkX, chunkZ));
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		assertEqual(Arrays.asList("minecraft:air", "minecraft:stone", "minecraft:stone", "minecraft:stone"),
			Arrays.asList(blockAt(x0, 1, z0), blockAt(x0 + 1, 255, z0), blockAt(x0 + 2, 2, z0), blockAt(x0 + 2, 0, z0)),
			"the blocks as saved (the empty column; the one topped at 255; a plain one, at 2 and 0)");
		assertEqual(Arrays.asList("minecraft:air", "minecraft:air"), Arrays.asList(blockAt(x0 + 2, -1, z0), blockAt(x0 + 2, FLOOR, z0)),
			"below 0, where plain 1.7.10 saved nothing");
		// 0 was plain 1.7.10's "nothing here"; every other height keeps its absolute value (spec §6)
		assertEqual(Arrays.asList(FLOOR, 256, 3), Arrays.asList(height(x0, z0), height(x0 + 1, z0), height(x0 + 2, z0)),
			"the heights of the empty column, the one topped at 255, and a plain one");
	}

	/** The `Y` of every entry in the chunk's section list of that name. */
	private static List<Integer> sectionNumbers(NBTTagCompound level, String list) {
		NBTTagList sections = level.getTagList(list, 10);
		List<Integer> numbers = new ArrayList<>();
		for (int i = 0; i < sections.tagCount(); i++) {
			numbers.add((int) sections.getCompoundTagAt(i).getByte("Y"));
		}
		return numbers;
	}

	/**
	 * A chunk's tag as plain 1.7.10 writes it: stone at y 0..2 in every column but the first (left empty, height 0), and
	 * one more stone at y 255 in the second column (height 256). Lit, populated, no entities.
	 */
	private static NBTTagCompound plainChunk(int chunkX, int chunkZ) {
		byte stone = (byte) Block.getIdFromBlock(Blocks.stone);
		int[] heights = new int[256];
		byte[] bottom = new byte[4096];
		byte[] top = new byte[4096];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (x == 0 && z == 0) {
					continue;
				}
				for (int y = 0; y <= 2; y++) {
					bottom[inSection(x, y, z)] = stone;
				}
				heights[column(x, z)] = 3;
			}
		}
		top[inSection(1, 255, 0)] = stone;
		heights[column(1, 0)] = 256;

		NBTTagList sections = new NBTTagList();
		sections.appendTag(plainSection(0, bottom));
		sections.appendTag(plainSection(15, top));
		byte[] biomes = new byte[256];
		Arrays.fill(biomes, (byte) 1);

		NBTTagCompound level = new NBTTagCompound();
		level.setByte("V", (byte) 1);
		level.setInteger("xPos", chunkX);
		level.setInteger("zPos", chunkZ);
		level.setLong("LastUpdate", 0L);
		level.setIntArray("HeightMap", heights);
		level.setBoolean("TerrainPopulated", true);
		level.setBoolean("LightPopulated", true);
		level.setLong("InhabitedTime", 0L);
		level.setTag("Sections", sections);
		level.setByteArray("Biomes", biomes);
		level.setTag("Entities", new NBTTagList());
		level.setTag("TileEntities", new NBTTagList());
		NBTTagCompound root = new NBTTagCompound();
		root.setTag("Level", level);
		return root;
	}

	private static NBTTagCompound plainSection(int number, byte[] blocks) {
		byte[] skyLight = new byte[2048];
		Arrays.fill(skyLight, (byte) 0xFF);
		NBTTagCompound section = new NBTTagCompound();
		section.setByte("Y", (byte) number);
		section.setByteArray("Blocks", blocks);
		section.setByteArray("Data", new byte[2048]);
		section.setByteArray("BlockLight", new byte[2048]);
		section.setByteArray("SkyLight", skyLight);
		return section;
	}
}
