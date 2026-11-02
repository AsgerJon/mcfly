package io.github.asgerjon.mcfly.mixins.early.light;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallChunk;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The column's light over the light store: a column with nothing in it is open to the sky below the floor too, and
 * 1.7.10's slow relight checks walk every section of the store. (The column passes that set sky light,
 * `generateSkylightMap` and `relightBlock`, are rewritten whole in `storage.MixinChunk`; new sections of the store are
 * made there too.)
 */
@Mixin(Chunk.class)
public abstract class MixinChunkLight {

	@Shadow
	private int queuedLightChecks;

	@Shadow
	public World worldObj;

	@Shadow
	@Final
	public int xPosition;

	@Shadow
	@Final
	public int zPosition;

	/** A new column has nothing left to check: 1.7.10 marks that with 4096, its 16 sections' worth. */
	@Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
	private void mcfly$checksDone(World world, int chunkX, int chunkZ, CallbackInfo ci) {
		this.queuedLightChecks = 256 * WorldHeights.range(world).lightSections;
	}

	/**
	 * A column whose height map is the floor has nothing that stops light in it: it sees the sky at every y, below the
	 * floor too (spec §5). 1.7.10's range has no light below its floor.
	 */
	@Redirect(method = "canBlockSeeTheSky", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;heightMap:[I", args = "array=get"),
		require = 1, allow = 1)
	private int mcfly$skyFrom(int[] heights, int column) {
		int height = heights[column];
		HeightRange range = ((TallChunk) this).mcfly$range();
		return height == range.floor && !range.vanilla ? Integer.MIN_VALUE : height;
	}

	/**
	 * @author AsgerJon
	 * @reason The checks walk every section of the light store (1.7.10: 16 sections, 4096 checks); in 1.7.10's range
	 * they are 1.7.10's.
	 */
	@Overwrite
	public void enqueueRelightChecks() {
		TallChunk tall = (TallChunk) this;
		HeightRange range = tall.mcfly$range();
		int sections = range.lightSections;
		for (int i = 0; i < 8; ++i) {
			if (this.queuedLightChecks >= 256 * sections) {
				return;
			}
			int index = this.queuedLightChecks % sections;
			int x = this.queuedLightChecks / sections % 16;
			int z = this.queuedLightChecks / (sections * 16);
			++this.queuedLightChecks;
			int number = range.lowestLightSection + index;
			ExtendedBlockStorage section = tall.mcfly$section(number);
			int worldX = (this.xPosition << 4) + x;
			int worldZ = (this.zPosition << 4) + z;
			for (int dy = 0; dy < 16; ++dy) {
				boolean edge = dy == 0 || dy == 15 || x == 0 || x == 15 || z == 0 || z == 15;
				if (section == null && edge || section != null && section.getBlockByExtId(x, dy, z).getMaterial() == Material.air) {
					mcfly$checkAround(worldX, (number << 4) + dy, worldZ);
				}
			}
		}
	}

	/** 1.7.10's check of one place: the light of each neighbour that gives light, then its own. */
	@Unique
	private void mcfly$checkAround(int x, int y, int z) {
		if (this.worldObj.getBlock(x, y - 1, z).getLightValue() > 0) {
			this.worldObj.func_147451_t(x, y - 1, z);
		}
		if (this.worldObj.getBlock(x, y + 1, z).getLightValue() > 0) {
			this.worldObj.func_147451_t(x, y + 1, z);
		}
		if (this.worldObj.getBlock(x - 1, y, z).getLightValue() > 0) {
			this.worldObj.func_147451_t(x - 1, y, z);
		}
		if (this.worldObj.getBlock(x + 1, y, z).getLightValue() > 0) {
			this.worldObj.func_147451_t(x + 1, y, z);
		}
		if (this.worldObj.getBlock(x, y, z - 1).getLightValue() > 0) {
			this.worldObj.func_147451_t(x, y, z - 1);
		}
		if (this.worldObj.getBlock(x, y, z + 1).getLightValue() > 0) {
			this.worldObj.func_147451_t(x, y, z + 1);
		}
		this.worldObj.func_147451_t(x, y, z);
	}
}
