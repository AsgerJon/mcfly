package io.github.asgerjon.mcfly.client.render;

/** A render section (1.7.10's `WorldRenderer`) as McFly sees it, given this by `mixins.early.client.render.MixinWorldRenderer`. */
public interface RenderSection {

	/**
	 * Whether marking it for redrawing would change nothing: its own 16 × 16 × 16 blocks are all air (or its chunk isn't
	 * there), and it draws nothing now, tile entities included.
	 */
	boolean mcfly$staysEmpty();

	/** How long a first build waits at most for the chunks around (decision 38): 2 s. */
	int FIRST_BUILD_WAIT_TICKS = 40;

	/**
	 * Whether its rebuild waits (decision 38): it hasn't been built since it came to its place, a chunk of the 8 around
	 * its own isn't on the client, and it hasn't waited {@link #FIRST_BUILD_WAIT_TICKS} yet, counted from the first time
	 * this was asked since then.
	 */
	boolean mcfly$waitsForTheChunksAround();
}
