package io.github.asgerjon.mcfly.unittas;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What the mixins and the client proxy need of unitTAS (UNITTAS.md in the project root): whether a run was asked for or is going on. */
public final class UnitTasMode {

	/** A unitTAS run's window title, so a window manager can tell it from an ordinary game window: nothing else in it. */
	public static final String WINDOW_TITLE = "unitTAS";

	/** Set when a run starts: the game then leaves the mouse pointer alone and ignores the real mouse and keyboard. */
	public static volatile boolean running;

	/** The sounds the client played during the current test, by name (mixins.early.client.unittas.MixinSoundHandler fills it). */
	public static final List<String> SOUNDS_HEARD = Collections.synchronizedList(new ArrayList<>());

	/**
	 * The sounds the client was asked to play during the current test but doesn't have (no sound event by that name, a
	 * name missing from sounds.json, say): these play nothing, silently.
	 */
	public static final List<String> SOUNDS_UNKNOWN = Collections.synchronizedList(new ArrayList<>());

	/** For each unknown sound, its name and where it was played from (the first calls outside the sound system). */
	public static final List<String> SOUNDS_UNKNOWN_FROM = Collections.synchronizedList(new ArrayList<>());

	private UnitTasMode() {
	}

	/** The request ./unittas.sh leaves in the game's folder. */
	public static File request(File gameDir) {
		return new File(gameDir, "unittas/request.properties");
	}

	public static boolean requested(File gameDir) {
		return request(gameDir).isFile();
	}
}
