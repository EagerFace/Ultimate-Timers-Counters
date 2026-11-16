package dev.keith.buffrings;

/**
 * Where to draw an element (the remaining-time text, or the potion icon) relative to its ring.
 * The first nine sit inside the ring's bounds; the last four sit outside it, offset by a gap.
 */
public enum ElementPosition
{
	CENTER,
	TOP,
	BOTTOM,
	LEFT,
	RIGHT,
	TOP_LEFT,
	TOP_RIGHT,
	BOTTOM_LEFT,
	BOTTOM_RIGHT,
	ABOVE,
	BELOW,
	OUTSIDE_LEFT,
	OUTSIDE_RIGHT
}
