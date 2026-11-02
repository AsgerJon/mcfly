package io.github.asgerjon.mcfly.mixins.early.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The player's digging carries its y as a signed short (1.7.10: one unsigned byte), so -64..319 travel (spec §7). The
 * calls aimed at are Netty's, which have no obfuscated names (hence `remap = false`).
 */
@Mixin(C07PacketPlayerDigging.class)
public abstract class MixinC07PacketPlayerDigging {

	@Redirect(method = "readPacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readUnsignedByte()S", ordinal = 1, remap = false),
		require = 1, allow = 1)
	private short mcfly$readY(PacketBuffer data) {
		return data.readShort();
	}

	@Redirect(method = "writePacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;writeByte(I)Lio/netty/buffer/ByteBuf;", ordinal = 1, remap = false),
		require = 1, allow = 1)
	private ByteBuf mcfly$writeY(PacketBuffer data, int y) {
		return data.writeShort(y);
	}
}
