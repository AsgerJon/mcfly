package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.TallWorld;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On the server, nothing can be placed outside floor..top. 1.7.10 says yes there (the block there reads air), so its
 * items that place blocks report a use that placed nothing, and the server can't tell "nothing happened" for the build
 * messages. The client keeps 1.7.10's answer: it must still send the click, for the server to answer it.
 */
@Mixin(World.class)
public abstract class MixinWorldPlacement {

	@Shadow
	@Final
	public boolean isRemote;

	@Inject(method = "canPlaceEntityOnSide", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
	private void mcfly$nothingOutside(Block block, int x, int y, int z, boolean ignoreEntities, int side, Entity entity, ItemStack stack,
		CallbackInfoReturnable<Boolean> cir) {
		if (!this.isRemote && !((TallWorld) this).mcfly$range().holdsBlocks(y)) {
			cir.setReturnValue(false);
		}
	}
}
