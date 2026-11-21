package dev.keith.ultimatetimers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TimerCategoryTest
{
	@Test
	public void storedIdsResolveToTheSameInstance()
	{
		// Saved assignments from before categories were editable must keep working, and == must hold.
		assertSame(TrackedBuff.TimerCategory.CATEGORY_1, TrackedBuff.TimerCategory.fromId("CATEGORY_1"));
		assertSame(TrackedBuff.TimerCategory.CATEGORY_5, TrackedBuff.TimerCategory.fromId("CATEGORY_5"));
		assertSame(TrackedBuff.TimerCategory.UNMAPPED, TrackedBuff.TimerCategory.fromId("UNMAPPED"));
		assertSame(TrackedBuff.TimerCategory.numbered(12), TrackedBuff.TimerCategory.fromId("CATEGORY_12"));
	}

	@Test
	public void numbersAndIds()
	{
		assertEquals(3, TrackedBuff.TimerCategory.CATEGORY_3.getNumber());
		assertEquals("CATEGORY_7", TrackedBuff.TimerCategory.numbered(7).name());
		assertTrue(TrackedBuff.TimerCategory.UNMAPPED.isUnmapped());
		assertEquals(5, TrackedBuff.TimerCategory.DEFAULTS.size());
	}

	@Test
	public void unrecognisedIdsAreRejected()
	{
		assertNull(TrackedBuff.TimerCategory.fromId(null));
		assertNull(TrackedBuff.TimerCategory.fromId("DISABLED"));
		assertNull(TrackedBuff.TimerCategory.fromId("CATEGORY_x"));
		assertNull(TrackedBuff.TimerCategory.fromId("CATEGORY_0"));
	}
}
