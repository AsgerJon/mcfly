package io.github.asgerjon.mcfly.mixins.early.storage;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.TallChunk;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * The column holds every section of its world's range (PLAN-mcfly.md §1 "Sections"). Sections 0..15 stay in 1.7.10's
 * `storageArrays`, so code walking `getBlockStorageArray()` sees y 0..255 as before; the light store's other sections
 * live in a second array. 1.7.10's `storageArrays[y >> 4]` reads go through the lookup by section number, and new
 * sections outside 0..15 are made before 1.7.10 would store them; y >> 4 and y & 15 are already right for negative y. The column scans whose bounds sit among light levels are
 * rewritten whole, with the range's bounds; in 1.7.10's range they do exactly what 1.7.10 does.
 */
@Mixin(Chunk.class)
public abstract class MixinChunk implements TallChunk {

	@Shadow
	private ExtendedBlockStorage[] storageArrays;

	@Shadow
	public int[] precipitationHeightMap;

	@Shadow
	public World worldObj;

	@Shadow
	public int[] heightMap;

	@Shadow
	@Final
	public int xPosition;

	@Shadow
	@Final
	public int zPosition;

	@Shadow
	public boolean isModified;

	@Shadow
	public int heightMapMinimum;

	@Shadow
	public abstract Block getBlock(int x, int y, int z);

	@Shadow
	public abstract int func_150808_b(int x, int y, int z);

	@Shadow
	private void updateSkylightNeighborHeight(int x, int z, int fromY, int toY) {
	}

	/** The range of the chunk's world, taken when the chunk is made. */
	@Unique
	private HeightRange mcfly$range;

	/** The light store's sections outside 0..15: the ones below 0, lowest first, then 16 and up. */
	@Unique
	private ExtendedBlockStorage[] mcfly$outside;

	/** Filled by {@link #mcfly$blockSections} on each call. */
	@Unique
	private ExtendedBlockStorage[] mcfly$blockSections;

	// the other two constructors start with this one
	@Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
	private void mcfly$takeRange(World world, int chunkX, int chunkZ, CallbackInfo ci) {
		HeightRange range = WorldHeights.range(world);
		mcfly$range = range;
		mcfly$outside = new ExtendedBlockStorage[range.lightSections - 16];
		if (!range.vanilla) {
			// a column with nothing in it: its height is the floor (spec §4)
			Arrays.fill(heightMap, range.floor);
		}
	}

	@Override
	public HeightRange mcfly$range() {
		return mcfly$range;
	}

	@Override
	public ExtendedBlockStorage mcfly$section(int number) {
		if ((number & ~15) == 0) {
			return storageArrays[number];
		}
		int slot = mcfly$slot(number);
		return slot < 0 ? null : mcfly$outside[slot];
	}

	@Override
	public void mcfly$setSection(int number, ExtendedBlockStorage section) {
		if ((number & ~15) == 0) {
			storageArrays[number] = section;
			return;
		}
		int slot = mcfly$slot(number);
		if (slot >= 0) {
			mcfly$outside[slot] = section;
		}
	}

	@Override
	public ExtendedBlockStorage[] mcfly$blockSections() {
		HeightRange range = mcfly$range;
		if (range.vanilla) {
			return storageArrays;
		}
		ExtendedBlockStorage[] all = mcfly$blockSections;
		if (all == null) {
			all = mcfly$blockSections = new ExtendedBlockStorage[range.sections];
		}
		for (int i = 0; i < all.length; i++) {
			all[i] = mcfly$section(range.lowestSection + i);
		}
		return all;
	}

	/**
	 * The height the light passes take for a column: its height map, except that an open column (height = the floor)
	 * is open down to the light store's bottom. The same in 1.7.10's range, which has no store below its floor.
	 */
	@Unique
	private int mcfly$lightHeight(int height) {
		HeightRange range = mcfly$range;
		return height == range.floor ? range.lightFloor : height;
	}

	/** The place in {@link #mcfly$outside} of a section number outside 0..15, or -1 when the store has none. */
	@Unique
	private int mcfly$slot(int number) {
		HeightRange range = mcfly$range;
		if (!range.hasLightSection(number)) {
			return -1;
		}
		return number < 0 ? number - range.lowestLightSection : number - 16 - range.lowestLightSection;
	}

	@Redirect(method = {"getBlock", "getBlockMetadata", "func_150807_a", "setBlockMetadata", "getSavedLightValue", "setLightValue",
		"getBlockLightValue"},
		at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
			args = "array=get"), require = 7, allow = 7)
	private ExtendedBlockStorage mcfly$sectionAt(ExtendedBlockStorage[] sections, int number) {
		return mcfly$section(number);
	}

	/** `getBlock` and `getBlockMetadata` read air above `y >> 4 < storageArrays.length`: one past the store's top section. */
	@Redirect(method = {"getBlock", "getBlockMetadata"},
		at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
			args = "array=length"), require = 2, allow = 2)
	private int mcfly$pastTheTopSection(ExtendedBlockStorage[] sections) {
		return mcfly$range.lowestLightSection + mcfly$range.lightSections;
	}

	/**
	 * The column refuses blocks outside floor..top, as the world does (1.7.10 would fail on the array there). A block
	 * going into a section outside 0..15 that the column doesn't have yet gets the section made here, first; 1.7.10
	 * makes its own new sections by storing into `storageArrays`, which can't hold these (and which can't be redirected:
	 * 1.7.10 keeps a copy of the new section on the stack, `dup_x2`). As 1.7.10 does for a new section above the height
	 * map, the sky light pass runs then: here before the block goes in, so 1.7.10 relights the column for it after.
	 */
	@Inject(method = "func_150807_a", at = @At("HEAD"), cancellable = true)
	private void mcfly$sectionForBlock(int x, int y, int z, Block block, int meta, CallbackInfoReturnable<Boolean> cir) {
		HeightRange range = mcfly$range;
		if (!range.holdsBlocks(y)) {
			cir.setReturnValue(false);
			return;
		}
		int number = y >> 4;
		if ((number & ~15) != 0 && block != Blocks.air && mcfly$section(number) == null) {
			mcfly$setSection(number, new ExtendedBlockStorage(number << 4, !this.worldObj.provider.hasNoSky));
			if (y >= this.heightMap[z << 4 | x]) {
				this.generateSkylightMap();
			}
		}
	}

	/** Light going into a section outside 0..15 that the column doesn't have yet: the section, made as 1.7.10 makes one. */
	@Inject(method = "setLightValue", at = @At("HEAD"))
	private void mcfly$sectionForLight(EnumSkyBlock type, int x, int y, int z, int value, CallbackInfo ci) {
		int number = y >> 4;
		if ((number & ~15) != 0 && mcfly$range.hasLightSection(number) && mcfly$section(number) == null) {
			mcfly$setSection(number, new ExtendedBlockStorage(number << 4, !this.worldObj.provider.hasNoSky));
			this.generateSkylightMap();
		}
	}

	/**
	 * @author AsgerJon
	 * @reason The highest section reaches past 15, and a column without sections has the floor as its top (spec §3).
	 */
	@Overwrite
	public int getTopFilledSegment() {
		HeightRange range = mcfly$range;
		for (int number = range.lowestSection + range.sections - 1; number >= range.lowestSection; --number) {
			ExtendedBlockStorage section = mcfly$section(number);
			if (section != null) {
				return section.getYLocation();
			}
		}
		return range.floor;
	}

	/**
	 * @author AsgerJon
	 * @reason The height map reaches down to the floor, and the first sky light over the whole light store. In the tall
	 * range the height map also sees the highest section's top layer, and a column with nothing in it gets the floor;
	 * 1.7.10's range keeps 1.7.10's pass, which does none of these.
	 */
	@Overwrite
	public void generateSkylightMap() {
		HeightRange range = mcfly$range;
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
				if (!this.worldObj.provider.hasNoSky) {
					// from the light store's top down to its bottom (1.7.10: from the highest section's top down to y 1)
					int light = 15;
					int y = range.vanilla ? top + 15 : range.lightTop;
					int lowest = range.vanilla ? 1 : range.lightFloor;
					do {
						int opacity = this.func_150808_b(x, y, z);
						if (opacity == 0 && light != 15) {
							opacity = 1;
						}
						light -= opacity;
						if (light > 0) {
							ExtendedBlockStorage section = mcfly$section(y >> 4);
							if (section != null) {
								section.setExtSkylightValue(x, y & 15, z, light);
								this.worldObj.func_147479_m((this.xPosition << 4) + x, y, (this.zPosition << 4) + z);
							}
						}
						--y;
					} while (y >= lowest && light > 0);
				}
			}
		}
		this.isModified = true;
	}

	/**
	 * @author AsgerJon
	 * @reason The column's height runs from the floor to top + 1 (1.7.10 cut it to 8 bits with `& 255` and stopped at
	 * 0), and the sky light below it to the bottom of the light store; an open column is lit below the floor too.
	 * 1.7.10's range keeps the 8 bits.
	 */
	@Overwrite
	private void relightBlock(int x, int y, int z) {
		HeightRange range = mcfly$range;
		int oldHeight = this.heightMap[z << 4 | x];
		if (range.vanilla) {
			// 1.7.10 reads its height as 8 bits, so 256 (a block at 255) reads 0
			oldHeight &= 255;
		}
		int height = oldHeight;
		if (y > oldHeight) {
			height = y;
		}
		while (height > range.floor && this.func_150808_b(x, height - 1, z) == 0) {
			--height;
		}
		if (height == oldHeight) {
			return;
		}
		// light uses an open column's height as the light store's bottom: it is open below the floor too (spec §5)
		int lightHeight = mcfly$lightHeight(height);
		int oldLightHeight = mcfly$lightHeight(oldHeight);
		// as in 1.7.10, the column's light is redone while the height map still has the old height
		this.worldObj.markBlocksDirtyVertical(x + this.xPosition * 16, z + this.zPosition * 16, lightHeight, oldLightHeight);
		this.heightMap[z << 4 | x] = height;
		int worldX = this.xPosition * 16 + x;
		int worldZ = this.zPosition * 16 + z;
		int low = Math.min(lightHeight, oldLightHeight);
		int high = Math.max(lightHeight, oldLightHeight);
		if (!this.worldObj.provider.hasNoSky) {
			// open from the new height up, shaded from the old height down to the new one
			int skyLight = height < oldHeight ? 15 : 0;
			for (int ly = low; ly < high; ++ly) {
				ExtendedBlockStorage section = mcfly$section(ly >> 4);
				if (section != null) {
					section.setExtSkylightValue(x, ly & 15, z, skyLight);
					this.worldObj.func_147479_m((this.xPosition << 4) + x, ly, (this.zPosition << 4) + z);
				}
			}
			int light = 15;
			int ly = lightHeight;
			while (ly > range.lightFloor && light > 0) {
				--ly;
				int opacity = this.func_150808_b(x, ly, z);
				if (opacity == 0) {
					opacity = 1;
				}
				light -= opacity;
				if (light < 0) {
					light = 0;
				}
				ExtendedBlockStorage section = mcfly$section(ly >> 4);
				if (section != null) {
					section.setExtSkylightValue(x, ly & 15, z, light);
				}
			}
		}
		if (height < this.heightMapMinimum) {
			this.heightMapMinimum = height;
		}
		if (!this.worldObj.provider.hasNoSky) {
			this.updateSkylightNeighborHeight(worldX - 1, worldZ, low, high);
			this.updateSkylightNeighborHeight(worldX + 1, worldZ, low, high);
			this.updateSkylightNeighborHeight(worldX, worldZ - 1, low, high);
			this.updateSkylightNeighborHeight(worldX, worldZ + 1, low, high);
			this.updateSkylightNeighborHeight(worldX, worldZ, low, high);
		}
		this.isModified = true;
	}

	/**
	 * @author AsgerJon
	 * @reason The column is searched down to the floor, and a column with nothing that blocks movement or holds a fluid
	 * gets the floor (spec §4). 1.7.10's range keeps 1.7.10's search, which leaves out y 0 and answers -1.
	 */
	@Overwrite
	public int getPrecipitationHeight(int x, int z) {
		HeightRange range = mcfly$range;
		int column = x | z << 4;
		int height = this.precipitationHeightMap[column];
		if (height == -999) {
			int lowest = range.vanilla ? 1 : range.floor;
			height = range.noSurface;
			for (int y = this.getTopFilledSegment() + 15; y >= lowest; --y) {
				Material material = this.getBlock(x, y, z).getMaterial();
				if (material.blocksMovement() || material.isLiquid()) {
					height = y + 1;
					break;
				}
			}
			this.precipitationHeightMap[column] = height;
		}
		return height;
	}

	/**
	 * @author AsgerJon
	 * @reason The span is clamped to floor..top, not 0..255, and read by section number.
	 */
	@Overwrite
	public boolean getAreLevelsEmpty(int fromY, int toY) {
		HeightRange range = mcfly$range;
		if (fromY < range.floor) {
			fromY = range.floor;
		}
		if (toY > range.top) {
			toY = range.top;
		}
		for (int y = fromY; y <= toY; y += 16) {
			ExtendedBlockStorage section = mcfly$section(y >> 4);
			if (section != null && !section.isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
