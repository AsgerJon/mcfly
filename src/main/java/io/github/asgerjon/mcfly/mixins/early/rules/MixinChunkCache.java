package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A `ChunkCache` (the path finder's view of the world, and the renderer's) reads blocks over floor..top (1.7.10:
 * 0..255). Its first zero test in each is of y; the others are of its chunk array's indices. Its light reads and its
 * empty check are the renderer's, in (e).
 */
@Mixin(ChunkCache.class)
public abstract class MixinChunkCache {

	@Shadow
	private World worldObj;

	@ModifyConstant(method = {"getBlock", "getBlockMetadata"}, constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO,
		Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}, ordinal = 0), require = 2, allow = 2)
	private int mcfly$floor(int zero) {
		return mcfly$range().floor;
	}

	@ModifyConstant(method = {"getBlock", "getBlockMetadata"}, constant = @Constant(intValue = 256), require = 2, allow = 2)
	private int mcfly$aboveTop(int vanilla) {
		return mcfly$range().top + 1;
	}

	@Unique
	private HeightRange mcfly$range() {
		return WorldHeights.range(worldObj);
	}
}
