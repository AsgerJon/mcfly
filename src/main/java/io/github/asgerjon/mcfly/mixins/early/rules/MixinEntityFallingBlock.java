package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.item.EntityFallingBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A falling block becomes its item after 100 ticks of falling at or below the floor or above the top (§4 answer 2;
 * 1.7.10: below y 1 or above 256), or after 600 anywhere, as in 1.7.10. Its `j < 1` is the method's seventh 1, by
 * 1.7.10's bytecode.
 */
@Mixin(EntityFallingBlock.class)
public abstract class MixinEntityFallingBlock {

	@ModifyConstant(method = "onUpdate", constant = @Constant(intValue = 1, ordinal = 6), require = 1, allow = 1)
	private int mcfly$aboveTheFloor(int vanilla) {
		return WorldHeights.floor(((EntityFallingBlock) (Object) this).worldObj) + 1;
	}

	@ModifyConstant(method = "onUpdate", constant = @Constant(intValue = 256), require = 1, allow = 1)
	private int mcfly$top(int vanilla) {
		return WorldHeights.top(((EntityFallingBlock) (Object) this).worldObj);
	}
}
