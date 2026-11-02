package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Nether portals in the Overworld (spec §11 answer 5). Only heights change: the search's square, 128 blocks out, and
 * its tie rule, the higher portal, stay 1.7.10's. The search for an existing portal covers every height down to the
 * floor (1.7.10: to 0). A new portal's columns are searched down to the floor, from 4 below the top, so its frame fits
 * inside (1.7.10 starts at the top and builds frames cut off by it). The fallback stays 1.7.10's 70..height - 10, which
 * gives 70..310. The tests on y are picked by position among `makePortal`'s zero tests, by 1.7.10's bytecode: its other
 * zero tests are of the frame's offsets.
 */
@Mixin(Teleporter.class)
public abstract class MixinTeleporter {

	@Shadow
	@Final
	private WorldServer worldServerInstance;

	/** `i2 >= 0`: down to the floor. */
	@ModifyConstant(method = "placeInExistingPortal", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}), require = 1, allow = 1)
	private int mcfly$searchToTheFloor(int zero) {
		return WorldHeights.floor(worldServerInstance);
	}

	/** The first pass's `i3 >= 0`. */
	@ModifyConstant(method = "makePortal", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}, ordinal = 0), require = 1, allow = 1)
	private int mcfly$firstPassToTheFloor(int zero) {
		return WorldHeights.floor(worldServerInstance);
	}

	/** The second pass's `i3 >= 0`. */
	@ModifyConstant(method = "makePortal", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}, ordinal = 3), require = 1, allow = 1)
	private int mcfly$secondPassToTheFloor(int zero) {
		return WorldHeights.floor(worldServerInstance);
	}

	/** Both passes' `while (i3 > 0 && ...)`, going down through the air to the ground. */
	@ModifyConstant(method = "makePortal", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}), require = 2, allow = 2)
	private int mcfly$downToTheGround(int zero) {
		return WorldHeights.floor(worldServerInstance);
	}

	/** Where both passes start: 4 below the top at most, so a frame (4 high above its bottom) fits (spec: bottom <= 315). */
	@Redirect(method = "makePortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;getActualHeight()I", ordinal = 0),
		require = 1, allow = 1)
	private int mcfly$firstPassStart(WorldServer world) {
		return mcfly$passStart(world);
	}

	@Redirect(method = "makePortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;getActualHeight()I", ordinal = 1),
		require = 1, allow = 1)
	private int mcfly$secondPassStart(WorldServer world) {
		return mcfly$passStart(world);
	}

	// 1.7.10's passes start at the returned value - 1
	@Unique
	private static int mcfly$passStart(WorldServer world) {
		HeightRange range = WorldHeights.range(world);
		return range.vanilla ? world.getActualHeight() : Math.min(world.getActualHeight(), range.top - 3);
	}
}
