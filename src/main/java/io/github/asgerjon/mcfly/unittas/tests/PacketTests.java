package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S0APacketUseBed;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S28PacketEffect;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stage (c), suite `network`: the packets that carry a position or a column, written out and read back (`WireTap.copy`),
 * keep every y of the range: -64 and 319 (spec §7, §10.8). 1.7.10 sends y as one byte in several of them. The chunk
 * test builds in chunks 5, 4 and 6, 4.
 */
public class PacketTests extends McFlyTestCase {

	public void testPositionsKeepTheirY() {
		List<String> wrong = new ArrayList<>();
		for (int y : new int[] {FLOOR, TOP}) {
			wrong.addAll(onServer(() -> {
				List<String> found = new ArrayList<>();
				int s23 = WireTap.copy(new S23PacketBlockChange(5, y, 7, world())).func_148878_e();
				int c07 = WireTap.copy(new C07PacketPlayerDigging(0, 5, y, 7, 1)).func_149503_d();
				int c08 = WireTap.copy(new C08PacketPlayerBlockPlacement(5, y, 7, 1, null, 0.5F, 0.5F, 0.5F)).func_149571_d();
				int s0a = WireTap.copy(new S0APacketUseBed(player(), 5, y, 7)).func_149090_d();
				int s28 = WireTap.copy(new S28PacketEffect(1000, 5, y, 7, 0, false)).func_149243_g();
				String[] names = {"S23PacketBlockChange", "C07PacketPlayerDigging", "C08PacketPlayerBlockPlacement", "S0APacketUseBed", "S28PacketEffect"};
				int[] read = {s23, c07, c08, s0a, s28};
				for (int i = 0; i < names.length; i++) {
					if (read[i] != y) {
						found.add(names[i] + " sent y " + y + ", read " + read[i]);
					}
				}
				return found;
			}));
		}
		assertEqual(Collections.emptyList(), wrong, "positions that came back with another y");
	}

	public void testChunkPacketsKeepEverySection() {
		// two chunks, as the server sends chunks to a player: stone at -64, -30, 256 and 319, and light in the store's
		// rooms past the floor and the top (glowstone at -64, a torch at 319)
		for (int chunkX = 5; chunkX <= 6; chunkX++) {
			int x = (chunkX << 4) + 4;
			for (int y : new int[] {-30, 256}) {
				place(x, y, 68, Blocks.stone);
			}
			place(x, FLOOR, 68, Blocks.glowstone);
			place(x, 318, 68, Blocks.stone);
			place(x, TOP, 68, Blocks.torch, 5);
		}
		serverTicks(2);
		S26PacketMapChunkBulk read = onServer(() -> WireTap.copy(new S26PacketMapChunkBulk(Arrays.asList(world().getChunkFromChunkCoords(5, 4),
			world().getChunkFromChunkCoords(6, 4)))));
		// the client fills a chunk of its own from what came, as it does from a real packet
		List<String> found = onClient(() -> {
			List<String> column = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				Chunk chunk = new Chunk(mc().theWorld, read.func_149255_a(i), read.func_149253_b(i));
				chunk.fillChunk(read.func_149256_c(i), read.func_149252_e()[i], read.func_149257_f()[i], true);
				column.add(chunk.xPosition + ": " + name(chunk.getBlock(4, FLOOR, 4)) + " " + name(chunk.getBlock(4, -30, 4)) + " "
					+ name(chunk.getBlock(4, 256, 4)) + " " + name(chunk.getBlock(4, 318, 4)) + " " + name(chunk.getBlock(4, TOP, 4)) + ", light "
					+ chunk.getSavedLightValue(EnumSkyBlock.Block, 4, FLOOR - 1, 4) + " " + chunk.getSavedLightValue(EnumSkyBlock.Block, 4, TOP + 1, 4));
			}
			return column;
		});
		String expected = ": minecraft:glowstone minecraft:stone minecraft:stone minecraft:stone minecraft:torch, light 14 13";
		assertEqual(Arrays.asList("5" + expected, "6" + expected), found,
			"two chunks sent as one bulk packet and read back: blocks at -64, -30, 256, 318 and 319, block light at -65 and 320");
	}
}
