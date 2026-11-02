package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.BlockFalling;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A falling block falls wherever the space below is free, at any y of the range (§4 answer 2; 1.7.10: only at y >= 0),
 * so at the floor it falls out of the world. Generation's instant fall goes down to the floor (1.7.10: to 0).
 */
@Mixin(BlockFalling.class)
public abstract class MixinBlockFalling {

	/** `y >= 0`, whether it falls at all. */
	@ModifyConstant(method = "func_149830_m", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}), require = 1, allow = 1)
	private int mcfly$fallsFrom(int zero, World world, int x, int y, int z) {
		return WorldHeights.floor(world);
	}

	/** `y > 0`, twice, in the instant fall. */
	@ModifyConstant(method = "func_149830_m", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}), require = 2, allow = 2)
	private int mcfly$fallsTo(int zero, World world, int x, int y, int z) {
		return WorldHeights.floor(world);
	}
}
