package io.github.asgerjon.mcfly.world;

import net.minecraft.world.World;

/** A world's height range, for any code: other mods, and EFR's `/fill`, `/clone` and `/setblock` at the fold. */
public final class WorldHeights {

	/** The most sections any dimension has: what the renderer makes room for when the game starts, before any world. */
	public static final int MOST_SECTIONS = HeightRange.TALL.sections;

	private WorldHeights() {
	}

	/** The range a dimension gets: the Overworld (0) is tall; every other dimension keeps 1.7.10's. */
	public static HeightRange forDimension(int dimension) {
		return dimension == 0 ? HeightRange.TALL : HeightRange.CLASSIC;
	}

	/** The world's range; 1.7.10's when there is no world. */
	public static HeightRange range(World world) {
		return world == null ? HeightRange.CLASSIC : ((TallWorld) world).mcfly$range();
	}

	/** The lowest y of the world that holds blocks. */
	public static int floor(World world) {
		return range(world).floor;
	}

	/** The highest y of the world that holds blocks. */
	public static int top(World world) {
		return range(world).top;
	}
}
