package io.github.asgerjon.mcfly.mixins.early.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S28PacketEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * A world effect (a sound or particles at a block) carries its y as a signed short (1.7.10: one byte, cut to 8 bits on
 * both sides). Rewritten whole: a redirect can't widen 1.7.10's `readByte`.
 */
@Mixin(S28PacketEffect.class)
public abstract class MixinS28PacketEffect {

	@Shadow
	private int field_149251_a;

	@Shadow
	private int field_149250_c;

	@Shadow
	private int field_149247_d;

	@Shadow
	private int field_149248_e;

	@Shadow
	private int field_149249_b;

	@Shadow
	private boolean field_149246_f;

	/**
	 * @author AsgerJon
	 * @reason The effect's y as a short.
	 */
	@Overwrite
	public void readPacketData(PacketBuffer data) {
		this.field_149251_a = data.readInt();
		this.field_149250_c = data.readInt();
		this.field_149247_d = data.readShort();
		this.field_149248_e = data.readInt();
		this.field_149249_b = data.readInt();
		this.field_149246_f = data.readBoolean();
	}

	/**
	 * @author AsgerJon
	 * @reason The effect's y as a short.
	 */
	@Overwrite
	public void writePacketData(PacketBuffer data) {
		data.writeInt(this.field_149251_a);
		data.writeInt(this.field_149250_c);
		data.writeShort(this.field_149247_d);
		data.writeInt(this.field_149248_e);
		data.writeInt(this.field_149249_b);
		data.writeBoolean(this.field_149246_f);
	}
}
