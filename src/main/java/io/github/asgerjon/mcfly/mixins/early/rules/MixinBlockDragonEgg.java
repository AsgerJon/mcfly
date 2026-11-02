package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDragonEgg;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The dragon egg falls as sand does, at any y of the range (`MixinBlockFalling`), and its teleport takes only a spot
 * inside floor..top (spec §11 answer 2). Outside, 1.7.10 reads air there and takes it, and the egg is lost. Inside, any
 * air block still does, as in 1.7.10 (later versions also want a block below it), so the egg can land in mid-air and
 * fall.
 */
@Mixin(BlockDragonEgg.class)
public abstract class MixinBlockDragonEgg {

	@ModifyConstant(method = "func_150018_e", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}), require = 1, allow = 1)
	private int mcfly$fallsFrom(int zero, World world, int x, int y, int z) {
		return WorldHeights.floor(world);
	}

	@ModifyConstant(method = "func_150018_e", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO,
		Constant.Condition.GREATER_THAN_ZERO}), require = 2, allow = 2)
	private int mcfly$fallsTo(int zero, World world, int x, int y, int z) {
		return WorldHeights.floor(world);
	}

	/** The teleport's candidate spot (the method's second block read): outside the range it reads as solid, so it's passed over. */
	@Redirect(method = "func_150019_m", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;", ordinal = 1),
		require = 1, allow = 1)
	private Block mcfly$onlyInside(World world, int x, int y, int z) {
		return WorldHeights.range(world).holdsBlocks(y) ? world.getBlock(x, y, z) : Blocks.bedrock;
	}
}
