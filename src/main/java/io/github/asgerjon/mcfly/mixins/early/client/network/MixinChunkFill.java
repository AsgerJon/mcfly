package io.github.asgerjon.mcfly.mixins.early.client.network;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallChunk;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The client fills a column from a chunk packet: every section of the light store the mask names (bit i: the store's
 * i-th section from the bottom), in the layout of `network.MixinS21PacketChunkData`. In 1.7.10's range, exactly 1.7.10's.
 */
@Mixin(Chunk.class)
public abstract class MixinChunkFill {

	@Shadow
	public Map<ChunkPosition, TileEntity> chunkTileEntityMap;

	@Shadow
	public World worldObj;

	@Shadow
	private byte[] blockBiomeArray;

	@Shadow
	public boolean isLightPopulated;

	@Shadow
	public boolean isTerrainPopulated;

	@Shadow
	public abstract void generateHeightMap();

	@Shadow
	public abstract Block getBlock(int x, int y, int z);

	@Shadow
	public abstract int getBlockMetadata(int x, int y, int z);

	/**
	 * @author AsgerJon
	 * @reason The sections are the light store's, by section number, not 1.7.10's 16 by array index.
	 */
	@Overwrite
	public void fillChunk(byte[] data, int mask, int highBitsMask, boolean whole) {
		// Forge: the block entities learn their blocks first
		for (TileEntity tile : this.chunkTileEntityMap.values()) {
			tile.updateContainingBlockInfo();
			tile.getBlockMetadata();
			tile.getBlockType();
		}
		TallChunk tall = (TallChunk) this;
		HeightRange range = tall.mcfly$range();
		boolean sky = !this.worldObj.provider.hasNoSky;
		ExtendedBlockStorage[] filled = new ExtendedBlockStorage[range.lightSections];
		int at = 0;
		for (int i = 0; i < range.lightSections; ++i) {
			int number = range.lowestLightSection + i;
			ExtendedBlockStorage section = tall.mcfly$section(number);
			if ((mask & 1 << i) != 0) {
				if (section == null) {
					section = new ExtendedBlockStorage(number << 4, sky);
					tall.mcfly$setSection(number, section);
				}
				filled[i] = section;
				at = mcfly$copy(data, at, section.getBlockLSBArray());
			} else if (whole && section != null) {
				tall.mcfly$setSection(number, null);
			}
		}
		for (ExtendedBlockStorage section : filled) {
			if (section != null) {
				at = mcfly$copy(data, at, section.getMetadataArray().data);
			}
		}
		for (ExtendedBlockStorage section : filled) {
			if (section != null) {
				at = mcfly$copy(data, at, section.getBlocklightArray().data);
			}
		}
		if (sky) {
			for (ExtendedBlockStorage section : filled) {
				if (section != null) {
					at = mcfly$copy(data, at, section.getSkylightArray().data);
				}
			}
		}
		for (int i = 0; i < range.lightSections; ++i) {
			ExtendedBlockStorage section = tall.mcfly$section(range.lowestLightSection + i);
			if ((highBitsMask & 1 << i) != 0) {
				if (section == null) {
					at += 2048;
				} else {
					NibbleArray highBits = section.getBlockMSBArray();
					if (highBits == null) {
						highBits = section.createBlockMSBArray();
					}
					at = mcfly$copy(data, at, highBits.data);
				}
			} else if (whole && section != null && section.getBlockMSBArray() != null) {
				section.clearMSBArray();
			}
		}
		if (whole) {
			System.arraycopy(data, at, this.blockBiomeArray, 0, this.blockBiomeArray.length);
		}
		for (ExtendedBlockStorage section : filled) {
			if (section != null) {
				section.removeInvalidBlocks();
			}
		}
		this.isLightPopulated = true;
		this.isTerrainPopulated = true;
		this.generateHeightMap();
		// Forge: block entities whose block changed go
		List<TileEntity> invalid = new ArrayList<>();
		for (TileEntity tile : this.chunkTileEntityMap.values()) {
			int x = tile.xCoord & 15;
			int y = tile.yCoord;
			int z = tile.zCoord & 15;
			Block block = tile.getBlockType();
			if ((block != getBlock(x, y, z) || tile.blockMetadata != this.getBlockMetadata(x, y, z))
				&& tile.shouldRefresh(block, getBlock(x, y, z), tile.blockMetadata, this.getBlockMetadata(x, y, z), worldObj, x, y, z)) {
				invalid.add(tile);
			}
			tile.updateContainingBlockInfo();
		}
		for (TileEntity tile : invalid) {
			tile.invalidate();
		}
	}

	@Unique
	private static int mcfly$copy(byte[] from, int at, byte[] to) {
		System.arraycopy(from, at, to, 0, to.length);
		return at + to.length;
	}
}
