package io.github.asgerjon.mcfly.mixins.early.storage;

import io.github.asgerjon.mcfly.world.ChunkSaving;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The tall parts of a saved chunk (`ChunkSaving`), in 1.7.10's own read and write rather than in Forge's
 * `ChunkDataEvent`: block entities load before that event, and one below 0 needs its section in place first.
 */
@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader {

	@Inject(method = "writeChunkToNBT", at = @At("TAIL"), require = 1, allow = 1)
	private void mcfly$writeTallParts(Chunk chunk, World world, NBTTagCompound level, CallbackInfo ci) {
		ChunkSaving.write(chunk, world, level);
	}

	// may run on Forge's chunk loading thread: it touches only the chunk being read, which nothing else has yet
	@Inject(method = "readChunkFromNBT", at = @At("RETURN"), require = 1, allow = 1)
	private void mcfly$readTallParts(World world, NBTTagCompound level, CallbackInfoReturnable<Chunk> cir) {
		ChunkSaving.read(cir.getReturnValue(), world, level);
	}
}
