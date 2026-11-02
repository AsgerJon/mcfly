package io.github.asgerjon.mcfly.mixins.early.client.sky;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The fog colour's darkening near the bottom of a tall world (§4 answer 8), measured from the floor. 1.7.10 multiplies
 * the colour by (camera y × the world type's factor)² while that is under 1: from y 32 down in a normal world, in the
 * last block in a flat one. Here it is (camera y − floor) × factor, and none while the camera is in lava. Blindness,
 * which 1.7.10 applies to the same number next, still darkens it.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererFog {

	@Shadow
	private Minecraft mc;

	@Unique
	private float mcfly$partialTicks;

	@Inject(method = "updateFogColor", at = @At("HEAD"), require = 1, allow = 1)
	private void mcfly$frame(float partialTicks, CallbackInfo ci) {
		mcfly$partialTicks = partialTicks;
	}

	/** The method's one double, as it is first stored: camera y × factor. */
	@ModifyVariable(method = "updateFogColor", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 1, allow = 1)
	private double mcfly$fromTheFloor(double fade) {
		WorldClient world = mc.theWorld;
		HeightRange range = WorldHeights.range(world);
		if (range.vanilla) {
			return fade;
		}
		double fromFloor = fade - range.floor * world.provider.getVoidFogYFactor();
		if (fromFloor < 1.0 && ActiveRenderInfo.getBlockAtEntityViewpoint(world, mc.renderViewEntity, mcfly$partialTicks).getMaterial() == Material.lava) {
			return 1.0;
		}
		return fromFloor;
	}
}
