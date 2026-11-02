package io.github.asgerjon.mcfly.mixins.early.network;

import io.github.asgerjon.mcfly.network.BlockChanges;
import io.github.asgerjon.mcfly.network.TallBlockChanges;
import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeModContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;

/**
 * A column's block changes in a tick, kept with their full y (1.7.10 packs each into a short with 8 bits of y), and a
 * section mask over the whole light store (1.7.10: 16 bits). Sent at the tick's end as 1.7.10 (with Forge) does: one
 * change alone, up to Forge's threshold batched, past it the changed sections whole, and the block entities of the
 * changed blocks after.
 */
@Mixin(targets = "net.minecraft.server.management.PlayerManager$PlayerInstance")
public abstract class MixinPlayerInstance {

	/** The PlayerManager this column belongs to: the inner class's outer instance. */
	@Shadow(remap = false)
	@Final
	PlayerManager this$0;

	@Shadow
	@Final
	private ChunkCoordIntPair chunkLocation;

	@Shadow
	private int numberOfTilesToUpdate;

	@Shadow
	private int flagsYAreasToUpdate;

	@Shadow
	public abstract void sendToAllPlayersWatchingChunk(Packet packet);

	@Shadow
	private void sendTileToAllPlayersWatchingChunk(TileEntity tile) {
	}

	/** The changed places this tick (`BlockChanges.position`), the first `numberOfTilesToUpdate` of them. */
	@Unique
	private int[] mcfly$changes = new int[64];

	/**
	 * @author AsgerJon
	 * @reason The change's place keeps its full y, and its section counts in a mask over the light store.
	 */
	@Overwrite
	public void flagChunkForUpdate(int x, int y, int z) {
		if (this.numberOfTilesToUpdate == 0) {
			((AccessorPlayerManager) this$0).mcfly$columnsToSend().add(this);
		}
		HeightRange range = WorldHeights.range(this$0.getWorldServer());
		this.flagsYAreasToUpdate |= 1 << ((y >> 4) - range.lowestLightSection);
		int position = BlockChanges.position(x, y, z);
		for (int i = 0; i < this.numberOfTilesToUpdate; ++i) {
			if (mcfly$changes[i] == position) {
				return;
			}
		}
		// Forge keeps every change, however many
		if (this.numberOfTilesToUpdate == mcfly$changes.length) {
			mcfly$changes = Arrays.copyOf(mcfly$changes, mcfly$changes.length << 1);
		}
		mcfly$changes[this.numberOfTilesToUpdate++] = position;
	}

	/**
	 * @author AsgerJon
	 * @reason The changes keep their full y on the way out: one alone as a block change, more as a batch of full
	 * places, many as the changed sections of the light store.
	 */
	@Overwrite
	public void sendChunkUpdate() {
		if (this.numberOfTilesToUpdate == 0) {
			return;
		}
		WorldServer world = this$0.getWorldServer();
		int chunkX = this.chunkLocation.chunkXPos;
		int chunkZ = this.chunkLocation.chunkZPos;
		if (this.numberOfTilesToUpdate == 1) {
			int position = mcfly$changes[0];
			int x = chunkX * 16 + BlockChanges.x(position);
			int y = BlockChanges.y(position);
			int z = chunkZ * 16 + BlockChanges.z(position);
			this.sendToAllPlayersWatchingChunk(new S23PacketBlockChange(x, y, z, world));
			if (world.getBlock(x, y, z).hasTileEntity(world.getBlockMetadata(x, y, z))) {
				this.sendTileToAllPlayersWatchingChunk(world.getTileEntity(x, y, z));
			}
		} else {
			if (this.numberOfTilesToUpdate >= ForgeModContainer.clumpingThreshold) {
				this.sendToAllPlayersWatchingChunk(new S21PacketChunkData(world.getChunkFromChunkCoords(chunkX, chunkZ), false, this.flagsYAreasToUpdate));
			} else {
				S22PacketMultiBlockChange batch = new S22PacketMultiBlockChange();
				((TallBlockChanges) batch).mcfly$fill(this.numberOfTilesToUpdate, mcfly$changes, world.getChunkFromChunkCoords(chunkX, chunkZ));
				this.sendToAllPlayersWatchingChunk(batch);
			}
			// Forge: only the block entities of the changed blocks
			for (int i = 0; i < this.numberOfTilesToUpdate; ++i) {
				int position = mcfly$changes[i];
				int x = chunkX * 16 + BlockChanges.x(position);
				int y = BlockChanges.y(position);
				int z = chunkZ * 16 + BlockChanges.z(position);
				if (world.getBlock(x, y, z).hasTileEntity(world.getBlockMetadata(x, y, z))) {
					this.sendTileToAllPlayersWatchingChunk(world.getTileEntity(x, y, z));
				}
			}
		}
		this.numberOfTilesToUpdate = 0;
		this.flagsYAreasToUpdate = 0;
	}
}
