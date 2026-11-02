package io.github.asgerjon.mcfly.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The tall parts of a saved chunk (PLAN-mcfly.md §1 "Saving"), beside what 1.7.10 writes itself. `Sections` keeps 0..15
 * and `HeightMap` what an unmodded 1.7.10 works out from y 0..255, so that game still reads the chunk (spec §0.4); the
 * other sections go in `TallSections` and the real height map in `TallHeightMap`. A chunk without `TallHeightMap` was
 * saved by plain 1.7.10. The tag names outlive the mod id: chunks saved before the fold into EFR load after it.
 */
public final class ChunkSaving {

	static final String TALL_SECTIONS = "TallSections";
	static final String TALL_HEIGHT_MAP = "TallHeightMap";

	private static final Logger LOG = LogManager.getLogger("McFly");

	private ChunkSaving() {
	}

	/** After 1.7.10 wrote the chunk's `Level` tag: adds the tall parts and makes `HeightMap` one plain 1.7.10 reads. */
	public static void write(Chunk chunk, World world, NBTTagCompound level) {
		TallChunk tall = (TallChunk) chunk;
		HeightRange range = tall.mcfly$range();
		if (range.vanilla) {
			return;
		}
		boolean sky = !world.provider.hasNoSky;
		NBTTagList sections = new NBTTagList();
		for (int number = range.lowestLightSection; number < range.lowestLightSection + range.lightSections; number++) {
			ExtendedBlockStorage section = tall.mcfly$section(number);
			if ((number & ~15) != 0 && section != null) {
				sections.appendTag(sectionTag(number, section, sky));
			}
		}
		level.setTag(TALL_SECTIONS, sections);
		level.setIntArray(TALL_HEIGHT_MAP, chunk.heightMap.clone());
		level.setIntArray("HeightMap", plainHeightMap(chunk));
	}

	/** After 1.7.10 read the chunk from its `Level` tag: the tall parts, or a plain 1.7.10 chunk's height map re-based. */
	public static void read(Chunk chunk, World world, NBTTagCompound level) {
		if (chunk == null) {
			return;
		}
		TallChunk tall = (TallChunk) chunk;
		HeightRange range = tall.mcfly$range();
		if (range.vanilla) {
			return;
		}
		int[] tallHeights = level.getIntArray(TALL_HEIGHT_MAP);
		if (tallHeights.length == 256) {
			chunk.heightMap = tallHeights;
		} else {
			// saved by plain 1.7.10, whose 0 meant an empty column; every other height keeps its y (spec §6)
			for (int column = 0; column < chunk.heightMap.length; column++) {
				if (chunk.heightMap[column] == 0) {
					chunk.heightMap[column] = range.floor;
				}
			}
		}
		boolean sky = !world.provider.hasNoSky;
		NBTTagList sections = level.getTagList(TALL_SECTIONS, 10);
		for (int i = 0; i < sections.tagCount(); i++) {
			NBTTagCompound tag = sections.getCompoundTagAt(i);
			int number = tag.getByte("Y");
			// 0..15 come from Sections; a number outside the store has no place in this world
			if ((number & ~15) == 0 || !range.hasLightSection(number)) {
				continue;
			}
			ExtendedBlockStorage section = section(tag, number, sky);
			if (section == null) {
				LOG.warn("Chunk {}, {}: dropped the damaged section {} of {}", chunk.xPosition, chunk.zPosition, number, TALL_SECTIONS);
			} else {
				tall.mcfly$setSection(number, section);
			}
		}
	}

	/** A section's entry, as 1.7.10 writes one in `Sections`. */
	private static NBTTagCompound sectionTag(int number, ExtendedBlockStorage section, boolean sky) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setByte("Y", (byte) number);
		tag.setByteArray("Blocks", section.getBlockLSBArray());
		if (section.getBlockMSBArray() != null) {
			tag.setByteArray("Add", section.getBlockMSBArray().data);
		}
		tag.setByteArray("Data", section.getMetadataArray().data);
		tag.setByteArray("BlockLight", section.getBlocklightArray().data);
		tag.setByteArray("SkyLight", sky ? section.getSkylightArray().data : new byte[section.getBlocklightArray().data.length]);
		return tag;
	}

	/** The section an entry describes, as 1.7.10 reads one from `Sections`; null when its arrays have the wrong size. */
	private static ExtendedBlockStorage section(NBTTagCompound tag, int number, boolean sky) {
		byte[] blocks = tag.getByteArray("Blocks");
		byte[] data = tag.getByteArray("Data");
		byte[] blockLight = tag.getByteArray("BlockLight");
		byte[] skyLight = tag.getByteArray("SkyLight");
		boolean hasAdd = tag.hasKey("Add", 7);
		if (blocks.length != 4096 || data.length != 2048 || blockLight.length != 2048 || sky && skyLight.length != 2048
			|| hasAdd && tag.getByteArray("Add").length != 2048) {
			return null;
		}
		ExtendedBlockStorage section = new ExtendedBlockStorage(number << 4, sky);
		section.setBlockLSBArray(blocks);
		if (hasAdd) {
			section.setBlockMSBArray(new NibbleArray(tag.getByteArray("Add"), 4));
		}
		section.setBlockMetadataArray(new NibbleArray(data, 4));
		section.setBlocklightArray(new NibbleArray(blockLight, 4));
		if (sky) {
			section.setSkylightArray(new NibbleArray(skyLight, 4));
		}
		section.removeInvalidBlocks();
		return section;
	}

	/**
	 * The height map plain 1.7.10 works out from y 0..255 alone: the first y above the highest block there that stops
	 * light, 0 when there is none. Only a column whose highest such block is above 255 needs looking at.
	 */
	private static int[] plainHeightMap(Chunk chunk) {
		int[] plain = new int[256];
		for (int column = 0; column < 256; column++) {
			int height = chunk.heightMap[column];
			if (height > 256) {
				int x = column & 15;
				int z = column >> 4;
				height = 256;
				while (height > 0 && chunk.func_150808_b(x, height - 1, z) == 0) {
					height--;
				}
			} else if (height < 0) {
				height = 0;
			}
			plain[column] = height;
		}
		return plain;
	}
}
