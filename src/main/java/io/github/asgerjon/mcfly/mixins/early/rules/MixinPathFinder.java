package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.Entity;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A walking mob's path down a drop goes down to the floor, and is refused when it gets there without ground: no path
 * into the void (spec §11 answer 4). 1.7.10 stops looking at 0, so below 0 it takes any drop as ground. Its `y > 0`
 * tests are the second and third zero tests of `getSafePoint` (the first is the step height), by 1.7.10's bytecode.
 */
@Mixin(PathFinder.class)
public abstract class MixinPathFinder {

	@Shadow
	public abstract int getVerticalOffset(Entity entity, int x, int y, int z, PathPoint size);

	/** `while (y > 0)`: the drop goes on while y is at the floor or above. */
	@ModifyConstant(method = "getSafePoint", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}, ordinal = 1), require = 1, allow = 1)
	private int mcfly$dropWhile(int zero, Entity entity, int x, int y, int z, PathPoint size, int step) {
		return mcfly$belowTheFloor(entity);
	}

	/** `if (y > 0)`: a point of the drop, at the floor or above. */
	@ModifyConstant(method = "getSafePoint", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}, ordinal = 2), require = 1, allow = 1)
	private int mcfly$dropPoint(int zero, Entity entity, int x, int y, int z, PathPoint size, int step) {
		return mcfly$belowTheFloor(entity);
	}

	/** Below the floor, the drop finds what 1.7.10 refuses a path for at once: -2 (lava). */
	@Redirect(method = "getSafePoint",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/pathfinding/PathFinder;getVerticalOffset(Lnet/minecraft/entity/Entity;IIILnet/minecraft/pathfinding/PathPoint;)I",
			ordinal = 2), require = 1, allow = 1)
	private int mcfly$noGroundBelowTheFloor(PathFinder finder, Entity entity, int x, int y, int z, PathPoint size) {
		HeightRange range = WorldHeights.range(entity.worldObj);
		return !range.vanilla && y < range.floor ? -2 : this.getVerticalOffset(entity, x, y, z, size);
	}

	@Unique
	private static int mcfly$belowTheFloor(Entity entity) {
		HeightRange range = WorldHeights.range(entity.worldObj);
		return range.vanilla ? 0 : range.floor - 1;
	}
}
