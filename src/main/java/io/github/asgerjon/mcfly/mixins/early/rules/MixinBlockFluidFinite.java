package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidFinite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Forge's finite fluids flow down to the floor (Forge: to 0; the top is `getHeight()`, right since (a)). Its first zero
 * test is of y; the others are of amounts. Forge's own class, no obfuscated names. No test: nothing in plain Forge uses it.
 */
@Mixin(BlockFluidFinite.class)
public abstract class MixinBlockFluidFinite {

	@ModifyConstant(method = "tryToFlowVerticallyInto", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}, ordinal = 0), require = 1, allow = 1, remap = false)
	private int mcfly$floor(int zero, World world, int x, int y, int z, int amount) {
		return WorldHeights.floor(world);
	}
}
