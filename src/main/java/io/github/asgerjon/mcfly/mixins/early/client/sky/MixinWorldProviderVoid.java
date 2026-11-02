package io.github.asgerjon.mcfly.mixins.early.client.sky;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A tall world has no void particles (spec §11 answer 8). 1.7.10 shows them in a normal world near y 0, and the same
 * answer shortens the fog's distance there (`EntityRenderer.setupFog`); both end.
 */
@Mixin(WorldProvider.class)
public abstract class MixinWorldProviderVoid {

	@Shadow
	public World worldObj;

	@Inject(method = "getWorldHasVoidParticles", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
	private void mcfly$noneInATallWorld(CallbackInfoReturnable<Boolean> cir) {
		if (!WorldHeights.range(worldObj).vanilla) {
			cir.setReturnValue(false);
		}
	}
}
