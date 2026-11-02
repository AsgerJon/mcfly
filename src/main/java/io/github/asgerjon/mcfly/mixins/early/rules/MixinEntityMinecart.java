package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.item.EntityMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A minecart's own copy of the void line (1.7.10 checks it in its own update): 64 below the floor. */
@Mixin(EntityMinecart.class)
public abstract class MixinEntityMinecart {

	@ModifyConstant(method = "onUpdate", constant = @Constant(doubleValue = -64.0), require = 1, allow = 1)
	private double mcfly$voidLine(double vanilla) {
		return WorldHeights.floor(((EntityMinecart) (Object) this).worldObj) - 64;
	}
}
