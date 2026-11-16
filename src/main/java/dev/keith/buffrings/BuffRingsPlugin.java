package dev.keith.buffrings;

import com.google.inject.Provides;
import java.awt.Color;
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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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
		"overlay"}
)
public class BuffRingsPlugin extends Plugin
{
	static final String DEFAULT_GROUP = "default";

	private static final Color FALLBACK_COLOR = new Color(170, 170, 170);
	private static final String GROUP_ASSIGNMENT_CONFIG_GROUP = "buffringsgroupassignment";
	private static final String GROUP_LAYOUT_CONFIG_GROUP = "buffringsgrouplayout";
	private static final String ORDER_CONFIG_GROUP = "buffringsorder";

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
	private BuffRingsConfig config;

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

	private final Map<TrackedBuff, Long> peakMillis = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, BufferedImage> sampledIcons = new EnumMap<>(TrackedBuff.class);
	private final Map<Integer, BufferedImage> slayerTaskIconCache = new HashMap<>();
	private final Map<TrackedBuff, Long> expiredAtMillis = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, Integer> manualTicks = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, String> buffGroup = new EnumMap<>(TrackedBuff.class);
	private final Map<TrackedBuff, Integer> buffOrder = new EnumMap<>(TrackedBuff.class);
	private final Map<String, BuffRingsGroupOverlay> groupOverlays = new HashMap<>();
	private final Map<String, List<ActiveBuffDisplay>> displaysByGroup = new HashMap<>();

	@Override
	protected void startUp()
	{
		migrateRingDiameter();
		migrateTeleportColor();
		migrateSplitCategories();
		migrateRingShape();
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "sizePreset");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "lockAspectRatio");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "debugShowBuffs");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "debugShowDebuffs");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "debugShowCooldowns");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "debugShowOtherTimers");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "debugShowCounters");

		peakMillis.clear();
		expiredAtMillis.clear();
		manualTicks.clear();
		displaysByGroup.clear();
		groupOverlays.clear();
		buffGroup.clear();
		buffOrder.clear();

		Set<String> groupNames = new HashSet<>();
		groupNames.add(DEFAULT_GROUP);
		for (TrackedBuff buff : TrackedBuff.values())
		{
			String group = loadBuffGroup(buff);
			buffGroup.put(buff, group);
			groupNames.add(group);
			buffOrder.put(buff, loadBuffOrder(buff));
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

		requestIcons();
		initializeEnumDefaults();
	}

	/**
	 * The single "ringDiameter" setting was replaced by independent "ringWidth"/"ringHeight"
	 * settings. Carry over anyone's existing saved diameter into both new keys the first time
	 * this version starts, then remove the old key so this only ever runs once.
	 */
	private void migrateRingDiameter()
	{
		String oldDiameter = configManager.getConfiguration(BuffRingsConfig.GROUP, "ringDiameter");
		if (oldDiameter == null)
		{
			return;
		}

		if (configManager.getConfiguration(BuffRingsConfig.GROUP, "ringWidth") == null)
		{
			configManager.setConfiguration(BuffRingsConfig.GROUP, "ringWidth", oldDiameter);
		}
		if (configManager.getConfiguration(BuffRingsConfig.GROUP, "ringHeight") == null)
		{
			configManager.setConfiguration(BuffRingsConfig.GROUP, "ringHeight", oldDiameter);
		}
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "ringDiameter");
	}

	/**
	 * The single "teleportColor" setting was split into independent "homeTeleportColor" and
	 * "minigameTeleportColor" settings. Carry over anyone's existing saved color into both new
	 * keys the first time this version starts, then remove the old key so this only ever runs
	 * once.
	 */
	private void migrateTeleportColor()
	{
		String oldColor = configManager.getConfiguration(BuffRingsConfig.GROUP, "teleportColor");
		if (oldColor == null)
		{
			return;
		}

		if (configManager.getConfiguration(BuffRingsConfig.GROUP, "homeTeleportColor") == null)
		{
			configManager.setConfiguration(BuffRingsConfig.GROUP, "homeTeleportColor", oldColor);
		}
		if (configManager.getConfiguration(BuffRingsConfig.GROUP, "minigameTeleportColor") == null)
		{
			configManager.setConfiguration(BuffRingsConfig.GROUP, "minigameTeleportColor", oldColor);
		}
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "teleportColor");
	}

	/**
	 * The "ringShape" CIRCLE/SQUARE enum was replaced by a numeric "cornerRadius" (0-100) so shapes
	 * in between a square and a circle are possible. Carry over anyone's existing saved shape into
	 * the matching radius extreme the first time this version starts, then remove the old key so
	 * this only ever runs once.
	 */
	private void migrateRingShape()
	{
		String oldShape = configManager.getConfiguration(BuffRingsConfig.GROUP, "ringShape");
		if (oldShape == null)
		{
			return;
		}

		if (configManager.getConfiguration(BuffRingsConfig.GROUP, "cornerRadius") == null)
		{
			configManager.setConfiguration(BuffRingsConfig.GROUP, "cornerRadius",
				"SQUARE".equals(oldShape) ? "0" : "100");
		}
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "ringShape");
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
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "showCombatEffect");
		copyToUnsetKeys("combatEffectColor",
			"vengeanceColor", "teleblockColor", "healGroupColor", "chargeColor", "spellbookSwapColor");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "combatEffectColor");

		// "showEquipment"/"equipmentColor" were likewise removed entirely.
		copyToUnsetKeys("showEquipment",
			"showCannon", "showStaffOfTheDead", "showDragonfireShield", "showSmouldering");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "showEquipment");
		copyToUnsetKeys("equipmentColor",
			"cannonColor", "staffOfTheDeadColor", "dragonfireShieldColor", "smoulderingColor");
		configManager.unsetConfiguration(BuffRingsConfig.GROUP, "equipmentColor");

		// "showBossMechanic"/"bossMechanicColor" are still live settings (Abyssal Sire/Scurrius/
		// GWA stayed under them) - only pickpocket stun was carved out, so just seed its new
		// keys from the old ones without touching the old keys themselves.
		copyToUnsetKeys("showBossMechanic", "showPickpocketStun");
		copyToUnsetKeys("bossMechanicColor", "pickpocketStunColor");
	}

	private void copyToUnsetKeys(String sourceKey, String... destKeys)
	{
		String sourceValue = configManager.getConfiguration(BuffRingsConfig.GROUP, sourceKey);
		if (sourceValue == null)
		{
			return;
		}

		for (String destKey : destKeys)
		{
			if (configManager.getConfiguration(BuffRingsConfig.GROUP, destKey) == null)
			{
				configManager.setConfiguration(BuffRingsConfig.GROUP, destKey, sourceValue);
			}
		}
	}

	/**
	 * RuneLite's config panel crashes with an uncaught NullPointerException (not the
	 * IllegalArgumentException it actually catches) when an enum ConfigItem has never been
	 * saved, since it calls Enum.valueOf() on a null stored value. Writing the default the
	 * first time this plugin starts guarantees the stored value is never null.
	 */
	private void initializeEnumDefaults()
	{
		ensureEnumDefault("textPosition", config.textPosition());
		ensureEnumDefault("descriptionPosition", config.descriptionPosition());
		ensureEnumDefault("iconPosition", config.iconPosition());
		ensureEnumDefault("fillDirection", config.fillDirection());
	}

	private void ensureEnumDefault(String keyName, Enum<?> defaultValue)
	{
		if (configManager.getConfiguration(BuffRingsConfig.GROUP, keyName) == null)
		{
			configManager.setConfiguration(BuffRingsConfig.GROUP, keyName, defaultValue.name());
		}
	}

	@Override
	protected void shutDown()
	{
		for (BuffRingsGroupOverlay groupOverlay : groupOverlays.values())
		{
			overlayManager.remove(groupOverlay);
		}
		groupOverlays.clear();
		buffGroup.clear();
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

		boolean debugForceShowAll = config.debugForceShowAll();
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
			// still respects that timer's own show/hide toggle - it's meant to preview/arrange the
			// timers you actually use, not every timer that exists.
			if (debugForceShowAll && isCategoryEnabled(buff.getCategory()))
			{
				addDisplay(newDisplaysByGroup, buff,
					new ActiveBuffDisplay(buff, debugRemainingMillis, debugPeakMillis, colorFor(buff),
						iconFor(buff, dynamicIcons)));
				continue;
			}

			long remaining = currentMillis.get(buff);
			boolean enabled = isCategoryEnabled(buff.getCategory());

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
			if (!enabled || !isLingerEnabled(buff.getKind()) || suppressedThisTick.contains(buff))
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

			if (nowMillis - expiredAt >= lingerMillisFor(buff.getKind()))
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
			BuffRingsGroupOverlay sourceOverlay = groupOverlays.remove(sourceGroup);
			if (sourceOverlay != null)
			{
				overlayManager.remove(sourceOverlay);
			}
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (!(event.getOverlay() instanceof BuffRingsGroupOverlay))
		{
			return;
		}

		BuffRingsGroupOverlay groupOverlay = (BuffRingsGroupOverlay) event.getOverlay();
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

	private BuffRingsGroupOverlay ensureGroupOverlay(String groupName)
	{
		return groupOverlays.computeIfAbsent(groupName, name ->
		{
			BuffRingsGroupOverlay groupOverlay = new BuffRingsGroupOverlay(this, config, client, tooltipManager, name);
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
		if (config.overrideColors())
		{
			return config.overrideColor();
		}
		Color configured = configuredColorFor(buff);
		if (configured != null && configured.getAlpha() > 0)
		{
			return configured;
		}
		return FALLBACK_COLOR;
	}

	private Color configuredColorFor(TrackedBuff buff)
	{
		switch (buff)
		{
			case ANTIFIRE:
				return config.antifireColor();
			case SUPER_ANTIFIRE:
				return config.superAntifireColor();
			case STAMINA:
				return config.staminaColor();
			case ANTIVENOM:
				return config.antivenomColor();
			case ANTIPOISON:
				return config.antipoisonColor();
			case GOADING:
				return config.goadingColor();
			case DIVINE_SUPER_COMBAT:
				return config.divineSuperCombatColor();
			case DIVINE_BASTION:
				return config.divineBastionColor();
			case DIVINE_BATTLEMAGE:
				return config.divineBattlemageColor();
			case DIVINE_SUPER_ATTACK:
				return config.divineSuperAttackColor();
			case DIVINE_SUPER_STRENGTH:
				return config.divineSuperStrengthColor();
			case DIVINE_SUPER_DEFENCE:
				return config.divineSuperDefenceColor();
			case DIVINE_RANGING:
				return config.divineRangingColor();
			case DIVINE_MAGIC:
				return config.divineMagicColor();
			case PRAYER_REGEN:
				return config.prayerRegenColor();
			case THRALL:
				return config.thrallColor();
			case BIND:
			case SNARE:
			case ENTANGLE:
			case ICE_RUSH:
			case ICE_BURST:
			case ICE_BLITZ:
			case ICE_BARRAGE:
				return config.freezeColor();
			case SHADOW_VEIL:
			case WARD_OF_ARCEUUS:
			case DEATH_CHARGE:
			case MARK_OF_DARKNESS:
			case SHADOW_VEIL_COOLDOWN:
			case RESURRECT_THRALL_COOLDOWN:
			case WARD_OF_ARCEUUS_COOLDOWN:
			case DEATH_CHARGE_COOLDOWN:
			case CORRUPTION_COOLDOWN:
			case MARK_OF_DARKNESS_COOLDOWN:
				return config.necromancyColor();
			case OVERLOAD:
			case OVERLOAD_RAID:
			case BLIGHTED_OVERLOAD:
				return config.overloadColor();
			case MAGIC_IMBUE:
				return config.magicImbueColor();
			case IMBUED_HEART:
				return config.imbuedHeartColor();
			case PRAYER_ENHANCE:
				return config.prayerEnhanceColor();
			case FARMERS_AFFINITY:
				return config.farmersAffinityColor();
			case MENAPHITE_REMEDY:
				return config.menaphiteRemedyColor();
			case SURGE_POTION:
				return config.surgePotionColor();
			case MOONLIGHT_POTION:
				return config.moonlightPotionColor();
			case LIQUID_ADRENALINE:
			case SILK_DRESSING:
			case BLESSED_CRYSTAL_SCARAB:
			case SMELLING_SALTS:
				return config.raidSupplyColor();
			case VENGEANCE:
				return config.vengeanceColor();
			case TELEBLOCK:
				return config.teleblockColor();
			case HEAL_GROUP:
				return config.healGroupColor();
			case CHARGE:
				return config.chargeColor();
			case SPELLBOOK_SWAP:
				return config.spellbookSwapColor();
			case CANNON:
			case CANNON_REPAIR:
				return config.cannonColor();
			case STAFF_OF_THE_DEAD:
				return config.staffOfTheDeadColor();
			case DRAGON_FIRE_SHIELD:
				return config.dragonfireShieldColor();
			case SMOULDERING_HEART:
			case SMOULDERING_GLAND:
				return config.smoulderingColor();
			case ABYSSAL_SIRE_STUN:
			case SCURRIUS_FOOD_PILE:
			case GOD_WARS_ALTAR:
				return config.bossMechanicColor();
			case PICKPOCKET_STUN:
				return config.pickpocketStunColor();
			case HOME_TELEPORT:
				return config.homeTeleportColor();
			case MINIGAME_TELEPORT:
				return config.minigameTeleportColor();
			case DELAYED_HEAL:
				return config.delayedHealColor();
			case SLAYER_TASK:
				return config.slayerTaskColor();
			case BOSS_RESPAWN:
				return config.bossRespawnColor();
			case AFK_LOGOUT:
				return config.afkLogoutColor();
			default:
				return null;
		}
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

	private boolean isLingerEnabled(TrackedBuff.Kind kind)
	{
		switch (kind)
		{
			case BUFF:
				return config.enableLinger();
			case DEBUFF:
				return config.enableDebuffLinger();
			case COOLDOWN:
				return config.enableCooldownLinger();
			case OTHER_TIMER:
				return config.enableOtherTimerLinger();
			default:
				return false;
		}
	}

	private long lingerMillisFor(TrackedBuff.Kind kind)
	{
		switch (kind)
		{
			case BUFF:
				return config.lingerSeconds() * 1000L;
			case DEBUFF:
				return config.debuffLingerSeconds() * 1000L;
			case COOLDOWN:
				return config.cooldownLingerSeconds() * 1000L;
			case OTHER_TIMER:
				return config.otherTimerLingerSeconds() * 1000L;
			default:
				return 0L;
		}
	}

	private boolean isCategoryEnabled(TrackedBuff.Category category)
	{
		switch (category)
		{
			case ANTIFIRE:
				return config.showAntifire();
			case STAMINA:
				return config.showStamina();
			case DIVINE:
				return config.showDivine();
			case ANTIPOISON:
				return config.showAntipoison();
			case GOADING:
				return config.showGoading();
			case PRAYER_REGEN:
				return config.showPrayerRegen();
			case THRALL:
				return config.showThrall();
			case FREEZE:
				return config.showFreeze();
			case NECROMANCY:
				return config.showNecromancy();
			case OVERLOAD:
				return config.showOverload();
			case MAGIC_IMBUE:
				return config.showMagicImbue();
			case IMBUED_HEART:
				return config.showImbuedHeart();
			case PRAYER_ENHANCE:
				return config.showPrayerEnhance();
			case FARMERS_AFFINITY:
				return config.showFarmersAffinity();
			case MENAPHITE_REMEDY:
				return config.showMenaphiteRemedy();
			case SURGE_POTION:
				return config.showSurgePotion();
			case MOONLIGHT_POTION:
				return config.showMoonlightPotion();
			case RAID_SUPPLY:
				return config.showRaidSupply();
			case VENGEANCE:
				return config.showVengeance();
			case TELEBLOCK:
				return config.showTeleblock();
			case HEAL_GROUP:
				return config.showHealGroup();
			case CHARGE:
				return config.showCharge();
			case SPELLBOOK_SWAP:
				return config.showSpellbookSwap();
			case CANNON:
				return config.showCannon();
			case STAFF_OF_THE_DEAD:
				return config.showStaffOfTheDead();
			case DRAGON_FIRE_SHIELD:
				return config.showDragonfireShield();
			case SMOULDERING:
				return config.showSmouldering();
			case BOSS_MECHANIC:
				return config.showBossMechanic();
			case PICKPOCKET_STUN:
				return config.showPickpocketStun();
			case TELEPORT:
				return config.showTeleport();
			case DELAYED_HEAL:
				return config.showDelayedHeal();
			case SLAYER_TASK:
				return config.showSlayerTask();
			case BOSS_RESPAWN:
				return config.showBossRespawn();
			case AFK_LOGOUT:
				return config.showAfkLogout();
			default:
				return false;
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
	BuffRingsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BuffRingsConfig.class);
	}
}
