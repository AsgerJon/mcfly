package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Any block can be placed in the Overworld's top layer, 319 (§4 answer 3). 1.7.10 refuses a solid block at y 255; the
 * Nether and the End keep that.
 */
@Mixin(ItemBlock.class)
public abstract class MixinItemBlock {

	@ModifyConstant(method = "onItemUse", constant = @Constant(intValue = 255), require = 1, allow = 1)
	private int mcfly$topLayerRule(int vanilla, ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY,
		float hitZ) {
		// a y no block has: the rule never applies
		return WorldHeights.range(world).vanilla ? vanilla : Integer.MIN_VALUE;
	}
}
