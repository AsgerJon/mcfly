package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallChunk;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Where natural spawning tries in a chunk: a random column, and a y from the bottom of its lowest block section up to
 * the top of its highest. 1.7.10 tries from 0; later versions from the floor, which spreads the same tries over 64 more
 * empty blocks. A chunk with nothing below 0 keeps 1.7.10's spawn rates. One with no sections at all tries 0..14, as
 * 1.7.10 does.
 */
@Mixin(SpawnerAnimals.class)
public abstract class MixinSpawnerAnimals {

	/**
	 * @author AsgerJon
	 * @reason Heights below 0 too, where the chunk has blocks there.
	 */
	@Overwrite
	protected static ChunkPosition func_151350_a(World world, int chunkX, int chunkZ) {
		Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
		int x = chunkX * 16 + world.rand.nextInt(16);
		int z = chunkZ * 16 + world.rand.nextInt(16);
		if (chunk == null) {
			return new ChunkPosition(x, world.rand.nextInt(world.getActualHeight()), z);
		}
		TallChunk tall = (TallChunk) chunk;
		HeightRange range = tall.mcfly$range();
		int lowest = 0;
		int highest = 0;
		boolean any = false;
		for (int number = range.lowestSection; number < range.lowestSection + range.sections; ++number) {
			if (tall.mcfly$section(number) != null) {
				if (!any) {
					lowest = Math.min(number << 4, 0);
					any = true;
				}
				highest = number << 4;
			}
		}
		// 1.7.10: nextInt(top of the highest section's base + 15), from 0
		return new ChunkPosition(x, lowest + world.rand.nextInt(highest + 15 - lowest), z);
	}
}
