package io.github.asgerjon.mcfly.world;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * A column as McFly sees it: 1.7.10's `Chunk`, given its other sections by `mixins.early.storage.MixinChunk`. Sections
 * 0..15 stay in 1.7.10's own array (`getBlockStorageArray()`); the rest of the light store is kept beside it.
 */
public interface TallChunk {

	/** The range of the chunk's world. */
	HeightRange mcfly$range();

	/** The section of that number (y >> 4), or null when the column has none there or the store has no such number. */
	ExtendedBlockStorage mcfly$section(int number);

	/** Puts the section in at its number; a number outside the light store is ignored. */
	void mcfly$setSection(int number, ExtendedBlockStorage section);

	/**
	 * Every block section, from the floor up, null where empty: in 1.7.10's range its own array, otherwise an array the
	 * chunk keeps and fills again on each call (for one thread's use at a time).
	 */
	ExtendedBlockStorage[] mcfly$blockSections();
}
