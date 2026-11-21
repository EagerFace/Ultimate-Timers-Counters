package dev.keith.ultimatetimers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;

/**
 * Each entry's gameTimerName must exactly match the name of the corresponding (package-private)
 * net.runelite.client.plugins.timersandbuffs.GameTimer enum constant - that's how
 * UltimateTimersPlugin finds the right already-computed net.runelite.client.ui.overlay.infobox.Timer
 * to read, rather than re-deriving each buff's remaining duration itself. Icons are likewise
 * copied from whatever GameTimer uses for that same entry (item icon or interface sprite).
 */
enum TrackedBuff
{
	ANTIFIRE(Category.ANTIFIRE, TimerCategory.CATEGORY_1, "Antifire", Icon.item(ItemID._4DOSE1ANTIDRAGON), "ANTIFIRE"),
	SUPER_ANTIFIRE(Category.ANTIFIRE, TimerCategory.CATEGORY_1, "Sup. Antifire", Icon.item(ItemID._4DOSE3ANTIDRAGON), "SUPERANTIFIRE"),
	STAMINA(Category.STAMINA, TimerCategory.CATEGORY_1, "Stamina", Icon.item(ItemID._4DOSESTAMINA), "STAMINA"),
	DIVINE_SUPER_COMBAT(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Combat", Icon.item(ItemID._4DOSEDIVINECOMBAT), "DIVINE_SUPER_COMBAT"),
	DIVINE_BASTION(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Bastion", Icon.item(ItemID._4DOSEDIVINEBASTION), "DIVINE_BASTION"),
	DIVINE_BATTLEMAGE(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Battlemage", Icon.item(ItemID._4DOSEDIVINEBATTLEMAGE), "DIVINE_BATTLEMAGE"),
	DIVINE_SUPER_ATTACK(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Attack", Icon.item(ItemID._4DOSEDIVINEATTACK), "DIVINE_SUPER_ATTACK"),
	DIVINE_SUPER_STRENGTH(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Strength", Icon.item(ItemID._4DOSEDIVINESTRENGTH), "DIVINE_SUPER_STRENGTH"),
	DIVINE_SUPER_DEFENCE(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Defence", Icon.item(ItemID._4DOSEDIVINEDEFENCE), "DIVINE_SUPER_DEFENCE"),
	DIVINE_RANGING(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Ranging", Icon.item(ItemID._4DOSEDIVINERANGE), "DIVINE_RANGING"),
	DIVINE_MAGIC(Category.DIVINE, TimerCategory.CATEGORY_1, "Div. Magic", Icon.item(ItemID._4DOSEDIVINEMAGIC), "DIVINE_MAGIC"),
	ANTIVENOM(Category.ANTIPOISON, TimerCategory.CATEGORY_1, "Antivenom", Icon.item(ItemID.ANTIVENOM4), "ANTIVENOM"),
	ANTIPOISON(Category.ANTIPOISON, TimerCategory.CATEGORY_1, "Antipoison", Icon.item(ItemID._4DOSEANTIPOISON), "ANTIPOISON"),
	GOADING(Category.GOADING, TimerCategory.CATEGORY_1, "Goading", Icon.item(ItemID._4DOSEGOADING), "GOADING"),
	PRAYER_REGEN(Category.PRAYER_REGEN, TimerCategory.CATEGORY_1, "Prayer Regen", Icon.item(ItemID._4DOSE1PRAYER_REGENERATION), "PRAYER_REGENERATION"),
	THRALL(Category.THRALL, TimerCategory.CATEGORY_1, "Thrall", Icon.sprite(SpriteID.MagicNecroOn.RESURRECT_SUPERIOR_SKELETON), "RESURRECT_THRALL"),

	// Freeze spells
	BIND(Category.FREEZE, TimerCategory.CATEGORY_2, "Bind", Icon.sprite(SpriteID.Magicon2.BIND), "BIND"),
	SNARE(Category.FREEZE, TimerCategory.CATEGORY_2, "Snare", Icon.sprite(SpriteID.Magicon2.SNARE), "SNARE"),
	ENTANGLE(Category.FREEZE, TimerCategory.CATEGORY_2, "Entangle", Icon.sprite(SpriteID.Magicon2.ENTANGLE), "ENTANGLE"),
	ICE_RUSH(Category.FREEZE, TimerCategory.CATEGORY_2, "Ice Rush", Icon.sprite(SpriteID.Magicon2.ICE_RUSH), "ICERUSH"),
	ICE_BURST(Category.FREEZE, TimerCategory.CATEGORY_2, "Ice Burst", Icon.sprite(SpriteID.Magicon2.ICE_BURST), "ICEBURST"),
	ICE_BLITZ(Category.FREEZE, TimerCategory.CATEGORY_2, "Ice Blitz", Icon.sprite(SpriteID.Magicon2.ICE_BLITZ), "ICEBLITZ"),
	ICE_BARRAGE(Category.FREEZE, TimerCategory.CATEGORY_2, "Ice Barrage", Icon.sprite(SpriteID.Magicon2.ICE_BARRAGE), "ICEBARRAGE"),

	// Necromancy spells and their cooldowns
	SHADOW_VEIL(Category.NECROMANCY, TimerCategory.CATEGORY_1, "Shadow Veil", Icon.sprite(SpriteID.MagicNecroOn.SHADOW_VEIL), "SHADOW_VEIL"),
	WARD_OF_ARCEUUS(Category.NECROMANCY, TimerCategory.CATEGORY_1, "Ward of Arceuus", Icon.sprite(SpriteID.MagicNecroOn.WARD_OF_ARCEUUS), "WARD_OF_ARCEUUS"),
	DEATH_CHARGE(Category.NECROMANCY, TimerCategory.CATEGORY_1, "Death Charge", Icon.sprite(SpriteID.MagicNecroOn.DEATH_CHARGE), "DEATH_CHARGE"),
	MARK_OF_DARKNESS(Category.NECROMANCY, TimerCategory.CATEGORY_1, "Mark of Darkness", Icon.sprite(SpriteID.MagicNecroOn.MARK_OF_DARKNESS), "MARK_OF_DARKNESS"),
	SHADOW_VEIL_COOLDOWN(Category.NECROMANCY, TimerCategory.CATEGORY_3, "Shadow Veil CD", Icon.sprite(SpriteID.MagicNecroOff.SHADOW_VEIL_DISABLED), "SHADOW_VEIL_COOLDOWN"),
	RESURRECT_THRALL_COOLDOWN(Category.NECROMANCY, TimerCategory.CATEGORY_3, "Thrall CD", Icon.sprite(SpriteID.MagicNecroOff.RESURRECT_SUPERIOR_SKELETON_DISABLED), "RESURRECT_THRALL_COOLDOWN"),
	WARD_OF_ARCEUUS_COOLDOWN(Category.NECROMANCY, TimerCategory.CATEGORY_3, "Ward CD", Icon.sprite(SpriteID.MagicNecroOff.WARD_OF_ARCEUUS_DISABLED), "WARD_OF_ARCEUUS_COOLDOWN"),
	DEATH_CHARGE_COOLDOWN(Category.NECROMANCY, TimerCategory.CATEGORY_3, "Death Charge CD", Icon.sprite(SpriteID.MagicNecroOff.DEATH_CHARGE_DISABLED), "DEATH_CHARGE_COOLDOWN"),
	CORRUPTION_COOLDOWN(Category.NECROMANCY, TimerCategory.CATEGORY_3, "Corruption CD", Icon.sprite(SpriteID.MagicNecroOff.GREATER_CORRUPTION_DISABLED), "CORRUPTION_COOLDOWN"),
	MARK_OF_DARKNESS_COOLDOWN(Category.NECROMANCY, TimerCategory.CATEGORY_3, "Mark of Darkness CD", Icon.sprite(SpriteID.MagicNecroOff.MARK_OF_DARKNESS_DISABLED), "MARK_OF_DARKNESS_COOLDOWN"),

	// Overload-family potions
	OVERLOAD(Category.OVERLOAD, TimerCategory.CATEGORY_1, "Overload", Icon.item(ItemID.NZONE4DOSEOVERLOADPOTION), "OVERLOAD"),
	OVERLOAD_RAID(Category.OVERLOAD, TimerCategory.CATEGORY_1, "Overload (Raids)", Icon.item(ItemID.RAIDS_VIAL_OVERLOAD_STRONG_4), "OVERLOAD_RAID"),
	BLIGHTED_OVERLOAD(Category.OVERLOAD, TimerCategory.CATEGORY_1, "Blighted Overload", Icon.item(ItemID.DEADMAN4DOSEOVERLOAD), "BLIGHTED_OVERLOAD"),

	// Other potions and magic utility effects
	MAGIC_IMBUE(Category.MAGIC_IMBUE, TimerCategory.CATEGORY_1, "Magic Imbue", Icon.sprite(SpriteID.LunarMagicOn.MAGIC_IMBUE), "MAGICIMBUE"),
	IMBUED_HEART(Category.IMBUED_HEART, TimerCategory.CATEGORY_1, "Imbued Heart", Icon.item(ItemID.IMBUED_HEART), "IMBUEDHEART"),
	PRAYER_ENHANCE(Category.PRAYER_ENHANCE, TimerCategory.CATEGORY_1, "Prayer Enhance", Icon.item(ItemID.RAIDS_VIAL_PRAYER_WEAK_4), "PRAYER_ENHANCE"),
	FARMERS_AFFINITY(Category.FARMERS_AFFINITY, TimerCategory.CATEGORY_4, "Farmer's Affinity", Icon.item(ItemID.GRAIN), "FARMERS_AFFINITY"),
	MENAPHITE_REMEDY(Category.MENAPHITE_REMEDY, TimerCategory.CATEGORY_1, "Menaphite Remedy", Icon.item(ItemID._4DOSESTATRENEWAL), "MENAPHITE_REMEDY"),
	SURGE_POTION(Category.SURGE_POTION, TimerCategory.CATEGORY_3, "Surge Potion", Icon.item(ItemID._4DOSESURGE), "SURGE_POTION"),
	MOONLIGHT_POTION(Category.MOONLIGHT_POTION, TimerCategory.CATEGORY_1, "Moonlight Potion", Icon.item(ItemID._4DOSEMOONLIGHTPOTION), "MOONLIGHT_POTION"),

	// Tombs of Amascut supplies
	LIQUID_ADRENALINE(Category.RAID_SUPPLY, TimerCategory.CATEGORY_1, "Liquid Adrenaline", Icon.item(ItemID.TOA_SUPPLY_ENERGY_2), "LIQUID_ADRENALINE"),
	SILK_DRESSING(Category.RAID_SUPPLY, TimerCategory.CATEGORY_1, "Silk Dressing", Icon.item(ItemID.TOA_SUPPLY_HEAL_OVERTIME_2), "SILK_DRESSING"),
	BLESSED_CRYSTAL_SCARAB(Category.RAID_SUPPLY, TimerCategory.CATEGORY_1, "Blessed Crystal Scarab", Icon.item(ItemID.TOA_SUPPLY_PRAYER_OVERTIME_2), "BLESSED_CRYSTAL_SCARAB"),
	SMELLING_SALTS(Category.RAID_SUPPLY, TimerCategory.CATEGORY_1, "Smelling Salts", Icon.item(ItemID.TOA_SUPPLY_STATS_2), "SMELLING_SALTS"),

	// Other combat-magic effects - each split into its own toggle/color
	VENGEANCE(Category.VENGEANCE, TimerCategory.CATEGORY_1, "Vengeance", Icon.sprite(SpriteID.LunarMagicOn.VENGEANCE), "VENGEANCE"),
	TELEBLOCK(Category.TELEBLOCK, TimerCategory.CATEGORY_2, "Teleblock", Icon.sprite(SpriteID.Magicon2.TELE_BLOCK), "TELEBLOCK"),
	HEAL_GROUP(Category.HEAL_GROUP, TimerCategory.CATEGORY_3, "Heal Group", Icon.sprite(SpriteID.LunarMagicOn.HEAL_GROUP), "HEAL_GROUP"),
	CHARGE(Category.CHARGE, TimerCategory.CATEGORY_1, "Charge", Icon.sprite(SpriteID.Magicon2.CHARGE), "CHARGE"),
	SPELLBOOK_SWAP(Category.SPELLBOOK_SWAP, TimerCategory.CATEGORY_3, "Spellbook Swap", Icon.sprite(SpriteID.LunarMagicOn.SPELLBOOK_SWAP), "SPELLBOOK_SWAP"),

	// Equipment and item-based timers
	CANNON(Category.CANNON, TimerCategory.CATEGORY_4, "Cannon", Icon.item(ItemID.TWPART3), "CANNON"),
	CANNON_REPAIR(Category.CANNON, TimerCategory.CATEGORY_4, "Broken Cannon", Icon.item(ItemID.MCANNONTOOLKIT), "CANNON_REPAIR"),
	STAFF_OF_THE_DEAD(Category.STAFF_OF_THE_DEAD, TimerCategory.CATEGORY_3, "Staff of the Dead", Icon.item(ItemID.SOTD), "STAFF_OF_THE_DEAD"),
	DRAGON_FIRE_SHIELD(Category.DRAGON_FIRE_SHIELD, TimerCategory.CATEGORY_3, "Dragonfire Shield", Icon.item(ItemID.DRAGONFIRE_SHIELD), "DRAGON_FIRE_SHIELD"),
	SMOULDERING_HEART(Category.SMOULDERING, TimerCategory.CATEGORY_1, "Smouldering Heart", Icon.item(ItemID.SMOULDERING_HEART), "SMOULDERING_HEART"),
	SMOULDERING_GLAND(Category.SMOULDERING, TimerCategory.CATEGORY_1, "Smouldering Gland", Icon.item(ItemID.SMOULDERING_GLAND), "SMOULDERING_GLAND"),

	// Boss/minigame mechanics
	ABYSSAL_SIRE_STUN(Category.BOSS_MECHANIC, TimerCategory.CATEGORY_4, "Abyssal Sire Stun", Icon.item(ItemID.ABYSSALSIRE_PET), "ABYSSAL_SIRE_STUN"),
	SCURRIUS_FOOD_PILE(Category.BOSS_MECHANIC, TimerCategory.CATEGORY_4, "Scurrius Food Pile", Icon.item(ItemID.CHEESE), "SCURRIUS_FOOD_PILE"),
	GOD_WARS_ALTAR(Category.BOSS_MECHANIC, TimerCategory.CATEGORY_4, "God Wars Altar", Icon.sprite(SpriteID.Staticons.PRAYER), "GOD_WARS_ALTAR"),
	PICKPOCKET_STUN(Category.PICKPOCKET_STUN, TimerCategory.CATEGORY_2, "Pickpocket Stun", Icon.sprite(SpriteID.Staticons.THIEVING), "PICKPOCKET_STUN"),

	// Teleports
	HOME_TELEPORT(Category.TELEPORT, TimerCategory.CATEGORY_3, "Home Teleport", Icon.sprite(SpriteID.Magicon2.LUMBRIDGE_HOME_TELEPORT), "HOME_TELEPORT"),
	MINIGAME_TELEPORT(Category.TELEPORT, TimerCategory.CATEGORY_3, "Minigame Teleport", Icon.sprite(SpriteID.SideiconsInterface.MINIGAMES), "MINIGAME_TELEPORT"),

	/**
	 * Hunter meats (cooked kebbits/antelope) heal part of their HP immediately and the rest 8
	 * ticks later. RuneLite's core Timers & Buffs plugin doesn't track this at all - only the
	 * separate "Consumable Cooldowns" hub plugin does, and even that one doesn't expose a
	 * readable end time (its timer extends raw InfoBox, not Timer, with no public tick-count
	 * getter). Tracked manually instead (see UltimateTimersPlugin's MenuOptionClicked listener).
	 */
	DELAYED_HEAL(Category.DELAYED_HEAL, TimerCategory.CATEGORY_4, "Delayed Heal", Icon.item(ItemID.ANTELOPEMOON_COOKED)),

	/**
	 * Kills remaining on the current slayer task, out of the task's original size. Both come
	 * straight from the same varps the core Slayer plugin itself reads (VarPlayerID.SLAYER_COUNT/
	 * SLAYER_COUNT_ORIGINAL) - no reimplementation of task tracking here. The per-task monster
	 * icon is a different story: Slayer's own name->icon mapping (the Task enum) is
	 * package-private, so instead of re-deriving that whole table ourselves, UltimateTimersPlugin
	 * grabs the icon already loaded into Slayer's own infobox (InfoBox.getImage(), matched by
	 * InfoBox.getName() == "SlayerPlugin_TaskCounter") - the same "read what's already computed"
	 * approach used for the Timers & Buffs integration. That requires the Slayer plugin's "Show
	 * infobox" setting to be on; this icon (the slayer gem) is only a fallback for when it's off.
	 */
	SLAYER_TASK(Category.SLAYER_TASK, TimerCategory.CATEGORY_5, "Slayer Task", Icon.item(ItemID.SLAYER_GEM)),

	/**
	 * One shared ring for every boss the core "Boss Timers" plugin tracks (Boss Timers' own
	 * respawn infobox - net.runelite.client.plugins.bosstimer.RespawnTimer - extends the public
	 * Timer class, so its already-computed getEndTime() can be read directly, same as the
	 * Timers & Buffs integration). Its getName() is the same fixed string for every boss (no
	 * per-boss distinction via name), so for now this is a single combined category rather than
	 * one ring per boss - only one boss's timer can display at a time.
	 */
	BOSS_RESPAWN(Category.BOSS_RESPAWN, TimerCategory.CATEGORY_4, "Boss Respawn", Icon.item(ItemID.SKULL),
		"BossTimersPlugin_RespawnTimer"),

	/**
	 * Time remaining before the client auto-logs-out from inactivity. Computed directly from the
	 * same public Client idle-tracking API RuneLite's own IdleNotifierPlugin uses internally
	 * (getIdleTimeout() minus whichever of getKeyboardIdleTicks()/getMouseIdleTicks() is lower),
	 * in 20ms "client ticks" (Constants.CLIENT_TICK_LENGTH) rather than 600ms game ticks - there's
	 * no existing InfoBox/Timer to read here since IdleNotifierPlugin only fires notifications,
	 * it doesn't display anything.
	 */
	AFK_LOGOUT(Category.AFK_LOGOUT, TimerCategory.CATEGORY_4, "AFK Logout", Icon.sprite(SpriteID.SideiconsInterface.LOGOUT));

	private final Category category;
	private final TimerCategory defaultCategory;
	private final String label;
	private final Icon icon;
	private final String gameTimerName;

	TrackedBuff(Category category, TimerCategory defaultCategory, String label, Icon icon, String gameTimerName)
	{
		this.category = category;
		this.defaultCategory = defaultCategory;
		this.label = label;
		this.icon = icon;
		this.gameTimerName = gameTimerName;
	}

	TrackedBuff(Category category, TimerCategory defaultCategory, String label, Icon icon)
	{
		this(category, defaultCategory, label, icon, null);
	}

	Category getCategory()
	{
		return category;
	}

	TimerCategory getDefaultCategory()
	{
		return defaultCategory;
	}

	String getLabel()
	{
		return label;
	}

	/** So Swing components (e.g. the side panel's timer lists) show the readable label by default. */
	@Override
	public String toString()
	{
		return label;
	}

	Icon getIcon()
	{
		return icon;
	}

	String getGameTimerName()
	{
		return gameTimerName;
	}

	boolean isManuallyTracked()
	{
		return gameTimerName == null;
	}

	/**
	 * True only for SLAYER_TASK: its "remaining"/"peak" are a kill count, not milliseconds, so
	 * it's drawn as a bare number rather than a time format, and its ring is pinned to a fixed
	 * value between kills rather than live-ticking down every render frame.
	 */
	boolean isKillCounter()
	{
		return this == SLAYER_TASK;
	}

	/**
	 * True only for AFK_LOGOUT: its endTime is computed directly from Client's idle-tracking
	 * API every tick, rather than looked up from a Timers & Buffs GameTimer or a manually ticked
	 * counter.
	 */
	boolean isAfkLogoutTimer()
	{
		return this == AFK_LOGOUT;
	}

	/**
	 * A user-managed group of timers that share one set of appearance/flash/linger settings
	 * (timers are dragged between them in the side panel). Users can add
	 * and delete categories freely. The original five keep their ids CATEGORY_1-5, so saved
	 * assignments and settings carry over. Added ones are CATEGORY_6, 7, ... (numbers are never
	 * reused). UNMAPPED always exists, can't be deleted, and is where a deleted category's
	 * timers go. Separate from whether a timer is shown at all - see isTimerEnabled in
	 * UltimateTimersPlugin.
	 */
	static final class TimerCategory
	{
		// Declared first: the constants below are created through it. One shared instance per id,
		// so categories can be compared with == like the enum they replaced.
		private static final Map<String, TimerCategory> INSTANCES = new ConcurrentHashMap<>();

		static final TimerCategory CATEGORY_1 = numbered(1);
		static final TimerCategory CATEGORY_2 = numbered(2);
		static final TimerCategory CATEGORY_3 = numbered(3);
		static final TimerCategory CATEGORY_4 = numbered(4);
		static final TimerCategory CATEGORY_5 = numbered(5);
		static final TimerCategory UNMAPPED = new TimerCategory("UNMAPPED", -1);

		static
		{
			INSTANCES.put(UNMAPPED.id, UNMAPPED);
		}

		/** The categories a fresh install (or anyone from before categories were editable) starts with. */
		static final List<TimerCategory> DEFAULTS =
			Collections.unmodifiableList(Arrays.asList(CATEGORY_1, CATEGORY_2, CATEGORY_3, CATEGORY_4, CATEGORY_5));

		private static final String NUMBERED_PREFIX = "CATEGORY_";

		private final String id;
		private final int number;

		private TimerCategory(String id, int number)
		{
			this.id = id;
			this.number = number;
		}

		static TimerCategory numbered(int number)
		{
			String id = NUMBERED_PREFIX + number;
			return INSTANCES.computeIfAbsent(id, k -> new TimerCategory(k, number));
		}

		/** Parses a stored id. Returns null for anything unrecognised. */
		static TimerCategory fromId(String id)
		{
			if (id == null)
			{
				return null;
			}
			if (UNMAPPED.id.equals(id))
			{
				return UNMAPPED;
			}
			if (id.startsWith(NUMBERED_PREFIX))
			{
				try
				{
					int number = Integer.parseInt(id.substring(NUMBERED_PREFIX.length()));
					return number > 0 ? numbered(number) : null;
				}
				catch (NumberFormatException e)
				{
					return null;
				}
			}
			return null;
		}

		/** Stable id used in config keys. Named like Enum.name() so existing call sites read the same. */
		String name()
		{
			return id;
		}

		/** 1, 2, 3, ... for numbered categories; -1 for UNMAPPED. */
		int getNumber()
		{
			return number;
		}

		boolean isUnmapped()
		{
			return number < 0;
		}

		@Override
		public String toString()
		{
			return id;
		}
	}

	enum Category
	{
		ANTIFIRE,
		STAMINA,
		DIVINE,
		ANTIPOISON,
		GOADING,
		PRAYER_REGEN,
		THRALL,
		FREEZE,
		NECROMANCY,
		OVERLOAD,
		MAGIC_IMBUE,
		IMBUED_HEART,
		PRAYER_ENHANCE,
		FARMERS_AFFINITY,
		MENAPHITE_REMEDY,
		SURGE_POTION,
		MOONLIGHT_POTION,
		RAID_SUPPLY,
		VENGEANCE,
		TELEBLOCK,
		HEAL_GROUP,
		CHARGE,
		SPELLBOOK_SWAP,
		CANNON,
		STAFF_OF_THE_DEAD,
		DRAGON_FIRE_SHIELD,
		SMOULDERING,
		BOSS_MECHANIC,
		PICKPOCKET_STUN,
		TELEPORT,
		DELAYED_HEAL,
		SLAYER_TASK,
		BOSS_RESPAWN,
		AFK_LOGOUT
	}

	static final class Icon
	{
		private final boolean sprite;
		private final int id;

		private Icon(boolean sprite, int id)
		{
			this.sprite = sprite;
			this.id = id;
		}

		static Icon item(int itemId)
		{
			return new Icon(false, itemId);
		}

		static Icon sprite(int spriteId)
		{
			return new Icon(true, spriteId);
		}

		boolean isSprite()
		{
			return sprite;
		}

		int getId()
		{
			return id;
		}
	}
}
