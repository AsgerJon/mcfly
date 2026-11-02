package io.github.asgerjon.mcfly.rules;

import io.github.asgerjon.mcfly.world.HeightRange;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBed;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemRedstone;
import net.minecraft.item.ItemReed;
import net.minecraft.item.ItemSeedFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemSign;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * Building at the floor and the top (spec §8), for `mixins.early.rules`. The limits are the world's own; the server's
 * `max-build-height` (1.7.10's single limit for every world) still lowers the top when it is set below 256.
 */
public final class BuildRules {

	private BuildRules() {
	}

	/** The highest y the player may build at in the world. */
	public static int buildTop(World world, MinecraftServer server) {
		HeightRange range = WorldHeights.range(world);
		int limit = server.getBuildLimit();
		return limit < 256 ? Math.min(range.top, limit - 1) : range.top;
	}

	/** Whether the item is one that places a block where it is used (1.7.10 has several kinds besides `ItemBlock`). */
	public static boolean placesBlocks(ItemStack stack) {
		if (stack == null) {
			return false;
		}
		Item item = stack.getItem();
		return item instanceof ItemBlock || item instanceof ItemReed || item instanceof ItemDoor || item instanceof ItemBed || item instanceof ItemSign
			|| item instanceof ItemSkull || item instanceof ItemSeeds || item instanceof ItemSeedFood || item instanceof ItemRedstone;
	}

	/** The red line "The height limit for building is ..." with the top. */
	public static void tellTooHigh(EntityPlayerMP player, int top) {
		tell(player, "build.tooHigh", top);
	}

	/** The red line "The minimum height for building is ..." with the floor. */
	public static void tellTooLow(EntityPlayerMP player, int floor) {
		tell(player, "build.tooLow", floor);
	}

	// a red chat line: 1.7.10's server can only send chat (the spec's lines above the hotbar come with the fold into EFR)
	private static void tell(EntityPlayerMP player, String key, int y) {
		ChatComponentTranslation message = new ChatComponentTranslation(key, y);
		message.getChatStyle().setColor(EnumChatFormatting.RED);
		player.playerNetServerHandler.sendPacket(new S02PacketChat(message));
	}
}
