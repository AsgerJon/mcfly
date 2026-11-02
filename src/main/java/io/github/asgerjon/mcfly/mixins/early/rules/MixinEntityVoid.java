package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The void line, where an entity gets the out-of-world treatment, is 64 below the floor (spec §8): -128 in the Overworld. */
@Mixin(Entity.class)
public abstract class MixinEntityVoid {

	@Shadow
	public World worldObj;

	@ModifyConstant(method = "onEntityUpdate", constant = @Constant(doubleValue = -64.0), require = 1, allow = 1)
	private double mcfly$voidLine(double vanilla) {
		return WorldHeights.floor(worldObj) - 64;
	}
}
