package dev.keith.ultimatetimers;

/** How a category picks each timer's foreground (fill) color. */
enum ColorStyle
{
	/** Each timer uses its own color from the plugin's Colors config section. */
	INDIVIDUAL("Fixed Individual Colors"),
	/** Every timer in the category uses the category's one override color. */
	OVERRIDE("Fixed Override Color"),
	/** Fades from the 100% color through the 50% color to the 0% color as the timer runs down. */
	PERCENTAGE("Percentage-Based Color");

	private final String label;

	ColorStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
