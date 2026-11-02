package io.github.asgerjon.mcfly.unittas.tests;

import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.List;

/**
 * Stage (d), suite `rules`: building at the floor and the top (spec §8), digging at the top, and the void line at -128.
 * The building tests play it: the player stands or flies by the block, looks at the face and right-clicks, as a player
 * does, so the client's ray trace, its packet and the server's checks all take part. Each test builds at its own x at
 * z 100..102, south of spawn.
 */
public class RulesTests extends McFlyTestCase {

	@Override
	protected void tearDown() {
		onServer(() -> {
			player().capabilities.isFlying = false;
			player().sendPlayerAbilities();
		});
		onClient(() -> {
			if (mc().currentScreen != null) {
				mc().thePlayer.closeScreen();
			}
		});
	}

	public void testTooHighMessage() {
		place(4, TOP, 100, Blocks.stone);
		place(4, TOP, 102, Blocks.stone);
		// beside it: the client sends no click that would put a block where the player stands
		standOn(4.5, TOP + 1, 102.5);
		setHeld(new ItemStack(Blocks.stone));
		// north, down at the top face of the block at 319 two blocks away
		List<String> said = rightClick(180.0F, pitchTo(1.62, 2.0));
		assertChatSaid(said, "The height limit for building is 319");
		assertEqual("minecraft:air", blockAt(4, TOP + 1, 100), "above the top");
	}

	public void testTooLowMessage() {
		assertVoidLineBelow(-70);
		place(8, FLOOR, 100, Blocks.stone);
		flyAt(8.5, FLOOR - 3, 100.5);
		setHeld(new ItemStack(Blocks.stone));
		// straight up at the block above the player, below the floor: its bottom face
		List<String> said = rightClick(0.0F, -90.0F);
		assertChatSaid(said, "The minimum height for building is -64");
	}

	public void testPlaceInTheTopLayer() {
		place(12, 318, 100, Blocks.stone);
		place(12, 318, 102, Blocks.stone);
		standOn(12.5, TOP, 102.5);
		setHeld(new ItemStack(Blocks.stone));
		// north, down at the top face of the block at 318 two blocks away
		rightClick(180.0F, pitchTo(1.62, 2.0));
		assertEqual("minecraft:stone", blockAt(12, TOP, 100), "the block placed on top of the one at 318");
	}

	public void testPlaceAtTheFloor() {
		assertVoidLineBelow(-70);
		place(16, FLOOR + 1, 100, Blocks.stone);
		// under the block at -63, head below -64
		flyAt(16.5, FLOOR - 2.5, 100.5);
		setHeld(new ItemStack(Blocks.stone));
		rightClick(0.0F, -90.0F);
		assertEqual("minecraft:stone", blockAt(16, FLOOR, 100), "the block placed under the one at -63");
	}

	public void testUseAtTheTopStillWorks() {
		place(20, TOP, 100, Blocks.chest);
		place(20, TOP, 102, Blocks.stone);
		standOn(20.5, TOP + 1, 102.5);
		// north, down at the chest's lid (0.875 high) two blocks away; an empty hand
		rightClick(180.0F, pitchTo(1.745, 2.0));
		waitUntilOnClient("the chest at 319 to open", () -> mc().currentScreen instanceof GuiChest, 20);
	}

	public void testDigAtTheTop() {
		place(24, 300, 100, Blocks.stone);
		place(24, 299, 102, Blocks.stone);
		standOn(24.5, 300, 102.5);
		look(180.0F, pitchTo(1.12, 2.0));
		tap(mc().gameSettings.keyBindAttack);
		waitUntil("the block at 300 to be dug (creative: one click)", () -> world().isAirBlock(24, 300, 100), 20);
	}

	public void testVoidLineAt128() {
		EntityPig high = spawn(EntityPig::new, 28.5, -100.0, 100.5);
		EntityItem stays = onServer(() -> dirtItem(32.5, -100.0, 100.5));
		// both at once: one let go would fall past the line meanwhile
		for (int tick = 0; tick < 20; tick++) {
			onServer(() -> {
				still(high, 28.5, -100.0, 100.5);
				still(stays, 32.5, -100.0, 100.5);
			});
			serverTicks(1);
		}
		assertEqual(10.0F, onServer(() -> high.getHealth()), "a pig held at -100 for 20 ticks: its health");
		assertFalse(onServer(() -> stays.isDead), "an item held at -100 for 20 ticks is gone");
		EntityPig low = spawn(EntityPig::new, 28.5, -130.0, 100.5);
		EntityItem goes = onServer(() -> dirtItem(32.5, -130.0, 100.5));
		holdAt(low, 28.5, -130.0, 100.5, 1);
		holdAt(goes, 32.5, -130.0, 100.5, 1);
		assertTrue(onServer(() -> low.getHealth() < 10.0F || low.isDead), "a pig at -130 takes the out-of-world damage");
		assertTrue(onServer(() -> goes.isDead), "an item at -130 is gone");
	}

	/** Guard: passes before and after; the Nether's void line stays at -64. Its entities don't tick without players, so the update runs by hand. */
	public void testVoidLineInTheNetherStays() {
		List<Float> health = onServer(() -> {
			World nether = dimension(-1);
			EntityPig above = new EntityPig(nether);
			EntityPig below = new EntityPig(nether);
			above.setPosition(8.5, -50.0, 8.5);
			below.setPosition(8.5, -70.0, 8.5);
			above.onEntityUpdate();
			below.onEntityUpdate();
			return Arrays.asList(above.getHealth(), below.getHealth());
		});
		assertEqual(Arrays.asList(10.0F, 6.0F), health, "in the Nether, a pig's health after one update at -50 and at -70");
	}

	/**
	 * Fails the test, before the player goes below the floor, when a pig held at that y takes damage: the void line is
	 * still above it, and would kill the player too, which would spoil the tests after.
	 */
	private void assertVoidLineBelow(int y) {
		EntityPig pig = spawn(EntityPig::new, 0.5, y, 104.5);
		holdAt(pig, 0.5, y, 104.5, 2);
		boolean hurt = onServer(() -> pig.getHealth() < 10.0F || pig.isDead);
		onServer(() -> pig.setDead());
		assertFalse(hurt, "the void line still hurts at y " + y + ", so the player isn't taken there");
	}

	/** Puts the player on their feet at x, y, z (the block below must hold them) and waits for the client. */
	private void standOn(double x, double y, double z) {
		onServer(() -> player().playerNetServerHandler.setPlayerLocation(x, y, z, 0.0F, 0.0F));
		waitUntilOnClient("the player on their feet at y " + y, () -> Math.abs(mc().thePlayer.boundingBox.minY - y) < 0.01 && mc().thePlayer.onGround, 40);
	}

	/** Puts the player at x, y, z flying (creative), where nothing holds them, and waits for the client. */
	private void flyAt(double x, double y, double z) {
		onServer(() -> {
			player().capabilities.isFlying = true;
			player().sendPlayerAbilities();
			player().playerNetServerHandler.setPlayerLocation(x, y, z, 0.0F, 0.0F);
		});
		waitUntilOnClient("the player flying at y " + y, () -> mc().thePlayer.capabilities.isFlying && Math.abs(mc().thePlayer.boundingBox.minY - y) < 0.01, 40);
	}

	/** Looks that way, right-clicks, and returns what the chat said meanwhile. */
	private List<String> rightClick(float yaw, float pitch) {
		look(yaw, pitch);
		int mark = chatMark();
		tap(mc().gameSettings.keyBindUseItem);
		serverTicks(5);
		ticks(2);
		return chatSince(mark);
	}

	/** The pitch to look down (up, if negative) at a point dy below the eyes and that far away across. */
	private static float pitchTo(double dy, double across) {
		return (float) Math.toDegrees(Math.atan2(dy, across));
	}

	/** Keeps the entity at that place for the ticks, still, as the server updates it. */
	private void holdAt(Entity entity, double x, double y, double z, int ticks) {
		for (int tick = 0; tick < ticks; tick++) {
			onServer(() -> still(entity, x, y, z));
			serverTicks(1);
		}
	}

	/** Puts the entity back at that place, not moving; call inside a server step. */
	private static void still(Entity entity, double x, double y, double z) {
		entity.setPosition(x, y, z);
		entity.motionX = 0.0;
		entity.motionY = 0.0;
		entity.motionZ = 0.0;
	}

	private static EntityItem dirtItem(double x, double y, double z) {
		EntityItem item = new EntityItem(world(), x, y, z, new ItemStack(Blocks.dirt));
		item.delayBeforeCanPickup = 32767;
		world().spawnEntityInWorld(item);
		return item;
	}

	private static void assertChatSaid(List<String> said, String part) {
		for (String line : said) {
			if (line.contains(part)) {
				return;
			}
		}
		fail("'" + part + "' not in the chat; it said " + said);
	}
}
