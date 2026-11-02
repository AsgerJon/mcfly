package io.github.asgerjon.mcfly.mixins.early.client.render;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A chunk going (`doPreChunk` marks only on an unload) redraws its own column, floor..top, so its render sections stop
 * drawing at once; not the columns around it. 1.7.10 marks 0..256, and `RenderGlobal`'s extra block on each side makes
 * that the 8 columns around too, to draw their faces toward the gone chunk: back faces, never seen. So the range is
 * taken in by a block on each side, and that extra block makes it the gone column exactly.
 */
@Mixin(WorldClient.class)
public abstract class MixinWorldClientRedraw {

	@Redirect(method = "doPreChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;markBlockRangeForRenderUpdate(IIIIII)V"),
		require = 1, allow = 1)
	private void mcfly$ownColumn(WorldClient world, int x1, int y1, int z1, int x2, int y2, int z2) {
		HeightRange range = WorldHeights.range(world);
		world.markBlockRangeForRenderUpdate(x1 + 1, range.floor + 1, z1 + 1, x2 - 1, range.top - 1, z2 - 1);
	}
}
