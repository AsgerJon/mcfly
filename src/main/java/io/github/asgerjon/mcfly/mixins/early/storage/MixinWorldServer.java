package io.github.asgerjon.mcfly.mixins.early.storage;

import io.github.asgerjon.mcfly.world.TallChunk;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Random ticks reach every block section of the column, not only 0..15. */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

	// 1.7.10's loop takes each section's y from the section itself, so any array of sections will do
	@Redirect(method = "func_147456_g",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getBlockStorageArray()[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"),
		require = 1, allow = 1)
	private ExtendedBlockStorage[] mcfly$everyBlockSection(Chunk chunk) {
		return ((TallChunk) chunk).mcfly$blockSections();
	}
}
