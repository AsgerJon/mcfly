package io.github.asgerjon.mcfly.mixins.early.network;

import io.github.asgerjon.mcfly.network.BlockChanges;
import io.github.asgerjon.mcfly.network.TallBlockChanges;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A batched block change carries `BlockChanges` records, with full y, however it was made. Its reading and writing
 * pass the records as bytes, so they stay 1.7.10's; the client reads the records in `client.network`.
 */
@Mixin(S22PacketMultiBlockChange.class)
public abstract class MixinS22PacketMultiBlockChange implements TallBlockChanges {

	@Shadow
	private ChunkCoordIntPair field_148925_b;

	@Shadow
	private byte[] field_148926_c;

	@Shadow
	private int field_148924_d;

	@Override
	public void mcfly$fill(int count, int[] positions, Chunk chunk) {
		this.field_148925_b = new ChunkCoordIntPair(chunk.xPosition, chunk.zPosition);
		this.field_148924_d = count;
		this.field_148926_c = BlockChanges.records(count, positions, chunk);
	}

	/** 1.7.10's constructor, from its own shorts (y 0..255 only), still gives records of the one format. */
	@Inject(method = "<init>(I[SLnet/minecraft/world/chunk/Chunk;)V", at = @At("RETURN"), require = 1, allow = 1)
	private void mcfly$recordsFromShorts(int count, short[] changes, Chunk chunk, CallbackInfo ci) {
		int[] positions = new int[count];
		for (int i = 0; i < count; i++) {
			short change = changes[i];
			positions[i] = BlockChanges.position(change >> 12 & 15, change & 255, change >> 8 & 15);
		}
		mcfly$fill(count, positions, chunk);
	}
}
