package dev.keith.buffrings;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.FontType;
import net.runelite.client.config.Range;

@ConfigGroup(BuffRingsConfig.GROUP)
public interface BuffRingsConfig extends Config
{
	String GROUP = "buffrings";

	@ConfigSection(
		name = "Buffs",
		description = "Toggle timers for buff-related effects, each with its color right below it",
		position = 1
	)
	String buffsSection = "buffsSection";

	@ConfigItem(
		keyName = "showAntifire",
		name = "Antifire potions",
		description = "Show a timer for antifire and super antifire",
		position = 1,
		section = buffsSection
	)
	default boolean showAntifire()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAntipoison",
		name = "Antivenom/Poison potions",
		description = "Show a timer for antivenom/antipoison immunity (timer switches to antipoison once venom immunity runs out)",
		position = 2,
		section = buffsSection
	)
	default boolean showAntipoison()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCharge",
		name = "Charge",
		description = "Show a timer for the Charge buff effect",
		position = 3,
		section = buffsSection
	)
	default boolean showCharge()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDivine",
		name = "Divine potions",
		description = "Show a timer for divine potion effects",
		position = 4,
		section = buffsSection
	)
	default boolean showDivine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGoading",
		name = "Goading potions",
		description = "Show a timer for the goading potion's aggro-everything effect",
		position = 5,
		section = buffsSection
	)
	default boolean showGoading()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showImbuedHeart",
		name = "Imbued Heart",
		description = "Show a timer for Imbued Heart",
		position = 6,
		section = buffsSection
	)
	default boolean showImbuedHeart()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMagicImbue",
		name = "Magic Imbue",
		description = "Show a timer for Magic Imbue",
		position = 7,
		section = buffsSection
	)
	default boolean showMagicImbue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMenaphiteRemedy",
		name = "Menaphite Remedy",
		description = "Show a timer for Menaphite Remedy",
		position = 8,
		section = buffsSection
	)
	default boolean showMenaphiteRemedy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMoonlightPotion",
		name = "Moonlight Potion",
		description = "Show a timer for Moonlight Potion",
		position = 9,
		section = buffsSection
	)
	default boolean showMoonlightPotion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNecromancy",
		name = "Necromancy spells",
		description = "Show a timer for Shadow Veil/Ward of Arceuus/Death Charge/Mark of Darkness and their cooldowns",
		position = 10,
		section = buffsSection
	)
	default boolean showNecromancy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverload",
		name = "Overload potions",
		description = "Show a timer for overload (NMZ/raids/deadman variants)",
		position = 11,
		section = buffsSection
	)
	default boolean showOverload()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrayerEnhance",
		name = "Prayer Enhance",
		description = "Show a timer for Prayer Enhance",
		position = 12,
		section = buffsSection
	)
	default boolean showPrayerEnhance()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrayerRegen",
		name = "Prayer regeneration potions",
		description = "Show a timer for the prayer regeneration potion effect",
		position = 13,
		section = buffsSection
	)
	default boolean showPrayerRegen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSmouldering",
		name = "Smouldering heart/gland",
		description = "Show a timer for the smouldering heart/gland buff",
		position = 14,
		section = buffsSection
	)
	default boolean showSmouldering()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStamina",
		name = "Stamina potions",
		description = "Show a timer for the stamina potion's run-energy-drain immunity",
		position = 15,
		section = buffsSection
	)
	default boolean showStamina()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showThrall",
		name = "Thrall",
		description = "Show a timer for an active resurrected thrall",
		position = 16,
		section = buffsSection
	)
	default boolean showThrall()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRaidSupply",
		name = "Tombs of Amascut supplies",
		description = "Show a timer for liquid adrenaline, silk dressing, blessed crystal scarab, and smelling salts",
		position = 17,
		section = buffsSection
	)
	default boolean showRaidSupply()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showVengeance",
		name = "Vengeance",
		description = "Show a timer for Vengeance",
		position = 18,
		section = buffsSection
	)
	default boolean showVengeance()
	{
		return true;
	}

	@ConfigSection(
		name = "Debuffs",
		description = "Toggle timers for debuff effects, each with its color right below it",
		position = 2
	)
	String debuffsSection = "debuffsSection";

	@ConfigItem(
		keyName = "showFreeze",
		name = "Freeze spells",
		description = "Show a timer for Bind/Snare/Entangle/Ice spells cast on you",
		position = 1,
		section = debuffsSection
	)
	default boolean showFreeze()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPickpocketStun",
		name = "Pickpocket Stun",
		description = "Show a timer for pickpocket stun",
		position = 2,
		section = debuffsSection
	)
	default boolean showPickpocketStun()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTeleblock",
		name = "Teleblock",
		description = "Show a timer for Teleblock cast on you",
		position = 3,
		section = debuffsSection
	)
	default boolean showTeleblock()
	{
		return true;
	}

	@ConfigSection(
		name = "Cooldowns",
		description = "Toggle timers for ability/item reuse cooldowns, each with its color right below it",
		position = 3
	)
	String cooldownsSection = "cooldownsSection";

	@ConfigItem(
		keyName = "showDragonfireShield",
		name = "Dragonfire Shield",
		description = "Show a timer for the dragonfire shield special-attack cooldown",
		position = 1,
		section = cooldownsSection
	)
	default boolean showDragonfireShield()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHealGroup",
		name = "Heal Group",
		description = "Show a timer for Heal Group cooldown",
		position = 2,
		section = cooldownsSection
	)
	default boolean showHealGroup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSpellbookSwap",
		name = "Spellbook Swap",
		description = "Show a timer for Spellbook Swap cooldown",
		position = 3,
		section = cooldownsSection
	)
	default boolean showSpellbookSwap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStaffOfTheDead",
		name = "Staff of the Dead",
		description = "Show a timer for the staff of the dead special-attack cooldown",
		position = 4,
		section = cooldownsSection
	)
	default boolean showStaffOfTheDead()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSurgePotion",
		name = "Surge Potion",
		description = "Show a timer for Surge Potion",
		position = 5,
		section = cooldownsSection
	)
	default boolean showSurgePotion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTeleport",
		name = "Teleport cooldowns",
		description = "Show a timer for the home teleport and minigame teleport cooldowns",
		position = 6,
		section = cooldownsSection
	)
	default boolean showTeleport()
	{
		return true;
	}

	@ConfigSection(
		name = "Other Timers",
		description = "Toggle timers for everything else, each with its color right below it",
		position = 4
	)
	String otherTimersSection = "otherTimersSection";

	@ConfigItem(
		keyName = "showAfkLogout",
		name = "AFK logout",
		description = "Show a timer counting down to when you'll be auto-logged-out from inactivity",
		position = 1,
		section = otherTimersSection
	)
	default boolean showAfkLogout()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBossMechanic",
		name = "Boss mechanics",
		description = "Show a timer for Abyssal Sire stun, Scurrius food pile, and God Wars altar cooldowns",
		position = 2,
		section = otherTimersSection
	)
	default boolean showBossMechanic()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBossRespawn",
		name = "Boss respawn",
		description = "Show a timer for a boss's respawn, using the same data as the core \"Boss Timers\" "
			+ "plugin (requires it to be enabled) - covers every boss it tracks under one timer for now",
		position = 3,
		section = otherTimersSection
	)
	default boolean showBossRespawn()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCannon",
		name = "Cannon",
		description = "Show a timer for the cannon and broken-cannon repair timers",
		position = 4,
		section = otherTimersSection
	)
	default boolean showCannon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDelayedHeal",
		name = "Delayed heal (hunter meats)",
		description = "Show a timer for the delayed portion of cooked kebbit/antelope meat's healing",
		position = 5,
		section = otherTimersSection
	)
	default boolean showDelayedHeal()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFarmersAffinity",
		name = "Farmer's Affinity",
		description = "Show a timer for Farmer's Affinity",
		position = 6,
		section = otherTimersSection
	)
	default boolean showFarmersAffinity()
	{
		return true;
	}

	@ConfigSection(
		name = "Counters",
		description = "Toggle timers for count-based progress (kills left, not time left) - no flashing or "
			+ "lingering settings apply to these, each with its color right below it",
		position = 5
	)
	String countersSection = "countersSection";

	@ConfigItem(
		keyName = "showSlayerTask",
		name = "Slayer task",
		description = "Show a counter for kills left on your current slayer task, using the same data as the "
			+ "Slayer plugin (requires its \"Show infobox\" setting for the task's monster icon)",
		position = 1,
		section = countersSection
	)
	default boolean showSlayerTask()
	{
		return true;
	}

	@ConfigSection(
		name = "Colors",
		description = "Per-timer colors, in the same order as their toggles in the groups above",
		position = 8,
		closedByDefault = true
	)
	String colorsSection = "colorsSection";

	@ConfigItem(
		keyName = "overrideColors",
		name = "Override all colors",
		description = "Use a single color for every timer below instead of each one's individual color",
		position = 1,
		section = colorsSection
	)
	default boolean overrideColors()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "overrideColor",
		name = "Override color",
		description = "The single color used for every timer while \"Override all colors\" is checked",
		position = 2,
		section = colorsSection
	)
	default Color overrideColor()
	{
		return new Color(76, 76, 76);
	}

	@Alpha
	@ConfigItem(
		keyName = "antifireColor",
		name = "Antifire color",
		description = "Color for antifire",
		position = 3,
		section = colorsSection
	)
	default Color antifireColor()
	{
		return new Color(113, 0, 153);
	}

	@Alpha
	@ConfigItem(
		keyName = "superAntifireColor",
		name = "Super antifire color",
		description = "Color for super antifire",
		position = 4,
		section = colorsSection
	)
	default Color superAntifireColor()
	{
		return new Color(126, 56, 153);
	}

	@Alpha
	@ConfigItem(
		keyName = "antivenomColor",
		name = "Antivenom color",
		description = "Color for antivenom",
		position = 5,
		section = colorsSection
	)
	default Color antivenomColor()
	{
		return new Color(93, 99, 78);
	}

	@Alpha
	@ConfigItem(
		keyName = "antipoisonColor",
		name = "Antipoison color",
		description = "Color for antipoison",
		position = 6,
		section = colorsSection
	)
	default Color antipoisonColor()
	{
		return new Color(68, 153, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "chargeColor",
		name = "Charge color",
		description = "Color for Charge",
		position = 7,
		section = colorsSection
	)
	default Color chargeColor()
	{
		return new Color(170, 140, 40);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineSuperCombatColor",
		name = "Divine combat color",
		description = "Color for divine super combat",
		position = 8,
		section = colorsSection
	)
	default Color divineSuperCombatColor()
	{
		return new Color(28, 153, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineBastionColor",
		name = "Divine bastion color",
		description = "Color for divine bastion",
		position = 9,
		section = colorsSection
	)
	default Color divineBastionColor()
	{
		return new Color(153, 65, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineBattlemageColor",
		name = "Divine battlemage color",
		description = "Color for divine battlemage",
		position = 10,
		section = colorsSection
	)
	default Color divineBattlemageColor()
	{
		return new Color(153, 103, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineSuperAttackColor",
		name = "Divine attack color",
		description = "Color for divine super attack",
		position = 11,
		section = colorsSection
	)
	default Color divineSuperAttackColor()
	{
		return new Color(16, 19, 153);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineSuperStrengthColor",
		name = "Divine strength color",
		description = "Color for divine super strength",
		position = 12,
		section = colorsSection
	)
	default Color divineSuperStrengthColor()
	{
		return new Color(153, 148, 127);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineSuperDefenceColor",
		name = "Divine defence color",
		description = "Color for divine super defence",
		position = 13,
		section = colorsSection
	)
	default Color divineSuperDefenceColor()
	{
		return new Color(153, 116, 5);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineRangingColor",
		name = "Divine ranging color",
		description = "Color for divine ranging",
		position = 14,
		section = colorsSection
	)
	default Color divineRangingColor()
	{
		return new Color(16, 117, 153);
	}

	@Alpha
	@ConfigItem(
		keyName = "divineMagicColor",
		name = "Divine magic color",
		description = "Color for divine magic",
		position = 15,
		section = colorsSection
	)
	default Color divineMagicColor()
	{
		return new Color(153, 106, 81);
	}

	@Alpha
	@ConfigItem(
		keyName = "goadingColor",
		name = "Goading color",
		description = "Color for goading",
		position = 16,
		section = colorsSection
	)
	default Color goadingColor()
	{
		return new Color(153, 15, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "imbuedHeartColor",
		name = "Imbued Heart color",
		description = "Color for Imbued Heart",
		position = 17,
		section = colorsSection
	)
	default Color imbuedHeartColor()
	{
		return new Color(98, 60, 144);
	}

	@Alpha
	@ConfigItem(
		keyName = "magicImbueColor",
		name = "Magic Imbue color",
		description = "Color for Magic Imbue",
		position = 18,
		section = colorsSection
	)
	default Color magicImbueColor()
	{
		return new Color(60, 160, 170);
	}

	@Alpha
	@ConfigItem(
		keyName = "menaphiteRemedyColor",
		name = "Menaphite Remedy color",
		description = "Color for Menaphite Remedy",
		position = 19,
		section = colorsSection
	)
	default Color menaphiteRemedyColor()
	{
		return new Color(43, 105, 153);
	}

	@Alpha
	@ConfigItem(
		keyName = "moonlightPotionColor",
		name = "Moonlight Potion color",
		description = "Color for Moonlight Potion",
		position = 20,
		section = colorsSection
	)
	default Color moonlightPotionColor()
	{
		return new Color(145, 71, 150);
	}

	@Alpha
	@ConfigItem(
		keyName = "necromancyColor",
		name = "Necromancy color",
		description = "Shared color for necromancy spells and cooldowns",
		position = 21,
		section = colorsSection
	)
	default Color necromancyColor()
	{
		return new Color(110, 70, 140);
	}

	@Alpha
	@ConfigItem(
		keyName = "overloadColor",
		name = "Overload color",
		description = "Shared color for overload potions",
		position = 22,
		section = colorsSection
	)
	default Color overloadColor()
	{
		return new Color(81, 81, 81);
	}

	@Alpha
	@ConfigItem(
		keyName = "prayerEnhanceColor",
		name = "Prayer Enhance color",
		description = "Color for Prayer Enhance",
		position = 23,
		section = colorsSection
	)
	default Color prayerEnhanceColor()
	{
		return new Color(145, 71, 150);
	}

	@Alpha
	@ConfigItem(
		keyName = "prayerRegenColor",
		name = "Prayer regen color",
		description = "Color for prayer regeneration",
		position = 24,
		section = colorsSection
	)
	default Color prayerRegenColor()
	{
		return new Color(118, 68, 68);
	}

	@Alpha
	@ConfigItem(
		keyName = "smoulderingColor",
		name = "Smouldering heart/gland color",
		description = "Color for the smouldering heart/gland",
		position = 25,
		section = colorsSection
	)
	default Color smoulderingColor()
	{
		return new Color(190, 70, 40);
	}

	@Alpha
	@ConfigItem(
		keyName = "staminaColor",
		name = "Stamina color",
		description = "Color for stamina",
		position = 26,
		section = colorsSection
	)
	default Color staminaColor()
	{
		return new Color(153, 91, 15);
	}

	@Alpha
	@ConfigItem(
		keyName = "thrallColor",
		name = "Thrall color",
		description = "Color for the thrall timer",
		position = 27,
		section = colorsSection
	)
	default Color thrallColor()
	{
		return new Color(110, 70, 140);
	}

	@Alpha
	@ConfigItem(
		keyName = "raidSupplyColor",
		name = "Raid supplies color",
		description = "Shared color for Tombs of Amascut supplies",
		position = 28,
		section = colorsSection
	)
	default Color raidSupplyColor()
	{
		return new Color(200, 170, 60);
	}

	@Alpha
	@ConfigItem(
		keyName = "vengeanceColor",
		name = "Vengeance color",
		description = "Color for Vengeance",
		position = 29,
		section = colorsSection
	)
	default Color vengeanceColor()
	{
		return new Color(171, 74, 74);
	}

	@Alpha
	@ConfigItem(
		keyName = "freezeColor",
		name = "Freeze spells color",
		description = "Shared color for Bind/Snare/Entangle/Ice spells",
		position = 30,
		section = colorsSection
	)
	default Color freezeColor()
	{
		return new Color(100, 180, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "pickpocketStunColor",
		name = "Pickpocket Stun color",
		description = "Color for the pickpocket stun",
		position = 31,
		section = colorsSection
	)
	default Color pickpocketStunColor()
	{
		return new Color(145, 71, 150);
	}

	@Alpha
	@ConfigItem(
		keyName = "teleblockColor",
		name = "Teleblock color",
		description = "Color for Teleblock",
		position = 32,
		section = colorsSection
	)
	default Color teleblockColor()
	{
		return new Color(140, 40, 180);
	}

	@Alpha
	@ConfigItem(
		keyName = "dragonfireShieldColor",
		name = "Dragonfire Shield color",
		description = "Color for the dragonfire shield special-attack cooldown",
		position = 33,
		section = colorsSection
	)
	default Color dragonfireShieldColor()
	{
		return new Color(200, 100, 30);
	}

	@Alpha
	@ConfigItem(
		keyName = "healGroupColor",
		name = "Heal Group color",
		description = "Color for Heal Group",
		position = 34,
		section = colorsSection
	)
	default Color healGroupColor()
	{
		return new Color(80, 170, 120);
	}

	@Alpha
	@ConfigItem(
		keyName = "spellbookSwapColor",
		name = "Spellbook Swap color",
		description = "Color for Spellbook Swap",
		position = 35,
		section = colorsSection
	)
	default Color spellbookSwapColor()
	{
		return new Color(60, 140, 170);
	}

	@Alpha
	@ConfigItem(
		keyName = "staffOfTheDeadColor",
		name = "Staff of the Dead color",
		description = "Color for the staff of the dead special-attack cooldown",
		position = 36,
		section = colorsSection
	)
	default Color staffOfTheDeadColor()
	{
		return new Color(82, 116, 115);
	}

	@Alpha
	@ConfigItem(
		keyName = "surgePotionColor",
		name = "Surge Potion color",
		description = "Color for Surge Potion",
		position = 37,
		section = colorsSection
	)
	default Color surgePotionColor()
	{
		return new Color(60, 160, 170);
	}

	@Alpha
	@ConfigItem(
		keyName = "homeTeleportColor",
		name = "Home teleport color",
		description = "Color for the home teleport cooldown",
		position = 38,
		section = colorsSection
	)
	default Color homeTeleportColor()
	{
		return new Color(80, 130, 160);
	}

	@Alpha
	@ConfigItem(
		keyName = "minigameTeleportColor",
		name = "Minigame teleport color",
		description = "Color for the minigame teleport cooldown",
		position = 39,
		section = colorsSection
	)
	default Color minigameTeleportColor()
	{
		return new Color(160, 80, 80);
	}

	@Alpha
	@ConfigItem(
		keyName = "afkLogoutColor",
		name = "AFK logout color",
		description = "Color for the AFK logout timer",
		position = 40,
		section = colorsSection
	)
	default Color afkLogoutColor()
	{
		return new Color(130, 100, 30);
	}

	@Alpha
	@ConfigItem(
		keyName = "bossMechanicColor",
		name = "Boss mechanics color",
		description = "Shared color for Abyssal Sire stun, Scurrius food pile, and God Wars altar cooldowns",
		position = 41,
		section = colorsSection
	)
	default Color bossMechanicColor()
	{
		return new Color(190, 90, 30);
	}

	@Alpha
	@ConfigItem(
		keyName = "bossRespawnColor",
		name = "Boss respawn color",
		description = "Color for the boss respawn timer",
		position = 42,
		section = colorsSection
	)
	default Color bossRespawnColor()
	{
		return new Color(80, 20, 20);
	}

	@Alpha
	@ConfigItem(
		keyName = "cannonColor",
		name = "Cannon color",
		description = "Color for the cannon and broken cannon",
		position = 43,
		section = colorsSection
	)
	default Color cannonColor()
	{
		return new Color(107, 107, 107);
	}

	@Alpha
	@ConfigItem(
		keyName = "delayedHealColor",
		name = "Delayed heal color",
		description = "Color for the hunter-meat delayed-heal",
		position = 44,
		section = colorsSection
	)
	default Color delayedHealColor()
	{
		return new Color(153, 116, 81);
	}

	@Alpha
	@ConfigItem(
		keyName = "farmersAffinityColor",
		name = "Farmer's Affinity color",
		description = "Color for Farmer's Affinity",
		position = 45,
		section = colorsSection
	)
	default Color farmersAffinityColor()
	{
		return new Color(150, 120, 40);
	}

	@Alpha
	@ConfigItem(
		keyName = "slayerTaskColor",
		name = "Slayer task color",
		description = "Color for the slayer task kill counter",
		position = 46,
		section = colorsSection
	)
	default Color slayerTaskColor()
	{
		return new Color(100, 60, 140);
	}

	@ConfigSection(
		name = "Size & Shape",
		description = "Timer size, shape, border thickness, and the border/background colors. Each timer starts "
			+ "in one shared group - drag it to reposition, right-click a timer to Detach it into its own "
			+ "group, or drag one group onto another to merge them",
		position = 6
	)
	String layoutSection = "layoutSection";

	@Range(min = 16, max = 200)
	@ConfigItem(
		keyName = "ringWidth",
		name = "Width",
		description = "Width of each timer, in pixels",
		position = 1,
		section = layoutSection
	)
	default int ringWidth()
	{
		return 35;
	}

	@Range(min = 16, max = 200)
	@ConfigItem(
		keyName = "ringHeight",
		name = "Height",
		description = "Height of each timer, in pixels",
		position = 2,
		section = layoutSection
	)
	default int ringHeight()
	{
		return 35;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "cornerRadius",
		name = "Corner radius",
		description = "Roundness of each timer's corners - 0 for a square, 100 for a circle/ellipse, "
			+ "or anything in between for a rounded square",
		position = 3,
		section = layoutSection
	)
	default int cornerRadius()
	{
		return 0;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
		keyName = "ringBorderThickness",
		name = "Border thickness",
		description = "Thickness of the timer's outline, in pixels (0 to hide it)",
		position = 4,
		section = layoutSection
	)
	default int ringBorderThickness()
	{
		return 1;
	}

	@Alpha
	@ConfigItem(
		keyName = "borderColor",
		name = "Border color",
		description = "Color of the outline drawn around each timer",
		position = 5,
		section = layoutSection
	)
	default Color borderColor()
	{
		return Color.BLACK;
	}

	@Alpha
	@ConfigItem(
		keyName = "backgroundColor",
		name = "Background color",
		description = "Color of the depleted (unfilled) portion of each timer",
		position = 6,
		section = layoutSection
	)
	default Color backgroundColor()
	{
		return new Color(0, 0, 0, 160);
	}

	@ConfigItem(
		keyName = "fillDirection",
		name = "Fill direction",
		description = "Which way the depleting edge of the foreground moves as the timer counts down",
		position = 7,
		section = layoutSection
	)
	default FillDirection fillDirection()
	{
		return FillDirection.COUNTERCLOCKWISE;
	}

	@Range(min = 0, max = 40)
	@ConfigItem(
		keyName = "groupPadding",
		name = "Padding",
		description = "Spacing between timers that share the same group",
		position = 8,
		section = layoutSection
	)
	default int groupPadding()
	{
		return 6;
	}

	@ConfigSection(
		name = "Text & Icon Layout",
		description = "Whether and where the remaining-time text, description text, and icon show on each "
			+ "timer, plus their shared font/color/border/shadow and hover tooltips",
		position = 7
	)
	String textSection = "textSection";

	@ConfigItem(
		keyName = "showTimeText",
		name = "Show time",
		description = "Show the remaining-time text on each timer",
		position = 1,
		section = textSection
	)
	default boolean showTimeText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDescription",
		name = "Show description",
		description = "Show the timer's name as description text on each timer",
		position = 2,
		section = textSection
	)
	default boolean showDescription()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showIcon",
		name = "Show icon",
		description = "Show the effect's icon on each timer",
		position = 3,
		section = textSection
	)
	default boolean showIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableTooltips",
		name = "Show tooltips",
		description = "Show the timer's name in a tooltip when hovering over it",
		position = 4,
		section = textSection
	)
	default boolean enableTooltips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "textPosition",
		name = "Time position",
		description = "Where to draw the remaining-time text relative to each timer",
		position = 5,
		section = textSection
	)
	default ElementPosition textPosition()
	{
		return ElementPosition.BOTTOM;
	}

	@ConfigItem(
		keyName = "descriptionPosition",
		name = "Description position",
		description = "Where to draw the description text relative to each timer",
		position = 6,
		section = textSection
	)
	default ElementPosition descriptionPosition()
	{
		return ElementPosition.TOP;
	}

	@ConfigItem(
		keyName = "iconPosition",
		name = "Icon position",
		description = "Where to draw the icon, inside the timer or outside it",
		position = 7,
		section = textSection
	)
	default ElementPosition iconPosition()
	{
		return ElementPosition.CENTER;
	}

	@Range(min = 8, max = 64)
	@ConfigItem(
		keyName = "iconSize",
		name = "Icon size",
		description = "Size of the icon, in pixels",
		position = 8,
		section = textSection
	)
	default int iconSize()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "textFont",
		name = "Text font",
		description = "Font used for the remaining-time and description text (same picker as RuneLite's Infobox "
			+ "font setting)",
		position = 9,
		section = textSection
	)
	default FontType textFont()
	{
		return FontType.REGULAR;
	}

	@Alpha
	@ConfigItem(
		keyName = "textColor",
		name = "Text color",
		description = "Color of the remaining-time and description text",
		position = 10,
		section = textSection
	)
	default Color textColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "textBorderEnabled",
		name = "Text border",
		description = "Draw a full outline around the remaining-time and description text",
		position = 11,
		section = textSection
	)
	default boolean textBorderEnabled()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "textBorderColor",
		name = "Text border color",
		description = "Color of the text border/shadow",
		position = 12,
		section = textSection
	)
	default Color textBorderColor()
	{
		return Color.BLACK;
	}

	@ConfigItem(
		keyName = "textShadowEnabled",
		name = "Text shadow",
		description = "Draw a drop shadow behind the remaining-time and description text",
		position = 13,
		section = textSection
	)
	default boolean textShadowEnabled()
	{
		return true;
	}

	@ConfigSection(
		name = "Advanced Behaviors (Buffs)",
		description = "Flashing and lingering for buff timers",
		position = 9,
		closedByDefault = true
	)
	String flashSection = "flashSection";

	@ConfigItem(
		keyName = "enableFlash",
		name = "Flash when low",
		description = "Flash the timer when its remaining time drops to the threshold below",
		position = 1,
		section = flashSection
	)
	default boolean enableFlash()
	{
		return true;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "flashThresholdSeconds",
		name = "Flash threshold (seconds)",
		description = "Start flashing once this many seconds (or fewer) remain",
		position = 2,
		section = flashSection
	)
	default int flashThresholdSeconds()
	{
		return 15;
	}

	@Alpha
	@ConfigItem(
		keyName = "flashColor",
		name = "Flash color",
		description = "Color used for whichever parts below are set to flash",
		position = 3,
		section = flashSection
	)
	default Color flashColor()
	{
		return new Color(218, 35, 35);
	}

	@ConfigItem(
		keyName = "flashForeground",
		name = "Flash foreground",
		description = "Flash the filled (remaining-time) portion of the timer",
		position = 4,
		section = flashSection
	)
	default boolean flashForeground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "flashBackground",
		name = "Flash background",
		description = "Flash the depleted (unfilled) portion of the timer",
		position = 5,
		section = flashSection
	)
	default boolean flashBackground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "flashBorder",
		name = "Flash border",
		description = "Flash the timer's outline",
		position = 6,
		section = flashSection
	)
	default boolean flashBorder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "flashText",
		name = "Flash text",
		description = "Flash the remaining-time text",
		position = 7,
		section = flashSection
	)
	default boolean flashText()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableLinger",
		name = "Linger after finishing",
		description = "Keep showing a timer (still flashing, if enabled) for a while after it reaches 0",
		position = 8,
		section = flashSection
	)
	default boolean enableLinger()
	{
		return true;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "lingerSeconds",
		name = "Linger duration (seconds)",
		description = "How long a finished timer stays visible before disappearing",
		position = 9,
		section = flashSection
	)
	default int lingerSeconds()
	{
		return 15;
	}

	@ConfigSection(
		name = "Advanced Behaviors (Debuffs)",
		description = "Flashing and lingering for debuff timers",
		position = 10,
		closedByDefault = true
	)
	String debuffFlashSection = "debuffFlashSection";

	@ConfigItem(
		keyName = "enableDebuffFlash",
		name = "Flash when low",
		description = "Flash the timer when its remaining time drops to the threshold below",
		position = 1,
		section = debuffFlashSection
	)
	default boolean enableDebuffFlash()
	{
		return true;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "debuffFlashThresholdSeconds",
		name = "Flash threshold (seconds)",
		description = "Start flashing once this many seconds (or fewer) remain",
		position = 2,
		section = debuffFlashSection
	)
	default int debuffFlashThresholdSeconds()
	{
		return 5;
	}

	@Alpha
	@ConfigItem(
		keyName = "debuffFlashColor",
		name = "Flash color",
		description = "Color used for whichever parts below are set to flash",
		position = 3,
		section = debuffFlashSection
	)
	default Color debuffFlashColor()
	{
		return new Color(26, 221, 70);
	}

	@ConfigItem(
		keyName = "debuffFlashForeground",
		name = "Flash foreground",
		description = "Flash the filled (remaining-time) portion of the timer",
		position = 4,
		section = debuffFlashSection
	)
	default boolean debuffFlashForeground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debuffFlashBackground",
		name = "Flash background",
		description = "Flash the depleted (unfilled) portion of the timer",
		position = 5,
		section = debuffFlashSection
	)
	default boolean debuffFlashBackground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debuffFlashBorder",
		name = "Flash border",
		description = "Flash the timer's outline",
		position = 6,
		section = debuffFlashSection
	)
	default boolean debuffFlashBorder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debuffFlashText",
		name = "Flash text",
		description = "Flash the remaining-time text",
		position = 7,
		section = debuffFlashSection
	)
	default boolean debuffFlashText()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableDebuffLinger",
		name = "Linger after finishing",
		description = "Keep showing a timer (still flashing, if enabled) for a while after it reaches 0",
		position = 8,
		section = debuffFlashSection
	)
	default boolean enableDebuffLinger()
	{
		return false;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "debuffLingerSeconds",
		name = "Linger duration (seconds)",
		description = "How long a finished timer stays visible before disappearing",
		position = 9,
		section = debuffFlashSection
	)
	default int debuffLingerSeconds()
	{
		return 15;
	}

	@ConfigSection(
		name = "Advanced Behaviors (Cooldowns)",
		description = "Flashing and lingering for cooldown timers",
		position = 11,
		closedByDefault = true
	)
	String cooldownFlashSection = "cooldownFlashSection";

	@ConfigItem(
		keyName = "enableCooldownFlash",
		name = "Flash when low",
		description = "Flash the timer when its remaining time drops to the threshold below",
		position = 1,
		section = cooldownFlashSection
	)
	default boolean enableCooldownFlash()
	{
		return false;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "cooldownFlashThresholdSeconds",
		name = "Flash threshold (seconds)",
		description = "Start flashing once this many seconds (or fewer) remain",
		position = 2,
		section = cooldownFlashSection
	)
	default int cooldownFlashThresholdSeconds()
	{
		return 5;
	}

	@Alpha
	@ConfigItem(
		keyName = "cooldownFlashColor",
		name = "Flash color",
		description = "Color used for whichever parts below are set to flash",
		position = 3,
		section = cooldownFlashSection
	)
	default Color cooldownFlashColor()
	{
		return new Color(26, 221, 70);
	}

	@ConfigItem(
		keyName = "cooldownFlashForeground",
		name = "Flash foreground",
		description = "Flash the filled (remaining-time) portion of the timer",
		position = 4,
		section = cooldownFlashSection
	)
	default boolean cooldownFlashForeground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "cooldownFlashBackground",
		name = "Flash background",
		description = "Flash the depleted (unfilled) portion of the timer",
		position = 5,
		section = cooldownFlashSection
	)
	default boolean cooldownFlashBackground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "cooldownFlashBorder",
		name = "Flash border",
		description = "Flash the timer's outline",
		position = 6,
		section = cooldownFlashSection
	)
	default boolean cooldownFlashBorder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "cooldownFlashText",
		name = "Flash text",
		description = "Flash the remaining-time text",
		position = 7,
		section = cooldownFlashSection
	)
	default boolean cooldownFlashText()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableCooldownLinger",
		name = "Linger after finishing",
		description = "Keep showing a timer (still flashing, if enabled) for a while after it reaches 0",
		position = 8,
		section = cooldownFlashSection
	)
	default boolean enableCooldownLinger()
	{
		return false;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "cooldownLingerSeconds",
		name = "Linger duration (seconds)",
		description = "How long a finished timer stays visible before disappearing",
		position = 9,
		section = cooldownFlashSection
	)
	default int cooldownLingerSeconds()
	{
		return 15;
	}

	@ConfigSection(
		name = "Advanced Behaviors (Other Timers)",
		description = "Flashing and lingering for everything in the \"Other Timers\" group",
		position = 12,
		closedByDefault = true
	)
	String otherTimerFlashSection = "otherTimerFlashSection";

	@ConfigItem(
		keyName = "enableOtherTimerFlash",
		name = "Flash when low",
		description = "Flash the timer when its remaining time drops to the threshold below",
		position = 1,
		section = otherTimerFlashSection
	)
	default boolean enableOtherTimerFlash()
	{
		return false;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "otherTimerFlashThresholdSeconds",
		name = "Flash threshold (seconds)",
		description = "Start flashing once this many seconds (or fewer) remain",
		position = 2,
		section = otherTimerFlashSection
	)
	default int otherTimerFlashThresholdSeconds()
	{
		return 10;
	}

	@Alpha
	@ConfigItem(
		keyName = "otherTimerFlashColor",
		name = "Flash color",
		description = "Color used for whichever parts below are set to flash",
		position = 3,
		section = otherTimerFlashSection
	)
	default Color otherTimerFlashColor()
	{
		return new Color(218, 35, 35);
	}

	@ConfigItem(
		keyName = "otherTimerFlashForeground",
		name = "Flash foreground",
		description = "Flash the filled (remaining-time) portion of the timer",
		position = 4,
		section = otherTimerFlashSection
	)
	default boolean otherTimerFlashForeground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "otherTimerFlashBackground",
		name = "Flash background",
		description = "Flash the depleted (unfilled) portion of the timer",
		position = 5,
		section = otherTimerFlashSection
	)
	default boolean otherTimerFlashBackground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "otherTimerFlashBorder",
		name = "Flash border",
		description = "Flash the timer's outline",
		position = 6,
		section = otherTimerFlashSection
	)
	default boolean otherTimerFlashBorder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "otherTimerFlashText",
		name = "Flash text",
		description = "Flash the remaining-time text",
		position = 7,
		section = otherTimerFlashSection
	)
	default boolean otherTimerFlashText()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableOtherTimerLinger",
		name = "Linger after finishing",
		description = "Keep showing a timer (still flashing, if enabled) for a while after it reaches 0",
		position = 8,
		section = otherTimerFlashSection
	)
	default boolean enableOtherTimerLinger()
	{
		return false;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "otherTimerLingerSeconds",
		name = "Linger duration (seconds)",
		description = "How long a finished timer stays visible before disappearing",
		position = 9,
		section = otherTimerFlashSection
	)
	default int otherTimerLingerSeconds()
	{
		return 15;
	}

	@ConfigSection(
		name = "Debug",
		description = "Tools for arranging and previewing timers without triggering the real effects",
		position = 13,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@ConfigItem(
		keyName = "debugForceShowAll",
		name = "Force show all",
		description = "Show every enabled timer at once, regardless of whether it's actually active, so you can "
			+ "arrange/group them. Turn this off when you're done. Only applies to timers whose own show/hide "
			+ "toggle is already on",
		position = 1,
		section = debugSection
	)
	default boolean debugForceShowAll()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugForceFlashing",
		name = "Force flashing",
		description = "Make every timer flash regardless of its remaining time, so you can preview flash "
			+ "colors/style. Still only applies to timers whose kind has flashing enabled. Turn this off when "
			+ "you're done",
		position = 2,
		section = debugSection
	)
	default boolean debugForceFlashing()
	{
		return false;
	}

	@Range(min = 1, max = 3600)
	@ConfigItem(
		keyName = "debugPreviewTotalSeconds",
		name = "Preview total time (seconds)",
		description = "The \"out of\" value for the frozen preview timer shown by \"Force show all\"",
		position = 3,
		section = debugSection
	)
	default int debugPreviewTotalSeconds()
	{
		return 60;
	}

	@Range(min = 0, max = 3600)
	@ConfigItem(
		keyName = "debugPreviewRemainingSeconds",
		name = "Preview remaining time (seconds)",
		description = "The remaining-time value for the frozen preview timer shown by \"Force show all\"",
		position = 4,
		section = debugSection
	)
	default int debugPreviewRemainingSeconds()
	{
		return 50;
	}
}
