package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.BlockPortal;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A portal frame's search for its bottom goes down to the floor (1.7.10: while y > 0). The constructor's other zero
 * test is an index (`>= 0`), so only the `> 0` kind is changed.
 */
@Mixin(BlockPortal.Size.class)
public abstract class MixinBlockPortalSize {

	@ModifyConstant(method = "<init>", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}), require = 1, allow = 1)
	private int mcfly$downToTheFloor(int zero, World world, int x, int y, int z, int axis) {
		return WorldHeights.floor(world);
	}
}
