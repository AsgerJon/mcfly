package io.github.asgerjon.mcfly.mixins.early.client.render;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A chunk's data from the server (one chunk, or a bulk of them) redraws its whole column, floor..top (1.7.10: 0..256).
 * With 1.7.10's range, the same call.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerRedraw {

	@Redirect(method = {"handleChunkData", "handleMapChunkBulk"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;markBlockRangeForRenderUpdate(IIIIII)V"), require = 2, allow = 2)
	private void mcfly$wholeColumn(WorldClient world, int x1, int y1, int z1, int x2, int y2, int z2) {
		HeightRange range = WorldHeights.range(world);
		world.markBlockRangeForRenderUpdate(x1, range.floor, z1, x2, range.top + 1, z2);
	}
}
