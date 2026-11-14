package dev.keith.buffrings;

class ActiveBuffDisplay
{
	private final TrackedBuff buff;
	private final double fraction;
	private final int secondsRemaining;

	ActiveBuffDisplay(TrackedBuff buff, double fraction, int secondsRemaining)
	{
		this.buff = buff;
		this.fraction = fraction;
		this.secondsRemaining = secondsRemaining;
	}

	TrackedBuff getBuff()
	{
		return buff;
	}

	double getFraction()
	{
		return fraction;
	}

	int getSecondsRemaining()
	{
		return secondsRemaining;
	}
}
