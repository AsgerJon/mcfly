package io.github.asgerjon.mcfly.world;

/**
 * A dimension's vertical range (spec §1): blocks from the floor to the top, kept in sections 16 high, and the light
 * store, which may reach one section further each way. There are two: 1.7.10's own, and the taller Overworld's.
 */
public final class HeightRange {

	/** 1.7.10's range, which every dimension but the Overworld keeps exactly: y 0..255, light the same. */
	public static final HeightRange CLASSIC = new HeightRange(0, 16, 0, true);

	/** The taller Overworld: y -64..319 in sections -4..19; light from -80 to 335, sections -5..20. */
	public static final HeightRange TALL = new HeightRange(-4, 24, 1, false);

	/** The lowest and highest y that hold blocks. */
	public final int floor;
	public final int top;

	/** The lowest block section's number (the floor's y >> 4), and how many block sections there are. */
	public final int lowestSection;
	public final int sections;

	/** The light store: its lowest section's number, its section count, and the y it covers. */
	public final int lowestLightSection;
	public final int lightSections;
	public final int lightFloor;
	public final int lightTop;

	/** True for 1.7.10's own range: code keeps 1.7.10's ways there exactly, quirks included. */
	public final boolean vanilla;

	/**
	 * What the precipitation height and the highest solid block answer for a column with nothing in it: the floor, or -1
	 * in 1.7.10's range, as 1.7.10 has it.
	 */
	public final int noSurface;

	private HeightRange(int lowestSection, int sections, int lightRoom, boolean vanilla) {
		this.lowestSection = lowestSection;
		this.sections = sections;
		this.floor = lowestSection << 4;
		this.top = ((lowestSection + sections) << 4) - 1;
		this.lowestLightSection = lowestSection - lightRoom;
		this.lightSections = sections + 2 * lightRoom;
		this.lightFloor = lowestLightSection << 4;
		this.lightTop = ((lowestLightSection + lightSections) << 4) - 1;
		this.vanilla = vanilla;
		this.noSurface = vanilla ? -1 : floor;
	}

	/** Whether y lies between the floor and the top. */
	public boolean holdsBlocks(int y) {
		return y >= floor && y <= top;
	}

	/** Whether the section of that number holds blocks (the light store's rooms past the floor and top hold light only). */
	public boolean hasBlockSection(int number) {
		return number >= lowestSection && number < lowestSection + sections;
	}

	/** Whether the light store has a section of that number. */
	public boolean hasLightSection(int number) {
		return number >= lowestLightSection && number < lowestLightSection + lightSections;
	}
}
