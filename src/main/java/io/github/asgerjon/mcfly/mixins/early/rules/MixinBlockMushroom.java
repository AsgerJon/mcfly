package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.BlockMushroom;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A mushroom stays anywhere floor..top (1.7.10: only 0..255). */
@Mixin(BlockMushroom.class)
public abstract class MixinBlockMushroom {

	@ModifyConstant(method = "canBlockStay", constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}), require = 1, allow = 1)
	private int mcfly$floor(int zero, World world, int x, int y, int z) {
		return WorldHeights.floor(world);
	}

	@ModifyConstant(method = "canBlockStay", constant = @Constant(intValue = 256), require = 1, allow = 1)
	private int mcfly$aboveTop(int vanilla, World world, int x, int y, int z) {
		return WorldHeights.top(world) + 1;
	}
}
