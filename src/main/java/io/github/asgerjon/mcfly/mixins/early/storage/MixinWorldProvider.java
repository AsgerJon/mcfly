package io.github.asgerjon.mcfly.mixins.early.storage;

import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Forge's `getHeight()` and `getActualHeight()` give one above the top: 320 in the Overworld, so code that uses them as
 * its upper bound follows (pistons, doors, the beacon's box, the portal search). The Nether's 128 stays.
 */
@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider {

	@Shadow
	public World worldObj;

	// Forge's own methods, which have no obfuscated names
	@ModifyConstant(method = {"getHeight", "getActualHeight"}, constant = @Constant(intValue = 256), require = 2, allow = 2, remap = false)
	private int mcfly$height(int vanilla) {
		return WorldHeights.range(worldObj).top + 1;
	}
}
