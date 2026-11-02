package io.github.asgerjon.mcfly.world;

/** A world as McFly sees it: 1.7.10's `World`, given its range by `mixins.early.storage.MixinWorld`. */
public interface TallWorld {

	/** The world's range, from its dimension; fixed for the world's life. */
	HeightRange mcfly$range();
}
