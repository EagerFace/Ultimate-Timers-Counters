package dev.keith.ultimatetimers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.FontType;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.infobox.Timer;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@PluginDescriptor(
	name = "Ultimate Timers & Counters",
	description = "Countdown timer overlays for every timer RuneLite's core Timers & Buffs plugin tracks "
		+ "(requires Timers & Buffs to be enabled), a slayer task kill counter with its own task icon for most "
		+ "monsters (works without the Slayer plugin's infobox; a few uncommon tasks fall back to it if shown), "
		+ "a boss respawn timer (requires the core \"Boss Timers\" plugin), and an AFK logout countdown. Drag a "
		+ "timer group to reposition it, drag one group onto another to merge them, or right-click a timer to "
		+ "detach it into its own group",
	tags = {"buff", "timer", "potion", "antifire", "divine", "stamina", "antipoison", "antivenom", "goading",
		"prayer", "thrall", "freeze", "necromancy", "overload", "vengeance", "teleblock", "slayer", "boss", "afk",
		"overlay"},
	// Required by RuneLite's Filepath utility (used for settings export/import).
	internalName = "ultimatetimers"
)
public class UltimateTimersPlugin extends Plugin
{
	static final String DEFAULT_GROUP = "default";

	private static final Color FALLBACK_COLOR = new Color(170, 170, 170);
	private static final String GROUP_ASSIGNMENT_CONFIG_GROUP = "ultimatetimersgroupassignment";
	private static final String GROUP_LAYOUT_CONFIG_GROUP = "ultimatetimersgrouplayout";
	private static final String ORDER_CONFIG_GROUP = "ultimatetimersorder";
	private static final String TIMER_CATEGORY_CONFIG_GROUP = "ultimatetimersbehaviorcategory";
	private static final String CATEGORY_NAME_CONFIG_GROUP = "ultimatetimerscategorynames";
	private static final String ENABLED_CONFIG_GROUP = "ultimatetimersenabled";
	private static final String CATEGORY_EXPANDED_CONFIG_GROUP = "ultimatetimerscategoryexpanded";
	/** Which categories exist ("ids", comma-separated, in display order) and the next number to hand out. */
	private static final String CATEGORY_LIST_CONFIG_GROUP = "ultimatetimerscategorylist";
	/** Each timer's own color, keyed by TrackedBuff name - see getTimerColor. */
	private static final String TIMER_COLOR_CONFIG_GROUP = "ultimatetimerstimercolors";
	private static final int FIRST_ADDED_CATEGORY_NUMBER = TrackedBuff.TimerCategory.DEFAULTS.size() + 1;
	private static final Color DEFAULT_OVERRIDE_COLOR = new Color(76, 76, 76);

	/**
	 * Divine potions that combine multiple effects also set the varbits for each individual
	 * effect they contain. Without suppression, drinking e.g. divine super combat would show
	 * four overlapping rings (itself plus attack/strength/defence) that all drain in lockstep.
	 */
	private static final Map<TrackedBuff, Set<TrackedBuff>> SUPPRESSED_BY = buildSuppressionMap();

	/**
	 * Buffs that are really just two phases of "the same slot" to the player (one potion
	 * upgrades/degrades into the other) always share one group/position, so detaching or
	 * regrouping either one carries the other along with it - the ring that takes over when one
	 * expires stays in the exact same spot instead of defaulting back to the shared group.
	 */
	private static final Map<TrackedBuff, Set<TrackedBuff>> SYNCED_BUFFS = buildSyncedBuffsMap();

	/**
	 * Cooked kebbit/antelope meats ("hunter meats") heal part of their HP immediately and the
	 * rest 8 ticks later - confirmed against the "Consumable Cooldowns" hub plugin's source,
	 * which hardcodes this same value for its own delayed-heal timer.
	 */
	private static final int DELAYED_HEAL_DURATION_TICKS = 8;
	private static final Set<Integer> DELAYED_HEAL_ITEM_IDS = Set.of(
		ItemID.WILDKEBBIT_COOKED, ItemID.LARUPIA_COOKED, ItemID.BARBKEBBIT_COOKED,
		ItemID.GRAAHK_COOKED, ItemID.KYATT_COOKED, ItemID.FENNECFOX_COOKED,
		ItemID.ANTELOPESUN_COOKED, ItemID.DASHINGKEBBIT_COOKED, ItemID.ANTELOPEMOON_COOKED);

	/**
	 * InfoBox.getName() defaults to "<plugin class>_<InfoBox subclass>" - the core Slayer
	 * plugin's task-progress infobox is a TaskCounter (package-private, extends the public
	 * Counter), so this is the one string that identifies it without needing access to either
	 * package-private class. Only used as a bonus icon source for tasks SlayerTaskIcons doesn't
	 * cover - the kill count and most task icons work without this infobox being visible at all.
	 */
	private static final String SLAYER_TASK_INFOBOX_NAME = "SlayerPlugin_TaskCounter";

	@Inject
	private UltimateTimersConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Client client;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Gson gson;

	private NavigationButton navButton;
	private UltimateTimersPanel panel;

	private final Map<TrackedBuff, Long> peakMillis = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, BufferedImage> sampledIcons = new EnumMap<>(TrackedBuff.class);
	private final Map<Integer, BufferedImage> slayerTaskIconCache = new HashMap<>();
	private final Map<TrackedBuff, Long> expiredAtMillis = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, Integer> manualTicks = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, String> buffGroup = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, Integer> buffOrder = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, TrackedBuff.TimerCategory> buffTimerCategory = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, Boolean> buffEnabled = new EnumMap<>(TrackedBuff.class);
	/** User categories in display order (not including UNMAPPED). Replaced, never mutated - read from the client thread. */
	private volatile List<TrackedBuff.TimerCategory> categories = TrackedBuff.TimerCategory.DEFAULTS;
	private final Map<String, UltimateTimersGroupOverlay> groupOverlays = new HashMap<>();
	private final Map<String, List<ActiveBuffDisplay>> displaysByGroup = new HashMap<>();

	@Override
	protected void startUp()
	{
		runMigrations();
		loadState();
		requestIcons();

		panel = new UltimateTimersPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Ultimate Timers & Counters")
			.icon(buildNavIcon())
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	/** Identifies an exported settings file, and the layout version of its contents. */
	private static final String SETTINGS_FILE_FORMAT = "ultimate-timers-settings";
	private static final int SETTINGS_FILE_VERSION = 1;
	/** Each timer group overlay's position/size is saved by RuneLite under this key prefix in the "runelite" group. */
	private static final String OVERLAY_GROUP = "runelite";
	private static final String OVERLAY_KEY_PREFIX = "UltimateTimersGroup_";

	/**
	 * Everything this plugin saves, as group -> key -> raw value: every "ultimatetimers*" config
	 * group (settings, categories, timer assignments, colors, ...) plus the timer groups'
	 * on-screen positions. Not the plugin's own on/off switch.
	 */
	private Map<String, Map<String, String>> currentSettings()
	{
		Map<String, Map<String, String>> settings = new java.util.TreeMap<>();
		for (String wholeKey : configManager.getConfigurationKeys(UltimateTimersConfig.GROUP))
		{
			int dot = wholeKey.indexOf('.');
			if (dot <= 0)
			{
				continue;
			}
			addSetting(settings, wholeKey.substring(0, dot), wholeKey.substring(dot + 1));
		}
		for (String wholeKey : configManager.getConfigurationKeys(OVERLAY_GROUP + "." + OVERLAY_KEY_PREFIX))
		{
			addSetting(settings, OVERLAY_GROUP, wholeKey.substring(OVERLAY_GROUP.length() + 1));
		}
		return settings;
	}

	private void addSetting(Map<String, Map<String, String>> settings, String group, String key)
	{
		if (!isOwnSetting(group, key))
		{
			return;
		}
		String value = configManager.getConfiguration(group, key);
		if (value != null)
		{
			settings.computeIfAbsent(group, g -> new java.util.TreeMap<>()).put(key, value);
		}
	}

	/** Only this plugin's own settings may be exported or imported - a shared file can't touch anything else. */
	private static boolean isOwnSetting(String group, String key)
	{
		if (group == null || key == null || key.isEmpty())
		{
			return false;
		}
		if (OVERLAY_GROUP.equals(group))
		{
			return key.startsWith(OVERLAY_KEY_PREFIX);
		}
		return group.startsWith(UltimateTimersConfig.GROUP) && group.chars().allMatch(Character::isLetterOrDigit);
	}

	/** All of this plugin's settings as a JSON document, for saving to a file or sharing. */
	String exportSettings()
	{
		JsonObject root = new JsonObject();
		root.addProperty("format", SETTINGS_FILE_FORMAT);
		root.addProperty("version", SETTINGS_FILE_VERSION);
		root.add("settings", gson.toJsonTree(currentSettings()));
		return gson.newBuilder().setPrettyPrinting().create().toJson(root);
	}

	/**
	 * Checks an exported settings document without changing anything. Returns how many settings
	 * it would import, or throws IllegalArgumentException with a message fit to show the user.
	 */
	int validateSettings(String json)
	{
		return parseSettings(gson, json).values().stream().mapToInt(Map::size).sum();
	}

	/**
	 * Replaces all of this plugin's settings with the ones in an exported document, then reloads
	 * (overlays, categories, timer assignments) without needing a restart. onReloaded runs on
	 * the Swing thread once that's done. Throws IllegalArgumentException if the document isn't
	 * a valid settings file, in which case nothing is changed.
	 */
	void importSettings(String json, Runnable onReloaded)
	{
		Map<String, Map<String, String>> imported = parseSettings(gson, json);
		clientThread.invokeLater(() ->
		{
			for (Map.Entry<String, Map<String, String>> group : currentSettings().entrySet())
			{
				for (String key : group.getValue().keySet())
				{
					configManager.unsetConfiguration(group.getKey(), key);
				}
			}
			for (Map.Entry<String, Map<String, String>> group : imported.entrySet())
			{
				for (Map.Entry<String, String> setting : group.getValue().entrySet())
				{
					configManager.setConfiguration(group.getKey(), setting.getKey(), setting.getValue());
				}
			}
			for (UltimateTimersGroupOverlay groupOverlay : groupOverlays.values())
			{
				overlayManager.remove(groupOverlay);
			}
			// A file exported by an older version may still use old keys.
			runMigrations();
			loadState();
			SwingUtilities.invokeLater(onReloaded);
		});
	}

	/** Package-private and static so it can be unit tested. Only this plugin's own settings survive. */
	static Map<String, Map<String, String>> parseSettings(Gson gson, String json)
	{
		JsonObject root;
		try
		{
			root = gson.fromJson(json, JsonObject.class);
		}
		catch (RuntimeException e)
		{
			throw new IllegalArgumentException("That file isn't a valid settings file (it couldn't be read as JSON).");
		}
		if (root == null || !root.has("format") || !SETTINGS_FILE_FORMAT.equals(stringOrNull(root.get("format"))))
		{
			throw new IllegalArgumentException("That file isn't an Ultimate Timers & Counters settings file.");
		}
		int version;
		try
		{
			version = root.get("version").getAsInt();
		}
		catch (RuntimeException e)
		{
			throw new IllegalArgumentException("That settings file is damaged (its version is missing).");
		}
		if (version > SETTINGS_FILE_VERSION)
		{
			throw new IllegalArgumentException("That settings file was made by a newer version of the plugin. "
				+ "Update the plugin and try again.");
		}
		JsonElement settingsElement = root.get("settings");
		if (settingsElement == null || !settingsElement.isJsonObject())
		{
			throw new IllegalArgumentException("That settings file doesn't contain any settings.");
		}

		Map<String, Map<String, String>> settings = new java.util.TreeMap<>();
		for (Map.Entry<String, JsonElement> group : settingsElement.getAsJsonObject().entrySet())
		{
			if (!group.getValue().isJsonObject())
			{
				continue;
			}
			for (Map.Entry<String, JsonElement> setting : group.getValue().getAsJsonObject().entrySet())
			{
				String value = stringOrNull(setting.getValue());
				// Anything that isn't this plugin's own setting is skipped.
				if (value != null && isOwnSetting(group.getKey(), setting.getKey()))
				{
					settings.computeIfAbsent(group.getKey(), g -> new java.util.TreeMap<>()).put(setting.getKey(), value);
				}
			}
		}
		if (settings.isEmpty())
		{
			throw new IllegalArgumentException("That settings file doesn't contain any settings.");
		}
		return settings;
	}

	private static String stringOrNull(JsonElement element)
	{
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	/** One-time config migrations from older versions. Each is a no-op once it has run. */
	private void runMigrations()
	{
		migrateProjectRename();
		migrateRingDiameter();
		migrateTeleportColor();
		migrateSplitCategories();
		migrateRingShape();
		migrateHiddenTimersToEnabledFlag();
		migrateOverrideColorToCategories();
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "sizePreset");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "lockAspectRatio");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "debugShowBuffs");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "debugShowDebuffs");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "debugShowCooldowns");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "debugShowOtherTimers");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "debugShowCounters");
	}

	/**
	 * Reads every saved per-timer assignment (group, order, category, on/off) and the category
	 * list, and creates one overlay per timer group. Used at startup and after importing settings.
	 */
	private void loadState()
	{
		peakMillis.clear();
		expiredAtMillis.clear();
		manualTicks.clear();
		displaysByGroup.clear();
		groupOverlays.clear();
		buffGroup.clear();
		buffOrder.clear();
		buffTimerCategory.clear();
		buffEnabled.clear();
		categories = loadCategories();

		Set<String> groupNames = new HashSet<>();
		groupNames.add(DEFAULT_GROUP);
		for (TrackedBuff buff : TrackedBuff.values())
		{
			String group = loadBuffGroup(buff);
			buffGroup.put(buff, group);
			groupNames.add(group);
			buffOrder.put(buff, loadBuffOrder(buff));
			buffTimerCategory.put(buff, loadTimerCategory(buff));
			buffEnabled.put(buff, loadTimerEnabled(buff));
		}

		// Reconcile synced pairs (e.g. antivenom/antipoison) in case either was independently
		// detached before they were linked, or their persisted groups otherwise drifted apart.
		Set<TrackedBuff> reconciled = EnumSet.noneOf(TrackedBuff.class);
		for (TrackedBuff buff : TrackedBuff.values())
		{
			if (reconciled.contains(buff))
			{
				continue;
			}

			Set<TrackedBuff> syncSet = SYNCED_BUFFS.get(buff);
			if (syncSet == null)
			{
				reconciled.add(buff);
				continue;
			}

			String canonicalGroup = DEFAULT_GROUP;
			for (TrackedBuff synced : syncSet)
			{
				String g = buffGroup.get(synced);
				if (!DEFAULT_GROUP.equals(g))
				{
					canonicalGroup = g;
					break;
				}
			}

			for (TrackedBuff synced : syncSet)
			{
				buffGroup.put(synced, canonicalGroup);
				persistBuffGroup(synced, canonicalGroup);
				groupNames.add(canonicalGroup);
				reconciled.add(synced);
			}
		}

		for (String groupName : groupNames)
		{
			ensureGroupOverlay(groupName);
		}
	}

	/**
	 * A small drawn-on-the-fly ring (matching the plugin's own overlay style) rather than a
	 * bundled image resource, so there's no icon asset to ship/maintain.
	 */
	private static BufferedImage buildNavIcon()
	{
		BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setComposite(AlphaComposite.Src);
		Ellipse2D ring = new Ellipse2D.Double(2, 2, 20, 20);
		g.setColor(new Color(60, 60, 60, 220));
		g.fill(ring);
		g.setColor(new Color(60, 180, 90));
		g.fill(new Arc2D.Double(2, 2, 20, 20, 90, 230, Arc2D.PIE));
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(ring);
		g.dispose();
		return icon;
	}

	/**
	 * The plugin/project was renamed from "Buff Rings" to "Ultimate Timers & Counters" - its
	 * config group, two per-buff bookkeeping groups (group assignment/order), the group-layout
	 * group, and each overlay's own persisted position/size (keyed off Overlay.getName(), which
	 * used to start with "BuffRingsGroup_") all used the old "buffrings"-based names. Carries over
	 * every key in each old group/prefix to its new counterpart the first time this version
	 * starts; each copy loop naturally only runs once since the old group/prefix is empty after.
	 */
	private void migrateProjectRename()
	{
		migrateConfigGroup("buffrings", UltimateTimersConfig.GROUP);
		migrateConfigGroup("buffringsgroupassignment", GROUP_ASSIGNMENT_CONFIG_GROUP);
		migrateConfigGroup("buffringsgrouplayout", GROUP_LAYOUT_CONFIG_GROUP);
		migrateConfigGroup("buffringsorder", ORDER_CONFIG_GROUP);
		migrateKeyPrefix("runelite", "BuffRingsGroup_", "UltimateTimersGroup_");

		// RuneLite's own "is this plugin enabled" flag is keyed by the plugin class's simple name,
		// lowercased - renaming the class means a stale "buffringsplugin" flag would otherwise
		// leave the renamed plugin disabled by default for anyone who had it enabled before.
		String wasEnabled = configManager.getConfiguration("runelite", "buffringsplugin");
		if (wasEnabled != null)
		{
			configManager.setConfiguration("runelite", "ultimatetimersplugin", wasEnabled);
			configManager.unsetConfiguration("runelite", "buffringsplugin");
		}
	}

	private void migrateConfigGroup(String oldGroup, String newGroup)
	{
		for (String wholeKey : configManager.getConfigurationKeys(oldGroup + "."))
		{
			String key = wholeKey.substring(oldGroup.length() + 1);
			String value = configManager.getConfiguration(oldGroup, key);
			if (value != null)
			{
				configManager.setConfiguration(newGroup, key, value);
			}
			configManager.unsetConfiguration(oldGroup, key);
		}
	}

	private void migrateKeyPrefix(String group, String oldPrefix, String newPrefix)
	{
		for (String wholeKey : configManager.getConfigurationKeys(group + "." + oldPrefix))
		{
			String rest = wholeKey.substring(group.length() + 1 + oldPrefix.length());
			String value = configManager.getConfiguration(group, oldPrefix + rest);
			if (value != null)
			{
				configManager.setConfiguration(group, newPrefix + rest, value);
			}
			configManager.unsetConfiguration(group, oldPrefix + rest);
		}
	}

	/**
	 * The single "ringDiameter" setting was replaced by independent "ringWidth"/"ringHeight"
	 * settings. Carry over anyone's existing saved diameter into both new keys the first time
	 * this version starts, then remove the old key so this only ever runs once.
	 */
	private void migrateRingDiameter()
	{
		String oldDiameter = configManager.getConfiguration(UltimateTimersConfig.GROUP, "ringDiameter");
		if (oldDiameter == null)
		{
			return;
		}

		if (configManager.getConfiguration(UltimateTimersConfig.GROUP, "ringWidth") == null)
		{
			configManager.setConfiguration(UltimateTimersConfig.GROUP, "ringWidth", oldDiameter);
		}
		if (configManager.getConfiguration(UltimateTimersConfig.GROUP, "ringHeight") == null)
		{
			configManager.setConfiguration(UltimateTimersConfig.GROUP, "ringHeight", oldDiameter);
		}
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "ringDiameter");
	}

	/**
	 * The single "teleportColor" setting was split into independent "homeTeleportColor" and
	 * "minigameTeleportColor" settings. Carry over anyone's existing saved color into both new
	 * keys the first time this version starts, then remove the old key so this only ever runs
	 * once.
	 */
	private void migrateTeleportColor()
	{
		String oldColor = configManager.getConfiguration(UltimateTimersConfig.GROUP, "teleportColor");
		if (oldColor == null)
		{
			return;
		}

		if (configManager.getConfiguration(UltimateTimersConfig.GROUP, "homeTeleportColor") == null)
		{
			configManager.setConfiguration(UltimateTimersConfig.GROUP, "homeTeleportColor", oldColor);
		}
		if (configManager.getConfiguration(UltimateTimersConfig.GROUP, "minigameTeleportColor") == null)
		{
			configManager.setConfiguration(UltimateTimersConfig.GROUP, "minigameTeleportColor", oldColor);
		}
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "teleportColor");
	}

	/**
	 * The "ringShape" CIRCLE/SQUARE enum was replaced by a numeric "cornerRadius" (0-100) so shapes
	 * in between a square and a circle are possible. Carry over anyone's existing saved shape into
	 * the matching radius extreme the first time this version starts, then remove the old key so
	 * this only ever runs once.
	 */
	private void migrateRingShape()
	{
		String oldShape = configManager.getConfiguration(UltimateTimersConfig.GROUP, "ringShape");
		if (oldShape == null)
		{
			return;
		}

		if (configManager.getConfiguration(UltimateTimersConfig.GROUP, "cornerRadius") == null)
		{
			configManager.setConfiguration(UltimateTimersConfig.GROUP, "cornerRadius",
				"SQUARE".equals(oldShape) ? "0" : "100");
		}
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "ringShape");
	}

	/**
	 * "Override all colors"/"Override color" used to be one global setting covering every timer.
	 * They're per-category now: CATEGORY_1 reads the original bare keys (so it keeps its value
	 * with no migration), and this copies an existing saved global value into Categories 2-5 so
	 * nobody's timers change color on update. It only copies into a category with no value of
	 * its own yet, so it's a no-op after the first run.
	 */
	private void migrateOverrideColorToCategories()
	{
		String enabled = configManager.getConfiguration(UltimateTimersConfig.GROUP, "overrideColors");
		String color = configManager.getConfiguration(UltimateTimersConfig.GROUP, "overrideColor");
		for (TrackedBuff.TimerCategory category : TrackedBuff.TimerCategory.DEFAULTS)
		{
			if (category == TrackedBuff.TimerCategory.CATEGORY_1)
			{
				continue;
			}
			copyIfUnset(categoryKey(category, "overrideColors"), enabled);
			copyIfUnset(categoryKey(category, "overrideColor"), color);
		}
	}

	private void copyIfUnset(String key, String value)
	{
		if (value != null && configManager.getConfiguration(UltimateTimersConfig.GROUP, key) == null)
		{
			configManager.setConfiguration(UltimateTimersConfig.GROUP, key, value);
		}
	}

	/**
	 * The "showCombatEffect"/"combatEffectColor" and "showEquipment"/"equipmentColor" settings
	 * were each split into one toggle/color per timer (and "showBossMechanic"/"bossMechanicColor"
	 * lost pickpocket stun to its own "showPickpocketStun"/"pickpocketStunColor"). Carry over
	 * anyone's existing saved toggle/color into every new key it was split into the first time
	 * this version starts, then remove the old keys so this only ever runs once.
	 */
	private void migrateSplitCategories()
	{
		// "showCombatEffect"/"combatEffectColor" were removed entirely - every member got its
		// own toggle/color, so the old keys are no longer read anywhere and can be unset.
		copyToUnsetKeys("showCombatEffect",
			"showVengeance", "showTeleblock", "showHealGroup", "showCharge", "showSpellbookSwap");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "showCombatEffect");
		copyToUnsetKeys("combatEffectColor",
			"vengeanceColor", "teleblockColor", "healGroupColor", "chargeColor", "spellbookSwapColor");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "combatEffectColor");

		// "showEquipment"/"equipmentColor" were likewise removed entirely.
		copyToUnsetKeys("showEquipment",
			"showCannon", "showStaffOfTheDead", "showDragonfireShield", "showSmouldering");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "showEquipment");
		copyToUnsetKeys("equipmentColor",
			"cannonColor", "staffOfTheDeadColor", "dragonfireShieldColor", "smoulderingColor");
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, "equipmentColor");

		// "showBossMechanic"/"bossMechanicColor" are still live settings (Abyssal Sire/Scurrius/
		// GWA stayed under them) - only pickpocket stun was carved out, so just seed its new
		// keys from the old ones without touching the old keys themselves.
		copyToUnsetKeys("showBossMechanic", "showPickpocketStun");
		copyToUnsetKeys("bossMechanicColor", "pickpocketStunColor");
	}

	private void copyToUnsetKeys(String sourceKey, String... destKeys)
	{
		String sourceValue = configManager.getConfiguration(UltimateTimersConfig.GROUP, sourceKey);
		if (sourceValue == null)
		{
			return;
		}

		for (String destKey : destKeys)
		{
			if (configManager.getConfiguration(UltimateTimersConfig.GROUP, destKey) == null)
			{
				configManager.setConfiguration(UltimateTimersConfig.GROUP, destKey, sourceValue);
			}
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		UltimateTimersPanel closingPanel = panel;
		panel = null;
		if (closingPanel != null)
		{
			SwingUtilities.invokeLater(closingPanel::closeSettingsDialogs);
		}

		for (UltimateTimersGroupOverlay groupOverlay : groupOverlays.values())
		{
			overlayManager.remove(groupOverlay);
		}
		groupOverlays.clear();
		buffGroup.clear();
		buffTimerCategory.clear();
		buffEnabled.clear();
		displaysByGroup.clear();
		peakMillis.clear();
		expiredAtMillis.clear();
		manualTicks.clear();
		sampledIcons.clear();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if ("Eat".equals(event.getMenuOption()) && DELAYED_HEAL_ITEM_IDS.contains(event.getItemId()))
		{
			manualTicks.put(TrackedBuff.DELAYED_HEAL, DELAYED_HEAL_DURATION_TICKS);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Instant now = Instant.now();

		long debugPeakMillis = config.debugPreviewTotalSeconds() * 1000L;
		long debugRemainingMillis = config.debugPreviewRemainingSeconds() * 1000L;

		Map<String, Timer> gameTimersByName = new HashMap<>();
		Map<TrackedBuff, BufferedImage> dynamicIcons = new EnumMap<>(TrackedBuff.class);
		for (InfoBox infoBox : infoBoxManager.getInfoBoxes())
		{
			if (infoBox instanceof Timer)
			{
				gameTimersByName.put(infoBox.getName(), (Timer) infoBox);
				if (TrackedBuff.BOSS_RESPAWN.getGameTimerName().equals(infoBox.getName()))
				{
					dynamicIcons.put(TrackedBuff.BOSS_RESPAWN, infoBox.getImage());
				}
			}
			else if (SLAYER_TASK_INFOBOX_NAME.equals(infoBox.getName()))
			{
				dynamicIcons.put(TrackedBuff.SLAYER_TASK, infoBox.getImage());
			}
		}

		// Independent task icon, keyed off the same varp the core Slayer plugin reads for the
		// current task's identity (VarPlayerID.SLAYER_TARGET), so the ring's icon stays correct
		// even if the Slayer plugin's own infobox is hidden. Takes priority over the infobox-borrowed
		// icon above when a mapping exists; unmapped tasks keep whatever the infobox provided (if visible).
		Integer slayerTaskIconId = SlayerTaskIcons.ICON_BY_ROW.get(client.getVarpValue(VarPlayerID.SLAYER_TARGET));
		if (slayerTaskIconId != null)
		{
			BufferedImage cachedIcon = slayerTaskIconCache.get(slayerTaskIconId);
			if (cachedIcon != null)
			{
				dynamicIcons.put(TrackedBuff.SLAYER_TASK, cachedIcon);
			}
			else
			{
				AsyncBufferedImage slayerTaskIcon = itemManager.getImage(slayerTaskIconId);
				slayerTaskIcon.onLoaded(() -> slayerTaskIconCache.put(slayerTaskIconId, slayerTaskIcon));
			}
		}

		Map<TrackedBuff, Instant> endTimeByBuff = new EnumMap<>(TrackedBuff.class);
		Map<TrackedBuff, Long> currentMillis = new EnumMap<>(TrackedBuff.class);
		for (TrackedBuff buff : TrackedBuff.values())
		{
			if (buff.isKillCounter())
			{
				// Same varp the core Slayer plugin itself reads for the current task's
				// kills-remaining. Scaled by 1000 so it plugs into the existing millis-based
				// peak-tracking/flash-threshold/fraction math unchanged, with "seconds" recovering
				// the actual kill count (e.g. a flash threshold of 10 means "10 or fewer kills left").
				long remainingKills = Math.max(0, client.getVarpValue(VarPlayerID.SLAYER_COUNT));
				endTimeByBuff.put(buff, null);
				currentMillis.put(buff, remainingKills * 1000L);
				continue;
			}

			if (buff.isAfkLogoutTimer())
			{
				// Same public idle-tracking API RuneLite's own IdleNotifierPlugin uses, in 20ms
				// "client ticks" rather than 600ms game ticks - converted to a real endTime so it
				// plugs into the standard live-countdown rendering unchanged.
				int idleTimeout = client.getIdleTimeout();
				int idleClientTicks = Math.min(client.getKeyboardIdleTicks(), client.getMouseIdleTicks());
				int remainingClientTicks = idleTimeout > 0 ? Math.max(0, idleTimeout - idleClientTicks) : 0;
				Instant afkEndTime = remainingClientTicks > 0 ? now.plusMillis(remainingClientTicks * 20L) : null;
				endTimeByBuff.put(buff, afkEndTime);
				currentMillis.put(buff,
					afkEndTime == null ? 0 : Math.max(0, Duration.between(now, afkEndTime).toMillis()));
				continue;
			}

			Instant endTime;
			if (buff.isManuallyTracked())
			{
				int ticksLeft = manualTicks.getOrDefault(buff, 0);
				endTime = ticksLeft > 0 ? now.plusMillis(ticksLeft * 600L) : null;
			}
			else
			{
				Timer timer = gameTimersByName.get(buff.getGameTimerName());
				endTime = timer == null ? null : timer.getEndTime();
			}
			endTimeByBuff.put(buff, endTime);
			currentMillis.put(buff, endTime == null ? 0 : Math.max(0, Duration.between(now, endTime).toMillis()));
		}

		Set<TrackedBuff> suppressedThisTick = EnumSet.noneOf(TrackedBuff.class);
		for (Map.Entry<TrackedBuff, Set<TrackedBuff>> entry : SUPPRESSED_BY.entrySet())
		{
			if (currentMillis.get(entry.getKey()) > 0)
			{
				for (TrackedBuff suppressed : entry.getValue())
				{
					currentMillis.put(suppressed, 0L);
					endTimeByBuff.put(suppressed, null);
					suppressedThisTick.add(suppressed);
				}
			}
		}

		long nowMillis = System.currentTimeMillis();

		Map<String, List<ActiveBuffDisplay>> newDisplaysByGroup = new HashMap<>();

		for (TrackedBuff buff : TrackedBuff.values())
		{
			// Debug force-show bypasses real tracking/suppression/linger for every timer, but
			// still respects that timer's own enabled checkbox - it's meant to preview/arrange
			// the timers you actually use, not every timer that exists.
			// Unmapped timers are never shown, whatever their own checkbox says (it's kept, so moving
			// one back into a real category restores it).
			boolean enabled = isTimerEnabled(buff) && !getTimerCategory(buff).isUnmapped();
			if (isForceShowEnabled(getTimerCategory(buff)) && enabled)
			{
				addDisplay(newDisplaysByGroup, buff,
					new ActiveBuffDisplay(buff, debugRemainingMillis, debugPeakMillis, colorFor(buff),
						iconFor(buff, dynamicIcons)));
				continue;
			}

			long remaining = currentMillis.get(buff);

			if (remaining > 0 && enabled)
			{
				expiredAtMillis.remove(buff);

				Long peak = peakMillis.get(buff);
				if (peak == null || remaining > peak)
				{
					peak = remaining;
					peakMillis.put(buff, peak);
				}

				ActiveBuffDisplay display = buff.isKillCounter()
					? new ActiveBuffDisplay(buff, remaining, peak, colorFor(buff), iconFor(buff, dynamicIcons))
					: new ActiveBuffDisplay(buff, endTimeByBuff.get(buff), peak, colorFor(buff), iconFor(buff, dynamicIcons));
				addDisplay(newDisplaysByGroup, buff, display);
				continue;
			}

			// Not currently active (expired, cured, suppressed, or its category is disabled).
			boolean wasActive = peakMillis.containsKey(buff);
			Long peak = peakMillis.get(buff);
			peakMillis.remove(buff);

			// A buff suppressed by another (e.g. antipoison superseded by antivenom, or a divine
			// combo potion's sub-effects) didn't naturally run out - it was displaced. Disappear
			// immediately rather than lingering on a countdown that never actually hit zero.
			if (!enabled || !isLingerEnabled(getTimerCategory(buff)) || suppressedThisTick.contains(buff))
			{
				expiredAtMillis.remove(buff);
				continue;
			}

			if (wasActive)
			{
				expiredAtMillis.putIfAbsent(buff, nowMillis);
			}

			Long expiredAt = expiredAtMillis.get(buff);
			if (expiredAt == null)
			{
				continue;
			}

			if (nowMillis - expiredAt >= lingerMillisFor(getTimerCategory(buff)))
			{
				expiredAtMillis.remove(buff);
				continue;
			}

			// Frozen at 0 remaining for the configured linger window, then disappears.
			long lingerPeak = peak == null ? 0 : peak;
			ActiveBuffDisplay lingerDisplay = buff.isKillCounter()
				? new ActiveBuffDisplay(buff, 0L, lingerPeak, colorFor(buff), iconFor(buff, dynamicIcons))
				: new ActiveBuffDisplay(buff, now, lingerPeak, colorFor(buff), iconFor(buff, dynamicIcons));
			addDisplay(newDisplaysByGroup, buff, lingerDisplay);
		}

		// Sort each group by the persisted order rank (defaulting to enum declaration order),
		// not by whatever order buffs happened to become active in this tick - that's what lets
		// a reordered-but-currently-inactive timer reappear in its remembered slot later.
		for (List<ActiveBuffDisplay> displays : newDisplaysByGroup.values())
		{
			displays.sort(Comparator.comparingInt(d -> buffOrder.getOrDefault(d.getBuff(), d.getBuff().ordinal())));
		}

		displaysByGroup.clear();
		displaysByGroup.putAll(newDisplaysByGroup);

		for (Map.Entry<TrackedBuff, Integer> entry : manualTicks.entrySet())
		{
			if (entry.getValue() > 0)
			{
				entry.setValue(entry.getValue() - 1);
			}
		}
	}

	private void addDisplay(Map<String, List<ActiveBuffDisplay>> target, TrackedBuff buff, ActiveBuffDisplay display)
	{
		String group = buffGroup.getOrDefault(buff, DEFAULT_GROUP);
		target.computeIfAbsent(group, g -> new ArrayList<>()).add(display);
	}

	List<ActiveBuffDisplay> getActiveDisplaysForGroup(String groupName)
	{
		return displaysByGroup.getOrDefault(groupName, Collections.emptyList());
	}

	RingLayout getGroupLayout(String groupName)
	{
		String stored = configManager.getConfiguration(GROUP_LAYOUT_CONFIG_GROUP, groupName);
		if (stored != null)
		{
			try
			{
				return RingLayout.valueOf(stored);
			}
			catch (IllegalArgumentException e)
			{
				// fall through to default
			}
		}
		return RingLayout.VERTICAL;
	}

	void flipGroup(String groupName)
	{
		RingLayout next = getGroupLayout(groupName) == RingLayout.VERTICAL ? RingLayout.HORIZONTAL : RingLayout.VERTICAL;
		configManager.setConfiguration(GROUP_LAYOUT_CONFIG_GROUP, groupName, next.name());
	}

	/**
	 * Moves a single buff out of whatever group it's currently in and into a brand-new group of
	 * its own, mirroring InfoBoxManager's "Detach" action for a real infobox. Any buff synced
	 * with it (see SYNCED_BUFFS) moves along with it, so the pair keeps sharing one position.
	 */
	void detachBuff(TrackedBuff buff)
	{
		String newGroup = "detached_" + buff.name() + "_" + System.currentTimeMillis();
		for (TrackedBuff synced : SYNCED_BUFFS.getOrDefault(buff, EnumSet.of(buff)))
		{
			buffGroup.put(synced, newGroup);
			persistBuffGroup(synced, newGroup);
		}
		ensureGroupOverlay(newGroup);
	}

	/**
	 * Moves every buff currently in sourceGroup into destGroup, mirroring InfoBoxManager's
	 * drag-to-merge action.
	 */
	void mergeGroups(String sourceGroup, String destGroup)
	{
		if (sourceGroup.equals(destGroup))
		{
			return;
		}

		for (Map.Entry<TrackedBuff, String> entry : buffGroup.entrySet())
		{
			if (entry.getValue().equals(sourceGroup))
			{
				entry.setValue(destGroup);
				persistBuffGroup(entry.getKey(), destGroup);
			}
		}

		if (!sourceGroup.equals(DEFAULT_GROUP))
		{
			UltimateTimersGroupOverlay sourceOverlay = groupOverlays.remove(sourceGroup);
			if (sourceOverlay != null)
			{
				overlayManager.remove(sourceOverlay);
			}
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (!(event.getOverlay() instanceof UltimateTimersGroupOverlay))
		{
			return;
		}

		UltimateTimersGroupOverlay groupOverlay = (UltimateTimersGroupOverlay) event.getOverlay();
		String option = event.getEntry().getOption();

		if ("Flip".equals(option))
		{
			flipGroup(groupOverlay.getGroupName());
		}
		else if ("Detach".equals(option))
		{
			TrackedBuff buff = findBuffByLabel(event.getEntry().getTarget());
			if (buff != null)
			{
				detachBuff(buff);
			}
		}
		else if ("Move Up".equals(option) || "Move Down".equals(option))
		{
			TrackedBuff buff = findBuffByLabel(event.getEntry().getTarget());
			if (buff != null)
			{
				moveBuff(buff, "Move Up".equals(option) ? -1 : 1);
			}
		}
	}

	/**
	 * Don't use groupOverlay.getHoveredBuff() for this - it's continuously overwritten by
	 * render() every frame, including while the context menu itself is open, so by the time a
	 * click is processed the mouse has usually moved onto the popup menu and the field has
	 * already been reset to null. The entry's own target string was baked in at the moment the
	 * menu was built (when the hover was still correct), so match against that instead.
	 */
	private TrackedBuff findBuffByLabel(String label)
	{
		for (TrackedBuff buff : TrackedBuff.values())
		{
			if (buff.getLabel().equals(label))
			{
				return buff;
			}
		}
		return null;
	}

	private String loadBuffGroup(TrackedBuff buff)
	{
		String group = configManager.getConfiguration(GROUP_ASSIGNMENT_CONFIG_GROUP, buff.name());
		return (group == null || group.isEmpty()) ? DEFAULT_GROUP : group;
	}

	private void persistBuffGroup(TrackedBuff buff, String group)
	{
		if (DEFAULT_GROUP.equals(group))
		{
			configManager.unsetConfiguration(GROUP_ASSIGNMENT_CONFIG_GROUP, buff.name());
		}
		else
		{
			configManager.setConfiguration(GROUP_ASSIGNMENT_CONFIG_GROUP, buff.name(), group);
		}
	}

	TrackedBuff.TimerCategory getTimerCategory(TrackedBuff buff)
	{
		return buffTimerCategory.get(buff);
	}

	/** Called by the side panel when a timer is dropped into a different category/Disabled list. */
	void setTimerCategory(TrackedBuff buff, TrackedBuff.TimerCategory category)
	{
		buffTimerCategory.put(buff, category);
		persistTimerCategory(buff, category);
	}

	/** Whether this timer is shown at all - its own checkbox in the side panel, independent of
	 * which flash/linger category it's dragged into. */
	boolean isTimerEnabled(TrackedBuff buff)
	{
		return buffEnabled.get(buff);
	}

	void setTimerEnabled(TrackedBuff buff, boolean enabled)
	{
		buffEnabled.put(buff, enabled);
		persistTimerEnabled(buff, enabled);
	}

	private boolean loadTimerEnabled(TrackedBuff buff)
	{
		String stored = configManager.getConfiguration(ENABLED_CONFIG_GROUP, buff.name());
		return stored == null || Boolean.parseBoolean(stored);
	}

	private void persistTimerEnabled(TrackedBuff buff, boolean enabled)
	{
		if (enabled)
		{
			configManager.unsetConfiguration(ENABLED_CONFIG_GROUP, buff.name());
		}
		else
		{
			configManager.setConfiguration(ENABLED_CONFIG_GROUP, buff.name(), false);
		}
	}

	/**
	 * The side panel's editable header for each category - defaults to "Category N" (or
	 * "Disabled"). Purely cosmetic: the underlying "Advanced Behaviors (Category N)" config
	 * section titles can't be renamed at runtime (RuneLite config section names are fixed
	 * annotation metadata), so a rename only changes what the panel and tooltips call it.
	 */
	String getCategoryName(TrackedBuff.TimerCategory category)
	{
		String stored = configManager.getConfiguration(CATEGORY_NAME_CONFIG_GROUP, category.name());
		return (stored == null || stored.isEmpty()) ? defaultCategoryName(category) : stored;
	}

	void setCategoryName(TrackedBuff.TimerCategory category, String name)
	{
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty() || trimmed.equals(defaultCategoryName(category)))
		{
			configManager.unsetConfiguration(CATEGORY_NAME_CONFIG_GROUP, category.name());
		}
		else
		{
			configManager.setConfiguration(CATEGORY_NAME_CONFIG_GROUP, category.name(), trimmed);
		}
	}

	/** Whether the side panel shows this category's timer list. Defaults to collapsed. */
	boolean isCategoryExpanded(TrackedBuff.TimerCategory category)
	{
		return Boolean.parseBoolean(configManager.getConfiguration(CATEGORY_EXPANDED_CONFIG_GROUP, category.name()));
	}

	void setCategoryExpanded(TrackedBuff.TimerCategory category, boolean expanded)
	{
		if (expanded)
		{
			configManager.setConfiguration(CATEGORY_EXPANDED_CONFIG_GROUP, category.name(), true);
		}
		else
		{
			configManager.unsetConfiguration(CATEGORY_EXPANDED_CONFIG_GROUP, category.name());
		}
	}

	private static String defaultCategoryName(TrackedBuff.TimerCategory category)
	{
		return category.isUnmapped() ? "Unmapped" : "Category " + category.getNumber();
	}

	/**
	 * Every per-category setting stored through categoryKey() that "Apply to Other Categories"
	 * copies. These all share the same default whatever the category, so the raw saved value
	 * (or its absence) can be copied directly. When adding a new per-category setting, add its
	 * key here too. Deliberately left out: the debug toggles (debugForceShowAll,
	 * debugForceFlashing), which are temporary previews rather than settings.
	 */
	private static final List<String> COPYABLE_LAYOUT_KEYS = Collections.unmodifiableList(java.util.Arrays.asList(
		"ringWidth", "ringHeight", "cornerRadius", "groupPadding",
		"ringBorderThickness", "borderColor",
		"backgroundColor", "fillDirection", "colorStyle", "overrideColors", "overrideColor",
		"percentColorFull", "percentColorHalf", "percentColorEmpty",
		"showTimeText", "textPosition", "textOffset",
		"showDescription", "descriptionPosition", "descriptionOffset",
		"showIcon", "iconPosition", "iconOffset", "iconSize",
		"textFont", "textColor", "textBorderEnabled", "textBorderColor", "textShadowEnabled",
		"enableTooltips",
		"fixedMaximumEnabled", "fixedMaximumSeconds"));

	/**
	 * Copies all of one category's settings (everything on its settings tabs) onto another.
	 * Doesn't touch the target's name, its timers, or its debug toggles.
	 */
	void copyCategorySettings(TrackedBuff.TimerCategory from, TrackedBuff.TimerCategory to)
	{
		if (from == to || from.isUnmapped() || to.isUnmapped())
		{
			return;
		}
		for (String baseKey : COPYABLE_LAYOUT_KEYS)
		{
			String value = configManager.getConfiguration(UltimateTimersConfig.GROUP, categoryKey(from, baseKey));
			if (value == null)
			{
				configManager.unsetConfiguration(UltimateTimersConfig.GROUP, categoryKey(to, baseKey));
			}
			else
			{
				configManager.setConfiguration(UltimateTimersConfig.GROUP, categoryKey(to, baseKey), value);
			}
		}
		// Flash/linger defaults differ between the original five categories, so copy the
		// effective values rather than the raw saved ones.
		setFlashEnabled(to, isFlashEnabled(from));
		setFlashThresholdSeconds(to, getFlashThresholdSeconds(from));
		setFlashColor(to, getFlashColor(from));
		setFlashForeground(to, isFlashForeground(from));
		setFlashBackground(to, isFlashBackground(from));
		setFlashBorder(to, isFlashBorder(from));
		setFlashText(to, isFlashText(from));
		setLingerEnabled(to, isLingerEnabled(from));
		setLingerSeconds(to, getLingerSecondsSetting(from));
	}

	/**
	 * Puts every setting on a category's settings tabs (and its debug toggles) back to the
	 * default. Its name and timers are left alone.
	 */
	void resetCategorySettings(TrackedBuff.TimerCategory category)
	{
		if (category.isUnmapped())
		{
			return;
		}
		for (String baseKey : COPYABLE_LAYOUT_KEYS)
		{
			configManager.unsetConfiguration(UltimateTimersConfig.GROUP, categoryKey(category, baseKey));
		}
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, categoryKey(category, "debugForceShowAll"));
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, categoryKey(category, "debugForceFlashing"));
		CategorySettingKeys keys = settingKeys(category);
		for (String key : new String[]{keys.enableFlash, keys.flashThresholdSeconds, keys.flashColor,
			keys.flashForeground, keys.flashBackground, keys.flashBorder, keys.flashText, keys.enableLinger,
			keys.lingerSeconds})
		{
			configManager.unsetConfiguration(UltimateTimersConfig.GROUP, key);
		}
	}

	/**
	 * "Reset Default Categories": brings back the original five categories (re-creating any that
	 * were deleted, at the top in their usual order) with default names and settings, and moves
	 * every timer back to its default category. Categories the user added are kept, with their
	 * settings, but end up empty. Timer on/off checkboxes and timer colors aren't changed.
	 */
	void resetDefaultCategories()
	{
		List<TrackedBuff.TimerCategory> updated = new ArrayList<>(TrackedBuff.TimerCategory.DEFAULTS);
		for (TrackedBuff.TimerCategory category : categories)
		{
			if (!updated.contains(category))
			{
				updated.add(category);
			}
		}
		saveCategories(updated);
		for (TrackedBuff.TimerCategory category : TrackedBuff.TimerCategory.DEFAULTS)
		{
			resetCategorySettings(category);
			configManager.unsetConfiguration(CATEGORY_NAME_CONFIG_GROUP, category.name());
		}
		for (TrackedBuff buff : TrackedBuff.values())
		{
			setTimerCategory(buff, buff.getDefaultCategory());
		}
	}

	/** User categories in display order. UNMAPPED isn't included - it always exists and has no settings. */
	List<TrackedBuff.TimerCategory> getCategories()
	{
		return categories;
	}

	/** Adds a new category at the end, with default settings. Its number is never reused after deletion. */
	TrackedBuff.TimerCategory addCategory()
	{
		int number = nextCategoryNumber();
		configManager.setConfiguration(CATEGORY_LIST_CONFIG_GROUP, "nextNumber", number + 1);
		TrackedBuff.TimerCategory category = TrackedBuff.TimerCategory.numbered(number);
		List<TrackedBuff.TimerCategory> updated = new ArrayList<>(categories);
		updated.add(category);
		saveCategories(updated);
		return category;
	}

	/** Deletes a category: its timers move to UNMAPPED (hidden) and its saved settings are removed. */
	void deleteCategory(TrackedBuff.TimerCategory category)
	{
		if (category.isUnmapped() || !categories.contains(category))
		{
			return;
		}
		for (TrackedBuff buff : TrackedBuff.values())
		{
			if (buffTimerCategory.get(buff) == category)
			{
				setTimerCategory(buff, TrackedBuff.TimerCategory.UNMAPPED);
			}
		}
		List<TrackedBuff.TimerCategory> updated = new ArrayList<>(categories);
		updated.remove(category);
		saveCategories(updated);

		configManager.unsetConfiguration(CATEGORY_NAME_CONFIG_GROUP, category.name());
		configManager.unsetConfiguration(CATEGORY_EXPANDED_CONFIG_GROUP, category.name());
		// Settings for categories 2+ all start with "category<N>" + an uppercase letter. Category
		// 1's (and 2-5's older flash/linger keys) have legacy names and are left alone - harmless,
		// since a deleted number is never handed out again.
		if (category.getNumber() >= 2)
		{
			String prefix = "category" + category.getNumber();
			String groupPrefix = UltimateTimersConfig.GROUP + ".";
			for (String wholeKey : configManager.getConfigurationKeys(groupPrefix + prefix))
			{
				String key = wholeKey.substring(groupPrefix.length());
				if (key.length() > prefix.length() && Character.isUpperCase(key.charAt(prefix.length())))
				{
					configManager.unsetConfiguration(UltimateTimersConfig.GROUP, key);
				}
			}
		}
	}

	private int nextCategoryNumber()
	{
		Integer stored = configManager.getConfiguration(CATEGORY_LIST_CONFIG_GROUP, "nextNumber", Integer.class);
		int highest = 0;
		for (TrackedBuff.TimerCategory category : categories)
		{
			highest = Math.max(highest, category.getNumber());
		}
		int next = stored != null ? stored : FIRST_ADDED_CATEGORY_NUMBER;
		return Math.max(next, highest + 1);
	}

	private List<TrackedBuff.TimerCategory> loadCategories()
	{
		String stored = configManager.getConfiguration(CATEGORY_LIST_CONFIG_GROUP, "ids");
		if (stored == null)
		{
			return TrackedBuff.TimerCategory.DEFAULTS;
		}
		List<TrackedBuff.TimerCategory> loaded = new ArrayList<>();
		for (String id : stored.split(","))
		{
			TrackedBuff.TimerCategory category = TrackedBuff.TimerCategory.fromId(id.trim());
			if (category != null && !category.isUnmapped() && !loaded.contains(category))
			{
				loaded.add(category);
			}
		}
		return Collections.unmodifiableList(loaded);
	}

	private void saveCategories(List<TrackedBuff.TimerCategory> updated)
	{
		categories = Collections.unmodifiableList(new ArrayList<>(updated));
		StringBuilder ids = new StringBuilder();
		for (TrackedBuff.TimerCategory category : updated)
		{
			if (ids.length() > 0)
			{
				ids.append(',');
			}
			ids.append(category.name());
		}
		configManager.setConfiguration(CATEGORY_LIST_CONFIG_GROUP, "ids", ids.toString());
	}

	/**
	 * Each category's flash/linger settings (used to live in five "Advanced Behaviors (Category
	 * N)" config sections - 45 ConfigItems) now live only in the side panel's per-category
	 * settings dialog, read/written directly through ConfigManager under the same key names those
	 * ConfigItems used (so no migration is needed - the raw stored values are untouched).
	 */
	private static final class CategorySettingKeys
	{
		final String enableFlash, flashThresholdSeconds, flashColor, flashForeground, flashBackground, flashBorder,
			flashText, enableLinger, lingerSeconds;
		final boolean enableFlashDefault, flashForegroundDefault, flashBackgroundDefault, flashBorderDefault,
			flashTextDefault, enableLingerDefault;
		final int flashThresholdSecondsDefault, lingerSecondsDefault;
		final Color flashColorDefault;

		CategorySettingKeys(String enableFlash, String flashThresholdSeconds, String flashColor,
			String flashForeground, String flashBackground, String flashBorder, String flashText,
			String enableLinger, String lingerSeconds, boolean enableFlashDefault, int flashThresholdSecondsDefault,
			Color flashColorDefault, boolean flashBorderDefault, boolean enableLingerDefault, int lingerSecondsDefault)
		{
			this.enableFlash = enableFlash;
			this.flashThresholdSeconds = flashThresholdSeconds;
			this.flashColor = flashColor;
			this.flashForeground = flashForeground;
			this.flashBackground = flashBackground;
			this.flashBorder = flashBorder;
			this.flashText = flashText;
			this.enableLinger = enableLinger;
			this.lingerSeconds = lingerSeconds;
			this.enableFlashDefault = enableFlashDefault;
			this.flashThresholdSecondsDefault = flashThresholdSecondsDefault;
			this.flashColorDefault = flashColorDefault;
			this.flashForegroundDefault = false;
			this.flashBackgroundDefault = false;
			this.flashBorderDefault = flashBorderDefault;
			this.flashTextDefault = false;
			this.enableLingerDefault = enableLingerDefault;
			this.lingerSecondsDefault = lingerSecondsDefault;
		}

		/** The original five categories' key names, from when they were ConfigItems. */
		CategorySettingKeys(String prefix, boolean enableFlashDefault, int flashThresholdSecondsDefault,
			Color flashColorDefault, boolean flashBorderDefault, boolean enableLingerDefault,
			int lingerSecondsDefault)
		{
			this.enableFlash = "enable" + prefix + "Flash";
			this.flashThresholdSeconds = prefix.isEmpty() ? "flashThresholdSeconds" : (lower(prefix) + "FlashThresholdSeconds");
			this.flashColor = prefix.isEmpty() ? "flashColor" : (lower(prefix) + "FlashColor");
			this.flashForeground = prefix.isEmpty() ? "flashForeground" : (lower(prefix) + "FlashForeground");
			this.flashBackground = prefix.isEmpty() ? "flashBackground" : (lower(prefix) + "FlashBackground");
			this.flashBorder = prefix.isEmpty() ? "flashBorder" : (lower(prefix) + "FlashBorder");
			this.flashText = prefix.isEmpty() ? "flashText" : (lower(prefix) + "FlashText");
			this.enableLinger = "enable" + prefix + "Linger";
			this.lingerSeconds = prefix.isEmpty() ? "lingerSeconds" : (lower(prefix) + "LingerSeconds");
			this.enableFlashDefault = enableFlashDefault;
			this.flashThresholdSecondsDefault = flashThresholdSecondsDefault;
			this.flashColorDefault = flashColorDefault;
			this.flashForegroundDefault = false;
			this.flashBackgroundDefault = false;
			this.flashBorderDefault = flashBorderDefault;
			this.flashTextDefault = false;
			this.enableLingerDefault = enableLingerDefault;
			this.lingerSecondsDefault = lingerSecondsDefault;
		}

		private static String lower(String prefix)
		{
			return Character.toLowerCase(prefix.charAt(0)) + prefix.substring(1);
		}
	}

	/**
	 * Categories 1-5 keep their original key names. Added categories get keys through
	 * categoryKey() ("category6EnableFlash", ...), so all of a category's settings share one prefix.
	 */
	private static final Map<TrackedBuff.TimerCategory, CategorySettingKeys> CATEGORY_SETTING_KEYS =
		new java.util.concurrent.ConcurrentHashMap<>(buildCategorySettingKeys());

	private static CategorySettingKeys settingKeys(TrackedBuff.TimerCategory category)
	{
		return CATEGORY_SETTING_KEYS.computeIfAbsent(category, c -> new CategorySettingKeys(
			categoryKey(c, "enableFlash"), categoryKey(c, "flashThresholdSeconds"), categoryKey(c, "flashColor"),
			categoryKey(c, "flashForeground"), categoryKey(c, "flashBackground"), categoryKey(c, "flashBorder"),
			categoryKey(c, "flashText"), categoryKey(c, "enableLinger"), categoryKey(c, "lingerSeconds"),
			false, 15, new Color(218, 35, 35), true, false, 15));
	}

	private static Map<TrackedBuff.TimerCategory, CategorySettingKeys> buildCategorySettingKeys()
	{
		Map<TrackedBuff.TimerCategory, CategorySettingKeys> map = new HashMap<>();
		map.put(TrackedBuff.TimerCategory.CATEGORY_1,
			new CategorySettingKeys("", true, 15, new Color(218, 35, 35), true, true, 15));
		map.put(TrackedBuff.TimerCategory.CATEGORY_2,
			new CategorySettingKeys("Debuff", true, 5, new Color(26, 221, 70), true, false, 15));
		map.put(TrackedBuff.TimerCategory.CATEGORY_3,
			new CategorySettingKeys("Cooldown", false, 5, new Color(26, 221, 70), true, false, 15));
		map.put(TrackedBuff.TimerCategory.CATEGORY_4,
			new CategorySettingKeys("OtherTimer", false, 10, new Color(218, 35, 35), true, false, 15));
		map.put(TrackedBuff.TimerCategory.CATEGORY_5,
			new CategorySettingKeys("Category5", false, 15, new Color(218, 35, 35), true, false, 15));
		return map;
	}

	boolean isFlashEnabled(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Boolean stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.enableFlash, Boolean.class);
		return stored != null ? stored : keys.enableFlashDefault;
	}

	void setFlashEnabled(TrackedBuff.TimerCategory category, boolean value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP, settingKeys(category).enableFlash,
			value);
	}

	int getFlashThresholdSeconds(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Integer stored =
			configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.flashThresholdSeconds, Integer.class);
		return stored != null ? stored : keys.flashThresholdSecondsDefault;
	}

	void setFlashThresholdSeconds(TrackedBuff.TimerCategory category, int value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP,
			settingKeys(category).flashThresholdSeconds, value);
	}

	Color getFlashColor(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Color stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.flashColor, Color.class);
		return stored != null ? stored : keys.flashColorDefault;
	}

	void setFlashColor(TrackedBuff.TimerCategory category, Color value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP, settingKeys(category).flashColor,
			value);
	}

	boolean isFlashForeground(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Boolean stored =
			configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.flashForeground, Boolean.class);
		return stored != null ? stored : keys.flashForegroundDefault;
	}

	void setFlashForeground(TrackedBuff.TimerCategory category, boolean value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP,
			settingKeys(category).flashForeground, value);
	}

	boolean isFlashBackground(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Boolean stored =
			configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.flashBackground, Boolean.class);
		return stored != null ? stored : keys.flashBackgroundDefault;
	}

	void setFlashBackground(TrackedBuff.TimerCategory category, boolean value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP,
			settingKeys(category).flashBackground, value);
	}

	boolean isFlashBorder(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Boolean stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.flashBorder, Boolean.class);
		return stored != null ? stored : keys.flashBorderDefault;
	}

	void setFlashBorder(TrackedBuff.TimerCategory category, boolean value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP, settingKeys(category).flashBorder,
			value);
	}

	boolean isFlashText(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Boolean stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.flashText, Boolean.class);
		return stored != null ? stored : keys.flashTextDefault;
	}

	void setFlashText(TrackedBuff.TimerCategory category, boolean value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP, settingKeys(category).flashText,
			value);
	}

	void setLingerEnabled(TrackedBuff.TimerCategory category, boolean value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP, settingKeys(category).enableLinger,
			value);
	}

	void setLingerSeconds(TrackedBuff.TimerCategory category, int value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP,
			settingKeys(category).lingerSeconds, value);
	}

	int getLingerSecondsSetting(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Integer stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.lingerSeconds, Integer.class);
		return stored != null ? stored : keys.lingerSecondsDefault;
	}

	/**
	 * Size/shape, text/icon layout, and the two main debug toggles were each a single global
	 * config section - now they're per-category too, controlled from the same settings dialog as
	 * flash/linger. CATEGORY_1 reuses each setting's original bare key name (so its value carries
	 * over automatically with zero migration - it just reads the same key it always did);
	 * Categories 2-5 get a new "category2"/"category3"/... prefixed key, defaulting to the exact
	 * same compiled default CATEGORY_1 always had, since they're new and have no prior value.
	 */
	private static String categoryKey(TrackedBuff.TimerCategory category, String baseKey)
	{
		if (category == TrackedBuff.TimerCategory.CATEGORY_1)
		{
			return baseKey;
		}
		String prefix = category.isUnmapped() ? "unmapped" : "category" + category.getNumber();
		return prefix + Character.toUpperCase(baseKey.charAt(0)) + baseKey.substring(1);
	}

	private <T> T getLayoutSetting(TrackedBuff.TimerCategory category, String baseKey, Class<T> type, T defaultValue)
	{
		T stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, categoryKey(category, baseKey), type);
		return stored != null ? stored : defaultValue;
	}

	private void setLayoutSetting(TrackedBuff.TimerCategory category, String baseKey, Object value)
	{
		configManager.setConfiguration(UltimateTimersConfig.GROUP, categoryKey(category, baseKey), value);
	}

	int getRingWidth(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "ringWidth", Integer.class, 35);
	}

	void setRingWidth(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "ringWidth", v);
	}

	int getRingHeight(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "ringHeight", Integer.class, 35);
	}

	void setRingHeight(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "ringHeight", v);
	}

	int getCornerRadius(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "cornerRadius", Integer.class, 0);
	}

	void setCornerRadius(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "cornerRadius", v);
	}

	int getRingBorderThickness(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "ringBorderThickness", Integer.class, 1);
	}

	void setRingBorderThickness(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "ringBorderThickness", v);
	}

	/**
	 * How this category colors its timers' foreground (fill). This replaced the old "overrideColors"
	 * checkbox. Until a Color Style is saved, a category that had the checkbox on reads as
	 * OVERRIDE and everything else as INDIVIDUAL. Saving a style removes the old key.
	 */
	ColorStyle getColorStyle(TrackedBuff.TimerCategory c)
	{
		ColorStyle stored = getLayoutSetting(c, "colorStyle", ColorStyle.class, null);
		if (stored != null)
		{
			return stored;
		}
		return getLayoutSetting(c, "overrideColors", Boolean.class, false) ? ColorStyle.OVERRIDE : ColorStyle.INDIVIDUAL;
	}

	void setColorStyle(TrackedBuff.TimerCategory c, ColorStyle style)
	{
		setLayoutSetting(c, "colorStyle", style);
		configManager.unsetConfiguration(UltimateTimersConfig.GROUP, categoryKey(c, "overrideColors"));
	}

	Color getPercentColorFull(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "percentColorFull", Color.class, Color.GREEN);
	}

	void setPercentColorFull(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "percentColorFull", v);
	}

	Color getPercentColorHalf(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "percentColorHalf", Color.class, Color.YELLOW);
	}

	void setPercentColorHalf(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "percentColorHalf", v);
	}

	Color getPercentColorEmpty(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "percentColorEmpty", Color.class, Color.RED);
	}

	void setPercentColorEmpty(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "percentColorEmpty", v);
	}

	/**
	 * Percentage-Based Color: the "low" color is reached at 25% (not 0%), so a timer turns that
	 * color while there's still a quarter left and holds it to the end. It blends from low (25%) to
	 * half (50%), then from half to full (100%). Alpha is blended too. The low color is still saved
	 * as "percentColorEmpty" so existing choices carry over.
	 */
	static Color percentageColor(Color full, Color half, Color empty, double fraction)
	{
		double f = Math.max(0.0, Math.min(1.0, fraction));
		if (f >= 0.5)
		{
			return blend(half, full, (f - 0.5) * 2);
		}
		if (f <= 0.25)
		{
			return empty;
		}
		return blend(empty, half, (f - 0.25) * 4);
	}

	private static Color blend(Color from, Color to, double t)
	{
		return new Color(
			(int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
			(int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
			(int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
			(int) Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
	}

	Color getOverrideColor(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "overrideColor", Color.class, DEFAULT_OVERRIDE_COLOR);
	}

	void setOverrideColor(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "overrideColor", v);
	}

	Color getBorderColor(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "borderColor", Color.class, Color.BLACK);
	}

	void setBorderColor(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "borderColor", v);
	}

	Color getBackgroundColor(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "backgroundColor", Color.class, new Color(0, 0, 0, 160));
	}

	void setBackgroundColor(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "backgroundColor", v);
	}

	FillDirection getFillDirection(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "fillDirection", FillDirection.class, FillDirection.COUNTERCLOCKWISE);
	}

	void setFillDirection(TrackedBuff.TimerCategory c, FillDirection v)
	{
		setLayoutSetting(c, "fillDirection", v);
	}

	int getGroupPadding(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "groupPadding", Integer.class, 6);
	}

	void setGroupPadding(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "groupPadding", v);
	}

	boolean isShowTimeText(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "showTimeText", Boolean.class, true);
	}

	void setShowTimeText(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "showTimeText", v);
	}

	boolean isShowDescription(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "showDescription", Boolean.class, false);
	}

	void setShowDescription(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "showDescription", v);
	}

	boolean isShowIcon(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "showIcon", Boolean.class, true);
	}

	void setShowIcon(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "showIcon", v);
	}

	boolean isTooltipsEnabled(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "enableTooltips", Boolean.class, true);
	}

	void setTooltipsEnabled(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "enableTooltips", v);
	}

	ElementPosition getTextPosition(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "textPosition", ElementPosition.class, ElementPosition.BOTTOM);
	}

	void setTextPosition(TrackedBuff.TimerCategory c, ElementPosition v)
	{
		setLayoutSetting(c, "textPosition", v);
	}

	ElementPosition getDescriptionPosition(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "descriptionPosition", ElementPosition.class, ElementPosition.TOP);
	}

	void setDescriptionPosition(TrackedBuff.TimerCategory c, ElementPosition v)
	{
		setLayoutSetting(c, "descriptionPosition", v);
	}

	ElementPosition getIconPosition(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "iconPosition", ElementPosition.class, ElementPosition.CENTER);
	}

	void setIconPosition(TrackedBuff.TimerCategory c, ElementPosition v)
	{
		setLayoutSetting(c, "iconPosition", v);
	}

	int getIconSize(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "iconSize", Integer.class, 25);
	}

	void setIconSize(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "iconSize", v);
	}

	FontType getTextFont(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "textFont", FontType.class, FontType.REGULAR);
	}

	void setTextFont(TrackedBuff.TimerCategory c, FontType v)
	{
		setLayoutSetting(c, "textFont", v);
	}

	/**
	 * How far the time text is pushed in the direction of its position (up for Top/Above, left
	 * for Left/Outside left, diagonally for corners). Negative values pull it back toward the center.
	 */
	int getTextOffset(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "textOffset", Integer.class, 0);
	}

	void setTextOffset(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "textOffset", v);
	}

	/**
	 * Fixed maximum: when on, a timer's fill is measured against this many seconds rather than
	 * its own starting duration. Anything above it shows as full, and it only starts visibly
	 * counting down once it drops below. Doesn't apply to kill counters (Slayer task).
	 */
	boolean isFixedMaximumEnabled(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "fixedMaximumEnabled", Boolean.class, false);
	}

	void setFixedMaximumEnabled(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "fixedMaximumEnabled", v);
	}

	int getFixedMaximumSeconds(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "fixedMaximumSeconds", Integer.class, 120);
	}

	void setFixedMaximumSeconds(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "fixedMaximumSeconds", v);
	}

	/** Same as getTextOffset, for the icon. */
	int getIconOffset(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "iconOffset", Integer.class, 0);
	}

	void setIconOffset(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "iconOffset", v);
	}

	/** Same as getTextOffset, for the description text. */
	int getDescriptionOffset(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "descriptionOffset", Integer.class, 0);
	}

	void setDescriptionOffset(TrackedBuff.TimerCategory c, int v)
	{
		setLayoutSetting(c, "descriptionOffset", v);
	}

	Color getTextColor(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "textColor", Color.class, Color.WHITE);
	}

	void setTextColor(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "textColor", v);
	}

	boolean isTextBorderEnabled(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "textBorderEnabled", Boolean.class, false);
	}

	void setTextBorderEnabled(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "textBorderEnabled", v);
	}

	Color getTextBorderColor(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "textBorderColor", Color.class, Color.BLACK);
	}

	void setTextBorderColor(TrackedBuff.TimerCategory c, Color v)
	{
		setLayoutSetting(c, "textBorderColor", v);
	}

	boolean isTextShadowEnabled(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "textShadowEnabled", Boolean.class, true);
	}

	void setTextShadowEnabled(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "textShadowEnabled", v);
	}

	boolean isForceShowEnabled(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "debugForceShowAll", Boolean.class, false);
	}

	void setForceShowEnabled(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "debugForceShowAll", v);
	}

	boolean isForceFlashEnabled(TrackedBuff.TimerCategory c)
	{
		return getLayoutSetting(c, "debugForceFlashing", Boolean.class, false);
	}

	void setForceFlashEnabled(TrackedBuff.TimerCategory c, boolean v)
	{
		setLayoutSetting(c, "debugForceFlashing", v);
	}

	private TrackedBuff.TimerCategory loadTimerCategory(TrackedBuff buff)
	{
		String stored = configManager.getConfiguration(TIMER_CATEGORY_CONFIG_GROUP, buff.name());
		TrackedBuff.TimerCategory category = TrackedBuff.TimerCategory.fromId(stored);
		if (category == null)
		{
			category = buff.getDefaultCategory();
		}
		// A category that's since been deleted (or a default that no longer exists) means Unmapped.
		return category.isUnmapped() || categories.contains(category) ? category : TrackedBuff.TimerCategory.UNMAPPED;
	}

	private void persistTimerCategory(TrackedBuff buff, TrackedBuff.TimerCategory category)
	{
		if (category == buff.getDefaultCategory())
		{
			configManager.unsetConfiguration(TIMER_CATEGORY_CONFIG_GROUP, buff.name());
		}
		else
		{
			configManager.setConfiguration(TIMER_CATEGORY_CONFIG_GROUP, buff.name(), category.name());
		}
	}

	private int loadBuffOrder(TrackedBuff buff)
	{
		String stored = configManager.getConfiguration(ORDER_CONFIG_GROUP, buff.name());
		if (stored != null)
		{
			try
			{
				return Integer.parseInt(stored);
			}
			catch (NumberFormatException e)
			{
				// fall through to default
			}
		}
		return buff.ordinal();
	}

	private void persistBuffOrder(TrackedBuff buff, int order)
	{
		configManager.setConfiguration(ORDER_CONFIG_GROUP, buff.name(), String.valueOf(order));
	}

	/**
	 * Every buff currently assigned to groupName (active or not - the point is to remember
	 * relative positions even while a timer is inactive), sorted by its persisted order rank.
	 */
	List<TrackedBuff> getGroupMembersInOrder(String groupName)
	{
		List<TrackedBuff> members = new ArrayList<>();
		for (Map.Entry<TrackedBuff, String> entry : buffGroup.entrySet())
		{
			if (entry.getValue().equals(groupName))
			{
				members.add(entry.getKey());
			}
		}
		members.sort(Comparator.comparingInt(b -> buffOrder.getOrDefault(b, b.ordinal())));
		return members;
	}

	/**
	 * Swaps this buff's order rank with whichever group member sits delta slots away (-1 for up,
	 * +1 for down), persisting both so the new relative order survives either timer going
	 * inactive.
	 */
	void moveBuff(TrackedBuff buff, int delta)
	{
		String group = buffGroup.getOrDefault(buff, DEFAULT_GROUP);
		List<TrackedBuff> members = getGroupMembersInOrder(group);
		int index = members.indexOf(buff);
		int targetIndex = index + delta;
		if (index < 0 || targetIndex < 0 || targetIndex >= members.size())
		{
			return;
		}

		TrackedBuff other = members.get(targetIndex);
		int buffRank = buffOrder.getOrDefault(buff, buff.ordinal());
		int otherRank = buffOrder.getOrDefault(other, other.ordinal());
		buffOrder.put(buff, otherRank);
		buffOrder.put(other, buffRank);
		persistBuffOrder(buff, otherRank);
		persistBuffOrder(other, buffRank);
	}

	private UltimateTimersGroupOverlay ensureGroupOverlay(String groupName)
	{
		return groupOverlays.computeIfAbsent(groupName, name ->
		{
			UltimateTimersGroupOverlay groupOverlay = new UltimateTimersGroupOverlay(this, client, tooltipManager, name);
			overlayManager.add(groupOverlay);
			return groupOverlay;
		});
	}

	private BufferedImage iconFor(TrackedBuff buff, Map<TrackedBuff, BufferedImage> dynamicIcons)
	{
		BufferedImage dynamicIcon = dynamicIcons.get(buff);
		return dynamicIcon != null ? dynamicIcon : sampledIcons.get(buff);
	}

	private Color colorFor(TrackedBuff buff)
	{
		// PERCENTAGE is resolved per frame in the overlay, which knows the fill fraction. The
		// individual color here is only a fallback for that case.
		TrackedBuff.TimerCategory category = getTimerCategory(buff);
		if (getColorStyle(category) == ColorStyle.OVERRIDE)
		{
			return getOverrideColor(category);
		}
		Color configured = configuredColorFor(buff);
		if (configured != null && configured.getAlpha() > 0)
		{
			return configured;
		}
		return FALLBACK_COLOR;
	}

	/**
	 * Each timer's own color is set from the side panel (the swatch on its row) and saved per
	 * timer in TIMER_COLOR_CONFIG_GROUP. Before that, colors were ConfigItems in the plugin's
	 * Colors config section, and some were shared by a group of timers (freeze spells, overloads,
	 * ...). Until a timer gets its own color, it keeps reading that old key and default, so
	 * nothing changes color on update and no migration is needed.
	 */
	private static final class LegacyTimerColor
	{
		final String key;
		final Color defaultColor;

		LegacyTimerColor(String key, Color defaultColor)
		{
			this.key = key;
			this.defaultColor = defaultColor;
		}
	}

	private static final Map<TrackedBuff, LegacyTimerColor> LEGACY_TIMER_COLORS = buildLegacyTimerColors();

	private static Map<TrackedBuff, LegacyTimerColor> buildLegacyTimerColors()
	{
		Map<TrackedBuff, LegacyTimerColor> map = new EnumMap<>(TrackedBuff.class);
		map.put(TrackedBuff.ANTIFIRE, new LegacyTimerColor("antifireColor", new Color(113, 0, 153)));
		map.put(TrackedBuff.SUPER_ANTIFIRE, new LegacyTimerColor("superAntifireColor", new Color(126, 56, 153)));
		map.put(TrackedBuff.STAMINA, new LegacyTimerColor("staminaColor", new Color(153, 91, 15)));
		map.put(TrackedBuff.ANTIVENOM, new LegacyTimerColor("antivenomColor", new Color(93, 99, 78)));
		map.put(TrackedBuff.ANTIPOISON, new LegacyTimerColor("antipoisonColor", new Color(68, 153, 0)));
		map.put(TrackedBuff.GOADING, new LegacyTimerColor("goadingColor", new Color(153, 15, 0)));
		map.put(TrackedBuff.DIVINE_SUPER_COMBAT, new LegacyTimerColor("divineSuperCombatColor", new Color(28, 153, 0)));
		map.put(TrackedBuff.DIVINE_BASTION, new LegacyTimerColor("divineBastionColor", new Color(153, 65, 0)));
		map.put(TrackedBuff.DIVINE_BATTLEMAGE, new LegacyTimerColor("divineBattlemageColor", new Color(153, 103, 0)));
		map.put(TrackedBuff.DIVINE_SUPER_ATTACK, new LegacyTimerColor("divineSuperAttackColor", new Color(16, 19, 153)));
		map.put(TrackedBuff.DIVINE_SUPER_STRENGTH, new LegacyTimerColor("divineSuperStrengthColor", new Color(153, 148, 127)));
		map.put(TrackedBuff.DIVINE_SUPER_DEFENCE, new LegacyTimerColor("divineSuperDefenceColor", new Color(153, 116, 5)));
		map.put(TrackedBuff.DIVINE_RANGING, new LegacyTimerColor("divineRangingColor", new Color(16, 117, 153)));
		map.put(TrackedBuff.DIVINE_MAGIC, new LegacyTimerColor("divineMagicColor", new Color(153, 106, 81)));
		map.put(TrackedBuff.PRAYER_REGEN, new LegacyTimerColor("prayerRegenColor", new Color(118, 68, 68)));
		map.put(TrackedBuff.THRALL, new LegacyTimerColor("thrallColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.BIND, new LegacyTimerColor("freezeColor", new Color(100, 180, 220)));
		map.put(TrackedBuff.SNARE, new LegacyTimerColor("freezeColor", new Color(100, 180, 220)));
		map.put(TrackedBuff.ENTANGLE, new LegacyTimerColor("freezeColor", new Color(100, 180, 220)));
		map.put(TrackedBuff.ICE_RUSH, new LegacyTimerColor("freezeColor", new Color(100, 180, 220)));
		map.put(TrackedBuff.ICE_BURST, new LegacyTimerColor("freezeColor", new Color(100, 180, 220)));
		map.put(TrackedBuff.ICE_BLITZ, new LegacyTimerColor("freezeColor", new Color(100, 180, 220)));
		map.put(TrackedBuff.ICE_BARRAGE, new LegacyTimerColor("freezeColor", new Color(100, 180, 220)));
		map.put(TrackedBuff.SHADOW_VEIL, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.WARD_OF_ARCEUUS, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.DEATH_CHARGE, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.MARK_OF_DARKNESS, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.SHADOW_VEIL_COOLDOWN, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.RESURRECT_THRALL_COOLDOWN, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.WARD_OF_ARCEUUS_COOLDOWN, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.DEATH_CHARGE_COOLDOWN, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.CORRUPTION_COOLDOWN, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.MARK_OF_DARKNESS_COOLDOWN, new LegacyTimerColor("necromancyColor", new Color(110, 70, 140)));
		map.put(TrackedBuff.OVERLOAD, new LegacyTimerColor("overloadColor", new Color(81, 81, 81)));
		map.put(TrackedBuff.OVERLOAD_RAID, new LegacyTimerColor("overloadColor", new Color(81, 81, 81)));
		map.put(TrackedBuff.BLIGHTED_OVERLOAD, new LegacyTimerColor("overloadColor", new Color(81, 81, 81)));
		map.put(TrackedBuff.MAGIC_IMBUE, new LegacyTimerColor("magicImbueColor", new Color(60, 160, 170)));
		map.put(TrackedBuff.IMBUED_HEART, new LegacyTimerColor("imbuedHeartColor", new Color(98, 60, 144)));
		map.put(TrackedBuff.PRAYER_ENHANCE, new LegacyTimerColor("prayerEnhanceColor", new Color(145, 71, 150)));
		map.put(TrackedBuff.FARMERS_AFFINITY, new LegacyTimerColor("farmersAffinityColor", new Color(150, 120, 40)));
		map.put(TrackedBuff.MENAPHITE_REMEDY, new LegacyTimerColor("menaphiteRemedyColor", new Color(43, 105, 153)));
		map.put(TrackedBuff.SURGE_POTION, new LegacyTimerColor("surgePotionColor", new Color(60, 160, 170)));
		map.put(TrackedBuff.MOONLIGHT_POTION, new LegacyTimerColor("moonlightPotionColor", new Color(145, 71, 150)));
		map.put(TrackedBuff.LIQUID_ADRENALINE, new LegacyTimerColor("raidSupplyColor", new Color(200, 170, 60)));
		map.put(TrackedBuff.SILK_DRESSING, new LegacyTimerColor("raidSupplyColor", new Color(200, 170, 60)));
		map.put(TrackedBuff.BLESSED_CRYSTAL_SCARAB, new LegacyTimerColor("raidSupplyColor", new Color(200, 170, 60)));
		map.put(TrackedBuff.SMELLING_SALTS, new LegacyTimerColor("raidSupplyColor", new Color(200, 170, 60)));
		map.put(TrackedBuff.VENGEANCE, new LegacyTimerColor("vengeanceColor", new Color(171, 74, 74)));
		map.put(TrackedBuff.TELEBLOCK, new LegacyTimerColor("teleblockColor", new Color(140, 40, 180)));
		map.put(TrackedBuff.HEAL_GROUP, new LegacyTimerColor("healGroupColor", new Color(80, 170, 120)));
		map.put(TrackedBuff.CHARGE, new LegacyTimerColor("chargeColor", new Color(170, 140, 40)));
		map.put(TrackedBuff.SPELLBOOK_SWAP, new LegacyTimerColor("spellbookSwapColor", new Color(60, 140, 170)));
		map.put(TrackedBuff.CANNON, new LegacyTimerColor("cannonColor", new Color(107, 107, 107)));
		map.put(TrackedBuff.CANNON_REPAIR, new LegacyTimerColor("cannonColor", new Color(107, 107, 107)));
		map.put(TrackedBuff.STAFF_OF_THE_DEAD, new LegacyTimerColor("staffOfTheDeadColor", new Color(82, 116, 115)));
		map.put(TrackedBuff.DRAGON_FIRE_SHIELD, new LegacyTimerColor("dragonfireShieldColor", new Color(200, 100, 30)));
		map.put(TrackedBuff.SMOULDERING_HEART, new LegacyTimerColor("smoulderingColor", new Color(190, 70, 40)));
		map.put(TrackedBuff.SMOULDERING_GLAND, new LegacyTimerColor("smoulderingColor", new Color(190, 70, 40)));
		map.put(TrackedBuff.ABYSSAL_SIRE_STUN, new LegacyTimerColor("bossMechanicColor", new Color(190, 90, 30)));
		map.put(TrackedBuff.SCURRIUS_FOOD_PILE, new LegacyTimerColor("bossMechanicColor", new Color(190, 90, 30)));
		map.put(TrackedBuff.GOD_WARS_ALTAR, new LegacyTimerColor("bossMechanicColor", new Color(190, 90, 30)));
		map.put(TrackedBuff.PICKPOCKET_STUN, new LegacyTimerColor("pickpocketStunColor", new Color(145, 71, 150)));
		map.put(TrackedBuff.HOME_TELEPORT, new LegacyTimerColor("homeTeleportColor", new Color(80, 130, 160)));
		map.put(TrackedBuff.MINIGAME_TELEPORT, new LegacyTimerColor("minigameTeleportColor", new Color(160, 80, 80)));
		map.put(TrackedBuff.DELAYED_HEAL, new LegacyTimerColor("delayedHealColor", new Color(153, 116, 81)));
		map.put(TrackedBuff.SLAYER_TASK, new LegacyTimerColor("slayerTaskColor", new Color(100, 60, 140)));
		map.put(TrackedBuff.BOSS_RESPAWN, new LegacyTimerColor("bossRespawnColor", new Color(80, 20, 20)));
		map.put(TrackedBuff.AFK_LOGOUT, new LegacyTimerColor("afkLogoutColor", new Color(130, 100, 30)));
		return map;
	}

	/** A timer's own (individual) color: its saved per-timer color, else its old config color, else the default. */
	Color getTimerColor(TrackedBuff buff)
	{
		Color own = configManager.getConfiguration(TIMER_COLOR_CONFIG_GROUP, buff.name(), Color.class);
		if (own != null)
		{
			return own;
		}
		LegacyTimerColor legacy = LEGACY_TIMER_COLORS.get(buff);
		if (legacy == null)
		{
			return FALLBACK_COLOR;
		}
		Color saved = configManager.getConfiguration(UltimateTimersConfig.GROUP, legacy.key, Color.class);
		return saved != null ? saved : legacy.defaultColor;
	}

	void setTimerColor(TrackedBuff buff, Color color)
	{
		configManager.setConfiguration(TIMER_COLOR_CONFIG_GROUP, buff.name(), color);
	}

	/**
	 * Every timer's own color back to its default. Clears the old shared config keys too, so a
	 * color saved before colors moved to the side panel doesn't come back instead of the default.
	 */
	void resetTimerColors()
	{
		for (TrackedBuff buff : TrackedBuff.values())
		{
			configManager.unsetConfiguration(TIMER_COLOR_CONFIG_GROUP, buff.name());
			LegacyTimerColor legacy = LEGACY_TIMER_COLORS.get(buff);
			if (legacy != null)
			{
				configManager.unsetConfiguration(UltimateTimersConfig.GROUP, legacy.key);
			}
		}
	}

	private Color configuredColorFor(TrackedBuff buff)
	{
		return getTimerColor(buff);
	}

	private void requestIcons()
	{
		for (TrackedBuff buff : TrackedBuff.values())
		{
			TrackedBuff.Icon icon = buff.getIcon();
			if (icon.isSprite())
			{
				spriteManager.getSpriteAsync(icon.getId(), 0, image -> sampledIcons.put(buff, image));
			}
			else
			{
				AsyncBufferedImage image = itemManager.getImage(icon.getId());
				image.onLoaded(() -> sampledIcons.put(buff, image));
			}
		}
	}

	boolean isLingerEnabled(TrackedBuff.TimerCategory category)
	{
		CategorySettingKeys keys = settingKeys(category);
		Boolean stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, keys.enableLinger, Boolean.class);
		return stored != null ? stored : keys.enableLingerDefault;
	}

	private long lingerMillisFor(TrackedBuff.TimerCategory category)
	{
		return getLingerSecondsSetting(category) * 1000L;
	}

	/**
	 * Visibility has lived in two different places across this plugin's history: originally the
	 * per-fine-grained-Category "show X" toggles (and the Buffs/Debuffs/Cooldowns/Other
	 * Timers/Counters sections they lived in), then briefly a DISABLED TimerCategory. Both are
	 * gone now - visibility is its own per-timer checkbox (isTimerEnabled/setTimerEnabled),
	 * independent of which flash/linger category a timer is in. This carries over whichever of
	 * those two prior states still has data into the new flag: a literal "DISABLED" stored
	 * category override takes priority (it's the more recent of the two), otherwise falls back to
	 * the original show-toggle (reading its raw stored string directly, since the config item
	 * itself no longer exists, with its own old compiled default when never explicitly set).
	 * Skips any timer that already has an explicit enabled/disabled flag so this never clobbers a
	 * deliberate post-upgrade choice. Runs once - the old keys are gone afterward, so every future
	 * startup no-ops.
	 */
	private void migrateHiddenTimersToEnabledFlag()
	{
		Map<TrackedBuff.Category, String> legacyShowKey = new EnumMap<>(TrackedBuff.Category.class);
		legacyShowKey.put(TrackedBuff.Category.ANTIFIRE, "showAntifire");
		legacyShowKey.put(TrackedBuff.Category.STAMINA, "showStamina");
		legacyShowKey.put(TrackedBuff.Category.DIVINE, "showDivine");
		legacyShowKey.put(TrackedBuff.Category.ANTIPOISON, "showAntipoison");
		legacyShowKey.put(TrackedBuff.Category.GOADING, "showGoading");
		legacyShowKey.put(TrackedBuff.Category.PRAYER_REGEN, "showPrayerRegen");
		legacyShowKey.put(TrackedBuff.Category.THRALL, "showThrall");
		legacyShowKey.put(TrackedBuff.Category.FREEZE, "showFreeze");
		legacyShowKey.put(TrackedBuff.Category.NECROMANCY, "showNecromancy");
		legacyShowKey.put(TrackedBuff.Category.OVERLOAD, "showOverload");
		legacyShowKey.put(TrackedBuff.Category.MAGIC_IMBUE, "showMagicImbue");
		legacyShowKey.put(TrackedBuff.Category.IMBUED_HEART, "showImbuedHeart");
		legacyShowKey.put(TrackedBuff.Category.PRAYER_ENHANCE, "showPrayerEnhance");
		legacyShowKey.put(TrackedBuff.Category.FARMERS_AFFINITY, "showFarmersAffinity");
		legacyShowKey.put(TrackedBuff.Category.MENAPHITE_REMEDY, "showMenaphiteRemedy");
		legacyShowKey.put(TrackedBuff.Category.SURGE_POTION, "showSurgePotion");
		legacyShowKey.put(TrackedBuff.Category.MOONLIGHT_POTION, "showMoonlightPotion");
		legacyShowKey.put(TrackedBuff.Category.RAID_SUPPLY, "showRaidSupply");
		legacyShowKey.put(TrackedBuff.Category.VENGEANCE, "showVengeance");
		legacyShowKey.put(TrackedBuff.Category.TELEBLOCK, "showTeleblock");
		legacyShowKey.put(TrackedBuff.Category.HEAL_GROUP, "showHealGroup");
		legacyShowKey.put(TrackedBuff.Category.CHARGE, "showCharge");
		legacyShowKey.put(TrackedBuff.Category.SPELLBOOK_SWAP, "showSpellbookSwap");
		legacyShowKey.put(TrackedBuff.Category.CANNON, "showCannon");
		legacyShowKey.put(TrackedBuff.Category.STAFF_OF_THE_DEAD, "showStaffOfTheDead");
		legacyShowKey.put(TrackedBuff.Category.DRAGON_FIRE_SHIELD, "showDragonfireShield");
		legacyShowKey.put(TrackedBuff.Category.SMOULDERING, "showSmouldering");
		legacyShowKey.put(TrackedBuff.Category.BOSS_MECHANIC, "showBossMechanic");
		legacyShowKey.put(TrackedBuff.Category.PICKPOCKET_STUN, "showPickpocketStun");
		legacyShowKey.put(TrackedBuff.Category.TELEPORT, "showTeleport");
		legacyShowKey.put(TrackedBuff.Category.DELAYED_HEAL, "showDelayedHeal");
		legacyShowKey.put(TrackedBuff.Category.SLAYER_TASK, "showSlayerTask");
		legacyShowKey.put(TrackedBuff.Category.BOSS_RESPAWN, "showBossRespawn");
		legacyShowKey.put(TrackedBuff.Category.AFK_LOGOUT, "showAfkLogout");

		// Only these two defaulted to hidden; every other legacy toggle defaulted to shown.
		Set<TrackedBuff.Category> legacyDefaultHidden =
			EnumSet.of(TrackedBuff.Category.AFK_LOGOUT, TrackedBuff.Category.BOSS_RESPAWN);

		for (TrackedBuff buff : TrackedBuff.values())
		{
			if (configManager.getConfiguration(ENABLED_CONFIG_GROUP, buff.name()) != null)
			{
				continue;
			}

			String categoryOverride = configManager.getConfiguration(TIMER_CATEGORY_CONFIG_GROUP, buff.name());
			if ("DISABLED".equals(categoryOverride))
			{
				configManager.setConfiguration(ENABLED_CONFIG_GROUP, buff.name(), false);
				configManager.unsetConfiguration(TIMER_CATEGORY_CONFIG_GROUP, buff.name());
				continue;
			}

			String legacyKey = legacyShowKey.get(buff.getCategory());
			String stored = configManager.getConfiguration(UltimateTimersConfig.GROUP, legacyKey);
			boolean wasShown = stored != null
				? Boolean.parseBoolean(stored)
				: !legacyDefaultHidden.contains(buff.getCategory());
			if (!wasShown)
			{
				configManager.setConfiguration(ENABLED_CONFIG_GROUP, buff.name(), false);
			}
		}

		for (String legacyKey : legacyShowKey.values())
		{
			configManager.unsetConfiguration(UltimateTimersConfig.GROUP, legacyKey);
		}
	}

	private static Map<TrackedBuff, Set<TrackedBuff>> buildSuppressionMap()
	{
		Map<TrackedBuff, Set<TrackedBuff>> map = new EnumMap<>(TrackedBuff.class);
		map.put(TrackedBuff.DIVINE_SUPER_COMBAT, EnumSet.of(
			TrackedBuff.DIVINE_SUPER_ATTACK, TrackedBuff.DIVINE_SUPER_STRENGTH, TrackedBuff.DIVINE_SUPER_DEFENCE));
		map.put(TrackedBuff.DIVINE_BASTION, EnumSet.of(
			TrackedBuff.DIVINE_RANGING, TrackedBuff.DIVINE_SUPER_DEFENCE));
		map.put(TrackedBuff.DIVINE_BATTLEMAGE, EnumSet.of(
			TrackedBuff.DIVINE_MAGIC, TrackedBuff.DIVINE_SUPER_DEFENCE));
		// Antivenom and antipoison are two phases of the same underlying poison-immunity value
		// (RuneLite's own Timers & Buffs plugin keeps them mutually exclusive): venom immunity
		// degrades into plain poison immunity, or drinking antivenom while poisoned jumps
		// straight into venom immunity. Each should instantly replace the other, never linger.
		map.put(TrackedBuff.ANTIVENOM, EnumSet.of(TrackedBuff.ANTIPOISON));
		map.put(TrackedBuff.ANTIPOISON, EnumSet.of(TrackedBuff.ANTIVENOM));
		return map;
	}

	private static Map<TrackedBuff, Set<TrackedBuff>> buildSyncedBuffsMap()
	{
		Map<TrackedBuff, Set<TrackedBuff>> map = new EnumMap<>(TrackedBuff.class);
		addSyncedGroup(map, TrackedBuff.ANTIVENOM, TrackedBuff.ANTIPOISON);
		addSyncedGroup(map, TrackedBuff.ANTIFIRE, TrackedBuff.SUPER_ANTIFIRE);
		return map;
	}

	private static void addSyncedGroup(Map<TrackedBuff, Set<TrackedBuff>> map, TrackedBuff... buffs)
	{
		Set<TrackedBuff> group = EnumSet.noneOf(TrackedBuff.class);
		Collections.addAll(group, buffs);
		for (TrackedBuff buff : buffs)
		{
			map.put(buff, group);
		}
	}

	@Provides
	UltimateTimersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UltimateTimersConfig.class);
	}
}
