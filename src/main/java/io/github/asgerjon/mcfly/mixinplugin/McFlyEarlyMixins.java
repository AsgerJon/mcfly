package io.github.asgerjon.mcfly.mixinplugin;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** McFly's coremod: loads its mixins into 1.7.10's own classes, early, through UniMixins. */
@IFMLLoadingPlugin.Name("McFlyEarlyMixins")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class McFlyEarlyMixins implements IFMLLoadingPlugin, IEarlyMixinLoader {

	public static final MixinEnvironment.Side side = MixinEnvironment.getCurrentEnvironment().getSide();

	@Override
	public String getMixinConfig() {
		return "mixins.mcfly.early.json";
	}

	@Override
	public List<String> getMixins(Set<String> loadedCoreMods) {

		List<String> mixins = new ArrayList<>();

		// (a) storage, world access and saving (PLAN-mcfly.md)
		mixins.add("storage.MixinWorld");
		mixins.add("storage.MixinWorldProvider");
		mixins.add("storage.MixinChunk");
		mixins.add("storage.MixinAnvilChunkLoader");
		mixins.add("storage.MixinWorldServer");
		if (side == MixinEnvironment.Side.CLIENT) {
			mixins.add("client.storage.MixinChunkClient");
		}

		// (b) light
		mixins.add("light.MixinWorldLight");
		mixins.add("light.MixinChunkLight");

		// (c) network
		mixins.add("network.AccessorPlayerManager");
		mixins.add("network.MixinPlayerInstance");
		mixins.add("network.MixinS21PacketChunkData");
		mixins.add("network.MixinS26PacketMapChunkBulk");
		mixins.add("network.MixinS22PacketMultiBlockChange");
		mixins.add("network.MixinS23PacketBlockChange");
		mixins.add("network.MixinC07PacketPlayerDigging");
		mixins.add("network.MixinC08PacketPlayerBlockPlacement");
		mixins.add("network.MixinS0APacketUseBed");
		mixins.add("network.MixinS28PacketEffect");
		if (side == MixinEnvironment.Side.CLIENT) {
			mixins.add("client.network.MixinChunkFill");
			mixins.add("client.network.MixinNetHandlerPlayClient");
			mixins.add("client.light.MixinWorldBrightness");
		}

		// (d) the rules the player meets
		mixins.add("rules.MixinNetHandlerPlayServer");
		mixins.add("rules.MixinWorldPlacement");
		mixins.add("rules.MixinItemBlock");
		mixins.add("rules.MixinEntityVoid");
		mixins.add("rules.MixinEntityMinecart");
		mixins.add("rules.MixinBlockPistonBase");
		mixins.add("rules.MixinBlockFalling");
		mixins.add("rules.MixinBlockDragonEgg");
		mixins.add("rules.MixinEntityFallingBlock");
		mixins.add("rules.MixinEntityEnderman");
		mixins.add("rules.MixinSpreadPlayersPosition");
		mixins.add("rules.MixinPathFinder");
		mixins.add("rules.MixinTeleporter");
		mixins.add("rules.MixinBlockPortalSize");
		mixins.add("rules.MixinChunkCache");
		mixins.add("rules.MixinBlockLilyPad");
		mixins.add("rules.MixinBlockMushroom");
		mixins.add("rules.MixinBlockVine");
		mixins.add("rules.MixinBlockFluidFinite");
		mixins.add("rules.MixinSpawnerAnimals");

		// (e) client rendering: the whole column drawn, then the sky and fog near the floor
		if (side == MixinEnvironment.Side.CLIENT) {
			mixins.add("client.render.MixinRenderGlobal");
			mixins.add("client.render.MixinWorldRenderer");
			mixins.add("client.render.MixinChunkCacheLight");
			mixins.add("client.render.MixinWorldClientRedraw");
			mixins.add("client.render.MixinNetHandlerRedraw");
			mixins.add("client.sky.MixinWorldTypeHorizon");
			mixins.add("client.sky.MixinWorldProviderVoid");
			mixins.add("client.sky.MixinEntityRendererFog");
		}

		// unitTAS, the in-game test runner: they do nothing unless a run was asked for (UNITTAS.md)
		if (side == MixinEnvironment.Side.CLIENT) {
			mixins.add("client.unittas.MixinMinecraft");
			mixins.add("client.unittas.MixinMouseHelper");
			mixins.add("client.unittas.MixinSoundHandler");
		}

		return mixins;
	}

	@Override
	public String[] getASMTransformerClass() {
		return null;
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {

	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
