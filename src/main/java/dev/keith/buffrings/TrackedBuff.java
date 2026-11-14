package dev.keith.buffrings;

import java.awt.Color;
import net.runelite.api.gameval.VarbitID;

enum TrackedBuff
{
	ANTIFIRE(VarbitID.ANTIFIRE_POTION, 30, Category.ANTIFIRE, "Antifire", new Color(255, 140, 0)),
	SUPER_ANTIFIRE(VarbitID.SUPER_ANTIFIRE_POTION, 20, Category.ANTIFIRE, "Sup. Antifire", new Color(255, 100, 0)),
	STAMINA(VarbitID.STAMINA_DURATION, 10, Category.STAMINA, "Stamina", new Color(80, 200, 120)),
	DIVINE_SUPER_COMBAT(VarbitID.DIVINECOMBAT_POTION_TIME, 1, Category.DIVINE, "Div. Combat", new Color(180, 80, 255)),
	DIVINE_BASTION(VarbitID.DIVINEBASTION_POTION_TIME, 1, Category.DIVINE, "Div. Bastion", new Color(120, 180, 255)),
	DIVINE_BATTLEMAGE(VarbitID.DIVINEBATTLEMAGE_POTION_TIME, 1, Category.DIVINE, "Div. Battlemage", new Color(140, 120, 255)),
	DIVINE_SUPER_ATTACK(VarbitID.DIVINEATTACK_POTION_TIME, 1, Category.DIVINE, "Div. Attack", new Color(200, 160, 60)),
	DIVINE_SUPER_STRENGTH(VarbitID.DIVINESTRENGTH_POTION_TIME, 1, Category.DIVINE, "Div. Strength", new Color(200, 60, 60)),
	DIVINE_SUPER_DEFENCE(VarbitID.DIVINEDEFENCE_POTION_TIME, 1, Category.DIVINE, "Div. Defence", new Color(60, 160, 200)),
	DIVINE_RANGING(VarbitID.DIVINERANGE_POTION_TIME, 1, Category.DIVINE, "Div. Ranging", new Color(60, 200, 120)),
	DIVINE_MAGIC(VarbitID.DIVINEMAGIC_POTION_TIME, 1, Category.DIVINE, "Div. Magic", new Color(120, 120, 255));

	private final int varbit;
	private final int ticksPerUnit;
	private final Category category;
	private final String label;
	private final Color color;

	TrackedBuff(int varbit, int ticksPerUnit, Category category, String label, Color color)
	{
		this.varbit = varbit;
		this.ticksPerUnit = ticksPerUnit;
		this.category = category;
		this.label = label;
		this.color = color;
	}

	int getVarbit()
	{
		return varbit;
	}

	int getTicksPerUnit()
	{
		return ticksPerUnit;
	}

	Category getCategory()
	{
		return category;
	}

	String getLabel()
	{
		return label;
	}

	Color getColor()
	{
		return color;
	}

	enum Category
	{
		ANTIFIRE,
		STAMINA,
		DIVINE
	}
}
