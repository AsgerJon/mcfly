package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * An enderman's teleport looks straight down for a block to stand on, down to and including the floor block, also from
 * a start above the top; a start at or below the floor finds nothing (spec §11 answer 4). 1.7.10 looks down to 0, and
 * wants the start itself loaded, which nothing above the top is.
 */
@Mixin(EntityEnderman.class)
public abstract class MixinEntityEnderman {

	/** Whether the start is loaded: its column is (a start above the top or below the floor counts by its column). */
	@Redirect(method = "teleportTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;blockExists(III)Z"), require = 1, allow = 1)
	private boolean mcfly$columnLoaded(World world, int x, int y, int z) {
		HeightRange range = WorldHeights.range(world);
		return world.blockExists(x, range.vanilla ? y : range.floor, z);
	}

	/** `j > 0`, the search down. */
	@ModifyConstant(method = "teleportTo", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}), require = 1, allow = 1)
	private int mcfly$searchToTheFloor(int zero) {
		return WorldHeights.floor(((EntityEnderman) (Object) this).worldObj);
	}
}
