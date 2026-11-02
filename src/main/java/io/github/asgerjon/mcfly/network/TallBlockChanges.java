package io.github.asgerjon.mcfly.network;

import net.minecraft.world.chunk.Chunk;

/**
 * 1.7.10's batched block change (`S22PacketMultiBlockChange`) as McFly fills it, by `mixins.early.network`: its
 * constructor takes places as shorts with 8 bits of y, so McFly's server makes an empty one and fills it from full places.
 */
public interface TallBlockChanges {

	/** Fills the packet with the first `count` places of `BlockChanges` and the blocks the chunk has there. */
	void mcfly$fill(int count, int[] positions, Chunk chunk);
}
