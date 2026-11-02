package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.block.material.Material;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Where `/spreadplayers` puts a player in a column, and whether the column is safe: searched from top + 1 down to the
 * floor for its highest block; no block means unsafe, as does a liquid (or fire, as 1.7.10 has it) (§4 answer 4).
 * 1.7.10 searched from 256 down to 1 for the place, and for safety looked only at y 256, which is always air: every
 * column was safe. 1.7.10's range keeps all of that.
 */
// package-private in 1.7.10, so named by string
@Mixin(targets = "net.minecraft.command.CommandSpreadPlayers$Position")
public abstract class MixinSpreadPlayersPosition {

	@Shadow
	double field_111101_a;

	@Shadow
	double field_111100_b;

	/**
	 * @author AsgerJon
	 * @reason The search covers the whole range.
	 */
	@Overwrite
	public int func_111092_a(World world) {
		HeightRange range = WorldHeights.range(world);
		int x = MathHelper.floor_double(this.field_111101_a);
		int z = MathHelper.floor_double(this.field_111100_b);
		int lowest = range.vanilla ? 1 : range.floor;
		for (int y = range.top + 1; y >= lowest; --y) {
			if (world.getBlock(x, y, z).getMaterial() != Material.air) {
				return y + 1;
			}
		}
		return range.top + 2;
	}

	/**
	 * @author AsgerJon
	 * @reason Safe means a highest block that is no liquid or fire, searched over the whole range.
	 */
	@Overwrite
	public boolean func_111098_b(World world) {
		HeightRange range = WorldHeights.range(world);
		int x = MathHelper.floor_double(this.field_111101_a);
		int z = MathHelper.floor_double(this.field_111100_b);
		if (range.vanilla) {
			Material material = world.getBlock(x, 256, z).getMaterial();
			return !material.isLiquid() && material != Material.fire;
		}
		for (int y = range.top + 1; y >= range.floor; --y) {
			Material material = world.getBlock(x, y, z).getMaterial();
			if (material != Material.air) {
				return !material.isLiquid() && material != Material.fire;
			}
		}
		return false;
	}
}
