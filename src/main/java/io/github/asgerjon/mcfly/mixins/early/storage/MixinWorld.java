package io.github.asgerjon.mcfly.mixins.early.storage;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallWorld;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The world's own bounds move from 1.7.10's 0..255 to its range's floor..top (spec §2): reading, writing, "loaded",
 * block entities, light opacity, freezing and snow, the highest solid block, and the heights of unloaded chunks. Each
 * method changed here has exactly one comparison with 0 and one 255/256 of its own (checked against 1.7.10's bytecode),
 * hence the exact `require` counts.
 */
@Mixin(World.class)
public abstract class MixinWorld implements TallWorld {

	@Shadow
	@Final
	public WorldProvider provider;

	@Unique
	private HeightRange mcfly$range;

	@Override
	public HeightRange mcfly$range() {
		HeightRange range = mcfly$range;
		if (range == null) {
			if (provider == null) {
				return HeightRange.CLASSIC;
			}
			range = mcfly$range = WorldHeights.forDimension(provider.dimensionId);
		}
		return range;
	}

	/** `y >= 0` and `y < 0`, the lower bound: the floor. */
	@ModifyConstant(method = {"getBlock", "blockExists", "checkChunksExist", "getTileEntity", "setBlock(IIILnet/minecraft/block/Block;II)Z",
		"getBlockMetadata", "setBlockMetadataWithNotify", "getFullBlockLightValue", "getBlockLightValue_do"},
		constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO, Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}),
		require = 9, allow = 9)
	private int mcfly$floor(int zero) {
		return mcfly$range().floor;
	}

	/** The same in Forge's own methods, which have no obfuscated names. */
	@ModifyConstant(method = {"canBlockFreezeBody", "canSnowAtBody", "getBlockLightOpacity"},
		constant = @Constant(expandZeroConditions = {Constant.Condition.LESS_THAN_ZERO, Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO}),
		require = 3, allow = 3, remap = false)
	private int mcfly$floorInForgeMethods(int zero) {
		return mcfly$range().floor;
	}

	/** `y < 256` and `y >= 256`, the upper bound: one above the top. */
	@ModifyConstant(method = {"getBlock", "blockExists", "checkChunksExist", "getTileEntity", "setBlock(IIILnet/minecraft/block/Block;II)Z",
		"getBlockMetadata", "setBlockMetadataWithNotify", "getFullBlockLightValue", "getBlockLightValue_do"},
		constant = @Constant(intValue = 256), require = 9, allow = 9)
	private int mcfly$aboveTop(int vanilla) {
		return mcfly$range().top + 1;
	}

	/** The same in Forge's own methods. */
	@ModifyConstant(method = {"canBlockFreezeBody", "canSnowAtBody", "getBlockLightOpacity"}, constant = @Constant(intValue = 256),
		require = 3, allow = 3, remap = false)
	private int mcfly$aboveTopInForgeMethods(int vanilla) {
		return mcfly$range().top + 1;
	}

	/** A light read above the world reads the top layer instead. */
	@ModifyConstant(method = {"getFullBlockLightValue", "getBlockLightValue_do"}, constant = @Constant(intValue = 255), require = 2, allow = 2)
	private int mcfly$top(int vanilla) {
		return mcfly$range().top;
	}

	/** The search for the highest solid block runs down to the floor (1.7.10: down to 1). */
	@ModifyConstant(method = "getTopSolidOrLiquidBlock", constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_ZERO), require = 1, allow = 1)
	private int mcfly$belowLowestSearched(int zero) {
		HeightRange range = mcfly$range();
		return range.vanilla ? 0 : range.floor - 1;
	}

	/** No solid block in the column: the floor (1.7.10: -1, now a height in the world). */
	@ModifyConstant(method = "getTopSolidOrLiquidBlock", constant = @Constant(intValue = -1), require = 1, allow = 1)
	private int mcfly$noSolidBlock(int vanilla) {
		return mcfly$range().noSurface;
	}

	/** The height map of an unloaded chunk: the empty value, the floor (spec §4). */
	@ModifyConstant(method = {"getHeightValue", "getChunkHeightMapMinimum"}, constant = @Constant(intValue = 0), require = 2, allow = 2)
	private int mcfly$unloadedHeight(int zero) {
		return mcfly$range().floor;
	}

	/**
	 * Whether an entity moved to another part of its chunk: its y >> 4 against the chunk's entity list it is in. The chunk
	 * keeps 16 lists and puts an entity outside 0..255 in the end one (`Chunk.addEntity`), so compare with that list, or
	 * an entity below 0 or above 255 is taken out and put back every tick.
	 */
	@Redirect(method = "updateEntityWithOptionalForce", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;floor_double(D)I", ordinal = 3),
		require = 1, allow = 1)
	private int mcfly$entityListIndex(double sectionY) {
		int index = MathHelper.floor_double(sectionY);
		return mcfly$range().vanilla ? index : MathHelper.clamp_int(index, 0, 15);
	}
}
