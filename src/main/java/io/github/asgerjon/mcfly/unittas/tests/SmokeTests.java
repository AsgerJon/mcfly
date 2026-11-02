package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.init.Blocks;
import io.github.asgerjon.mcfly.unittas.TasTestCase;

/** A few tests of the plain game, to see unitTAS work in a new mod: the server, the client, keys, sounds, commands. */
public class SmokeTests extends TasTestCase {

	public void testBlockSetOnTheServerReachesTheClient() {
		onServer(() -> world().setBlock(3, 4, 3, Blocks.stone));
		waitUntilOnClient("the stone on the client", () -> mc().theWorld.getBlock(3, 4, 3) == Blocks.stone, 20);
	}

	public void testGiveCommand() {
		assertNoError(command("/give @p wool 1 14"));
		// give drops the stack at the player's feet, to be picked up a tick or so later
		waitUntil("the wool picked up", () -> player().getHeldItem() != null, 20);
		assertEqual("minecraft:wool:14", id(held()));
	}

	public void testSoundIsHeard() {
		onServer(() -> world().playSoundAtEntity(player(), "random.click", 1.0F, 1.0F));
		waitForSound("minecraft:random.click", 20);
	}

	public void testWalkingForward() {
		double before = onServer(() -> player().posZ);
		hold(mc().gameSettings.keyBindForward, 20);
		double after = onServer(() -> player().posZ);
		// facing south (yaw 0) is towards +z; a second of walking goes about 4 blocks
		assertGreater(after - before, 2.0);
	}
}
