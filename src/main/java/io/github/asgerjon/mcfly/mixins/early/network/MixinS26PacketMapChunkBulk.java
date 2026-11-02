package io.github.asgerjon.mcfly.mixins.early.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Whole chunks sent together, as the server sends them to a player: each with every section of the light store
 * (`MixinS21PacketChunkData`), masks as ints. The reader reads every chunk's header before inflating, to size the data
 * exactly, and keeps its compressed input in an array of its own (1.7.10: a static one).
 */
@Mixin(S26PacketMapChunkBulk.class)
public abstract class MixinS26PacketMapChunkBulk {

	@Shadow
	private int[] field_149266_a;

	@Shadow
	private int[] field_149264_b;

	@Shadow
	private int[] field_149265_c;

	@Shadow
	private int[] field_149262_d;

	@Shadow
	private byte[][] field_149260_f;

	@Shadow
	private int field_149261_g;

	@Shadow
	private byte[] field_149263_e;

	@Shadow
	private boolean field_149267_h;

	@Shadow(remap = false)
	private Semaphore deflateGate;

	@Shadow(remap = false)
	private void deflate() {
	}

	/** 1.7.10 asks for each chunk's sections with the mask 65535, "all 16": now all of the light store's. */
	@ModifyConstant(method = "<init>(Ljava/util/List;)V", constant = @Constant(intValue = 65535), require = 1, allow = 1)
	private int mcfly$everySection(int vanilla) {
		return -1;
	}

	/**
	 * @author AsgerJon
	 * @reason The masks are ints, over the whole light store; each chunk's share of the data follows from them.
	 */
	@Overwrite
	public void readPacketData(PacketBuffer data) throws IOException {
		int count = data.readShort();
		this.field_149261_g = data.readInt();
		this.field_149267_h = data.readBoolean();
		this.field_149266_a = new int[count];
		this.field_149264_b = new int[count];
		this.field_149265_c = new int[count];
		this.field_149262_d = new int[count];
		this.field_149260_f = new byte[count][];
		byte[] compressed = new byte[this.field_149261_g];
		data.readBytes(compressed);
		// the headers come after the data: each chunk's blocks, metadata and block light (8192 a section), sky light
		// (2048 a section, in a world with a sky), high id bits (2048 a section that has them) and biomes (256)
		int[] sizes = new int[count];
		int total = 0;
		for (int i = 0; i < count; ++i) {
			this.field_149266_a[i] = data.readInt();
			this.field_149264_b[i] = data.readInt();
			this.field_149265_c[i] = data.readInt();
			this.field_149262_d[i] = data.readInt();
			int sections = Integer.bitCount(this.field_149265_c[i]);
			sizes[i] = (this.field_149267_h ? 10240 : 8192) * sections + 2048 * Integer.bitCount(this.field_149262_d[i]) + 256;
			total += sizes[i];
		}
		byte[] all = new byte[total];
		Inflater inflater = new Inflater();
		inflater.setInput(compressed);
		try {
			inflater.inflate(all);
		} catch (DataFormatException e) {
			throw new IOException("Bad compressed data format");
		} finally {
			inflater.end();
		}
		for (int i = 0, at = 0; i < count; at += sizes[i], ++i) {
			this.field_149260_f[i] = new byte[sizes[i]];
			System.arraycopy(all, at, this.field_149260_f[i], 0, sizes[i]);
		}
	}

	/**
	 * @author AsgerJon
	 * @reason The masks are ints (1.7.10: shorts).
	 */
	@Overwrite
	public void writePacketData(PacketBuffer data) throws IOException {
		if (this.field_149263_e == null) {
			this.deflateGate.acquireUninterruptibly();
			if (this.field_149263_e == null) {
				this.deflate();
			}
			this.deflateGate.release();
		}
		data.writeShort(this.field_149266_a.length);
		data.writeInt(this.field_149261_g);
		data.writeBoolean(this.field_149267_h);
		data.writeBytes(this.field_149263_e, 0, this.field_149261_g);
		for (int i = 0; i < this.field_149266_a.length; ++i) {
			data.writeInt(this.field_149266_a[i]);
			data.writeInt(this.field_149264_b[i]);
			data.writeInt(this.field_149265_c[i]);
			data.writeInt(this.field_149262_d[i]);
		}
	}
}
