package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.BlockVine;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.util.Random;

/** A vine grows up into the top layer (1.7.10: only below 255). */
@Mixin(BlockVine.class)
public abstract class MixinBlockVine {

	@ModifyConstant(method = "updateTick", constant = @Constant(intValue = 255), require = 1, allow = 1)
	private int mcfly$top(int vanilla, World world, int x, int y, int z, Random random) {
		return WorldHeights.top(world);
	}
}
