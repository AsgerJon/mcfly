package io.github.asgerjon.mcfly.mixins.early.client.render;

import io.github.asgerjon.mcfly.client.render.RenderSection;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The renderer draws the world's whole column (spec §9): a column of render sections is as tall as the world's range
 * (24 in the Overworld, 1.7.10's 16 elsewhere) and starts at its floor. The display lists and occlusion queries are
 * made once, when the game starts, so for the tallest range. For speed, a mark reaches only the section at the marked
 * place, and a section's first build waits for the chunks around its own. Counts checked against 1.7.10's bytecode.
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

	@Shadow
	private WorldClient theWorld;

	@Shadow
	private List<Object> worldRenderersToUpdate;

	/** The block range `markBlocksForUpdate` was given, while it runs. */
	@Unique
	private int mcfly$markMinX, mcfly$markMinY, mcfly$markMinZ, mcfly$markMaxX, mcfly$markMaxY, mcfly$markMaxZ;

	/** The queued sections the current `updateRenderers` passed by because their first build waits. */
	@Unique
	private int mcfly$waiting;

	/** The constructor's 34 × 34 × 16 render sections (three display lists and an occlusion query each). */
	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 16), require = 1, allow = 1)
	private int mcfly$mostSections(int sixteen) {
		return WorldHeights.MOST_SECTIONS;
	}

	/** `renderChunksTall = 16`: the world's sections. (The method's other 16s are a render section's size.) */
	@ModifyConstant(method = "loadRenderers", constant = @Constant(intValue = 16, ordinal = 0), require = 1, allow = 1)
	private int mcfly$columnHeight(int sixteen) {
		return WorldHeights.range(theWorld).sections;
	}

	/** Each render section's bottom, counted from the floor, not from 0. */
	@Redirect(method = "markRenderersForNewPosition",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;setPosition(III)V"), require = 1, allow = 1)
	private void mcfly$fromTheFloor(WorldRenderer renderer, int x, int y, int z) {
		renderer.setPosition(x, y + WorldHeights.range(theWorld).floor, z);
	}

	/** The lowest section of a range marked for redrawing, as an index into the column: counted from the floor's. */
	@Redirect(method = "markBlocksForUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;bucketInt(II)I", ordinal = 1),
		require = 1, allow = 1)
	private int mcfly$lowestInColumn(int y, int sixteen) {
		return mcfly$inColumn(y, sixteen);
	}

	/** The highest, the same way. (The method's other `bucketInt`s are of x and z.) */
	@Redirect(method = "markBlocksForUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;bucketInt(II)I", ordinal = 4),
		require = 1, allow = 1)
	private int mcfly$highestInColumn(int y, int sixteen) {
		return mcfly$inColumn(y, sixteen);
	}

	@Inject(method = "markBlocksForUpdate", at = @At("HEAD"), require = 1, allow = 1)
	private void mcfly$markedRange(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
		mcfly$markMinX = x1;
		mcfly$markMinY = y1;
		mcfly$markMinZ = z1;
		mcfly$markMaxX = x2;
		mcfly$markMaxY = y2;
		mcfly$markMaxZ = z2;
	}

	/**
	 * Decision 37: the render section in a marked slot counts as marked already, so the mark passes it by, unless it is
	 * at a marked place. 1.7.10 takes the slot modulo the grid and marks whatever section is there: past the grid's
	 * edge, the one at the opposite edge; past the column's top or floor, the other end's.
	 */
	@Redirect(method = "markBlocksForUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/WorldRenderer;needsUpdate:Z"),
		require = 1, allow = 1)
	private boolean mcfly$markedOrElsewhere(WorldRenderer section) {
		return section.needsUpdate || section.posX > mcfly$markMaxX || section.posX + 15 < mcfly$markMinX || section.posY > mcfly$markMaxY
			|| section.posY + 15 < mcfly$markMinY || section.posZ > mcfly$markMaxZ || section.posZ + 15 < mcfly$markMinZ;
	}

	@Inject(method = "updateRenderers", at = @At("HEAD"), require = 1, allow = 1)
	private void mcfly$noneWaitingYet(EntityLivingBase viewer, boolean inViewOnly, CallbackInfoReturnable<Boolean> cir) {
		mcfly$waiting = 0;
	}

	/**
	 * Decision 38: a queued section whose first build waits is passed by in the search for the ones to rebuild (its
	 * first `get`; the cleanup's, the second, keeps it in the queue).
	 */
	@Redirect(method = "updateRenderers", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0, remap = false),
		require = 1, allow = 1)
	private Object mcfly$unlessItWaits(List<Object> queue, int index) {
		Object section = queue.get(index);
		if (section != null && ((RenderSection) section).mcfly$waitsForTheChunksAround()) {
			mcfly$waiting++;
			return null;
		}
		return section;
	}

	/**
	 * Decision 38: all done when only waiting sections are left. Under a frame-rate limit `renderWorld` calls this until
	 * it says so or the frame's time is up; it would go round the waiting ones till then for nothing.
	 */
	@Inject(method = "updateRenderers", at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
	private void mcfly$doneButTheWaiting(EntityLivingBase viewer, boolean inViewOnly, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && worldRenderersToUpdate.size() <= mcfly$waiting) {
			cir.setReturnValue(true);
		}
	}

	@Unique
	private int mcfly$inColumn(int y, int sixteen) {
		return MathHelper.bucketInt(y, sixteen) - WorldHeights.range(theWorld).lowestSection;
	}

	/**
	 * Into the queue of render sections to rebuild only when there is something to build (`MixinWorldRenderer`). The
	 * move in `markRenderersForNewPosition` already queues only what is still marked.
	 */
	@Redirect(method = {"loadRenderers", "markBlocksForUpdate"},
		at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false), require = 2, allow = 2)
	private boolean mcfly$queueUnlessEmpty(List<Object> queue, Object renderer) {
		return !((RenderSection) renderer).mcfly$staysEmpty() && queue.add(renderer);
	}
}
