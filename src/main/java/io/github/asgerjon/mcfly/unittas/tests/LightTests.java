package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;

import java.util.Arrays;
import java.util.List;

import static net.minecraft.world.EnumSkyBlock.Block;
import static net.minecraft.world.EnumSkyBlock.Sky;

/**
 * Stage (b), suite `light`: light below 0 and above 255, and in the light store's extra room past the floor and the
 * top (spec §5). Each test builds at its own x at z 40..50, east of spawn; one saves chunk 1, -3. The light is read as
 * the world stores it (`getSavedLightValue`); 1.7.10 clamps such reads to y 0..255 and refuses light writes outside.
 */
public class LightTests extends McFlyTestCase {

	public void testTorchBelowZeroLightsItsRoom() {
		// inside x 5..9, y -43..-39, z 45..49
		shell(4, -44, 44, 10, -38, 50, Blocks.stone);
		place(7, -43, 47, Blocks.torch, 5);
		serverTicks(2);
		assertEqual(Arrays.asList(14, 13, 12), Arrays.asList(light(Block, 7, -43, 47), light(Block, 8, -43, 47), light(Block, 9, -43, 47)),
			"block light at a torch at y -43 in a closed room, and one and two blocks from it");
	}

	public void testSkyLightDownAShaftBelowZero() {
		// inside x 17..21, y -43..-39, z 45..49; a hole in the ceiling, and the flat layers above it taken out
		shell(16, -44, 44, 22, -38, 50, Blocks.stone);
		onServer(() -> world().setBlockToAir(19, -38, 47));
		clearColumn(19, 47);
		serverTicks(2);
		assertEqual(15, light(Sky, 19, -43, 47), "sky light at the shaft's foot, on the room's floor at y -43");
		assertEqual(11, light(Sky, 17, -43, 45), "sky light in a corner of the room, four blocks from the shaft");
	}

	public void testLightUnderAnOverhangBelowZero() {
		// a 3x3 hole to the void at x 28..30, z 40..42, and a block at -50 over its middle
		for (int x = 28; x <= 30; x++) {
			for (int z = 40; z <= 42; z++) {
				clearColumn(x, z);
			}
		}
		place(29, -50, 41, Blocks.stone);
		serverTicks(2);
		assertEqual(15, light(Sky, 28, -60, 41), "sky light at -60 in an open column of the hole");
		assertEqual(14, light(Sky, 29, -60, 41), "sky light at -60 under the block at -50, from the open columns beside it");
	}

	public void testClosedBoxAboveOldTop() {
		// inside x 41..45, y 291..295, z 45..49
		shell(40, 290, 44, 46, 296, 50, Blocks.stone);
		place(43, 291, 47, Blocks.torch, 5);
		serverTicks(2);
		assertEqual(Arrays.asList(0, 14, 13), Arrays.asList(light(Sky, 42, 293, 46), light(Block, 43, 291, 47), light(Block, 44, 291, 47)),
			"in a closed box at 290..296: sky light, block light at its torch at 291, and one block from the torch");
		assertEqual(15, light(Sky, 43, 297, 47), "sky light on the box's roof at 297");
	}

	public void testTorchAtTopLightsTheRoomAbove() {
		place(52, 318, 47, Blocks.stone);
		place(52, TOP, 47, Blocks.torch, 5);
		serverTicks(2);
		// the light store reaches one section past the top: 320..335 (spec §5)
		assertEqual(Arrays.asList(14, 13, 12, 1, 0),
			Arrays.asList(light(Block, 52, 319, 47), light(Block, 52, 320, 47), light(Block, 52, 321, 47), light(Block, 52, 332, 47), light(Block, 52, 333, 47)),
			"block light at a torch at 319, and above it at 320, 321, 332 and 333");
	}

	public void testGlowstoneAtFloorLightsBelow() {
		place(56, FLOOR, 47, Blocks.glowstone);
		serverTicks(2);
		// and one section past the floor: -80..-65
		assertEqual(Arrays.asList(15, 14, 13), Arrays.asList(light(Block, 56, FLOOR, 47), light(Block, 56, FLOOR - 1, 47), light(Block, 56, FLOOR - 2, 47)),
			"block light at glowstone at -64, and below it at -65 and -66");
	}

	/** Guard: passes before and after (stage (a) made the height map tall); relighting a column across the range. */
	public void testRemovingATopBlockRelightsTheColumn() {
		// a 3x3 hole to the void at x 60..62, z 40..42, and a block at 319 over its middle
		for (int x = 60; x <= 62; x++) {
			for (int z = 40; z <= 42; z++) {
				clearColumn(x, z);
			}
		}
		place(61, TOP, 41, Blocks.stone);
		serverTicks(2);
		assertEqual(Arrays.asList(14, 14), Arrays.asList(light(Sky, 61, 200, 41), light(Sky, 61, -60, 41)), "sky light at 200 and -60 under the block at 319");
		onServer(() -> world().setBlockToAir(61, TOP, 41));
		serverTicks(2);
		assertEqual(Arrays.asList(15, 15), Arrays.asList(light(Sky, 61, 200, 41), light(Sky, 61, -60, 41)), "sky light at 200 and -60 with the block gone");
	}

	/** Guard: passes before and after (1.7.10 reads y 0 instead, open too); a column open to the void reads 15 below it. */
	public void testOpenColumnReads15BelowTheFloor() {
		clearColumn(66, 41);
		serverTicks(2);
		assertEqual(Arrays.asList(15, 15), Arrays.asList(light(Sky, 66, -70, 41), light(Sky, 66, -100, 41)), "sky light at -70 and -100 under an open column");
	}

	public void testLightSurvivesSaveAndLoad() {
		// chunk 1, -3: a closed room at -43 with a torch, and a torch at 319 lighting 320
		shell(17, -44, -46, 23, -38, -40, Blocks.stone);
		place(20, -43, -43, Blocks.torch, 5);
		place(26, 318, -43, Blocks.stone);
		place(26, TOP, -43, Blocks.torch, 5);
		serverTicks(2);
		saveChunk(1, -3);
		Chunk read = readBackFromDisk(1, -3);
		List<Integer> found = onServer(() -> Arrays.asList(read.getSavedLightValue(Block, 4, -43, 5), read.getSavedLightValue(Block, 5, -43, 5),
			read.getSavedLightValue(Block, 10, 320, 5)));
		assertEqual(Arrays.asList(14, 13, 13), found, "block light read back from disk: the torch at -43, one block from it, and above the torch at 319");
	}
}
