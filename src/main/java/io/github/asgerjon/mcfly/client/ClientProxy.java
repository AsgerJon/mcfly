package io.github.asgerjon.mcfly.client;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import io.github.asgerjon.mcfly.CommonProxy;
import io.github.asgerjon.mcfly.unittas.UnitTas;

/** The client's proxy: the client-only part of each step. */
public class ClientProxy extends CommonProxy {

	@Override
	public void init(FMLInitializationEvent event) {
		super.init(event);
		// a unitTAS run, when ./unittas.sh asked for one (UNITTAS.md); otherwise nothing
		UnitTas.startIfRequested();
	}
}
