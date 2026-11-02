package io.github.asgerjon.mcfly.mixins.early.rules;

import io.github.asgerjon.mcfly.rules.BuildRules;
import io.github.asgerjon.mcfly.world.WorldHeights;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.Facing;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player's building and digging at the world's floor and top (spec §8), in the spec's order: a block outside the
 * range is refused before anything, with the message; otherwise the use goes ahead, and only when it did nothing with an
 * item that places blocks does clicking the top face at the top (the bottom face at the floor) get the message.
 * 1.7.10 checked first, with one limit for every world, and had no message at the floor. Counts checked against
 * 1.7.10's bytecode.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

	@Shadow
	public EntityPlayerMP playerEntity;

	/**
	 * 1.7.10's check before the use, `y >= limit - 1 && (face == 1 || y >= limit)`: with these two limits it holds for
	 * a block above the top only, whatever the face.
	 */
	@Redirect(method = "processPlayerBlockPlacement", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I", ordinal = 0),
		require = 1, allow = 1)
	private int mcfly$aboveTheTopFirst(MinecraftServer server) {
		return BuildRules.buildTop(playerEntity.worldObj, server) + 2;
	}

	@Redirect(method = "processPlayerBlockPlacement", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I", ordinal = 1),
		require = 1, allow = 1)
	private int mcfly$aboveTheTopThen(MinecraftServer server) {
		return BuildRules.buildTop(playerEntity.worldObj, server) + 1;
	}

	/** The message's number: the top (319), not one above it (spec §10.10). */
	@Redirect(method = "processPlayerBlockPlacement", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I", ordinal = 2),
		require = 1, allow = 1)
	private int mcfly$topInTheMessage(MinecraftServer server) {
		return BuildRules.buildTop(playerEntity.worldObj, server);
	}

	/** A block below the floor: the message and nothing else, as 1.7.10 does above its top (it has no such floor). */
	@Inject(method = "processPlayerBlockPlacement", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
	private void mcfly$belowTheFloor(C08PacketPlayerBlockPlacement packet, CallbackInfo ci) {
		int face = packet.func_149568_f();
		int x = packet.func_149576_c();
		int y = packet.func_149571_d();
		int z = packet.func_149570_e();
		if (face == 255 || y >= WorldHeights.floor(playerEntity.worldObj)) {
			return;
		}
		BuildRules.tellTooLow(playerEntity, WorldHeights.floor(playerEntity.worldObj));
		// what the client may have shown there, put right, as 1.7.10 does after a refused use
		WorldServer world = (WorldServer) playerEntity.worldObj;
		playerEntity.playerNetServerHandler.sendPacket(new S23PacketBlockChange(x, y, z, world));
		if (face >= 0 && face < 6) {
			playerEntity.playerNetServerHandler.sendPacket(new S23PacketBlockChange(x + Facing.offsetsXForSide[face], y + Facing.offsetsYForSide[face],
				z + Facing.offsetsZForSide[face], world));
		}
		ci.cancel();
	}

	/** After the use: when it did nothing with an item that places blocks, at the top's top face or the floor's bottom face. */
	@Redirect(method = "processPlayerBlockPlacement",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/management/ItemInWorldManager;activateBlockOrUseItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIIIFFF)Z"),
		require = 1, allow = 1)
	private boolean mcfly$useThenTell(ItemInWorldManager manager, EntityPlayer player, World world, ItemStack stack, int x, int y, int z, int face,
		float hitX, float hitY, float hitZ) {
		boolean used = manager.activateBlockOrUseItem(player, world, stack, x, y, z, face, hitX, hitY, hitZ);
		if (!used && BuildRules.placesBlocks(stack)) {
			int top = BuildRules.buildTop(world, MinecraftServer.getServer());
			int floor = WorldHeights.floor(world);
			if (face == 1 && y >= top) {
				BuildRules.tellTooHigh(playerEntity, top);
			} else if (face == 0 && y <= floor) {
				BuildRules.tellTooLow(playerEntity, floor);
			}
		}
		return used;
	}

	/** Digging above the top is refused (1.7.10: at the server's one limit). */
	@Redirect(method = "processPlayerDigging", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"),
		require = 1, allow = 1)
	private int mcfly$aboveTheTopToDig(MinecraftServer server) {
		return BuildRules.buildTop(playerEntity.worldObj, server) + 1;
	}
}
