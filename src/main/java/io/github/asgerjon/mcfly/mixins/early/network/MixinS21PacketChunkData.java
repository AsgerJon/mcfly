package io.github.asgerjon.mcfly.mixins.early.network;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallChunk;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A chunk's sections on the wire (spec §7). The section masks cover the whole light store (bit i: the store's i-th
 * section from the bottom; 1.7.10: 16 bits, sections 0..15), and travel as ints, not shorts. The data keeps 1.7.10's
 * layout over more sections: every sent section's blocks, then their metadata, block light, sky light (in a world with
 * a sky) and high block-id bits, then the biomes of a whole chunk. A whole chunk leaves out empty sections, as 1.7.10
 * does, but not the store's light-only rooms past the floor and the top. Neither the builder nor the reader shares a
 * buffer any more (1.7.10 used one static array for both).
 */
@Mixin(S21PacketChunkData.class)
public abstract class MixinS21PacketChunkData {

	/** Each section's share in a world with a sky: blocks (4096), metadata, block light, sky light (2048 each). */
	@Unique
	private static final int SECTION_BYTES = 4096 + 2048 * 3;

	/** A section's high block-id bits, sent only when it has some. */
	@Unique
	private static final int HIGH_BITS_BYTES = 2048;

	@Shadow
	private int field_149284_a;

	@Shadow
	private int field_149282_b;

	@Shadow
	private int field_149283_c;

	@Shadow
	private int field_149280_d;

	@Shadow
	private byte[] field_149281_e;

	@Shadow
	private byte[] field_149278_f;

	@Shadow
	private boolean field_149279_g;

	@Shadow
	private int field_149285_h;

	@Shadow(remap = false)
	private Semaphore deflateGate;

	@Shadow(remap = false)
	private void deflate() {
	}

	/**
	 * @author AsgerJon
	 * @reason The largest a chunk's data can be: every section of the tall light store, with high id bits.
	 */
	@Overwrite
	public static int func_149275_c() {
		return HeightRange.TALL.lightSections * (SECTION_BYTES + HIGH_BITS_BYTES) + 256;
	}

	/**
	 * @author AsgerJon
	 * @reason The sections of the whole light store, by the wider mask; the data is built in an array of its exact size.
	 */
	@Overwrite
	public static S21PacketChunkData.Extracted func_149269_a(Chunk chunk, boolean whole, int mask) {
		TallChunk tall = (TallChunk) chunk;
		HeightRange range = tall.mcfly$range();
		boolean sky = !chunk.worldObj.provider.hasNoSky;
		S21PacketChunkData.Extracted extracted = new S21PacketChunkData.Extracted();
		if (whole) {
			chunk.sendUpdates = true;
		}
		ExtendedBlockStorage[] sent = new ExtendedBlockStorage[range.lightSections];
		int count = 0;
		int highBits = 0;
		for (int i = 0; i < range.lightSections; i++) {
			int number = range.lowestLightSection + i;
			ExtendedBlockStorage section = tall.mcfly$section(number);
			if (section != null && (!whole || !section.isEmpty() || !range.hasBlockSection(number)) && (mask & 1 << i) != 0) {
				sent[i] = section;
				extracted.field_150280_b |= 1 << i;
				count++;
				if (section.getBlockMSBArray() != null) {
					extracted.field_150281_c |= 1 << i;
					highBits++;
				}
			}
		}
		byte[] data = new byte[count * (sky ? SECTION_BYTES : SECTION_BYTES - 2048) + highBits * HIGH_BITS_BYTES + (whole ? 256 : 0)];
		int at = 0;
		for (ExtendedBlockStorage section : sent) {
			if (section != null) {
				at = mcfly$copy(section.getBlockLSBArray(), data, at);
			}
		}
		for (ExtendedBlockStorage section : sent) {
			if (section != null) {
				at = mcfly$copy(section.getMetadataArray(), data, at);
			}
		}
		for (ExtendedBlockStorage section : sent) {
			if (section != null) {
				at = mcfly$copy(section.getBlocklightArray(), data, at);
			}
		}
		if (sky) {
			for (ExtendedBlockStorage section : sent) {
				if (section != null) {
					at = mcfly$copy(section.getSkylightArray(), data, at);
				}
			}
		}
		for (ExtendedBlockStorage section : sent) {
			if (section != null && section.getBlockMSBArray() != null) {
				at = mcfly$copy(section.getBlockMSBArray(), data, at);
			}
		}
		if (whole) {
			mcfly$copy(chunk.getBiomeArray(), data, at);
		}
		extracted.field_150282_a = data;
		return extracted;
	}

	/**
	 * @author AsgerJon
	 * @reason The masks are ints. The compressed data goes into an array of this packet's own (1.7.10 used a static one
	 * that the server's chunk builder also used, so a game reading and building at once, singleplayer on a wire, broke).
	 */
	@Overwrite
	public void readPacketData(PacketBuffer data) throws IOException {
		this.field_149284_a = data.readInt();
		this.field_149282_b = data.readInt();
		this.field_149279_g = data.readBoolean();
		this.field_149283_c = data.readInt();
		this.field_149280_d = data.readInt();
		this.field_149285_h = data.readInt();
		byte[] compressed = new byte[this.field_149285_h];
		data.readBytes(compressed);
		// room for sky light whether or not the world has a sky, as 1.7.10 has; the client reads what it needs
		this.field_149278_f = new byte[SECTION_BYTES * Integer.bitCount(this.field_149283_c) + HIGH_BITS_BYTES * Integer.bitCount(this.field_149280_d)
			+ (this.field_149279_g ? 256 : 0)];
		Inflater inflater = new Inflater();
		inflater.setInput(compressed);
		try {
			inflater.inflate(this.field_149278_f);
		} catch (DataFormatException e) {
			throw new IOException("Bad compressed data format");
		} finally {
			inflater.end();
		}
	}

	/**
	 * @author AsgerJon
	 * @reason The masks are ints (1.7.10: shorts).
	 */
	@Overwrite
	public void writePacketData(PacketBuffer data) throws IOException {
		if (this.field_149281_e == null) {
			this.deflateGate.acquireUninterruptibly();
			if (this.field_149281_e == null) {
				this.deflate();
			}
			this.deflateGate.release();
		}
		data.writeInt(this.field_149284_a);
		data.writeInt(this.field_149282_b);
		data.writeBoolean(this.field_149279_g);
		data.writeInt(this.field_149283_c);
		data.writeInt(this.field_149280_d);
		data.writeInt(this.field_149285_h);
		data.writeBytes(this.field_149281_e, 0, this.field_149285_h);
	}

	@Unique
	private static int mcfly$copy(NibbleArray from, byte[] to, int at) {
		return mcfly$copy(from.data, to, at);
	}

	@Unique
	private static int mcfly$copy(byte[] from, byte[] to, int at) {
		System.arraycopy(from, 0, to, at, from.length);
		return at + from.length;
	}
}
