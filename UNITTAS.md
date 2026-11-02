# unitTAS

Tests that play the real game, in the manner of Python's `unittest`: a real client opens, a fresh test world is made,
and test classes drive the game tick by tick (commands, typing in the chat, keys, looking around) and check what
happened (inventory, entities, blocks, chat, sounds). Screenshots on failure or on request.

## Running

```
./unittas.sh                     # every suite
./unittas.sh storage             # one suite (several: storage,light)
./unittas.sh storage BelowZero   # only tests whose Class.method name contains "BelowZero"
```

McFly's suites, listed in `TasSuites`: `smoke` (the plain game, to see unitTAS work), then one per stage, `storage`,
`light`, `network` and `rules`, and two for client rendering: `bench` (the renderer's frame times, in `bench.txt`
among the results) and `render`. A run of every suite includes the bench.

The script leaves a request in `run/client/unittas/request.properties` (another folder: `UNITTAS_RUN_DIR=…`),
starts `./gradlew runClient` (another task: `UNITTAS_TASK=…`), shows each test as it finishes, and prints the report
when the game quits. Everything lands in `unittas-results/<time>/` (`unittas-results/latest` points at the newest):

- `report.txt`: one line per test (`ok`, `FAIL`, `ERROR`, `skipped`), and for each one that didn't pass the assertion
  or stack trace, the chat during the test and its screenshot. Ends with `OK` or `FAILED (failures=…, errors=…)`.
- `screenshots/`: one per failed test, plus the ones tests take on purpose.
- `progress.txt`, `gradle.log`: the run as it went.

A run that can't get into its world gives up and quits the game. The reason is the last line of `progress.txt`:

- "the main menu never showed (the screen is …)": after 180 s, another screen stood in front of the menu, such as a
  mod's notice.
- "the test world didn't load (the player never logged in)": the server is up and 30 s have passed with no world
  on the client. Normally the login takes a second. Now and then 1.7.10's own login stalls with no error: both sides
  wait and the main menu stays up. On this one the script starts the game again, twice at most. Each try gets its
  own folder, and the stalled one's stays.
- "the test world didn't load": the client had its world, but within 180 s the player wasn't in it on both the
  client and the server.

The window is titled just `unitTAS` from the moment it opens (an ordinary `runClient` window is
`Minecraft 1.7.10`), so a window rule can give it its own size and place (and "Accept focus: No" if it shouldn't
take focus when it opens). It needs no focus: the run ignores the real mouse and keyboard, never takes the mouse
pointer, and doesn't pause when the window is in the background. Close it with its window button or Ctrl+C in the
terminal. The test world is `run/client/saves/unittas`, made new each run; no other save is touched. Without a
request the mod never wakes unitTAS, so an ordinary `runClient` is unchanged.

## The world

Flat (bedrock, two dirt, grass: the surface is y 3), creative, cheats, seed 20261029, Easy, always noon, no weather,
no natural mob spawning, no fire spread. Before every test the player is put back at 0.5 4 0.5 looking south, in
creative, with an empty inventory, hotbar slot 1 selected, no effects, full health, the difficulty back at Easy, in
the overworld, and every other entity is removed. Blocks a test places stay, so tests that build use their own spot.

## Writing tests

A test class extends `unittas.TasTestCase` and goes in a suite in `TasSuites`. Every public method whose name starts
with `test` is a test; they run in name order, each on a new instance, with `setUp()` before and `tearDown()` after.
A test runs on its own thread and asks the game for each step:

```java
public class GiveTests extends TasTestCase {

	public void testRedWool() {
		assertNoError(command("/give @p wool 1 14"));
		waitUntil("the wool picked up", () -> player().getHeldItem() != null, 20);
		assertEqual("minecraft:wool:14", id(held()));
	}

	public void testTyping() {
		assertEqual("/give", type("/give").text());
	}
}
```

- Commands: `command(text)` runs it as the player (as a typed command, without the chat's 256-character limit) and
  returns a `TasCommand` (`lines()`, `errors()`, `result()`); `type(text)` opens the chat and types key by key, and
  the `TasChat` it returns has `text()`, `key(Keyboard.KEY_TAB)`, `typeMore(text)`, `enter()`, `close()`.
- The game: `onServer(() -> …)` and `onClient(() -> …)` run a step on that thread and return its result. Inside a
  server step, `player()` and `world()` are the server's player and world. Never touch game objects outside a step.
- Waiting: `ticks(n)`, `serverTicks(n)`, `waitUntil("what", () -> condition, maxTicks)` (checked on the server),
  `waitUntilOnClient(...)` (on the client: for what the server changed and the client must have before the test
  goes on, such as an item given with `setHeld` before a click).
- Sounds: `waitForSound("minecraft:random.bow", maxTicks)`, `soundsHeard()`, `clearSounds()`: every sound the client
  plays (muted or not), by name; each test starts with none. A sound the game doesn't have (it would play nothing)
  fails the test that caused it.
- Things: `held()`, `inventory()`, `setHeld(stack)`, `id(stack)` (`minecraft:wool:14`), `spawn(EntityZombie::new, x,
  y, z)`, `entitiesNear(Type.class, radius)` (inside a server step), `difficulty(EnumDifficulty.PEACEFUL)`.
- Input: `look(yaw, pitch)`, `hold(mc().gameSettings.keyBindForward, ticks)`, `tap(key)`, `keyDown(key)` /
  `keyUp(key)` (held until let go, as right click held down), `cameraView(0|1|2)` (first person, third person from
  behind, from the front; each test starts in first person).
- `screenshot("name")` saves the screen as it is now.
- Asserts, as unittest's: `assertEqual`, `assertNotEqual`, `assertTrue`, `assertFalse`, `assertIsNone`,
  `assertIsNotNone`, `assertIn`, `assertNotIn`, `assertGreater`, `assertAlmostEqual`, `fail`, `skipTest`; for
  commands `assertSaid`, `assertError` (a red line), `assertNoError`.
- `timeoutSeconds()` (default 60) stops a test that hangs; a step the game doesn't run within 15 s is an error.

Undead burn at noon: give zombies and skeletons a helmet when a test needs them to last.
