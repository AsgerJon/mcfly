package io.github.asgerjon.mcfly.mixins.early.light;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The world's light reads and writes cover the light store, which reaches one section past the floor and the top
 * (spec §5): -80..335 in the Overworld, 1.7.10's 0..255 elsewhere. A read outside it reads the store's edge, as 1.7.10
 * reads y 0 or 255; a write outside it is ignored. Counts checked against 1.7.10's bytecode.
 */
@Mixin(World.class)
public abstract class MixinWorldLight {

	/** `y < 0` (read) and `y >= 0` (write): the store's bottom. */
	@ModifyConstant(method = {"getSavedLightValue", "setLightValue"},
		constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO, Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}),
		require = 2, allow = 2)
	private int mcfly$belowLight(int zero) {
		return mcfly$lightRange().lightFloor;
	}

	/** A read below the store reads its bottom layer. */
	@ModifyConstant(method = "getSavedLightValue", constant = @Constant(intValue = 0), require = 1, allow = 1)
	private int mcfly$lowestLightRead(int zero) {
		return mcfly$lightRange().lightFloor;
	}

	/** `y >= 256` (read) and `y < 256` (write): one above the store's top. */
	@ModifyConstant(method = {"getSavedLightValue", "setLightValue"}, constant = @Constant(intValue = 256), require = 2, allow = 2)
	private int mcfly$aboveLight(int vanilla) {
		return mcfly$lightRange().lightTop + 1;
	}

	/** A read above the store reads its top layer. */
	@ModifyConstant(method = "getSavedLightValue", constant = @Constant(intValue = 255), require = 1, allow = 1)
	private int mcfly$highestLightRead(int vanilla) {
		return mcfly$lightRange().lightTop;
	}

	@Unique
	private HeightRange mcfly$lightRange() {
		return ((TallWorld) this).mcfly$range();
	}
}
