package io.github.asgerjon.mcfly.mixins.early.client.storage;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallChunk;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** The client's own height map pass (a client-only method of `Chunk`), down to the floor like the server's. */
@Mixin(Chunk.class)
public abstract class MixinChunkClient {

	@Shadow
	public int[] precipitationHeightMap;

	@Shadow
	public int[] heightMap;

	@Shadow
	public boolean isModified;

	@Shadow
	public int heightMapMinimum;

	@Shadow
	public abstract int getTopFilledSegment();

	@Shadow
	public abstract int func_150808_b(int x, int y, int z);

	/**
	 * @author AsgerJon
	 * @reason The scan reaches down to the floor. In the tall range it also sees the highest section's top layer, and a
	 * column with nothing in it gets the floor; 1.7.10's range keeps 1.7.10's scan, which does neither.
	 */
	@Overwrite
	public void generateHeightMap() {
		HeightRange range = ((TallChunk) this).mcfly$range();
		int top = this.getTopFilledSegment();
		this.heightMapMinimum = Integer.MAX_VALUE;
		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				this.precipitationHeightMap[x + (z << 4)] = -999;
				// 1.7.10 starts one lower and so never sees a block in the highest section's top layer (y 255 there)
				int height = range.vanilla ? top + 15 : top + 16;
				while (height > range.floor && this.func_150808_b(x, height - 1, z) == 0) {
					--height;
				}
				if (height > range.floor || !range.vanilla) {
					this.heightMap[z << 4 | x] = height;
					if (height < this.heightMapMinimum) {
						this.heightMapMinimum = height;
					}
				}
			}
		}
		this.isModified = true;
	}
}
