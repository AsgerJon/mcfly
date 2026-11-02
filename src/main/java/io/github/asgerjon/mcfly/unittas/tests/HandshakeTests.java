package io.github.asgerjon.mcfly.unittas.tests;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.NetworkModHolder;
import cpw.mods.fml.relauncher.Side;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Stage (c), suite `network`: McFly's packets carry y in a wider form than 1.7.10's, so both sides must have McFly
 * (spec §0.5). FML's own check turns away a client without McFly, but lets a client with McFly join a server without it.
 */
public class HandshakeTests extends McFlyTestCase {

	public void testBothSidesNeedMcFly() {
		ModContainer mcfly = Loader.instance().getIndexedModList().get("mcfly");
		NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mcfly);
		assertIsNotNone(holder, "McFly's entry in FML's network registry");
		Map<String, String> none = Collections.emptyMap();
		Map<String, String> same = Collections.singletonMap("mcfly", mcfly.getVersion());
		// FML asks with the side of the other end: SERVER when a client looks at the server it joins
		assertEqual(Arrays.asList(false, false, true, true),
			Arrays.asList(holder.check(none, Side.SERVER), holder.check(none, Side.CLIENT), holder.check(same, Side.SERVER), holder.check(same, Side.CLIENT)),
			"McFly's network check for: a server without McFly, a client without it, a server with the same McFly, a client with it");
	}
}
