package io.github.asgerjon.mcfly.mixins.early.client.render;

import io.github.asgerjon.mcfly.client.render.RenderSection;
import io.github.asgerjon.mcfly.world.TallChunk;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Speed: a render section with no blocks of its own that draws nothing now stays as it is when marked for redrawing:
 * built, empty, never queued (`MixinRenderGlobal`). 1.7.10 queues it and rebuilds it to find nothing, at two far ones a
 * frame with those in view first; in a tall world most of a column is such sections, so the ones with blocks waited
 * behind them, and the queue each frame scans grew. What is drawn is the same: a rebuild draws only the section's own
 * blocks. And a first build at its place waits for the chunks around its own.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer implements RenderSection {

	@Shadow
	public World worldObj;

	@Shadow
	public int posX;

	@Shadow
	public int posY;

	@Shadow
	public int posZ;

	@Shadow
	public boolean[] skipRenderPass;

	@Shadow
	public boolean needsUpdate;

	@Shadow
	private boolean isInitialized;

	@Shadow
	public List<TileEntity> tileEntityRenderers;

	/** The world time its first build began to wait, or -1. */
	@Unique
	private long mcfly$waitingSince = -1L;

	@Inject(method = "markDirty", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
	private void mcfly$skipWhenEmpty(CallbackInfo ci) {
		if (mcfly$staysEmpty()) {
			needsUpdate = false;
			isInitialized = true;
			ci.cancel();
		} else if (skipRenderPass[0] && skipRenderPass[1]) {
			// built empty, now with blocks: back under the frustum check until it is built, so the queue knows if it's in view;
			// and a first build with blocks ahead, with a wait of its own
			isInitialized = false;
			mcfly$waitingSince = -1L;
		}
	}

	/** A move (or a stop): it isn't built at its new place, so a first build lies ahead, with a wait of its own. */
	@Inject(method = "setDontDraw", at = @At("HEAD"), require = 1, allow = 1)
	private void mcfly$waitAnew(CallbackInfo ci) {
		mcfly$waitingSince = -1L;
	}

	/** A build: whatever wait there was is over. */
	@Inject(method = "updateRenderer", at = @At("HEAD"), require = 1, allow = 1)
	private void mcfly$waitOver(EntityLivingBase viewer, CallbackInfo ci) {
		mcfly$waitingSince = -1L;
	}

	@Override
	public boolean mcfly$waitsForTheChunksAround() {
		if (isInitialized || worldObj == null) {
			return false;
		}
		int chunkX = posX >> 4;
		int chunkZ = posZ >> 4;
		boolean missing = false;
		for (int dx = -1; dx <= 1 && !missing; dx++) {
			for (int dz = -1; dz <= 1 && !missing; dz++) {
				// the client's empty chunk stands in for one it hasn't got; a chunk's light comes in its packet
				missing = (dx != 0 || dz != 0) && worldObj.getChunkFromChunkCoords(chunkX + dx, chunkZ + dz).isEmpty();
			}
		}
		if (!missing) {
			return false;
		}
		long now = worldObj.getTotalWorldTime();
		if (mcfly$waitingSince < 0L || now < mcfly$waitingSince) {
			mcfly$waitingSince = now;
		}
		return now - mcfly$waitingSince < FIRST_BUILD_WAIT_TICKS;
	}

	@Override
	public boolean mcfly$staysEmpty() {
		if (!skipRenderPass[0] || !skipRenderPass[1] || !tileEntityRenderers.isEmpty()) {
			return false;
		}
		Chunk chunk = worldObj.getChunkFromChunkCoords(posX >> 4, posZ >> 4);
		ExtendedBlockStorage section = ((TallChunk) chunk).mcfly$section(posY >> 4);
		return section == null || section.isEmpty();
	}
}
