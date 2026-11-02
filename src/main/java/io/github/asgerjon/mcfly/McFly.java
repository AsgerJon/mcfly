package io.github.asgerjon.mcfly;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;

import java.util.Map;

/** McFly: a taller Overworld, y -64 to 319. Both the client and the server need it. */
@Mod(modid = McFly.MOD_ID, name = McFly.NAME, version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public class McFly {

	/** The mod's own id: nothing saved, sent or configured may depend on it (it goes when McFly is folded into EFR). */
	public static final String MOD_ID = "mcfly";
	public static final String NAME = "McFly";

	@SidedProxy(clientSide = "io.github.asgerjon.mcfly.client.ClientProxy", serverSide = "io.github.asgerjon.mcfly.CommonProxy")
	public static CommonProxy proxy;

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		proxy.init(event);
	}

	/**
	 * Whether McFly can talk to the other end, which FML asks when they connect: only one with this same McFly, as
	 * McFly's packets carry y wider than 1.7.10's (spec §0.5). FML's own check would let a client join a server without
	 * McFly, and then misread every chunk.
	 */
	@NetworkCheckHandler
	public boolean acceptsOtherEnd(Map<String, String> mods, Side otherEnd) {
		return Tags.VERSION.equals(mods.get(MOD_ID));
	}
}
