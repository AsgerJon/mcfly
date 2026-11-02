package io.github.asgerjon.mcfly.mixins.early.client.render;

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
 * The renderer's light reads through a `ChunkCache` (client-only methods) cover the light store: below it they read its
 * bottom layer and above it its top layer, as 1.7.10 reads y 0 and 255. Its `getHeight()` is one above the top, as the
 * world's is ((a)). Its block reads are in `rules.MixinChunkCache`. Counts checked against 1.7.10's bytecode.
 */
@Mixin(ChunkCache.class)
public abstract class MixinChunkCacheLight {

	@Shadow
	private World worldObj;

	/** `y < 0`, then `y >= 0` in the bounds check, in each: the store's bottom. */
	@ModifyConstant(method = {"getSkyBlockTypeBrightness", "getSpecialBlockBrightness"},
		constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO, Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}),
		require = 4, allow = 4)
	private int mcfly$belowLight(int zero) {
		return mcfly$range().lightFloor;
	}

	/** `y = 0`, the read moved up to the bottom (each method's first 0; the other is "no sky light here"). */
	@ModifyConstant(method = {"getSkyBlockTypeBrightness", "getSpecialBlockBrightness"}, constant = @Constant(intValue = 0, ordinal = 0),
		require = 2, allow = 2)
	private int mcfly$lowestLightRead(int zero) {
		return mcfly$range().lightFloor;
	}

	/** `y >= 256`, then `y < 256` in the bounds check, in each: one above the store's top. */
	@ModifyConstant(method = {"getSkyBlockTypeBrightness", "getSpecialBlockBrightness"}, constant = @Constant(intValue = 256), require = 4, allow = 4)
	private int mcfly$aboveLight(int vanilla) {
		return mcfly$range().lightTop + 1;
	}

	/** `y = 255`, the read moved down to the top. */
	@ModifyConstant(method = {"getSkyBlockTypeBrightness", "getSpecialBlockBrightness"}, constant = @Constant(intValue = 255), require = 2, allow = 2)
	private int mcfly$highestLightRead(int vanilla) {
		return mcfly$range().lightTop;
	}

	@ModifyConstant(method = "getHeight", constant = @Constant(intValue = 256), require = 1, allow = 1)
	private int mcfly$height(int vanilla) {
		return mcfly$range().top + 1;
	}

	@Unique
	private HeightRange mcfly$range() {
		return WorldHeights.range(worldObj);
	}
}
