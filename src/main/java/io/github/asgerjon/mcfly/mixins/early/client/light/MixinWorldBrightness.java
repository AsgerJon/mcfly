package io.github.asgerjon.mcfly.mixins.early.client.light;

import io.github.asgerjon.mcfly.world.TallWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The light the client's renderer reads (a client-only method of `World`) covers the light store: below it reads the
 * store's bottom layer, above it the sky's default, as 1.7.10 reads y 0 and above 255. Counts checked against 1.7.10's
 * bytecode.
 */
@Mixin(World.class)
public abstract class MixinWorldBrightness {

	/** `y < 0`: the store's bottom. */
	@ModifyConstant(method = "getSkyBlockTypeBrightness",
		constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO, Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}),
		require = 1, allow = 1)
	private int mcfly$belowLight(int zero) {
		return ((TallWorld) this).mcfly$range().lightFloor;
	}

	/** `y = 0`, the read moved up to the bottom: the store's bottom. (The method's first 0 is "no sky light here".) */
	@ModifyConstant(method = "getSkyBlockTypeBrightness", constant = @Constant(intValue = 0, ordinal = 1), require = 1, allow = 1)
	private int mcfly$lowestLightRead(int zero) {
		return ((TallWorld) this).mcfly$range().lightFloor;
	}

	/** `y >= 256`: one above the store's top. */
	@ModifyConstant(method = "getSkyBlockTypeBrightness", constant = @Constant(intValue = 256), require = 1, allow = 1)
	private int mcfly$aboveLight(int vanilla) {
		return ((TallWorld) this).mcfly$range().lightTop + 1;
	}
}
