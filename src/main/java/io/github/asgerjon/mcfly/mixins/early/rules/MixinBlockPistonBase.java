package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A piston moves no block to or from outside floor..top, and its head needs room inside (§4 answer 1). 1.7.10 refuses
 * y <= 0, the floor layer itself; that becomes below the floor. The upper bound is `getHeight()`, one above the top
 * since (a).
 */
@Mixin(BlockPistonBase.class)
public abstract class MixinBlockPistonBase {

	@ModifyConstant(method = "canExtend", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}), require = 1, allow = 1)
	private static int mcfly$belowTheFloor(int zero, World world, int x, int y, int z, int side) {
		return WorldHeights.floor(world) - 1;
	}

	@ModifyConstant(method = "tryExtend", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}), require = 1, allow = 1)
	private int mcfly$belowTheFloorToo(int zero, World world, int x, int y, int z, int side) {
		return WorldHeights.floor(world) - 1;
	}
}
