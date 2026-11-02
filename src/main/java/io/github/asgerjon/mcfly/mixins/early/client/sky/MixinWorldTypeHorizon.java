package io.github.asgerjon.mcfly.mixins.early.client.sky;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The horizon (where the sky's dark lower half begins) of a flat tall world is its floor, as a flat 1.7.10 world's is
 * its 0 (spec §11 answer 8). A normal world's 63 stays.
 */
@Mixin(WorldType.class)
public abstract class MixinWorldTypeHorizon {

	// Forge's method, which has no obfuscated name
	@Inject(method = "getHorizon", at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
	private void mcfly$floorWhenFlat(World world, CallbackInfoReturnable<Double> cir) {
		HeightRange range = WorldHeights.range(world);
		if ((Object) this == WorldType.FLAT && !range.vanilla) {
			cir.setReturnValue((double) range.floor);
		}
	}
}
