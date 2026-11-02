package io.github.asgerjon.mcfly.mixins.early.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S0APacketUseBed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * A player going to bed carries the bed's y as a signed short. 1.7.10 sends it as a signed byte, which is wrong for a bed
 * above 127 even there. Rewritten whole: a redirect can't widen 1.7.10's `readByte`.
 */
@Mixin(S0APacketUseBed.class)
public abstract class MixinS0APacketUseBed {

	@Shadow
	private int field_149097_a;

	@Shadow
	private int field_149095_b;

	@Shadow
	private int field_149096_c;

	@Shadow
	private int field_149094_d;

	/**
	 * @author AsgerJon
	 * @reason The bed's y as a short.
	 */
	@Overwrite
	public void readPacketData(PacketBuffer data) {
		this.field_149097_a = data.readInt();
		this.field_149095_b = data.readInt();
		this.field_149096_c = data.readShort();
		this.field_149094_d = data.readInt();
	}

	/**
	 * @author AsgerJon
	 * @reason The bed's y as a short.
	 */
	@Overwrite
	public void writePacketData(PacketBuffer data) {
		data.writeInt(this.field_149097_a);
		data.writeInt(this.field_149095_b);
		data.writeShort(this.field_149096_c);
		data.writeInt(this.field_149094_d);
	}
}
