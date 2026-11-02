package io.github.asgerjon.mcfly.mixins.early.network;

import net.minecraft.server.management.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The columns with changes to send this tick, which `MixinPlayerInstance` adds its column to. */
@Mixin(PlayerManager.class)
public interface AccessorPlayerManager {

	@Accessor("chunkWatcherWithPlayers")
	List<Object> mcfly$columnsToSend();
}
