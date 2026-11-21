package dev.keith.ultimatetimers;

import static org.junit.Assert.assertEquals;
import java.awt.Color;
import org.junit.Test;

public class PercentageColorTest
{
	private static final Color FULL = Color.GREEN;
	private static final Color HALF = Color.YELLOW;
	private static final Color LOW = Color.RED;

	private static Color at(double fraction)
	{
		return UltimateTimersPlugin.percentageColor(FULL, HALF, LOW, fraction);
	}

	@Test
	public void reachesLowColorAtQuarterAndHoldsIt()
	{
		assertEquals(LOW, at(0.25));
		assertEquals(LOW, at(0.1));
		assertEquals(LOW, at(0.0));
	}

	@Test
	public void keyPointsAndBlends()
	{
		assertEquals(FULL, at(1.0));
		assertEquals(HALF, at(0.5));
		// Halfway between 25% (red 255,0,0) and 50% (yellow 255,255,0).
		assertEquals(new Color(255, 128, 0), at(0.375));
		// Out-of-range input is clamped.
		assertEquals(FULL, at(1.5));
		assertEquals(LOW, at(-1));
	}
}
