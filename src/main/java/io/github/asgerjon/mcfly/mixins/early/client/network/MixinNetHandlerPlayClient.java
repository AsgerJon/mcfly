package io.github.asgerjon.mcfly.mixins.early.client.network;

import io.github.asgerjon.mcfly.network.BlockChanges;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** The client takes a batched block change's records with their full y (`BlockChanges`). */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

	@Shadow
	private WorldClient clientWorldController;

	/**
	 * @author AsgerJon
	 * @reason Each record's place is an int with y in full (1.7.10: a short with 8 bits of y).
	 */
	@Overwrite
	public void handleMultiBlockChange(S22PacketMultiBlockChange packet) {
		int baseX = packet.func_148920_c().chunkXPos * 16;
		int baseZ = packet.func_148920_c().chunkZPos * 16;
		if (packet.func_148921_d() == null) {
			return;
		}
		DataInputStream records = new DataInputStream(new ByteArrayInputStream(packet.func_148921_d()));
		try {
			for (int i = 0; i < packet.func_148922_e(); ++i) {
				int position = records.readInt();
				short block = records.readShort();
				this.clientWorldController.func_147492_c(baseX + BlockChanges.x(position), BlockChanges.y(position), baseZ + BlockChanges.z(position),
					Block.getBlockById(block >> 4 & 4095), block & 15);
			}
		} catch (IOException e) {
			// as 1.7.10: a short batch ends early
		}
	}
}
