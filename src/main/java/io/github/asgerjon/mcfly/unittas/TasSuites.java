package io.github.asgerjon.mcfly.unittas;

import io.github.asgerjon.mcfly.unittas.tests.HandshakeTests;
import io.github.asgerjon.mcfly.unittas.tests.LightTests;
import io.github.asgerjon.mcfly.unittas.tests.NetworkTests;
import io.github.asgerjon.mcfly.unittas.tests.PacketTests;
import io.github.asgerjon.mcfly.unittas.tests.RenderBench;
import io.github.asgerjon.mcfly.unittas.tests.RenderTests;
import io.github.asgerjon.mcfly.unittas.tests.RulesTests;
import io.github.asgerjon.mcfly.unittas.tests.SaveTests;
import io.github.asgerjon.mcfly.unittas.tests.SmokeTests;
import io.github.asgerjon.mcfly.unittas.tests.StorageTests;
import io.github.asgerjon.mcfly.unittas.tests.SweepTests;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The suites ./unittas.sh can ask for, each a list of test classes run in this order. A new test class goes here. */
final class TasSuites {

	private static final Map<String, List<Class<? extends TasTestCase>>> SUITES = new LinkedHashMap<>();

	static {
		// a few tests of the plain game, to see unitTAS work in a new mod
		SUITES.put("smoke", Arrays.asList(SmokeTests.class));
		// McFly, by stage (PLAN-mcfly.md): (a) storage, world access and saving
		SUITES.put("storage", Arrays.asList(StorageTests.class, SaveTests.class));
		// (b) light
		SUITES.put("light", Arrays.asList(LightTests.class));
		// (c) network
		SUITES.put("network", Arrays.asList(NetworkTests.class, PacketTests.class, HandshakeTests.class));
		// (d) the rules the player meets
		SUITES.put("rules", Arrays.asList(RulesTests.class, SweepTests.class));
		// (e) client rendering: the renderer's cost, measured (numbers in bench.txt), then what it draws
		SUITES.put("bench", Arrays.asList(RenderBench.class));
		SUITES.put("render", Arrays.asList(RenderTests.class));
	}

	private TasSuites() {
	}

	static Collection<String> names() {
		return Collections.unmodifiableSet(SUITES.keySet());
	}

	/** The suite's classes, or null when there is no suite of that name. */
	static List<Class<? extends TasTestCase>> get(String name) {
		return SUITES.get(name);
	}
}
