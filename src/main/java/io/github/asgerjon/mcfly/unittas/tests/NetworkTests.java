package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stage (c), suite `network`: blocks, block entities and light outside 0..255 reach the client, whether as a whole
 * chunk, one change, a batch, or a resent part of a chunk (spec §7). Every packet makes the trip through its wire
 * format both ways during these tests (`WireTap`), as over a real connection. Each test builds at its own x at z 64..79,
 * south of spawn, inside the client's view; the whole-chunk test goes to chunk 125, 4.
 */
public class NetworkTests extends McFlyTestCase {

	@Override
	protected void setUp() {
		WireTap.FAILURES.clear();
		onClient(() -> WireTap.install(mc().getNetHandler().getNetworkManager()));
		onServer(() -> WireTap.install(player().playerNetServerHandler.netManager));
	}

	@Override
	protected void tearDown() {
		onClient(() -> WireTap.remove(mc().getNetHandler().getNetworkManager()));
		onServer(() -> WireTap.remove(player().playerNetServerHandler.netManager));
		assertEqual(Collections.emptyList(), new ArrayList<>(WireTap.FAILURES), "packets that failed to make the trip through their wire format");
	}

	public void testAWholeChunkCarriesTallSections() {
		// chunk 125, 4: far outside the client's view of 12 chunks
		int x = 2004;
		int z = 68;
		for (int y : new int[] {FLOOR, -30, 256, TOP}) {
			place(x, y, z, Blocks.stone);
		}
		onServer(() -> player().playerNetServerHandler.setPlayerLocation(x + 0.5, 4.0, z + 4.5, 0.0F, 0.0F));
		waitUntilOnClient("the client to get the chunk at x 2000..2015 (its grass)", () -> mc().theWorld.getBlock(x, 3, z) == Blocks.grass, 200);
		assertEqual(Arrays.asList("minecraft:stone", "minecraft:stone", "minecraft:stone", "minecraft:stone"),
			onClient(() -> clientBlocks(x, z, FLOOR, -30, 256, TOP)), "the client's blocks at -64, -30, 256 and 319 of a chunk it was sent whole");
	}

	public void testOneChangeBelowZero() {
		place(4, -30, 64, Blocks.stone);
		waitUntilOnClient("the stone at -30 on the client", () -> mc().theWorld.getBlock(4, -30, 64) == Blocks.stone, 40);
	}

	public void testOneChangeAboveOldTop() {
		place(6, 300, 64, Blocks.stone);
		waitUntilOnClient("the stone at 300 on the client", () -> mc().theWorld.getBlock(6, 300, 64) == Blocks.stone, 40);
	}

	public void testABatchAcrossTheColumn() {
		int[] heights = {FLOOR, -50, -30, -1, 0, 100, 255, 256, 300, TOP};
		// one tick, so that they go as one batch
		int refused = onServer(() -> {
			int count = 0;
			for (int y : heights) {
				if (!world().setBlock(8, y, 64, Blocks.stone)) {
					count++;
				}
			}
			return count;
		});
		assertEqual(0, refused, "blocks the world refused");
		waitUntilOnClient("the ten stones from -64 to 319 on the client", () -> clientBlocks(8, 64, heights).stream().allMatch("minecraft:stone"::equals), 40);
	}

	public void testManyChangesResendSections() {
		// 100 changes in one tick, 50 at -60 and 50 at 310, are more than Forge sends one by one (64): the sections go again
		int refused = onServer(() -> {
			int count = 0;
			for (int x = 16; x < 26; x++) {
				for (int z = 64; z < 69; z++) {
					count += world().setBlock(x, -60, z, Blocks.stone) ? 0 : 1;
					count += world().setBlock(x, 310, z, Blocks.stone) ? 0 : 1;
				}
			}
			return count;
		});
		assertEqual(0, refused, "blocks the world refused");
		waitUntilOnClient("the 100 stones at -60 and 310 on the client", () -> {
			for (int x = 16; x < 26; x++) {
				for (int z = 64; z < 69; z++) {
					if (mc().theWorld.getBlock(x, -60, z) != Blocks.stone || mc().theWorld.getBlock(x, 310, z) != Blocks.stone) {
						return false;
					}
				}
			}
			return true;
		}, 40);
	}

	public void testSignBelowZeroReachesTheClient() {
		onServer(() -> {
			world().setBlock(12, -20, 64, Blocks.standing_sign);
			TileEntitySign sign = (TileEntitySign) world().getTileEntity(12, -20, 64);
			sign.signText[0] = "below zero";
			sign.markDirty();
			world().markBlockForUpdate(12, -20, 64);
		});
		waitUntilOnClient("the sign at -20 on the client, with its text", () -> {
			TileEntity tile = mc().theWorld.getTileEntity(12, -20, 64);
			return tile instanceof TileEntitySign && "below zero".equals(((TileEntitySign) tile).signText[0]);
		}, 40);
	}

	public void testClientLightMatchesTheServer() {
		// a closed room at -43 with a torch (inside x 31..35, z 65..69), and a torch at 319 lighting 320
		shell(30, -44, 64, 36, -38, 70, Blocks.stone);
		place(33, -43, 67, Blocks.torch, 5);
		place(40, 318, 67, Blocks.stone);
		place(40, TOP, 67, Blocks.torch, 5);
		waitUntilOnClient("both torches on the client", () -> mc().theWorld.getBlock(33, -43, 67) == Blocks.torch && mc().theWorld.getBlock(40, TOP, 67) == Blocks.torch, 40);
		ticks(5);
		List<Integer> server = onServer(() -> Arrays.asList(world().getSavedLightValue(EnumSkyBlock.Block, 33, -43, 67),
			world().getSavedLightValue(EnumSkyBlock.Block, 34, -43, 67), world().getSavedLightValue(EnumSkyBlock.Block, 40, 320, 67)));
		assertEqual(Arrays.asList(14, 13, 13), server, "the server's block light at the torch at -43, one block from it, and above the torch at 319");
		World client = onClient(() -> mc().theWorld);
		assertEqual(server, onClient(() -> Arrays.asList(client.getSavedLightValue(EnumSkyBlock.Block, 33, -43, 67),
			client.getSavedLightValue(EnumSkyBlock.Block, 34, -43, 67), client.getSavedLightValue(EnumSkyBlock.Block, 40, 320, 67))),
			"the client's block light there");
		// the renderer's read, at the two that are air: a torch reads its brightest neighbour instead (1.7.10 does that
		// for every block light passes through), so 13 at the torch itself is right
		assertEqual(server.subList(1, 3), onClient(() -> Arrays.asList(client.getSkyBlockTypeBrightness(EnumSkyBlock.Block, 34, -43, 67),
			client.getSkyBlockTypeBrightness(EnumSkyBlock.Block, 40, 320, 67))),
			"the client's block light one block from the torch at -43 and at 320, as its renderer reads it");
	}

	/** The names of the blocks the client's world has in the column at those heights; call on the client. */
	private static List<String> clientBlocks(int x, int z, int... heights) {
		List<String> names = new ArrayList<>();
		for (int y : heights) {
			Block block = mc().theWorld.getBlock(x, y, z);
			names.add(name(block));
		}
		return names;
	}
}
