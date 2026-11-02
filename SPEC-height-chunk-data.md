# Taller Overworld, chunk data side: behaviour specification

**Provenance (clean-room record).** Written on 2026-10-27 by Claude (Opus 5.5) acting as the *dirty side*: in the
session that wrote it, the author read decompiled code of Minecraft Java Edition 26.3 (client jar SHA-1
`e877b6a07acd633fb3bb475002175cec036e7b87`), 36 classes plus a handful more, covering world height, chunk storage,
height maps, light, saving, the old-chunk upgrade, network packets, the client's chunk cache and render area, and the
places that enforce limits. The list of classes stays with the decompile, on the dirty side. This document contains no
code, no pseudo-code and no names of modern classes, methods or fields; save-file tag names, block ids, translation
keys and in-game text appear because they are data the files or the player see. The implementers (the *clean side*)
must work from this document and must not read the decompiled sources.

**Tags.** **[code]** read in the decompiled 26.3 code. **[data]** read in 26.3's data files (dimension definitions,
language file). **[inferred]** the author's conclusion, not read directly. Where something wasn't settled, it is under
"Open questions".

**Words used here.** A *column* is a 16×16 chunk, top to bottom. A *section* is a 16×16×16 cube of a column. A
section's *number* is its vertical position counted from y 0 (section 0 holds y 0..15, section −1 holds y −16..−1). A
section's *index* is its place in its column counted from the bottom (the lowest section has index 0). The *floor* is
the lowest y of a dimension; the *top* is its highest usable y. All ranges below are inclusive unless they say
otherwise.

---

## 0. Project requirements (from the project owner, not from modern Minecraft)

These shape the port; they are not modern behaviour.

1. Scope is the chunk data side only. No world generation of any kind: the mod opens the space, and 1.7.10's own
   generator keeps producing its terrain in y 0..255. Nothing exists below 0 or above 255 until something puts it
   there.
2. Only the Overworld grows. The Nether and the End keep 0..255, as in modern (section 1).
3. Existing 1.7.10 worlds load as they are: y 0..255 keeps its meaning, so every saved section keeps its number.
4. A world saved with the mod and later opened without it must not lose what it has in y 0..255. (In 1.7.10, a saved
   section numbered outside 0..15 makes the game fail the whole chunk, discard it and generate a fresh one in its
   place.) The extra sections have to be stored where an unmodded game won't look.
5. Client and server both need the mod.

---

## 1. Ranges

| Dimension | Floor | Height | Top   | Sections (numbers) | Light store (section numbers) | Void line |
|-----------|-------|--------|-------|--------------------|-------------------------------|-----------|
| Overworld | −64   | 384    | 319   | −4..19 (24)        | −5..20 (26), so y −80..335    | below −128 |
| Nether    | 0     | 256    | 255   | 0..15 (16)         | −1..16 (18), so y −16..271    | below −64 |
| End       | 0     | 256    | 255   | 0..15 (16)         | −1..16 (18), so y −16..271    | below −64 |

- Floor, height and logical height come from the dimension definitions: Overworld floor −64, height 384, logical
  height 384; Nether 0, 256, 128; End 0, 256, 256. **[data]** The top is floor + height − 1. **[code]** The Nether's
  logical height of 128 limits things outside the chunk data side (where portals and teleports may put you); it does
  not change the Nether's block range. **[inferred]**
- The light store reaches one section below the floor and one above the top. **[code]** (Section 5.)
- The void line is 64 blocks below the floor. **[code]** (Section 8.)
- General limits on any dimension's range: the floor is a multiple of 16 between −2032 and 2031; the height is a
  multiple of 16 between 16 and 4064; floor + height is at most 2032; the logical height is at most the height.
  **[code]** These matter only if the port ever makes the range configurable.

## 2. Reading and writing blocks at any height

At the world level (anything that asks the world, not a single column):

- A position counts as inside when its y is between the floor and the top and its column lies in the valid area of
  chunks. Everything below uses that test. **[code]**
- Reading a block outside gives a special air block, id `minecraft:void_air`, that means "outside the world". It
  behaves as air. **[code]** A port without such a block can treat it as plain air. **[inferred]**
- Reading the fluid outside gives no fluid. Asking for a block entity outside gives none. **[code]**
- Writing a block outside is refused: nothing changes and the write reports failure. Adding or removing a block entity
  outside does nothing. **[code]**
- Asking whether a position is loaded answers "no" for any y outside the range, even in a loaded column. **[code]** So
  checks that require a loaded position (updates passed to neighbours, entities testing what they stand on) never
  reach past the floor or the top. **[inferred]**

Inside a single column:

- Reading outside the column's sections gives plain air (not void air) and no fluid. **[code]**
- A question like "is this vertical span empty" first clamps the span to floor..top. **[code]**

Positions themselves:

- A section number is y divided by 16 rounded toward minus infinity: y −1 and y −16 are in section −1, y −17 in
  section −2. The position inside a section is y minus 16 × its section number, always 0..15. **[code]**
- A section's index in its column is its number minus the lowest section number (in the Overworld, section −4 has
  index 0 and section 19 has index 23). **[code]**

## 3. Column storage

- A column always holds a section for every section number of its dimension's range: 24 in the Overworld. Sections
  with nothing in them exist as empty sections; they are never missing. **[code]**
- A section counts as empty when it holds no block other than air. Fluids and every other block count as content.
  **[code]**
- The column's highest non-empty section is found by searching from the top down. When every section is empty, the
  column's top is taken to be the floor. **[code]**

## 4. Height maps

- A height map stores, for each of the column's 16×16 positions, the first free y above the highest block that matters
  for that map. A column with no such block stores the floor (−64 in the Overworld). So values run from the floor up to
  top + 1. **[code]**
- Values are kept relative to the floor, packed with just enough bits for height + 1 different values (9 bits for both
  a 256 and a 384 block range). **[code]**
- Modern keeps several maps. Four are kept once a chunk is finished: `WORLD_SURFACE` (any block that isn't air),
  `OCEAN_FLOOR` (blocks that stop movement), `MOTION_BLOCKING` (blocks that stop movement, or any block holding a
  fluid) and `MOTION_BLOCKING_NO_LEAVES` (the same without leaves). The client is sent `WORLD_SURFACE`,
  `MOTION_BLOCKING` and `MOTION_BLOCKING_NO_LEAVES`. **[code]** Two more exist only during world generation.
  **[code]** A saved chunk stores the maps it holds, keyed by name. **[code]** A port only needs whatever its own
  engine uses, with the range and the empty-column value above. **[inferred]**

## 5. Light

Where light is kept:

- Light is stored per section, separately for sky light and block light, for every section of the light store: the
  dimension's sections plus one more below the floor and one more above the top (Overworld: sections −5..20). Light
  that spreads past the edges, such as a torch at y 319 lighting y 320..335 or sky light at the very top, is therefore
  kept, saved and sent like any other. **[code]**
- Light changes in sections outside the light store are ignored. **[code]**

What a light reading returns:

- **Block light:** the stored value. Where no light data is stored for that section, 0. **[code]**
- **Sky light, in a sky-lit dimension:**
  - Above the highest section of a column that has stored sky light data: 15. **[code]**
  - In a column with no sky light data at all: 15. **[code]**
  - In a section below that, which has no data of its own: the value from the bottom layer of the nearest section
    above it that has data, at the same x and z. Sky light passes down through sections without data unchanged.
    **[code]**
  - Below the light store (Overworld below y −80): by the same rule, the bottom layer of the lowest section with data.
    **[code]** So a column that is open all the way down reads 15 below the world; a covered one reads what reaches
    the bottom of the store, usually 0. **[inferred]**
  - A new section of sky light data starting below existing data is filled by repeating the bottom layer of the
    nearest data above it through all 16 layers. **[code]**

Where full sky light starts in a column:

- For each of the column's 16×16 positions, full sky light (15) comes straight down from the top until the first
  edge between two blocks where the lower block reduces light at all, or where the touching faces of the two blocks
  close the gap between them. That edge is where the light source of that position ends. **[code]**
- A position with no such edge all the way down counts as open below the floor too: its source has no lower end.
  **[code]**
- An empty column is open all the way down. **[code]**

What the client gets:

- With a chunk, and after light changes, the client receives, per light kind (sky, block), two bitmasks over the light
  store's sections (26 bits in the Overworld; bit 0 is the section below the floor): one marks the sections whose light
  data follows (2048 bytes each, one value per block, 4 bits each), the other marks the sections whose light is all
  zero. A section in neither mask carries no light information in that message. **[code]**
- After a light change, only the changed light sections are sent, and only to players for whom the chunk lies on the
  border of the area they have loaded. **[code]** Players who have the chunk well inside their area get only the block
  changes; their client works out the light itself. **[inferred]** (The border clients lack the neighbouring chunks
  needed to do that.) **[inferred]**
- On the client, a light change marks that section to be redrawn. **[code]**

## 6. Saving

What a saved chunk contains, as far as height is concerned:

- A list of section entries, each with:
  - `Y`: the section number, as a signed byte;
  - `block_states` and `biomes`, for sections inside the block range;
  - `BlockLight` and `SkyLight`, 2048 bytes each, when that light is present and not all zero. **[code]**
- `yPos`: the lowest section number of the dimension when the chunk was saved (−4 in the Overworld). **[code]**
- `Heightmaps`: the maps of section 4, keyed by their names. **[code]**
- `isLightOn`: whether the saved light is complete. **[code]** When it isn't, the light gets recomputed after
  loading. **[inferred]**

Which sections get written:

- Every section of the block range, empty ones included. **[code]**
- The section below the floor and the section above the top, only when they hold some non-zero light; such an entry
  has light and no blocks. **[code]**

Loading:

- An entry whose number lies inside the block range becomes that block section. An entry outside it keeps only its
  light. Sections without an entry become empty sections. **[code]** Nothing is rejected for its number. **[inferred]**

Chunks saved before the range grew. Modern upgrades them once, on load, at the data level:

- **Nether and End:** nothing about height changes. **[code]**
- **Overworld:**
  - Every old section keeps its number, so old blocks keep their y. **[code]**
  - Every section number of the new range without an entry gets an all-air section: −4..−1, and 16..19 where they
    were missing. **[code]**
  - Height map values are re-based to the new floor: an empty column stays empty, and every other value keeps its
    absolute height. **[code]**
  - Per-section lists kept in the chunk (scheduled ticks, post-processing) get four empty entries at the bottom, so
    their entries still line up with the sections they belong to. **[code]**
  - For chunks of the default generator with a stored section 0, modern records which positions of the y 0 layer were
    air (its "missing bedrock" columns) and marks the chunk to have the space below 0 generated later, after which the
    chunk goes back to its earlier state. It also marks the chunk's light for recomputation. **[code]** Generation is
    outside this project (section 0): for the port, old chunks simply keep empty sections below 0. **[inferred]**

## 7. Network

Chunks:

- A chunk is sent with all of its sections, from the bottom to the top, back to back, empty ones included. There is no
  mask of which sections are present. Each section carries its count of non-air blocks, its count of fluid blocks,
  its blocks and its biomes. **[code]**
- The message also carries the client's height maps (section 4) and the chunk's block entities. Light comes with it,
  as in section 5. **[code]**

Block changes:

- Changes are collected per section during a tick and sent at the end of it, in this order: light changes first
  (only to players at the border of their loaded area, section 5), then, to every player who has the chunk, one message
  for each section with changes, from the bottom up. **[code]**
- A section with exactly one change sends a single-block update with the full position; if that block has a block
  entity, the block entity's data follows. **[code]**
- A section with more than one change sends one batched update. It names the section by column and section number,
  and lists each change as the position inside the section (x, y and z, each 0..15) plus the new block. Block entity
  data follows for the changed blocks that have one. **[code]** It can hold any number of changes, up to the whole
  section. **[code]**

Positions:

- Every block position that travels on its own, in either direction, carries y as a signed 12-bit value, so −2048..2047.
  That covers the whole allowed range of section 1. This includes the digging message and the use-on-block message
  from the client, and the single-block update from the server. **[code]**

## 8. Rules the player meets

Messages:

- Height messages appear in red in the overlay above the hotbar, not in chat. **[code]**
  - `build.tooHigh`: "The height limit for building is %s", where %s is the top (319 in the Overworld).
  - `build.tooLow`: "The minimum height for building is %s", where %s is the floor (−64 in the Overworld). **[data]**

Using an item on a block:

- If the targeted block's y is above the top, the player gets `build.tooHigh` and nothing else happens. If it is below
  the floor, `build.tooLow`. **[code]**
- Otherwise the use goes ahead normally. When it achieves nothing and the held item is one that places blocks:
  - clicking the top face of a block at y ≥ top gives `build.tooHigh`;
  - clicking the bottom face of a block at y ≤ floor gives `build.tooLow`. **[code]**
- Quirk: when the player may not interact at that spot (for example outside the world border) or the server is still
  waiting for the client to confirm a teleport, the player also gets `build.tooHigh`. **[code]**
- After every use attempt within reach on a block inside the range, the server sends the client the clicked block and
  the block next to it on the clicked face, correcting whatever the client predicted. **[code]**

Digging:

- A dig action aimed above the top is refused, and the server sends the client the block it has there (void air).
  **[code]** There is no separate check below the floor; there is nothing there to break. **[inferred]**

Falling out of the world:

- An entity whose y is below the void line (floor − 64: −128 in the Overworld, −64 in the Nether and the End) gets
  the out-of-world treatment every tick. A living entity takes 4 damage of the out-of-world kind each tick; any other
  entity is removed. **[code]**
- Entities may otherwise be anywhere above the void line, including above the top. **[inferred]**

Commands:

- `/setblock`, both corners of `/fill`, and the corners and destination of `/clone` check each position in turn:
  - if the position's column isn't loaded: `argument.pos.unloaded`, "That position is not loaded";
  - otherwise, if y is outside floor..top or the position is more than 30,000,000 blocks out horizontally:
    `argument.pos.outofworld`, "That position is out of this world!". **[code]** **[data]**
- `/clone` also requires its source box and its destination box to have loaded columns. A box lying entirely above the
  top or entirely below the floor counts as not loaded. **[code]** Parts of a box that stick out past the floor or the
  top are skipped, because writes there are refused (section 2). **[inferred]**
- Positions that only need to be valid places for entities are checked against a far larger range: y from −20,000,000
  up to but not including 20,000,000, with `argument.pos.outofbounds`, "That position is outside the allowed
  boundaries". **[code]** **[data]**

## 9. The client

- The client builds render sections for every section of the dimension's range, from the floor to the top, and none
  outside it. **[code]**
- The client keeps a horizon height: 63 in normal worlds, and the floor (−64 in the Overworld) in superflat worlds.
  **[code]** It is the height below which the sky starts to darken when the camera goes under it. **[inferred]**
- Beyond the floor and the top nothing is drawn, because there are no sections there. **[inferred]**

## 10. Things a port would easily get wrong

1. **Rounding of negative y.** Section numbers round toward minus infinity, and the position inside a section is
   always 0..15 (section 2). Any code that assumes y ≥ 0 when dividing or masking breaks below 0.
2. **Two kinds of air outside the range.** Reads through the world give void air; reads through a single column give
   plain air (section 2).
3. **"Loaded" is false outside the range.** That quietly stops updates, checks and spreading at the floor and the top
   (section 2).
4. **Height maps and empty columns.** The empty value is the floor, not 0 (section 4).
5. **Light needs room past the edges.** Without the extra section above the top and below the floor, light spreading
   past them has nowhere to go (section 5).
6. **Light below the floor.** It follows the sky light rule: open columns read 15 down there (section 5).
7. **Empty sections are still sections.** They are always present in storage, in saves and on the network
   (sections 3, 6 and 7).
8. **Every position on the wire needs the full range.** 1.7.10 sends y as one byte in several messages and packs it
   into 8 bits in batched changes; all of them must carry −64..319 at least (section 7).
9. **The void line moves with the floor** (section 8).
10. **The build messages show the top (319) and the floor (−64)**, not 320 (section 8).

## 11. Answers to the implementers' questions (2026-10-30)

Asked in the clean side's plan (its §4), answered by the dirty side from the 26.3 code, as behaviour only.
Everything here is **[code]** unless marked (inferred).

1. Pistons
- A piston never moves a block to or from a position outside floor..top (or outside the world border). A block at
  the top (319) can't be pushed up; a block at the floor (-64) can't be pushed down. Inside the range, pushing a
  block into the floor layer or the top layer works.
- The head needs room inside the range: a piston at 319 facing up, or at -64 facing down, doesn't extend. The space
  beyond the limit counts as unmovable, not as something to break.

2. Falling blocks
- A falling block starts to fall whenever the space below it is free (air, fire, a liquid, or a replaceable block),
  at any y in the range. Outside the range reads as air, so sand at -64 falls out of the world.
- One that isn't on the ground (and isn't stuck in water) becomes its item and is removed when it has fallen for
  more than 100 ticks while its block position is at or below the floor (y <= -64) or above the top (y > 319), or
  for more than 600 ticks anywhere. The item only drops when that falling block drops items and entity drops are on.
- The dragon egg's teleport picks a spot within 15 blocks across and 7 up or down that is air, has a non-air block
  below it, and lies inside the border and inside floor..top. Below the floor counts as air, so it never picks -64.

3. The top layer
- Yes. There is no rule about solid blocks in the top layer: any block can be placed at 319. The only height rules
  when placing are spec §8's, plus the refused write outside the range.

4. Searches for ground
- Enderman, random teleport: target y is its own y plus a random -32..31 (when teleporting towards a target: its
  y plus -8..7, minus 16 times the vertical part of the unit vector from the target to the enderman). Not clamped. From there it looks
  straight down, one block at a time, for a block it may stand on, down to and including the block at the floor
  (-64). A start above 319 searches down from there; a start at or below the floor finds nothing, and the teleport
  fails.
- /spreadplayers: each column is searched from top + 1 (320) down to the floor for the first block with two air
  blocks above it; the player goes on top of it. A column without one is unsafe and the spot is chosen again. A
  spot is also unsafe when that block is a liquid. (The later version adds an optional lower maximum height to the
  command; 1.7.10's form just uses 320.)
- A walking mob's path down a drop: from the edge straight down to the floor. The drop is refused when it is longer
  than the mob's maximum fall distance, and also when it reaches the floor without finding ground: no path into the
  void. A position at the floor has nothing below to stand on.

5. Nether portals in the Overworld
- Search for an existing portal: every height of the dimension (-64..319 in the Overworld), in a square around the
  target: 128 blocks out when arriving in the Overworld, 16 when arriving in the Nether. The nearest wins (3D
  distance), and on a tie the lower one.
- A new portal: in a spiral 16 blocks around the target, each column from its height down to the floor. The height
  is the first free y above the column's highest motion-blocking block, capped at the highest y a portal may use.
  A portal's bottom must be at least 4 below that cap. The cap is the lower of the top and floor + logical height
  - 1: 319 in the Overworld, 127 in the Nether.
- If no spot is found, it goes at the target's y clamped to max(floor + 1, 70) .. cap - 9: 70..310 in the
  Overworld, 70..118 in the Nether. (Inferred: with the height at 320, 1.7.10's own 70..height - 10 gives the same
  70..310.)

6. Natural mob spawning
- Each attempt picks a random column of the chunk and a y uniformly from the floor up to one above the column's
  highest non-air block, both ends included. An attempt at the floor itself is skipped. So the later version does
  try from the floor (-64), not from the lowest block.

7. Rain, snow and lightning over a column with nothing in it
- Rain and snow fall to the column's precipitation height: the first free y above its highest block that blocks
  motion or holds a fluid. In an empty column that is the floor, -64. The client draws rain near the camera
  (within its weather radius above and below the camera) down to that height. A position counts as rained on when
  it sees the sky, isn't below that height, and its biome has rain there.
- Ice forms on water just below that height and snow layers on it. In an empty column neither forms: the block
  below the floor is outside, and a snow layer at -64 has nothing to stand on.
- Lightning aims at a random column's precipitation height. It goes to one living entity in the open, chosen at
  random, if any is within 3 blocks of that column, anywhere from that height up to above the top. Otherwise it
  goes to that height. (The later version's lightning rod comes first; 1.7.10 has none.) It strikes only if it is
  raining at the chosen spot. So yes: in an empty column of a rainy biome it can strike at -64, or at a mob in the
  open below 0.

8. The camera near or below the floor (the spec's open question 3)
- Yes, the fog colour changes; the fog distance doesn't. When the camera is less than 32 blocks above the floor
  (superflat: less than 1 block), the fog colour is multiplied by ((camera y - floor) / 32)^2 (superflat:
  (camera y - floor)^2). Above that it is unchanged; at and below the floor, black. In the Overworld that starts at
  -32 and is black at -64 and below. It doesn't apply in lava. Blindness can darken it further, as in 1.7.10. No
  particles, and no height-dependent fog distance.
- Inferred: that is 1.7.10's own darkening near y 0 (its 1/32 factor, flat worlds 1), measured from the floor
  instead of from 0. Check it against your 1.7.10 sources.

## 12. When the later version rebuilds a section (2026-11-01, for stage (e)'s choice)

Behaviour only, read from the later version's client [code]. Compare it with 1.7.10 in your own sources.

- Marks merge. A mark sets a section's "needs a rebuild" flag; more marks before the rebuild change nothing. When
  the rebuild is handed out, the flag is cleared. A mark made by the player's own action is remembered as such
  until then.
- Only sections the visibility walk currently reaches are looked at for a rebuild. A marked section out of view
  keeps its flag until it comes into view.
- The first build waits for the neighbours. A section that has never been built is built only once all 8 chunks
  around its own chunk (the 4 beside it and the 4 diagonal ones) are on the client and have had their light data
  applied. There is no time limit: while a neighbour is missing it simply isn't drawn. So the outermost ring of
  chunks the client holds stays undrawn until the ring beyond it arrives.
- After that, no waiting. A section that has been built before is rebuilt as soon as it is marked, whether or not
  its neighbours are there.
- What a chunk's arrival marks. When the arrived chunk's light data is applied (see below), every section in the
  3x3 chunk columns around it is marked, at all heights, as in 1.7.10. An unload marks nothing around it; it only
  switches off that chunk's light.
- Light data is applied over several frames. Arrived chunks' light jobs wait in a queue. Each frame takes
  max(10, queue size / 10) of them while fewer than 1000 wait, and all of them once 1000 or more wait. A chunk's
  marks happen when its job runs, not when the packet arrives.
- No wrap-around. A mark for a section outside the area currently kept around the camera is ignored. (The area is
  stored as a ring like 1.7.10's, but each lookup is checked against the area's bounds first.)
- Near the camera, at once. With the default setting, a section whose centre is within sqrt(768), about 27.7
  blocks, of the camera, or one marked by the player's own action, is rebuilt on the spot in that frame. Every
  other one goes to the background builders.

## Open questions

- What the client does with a light section that appears in neither light mask (section 5). The client's handling of
  incoming light wasn't read.
- The client's own prediction when placing at the floor or the top: whether it shows anything before the server
  answers. The client side of block placement wasn't read.
- (Answered 2026-10-30, section 11, answer 8: the fog colour darkens near the floor.)
