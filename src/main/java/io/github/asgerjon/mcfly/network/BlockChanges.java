package io.github.asgerjon.mcfly.network;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;

/**
 * Changed blocks of one column, as the server collects them in a tick (`PlayerManager.PlayerInstance`) and sends them
 * batched (`S22PacketMultiBlockChange`). 1.7.10 packs a change's place into a short with 8 bits of y; here it is an int
 * with x and z inside the chunk in its top 8 bits and y in full in its low 16 (spec §7: y as a signed 12-bit value at
 * least). A batched record is that int, then the block as 1.7.10 sends it: its id (12 bits) and metadata (4 bits).
 */
public final class BlockChanges {

	/** The bytes of one record in a batch: 1.7.10's were 4. */
	public static final int RECORD_BYTES = 6;

	private BlockChanges() {
	}

	/** The place of a changed block: x and z inside the chunk, 0..15; y in full. */
	public static int position(int x, int y, int z) {
		return x << 28 | z << 24 | y & 0xFFFF;
	}

	public static int x(int position) {
		return position >>> 28;
	}

	public static int y(int position) {
		return (short) position;
	}

	public static int z(int position) {
		return position >>> 24 & 15;
	}

	/** The batch's records for the first `count` places, with the blocks the chunk has there now. */
	public static byte[] records(int count, int[] positions, Chunk chunk) {
		byte[] records = new byte[count * RECORD_BYTES];
		for (int i = 0, at = 0; i < count; i++, at += RECORD_BYTES) {
			int position = positions[i];
			int x = x(position);
			int y = y(position);
			int z = z(position);
			int block = (Block.getIdFromBlock(chunk.getBlock(x, y, z)) & 4095) << 4 | chunk.getBlockMetadata(x, y, z) & 15;
			records[at] = (byte) (position >>> 24);
			records[at + 1] = (byte) (position >>> 16);
			records[at + 2] = (byte) (position >>> 8);
			records[at + 3] = (byte) position;
			records[at + 4] = (byte) (block >>> 8);
			records[at + 5] = (byte) block;
		}
		return records;
	}
}
