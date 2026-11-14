package dev.keith.buffrings;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Buff Rings",
	description = "Countdown ring overlays for antifire, stamina, and divine potion timers",
	tags = {"buff", "timer", "potion", "antifire", "divine", "stamina", "overlay"}
)
public class BuffRingsPlugin extends Plugin
{
	/**
	 * Divine potions that combine multiple effects also set the varbits for each individual
	 * effect they contain. Without suppression, drinking e.g. divine super combat would show
	 * four overlapping rings (itself plus attack/strength/defence) that all drain in lockstep.
	 */
	private static final Map<TrackedBuff, Set<TrackedBuff>> SUPPRESSED_BY = buildSuppressionMap();

	@Inject
	private Client client;

	@Inject
	private BuffRingsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BuffRingsOverlay overlay;

	private final Map<TrackedBuff, Integer> peakTicks = new EnumMap<>(TrackedBuff.class);

	private List<ActiveBuffDisplay> activeDisplays = Collections.emptyList();

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		peakTicks.clear();
		activeDisplays = Collections.emptyList();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		peakTicks.clear();
		activeDisplays = Collections.emptyList();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Map<TrackedBuff, Integer> currentTicks = new EnumMap<>(TrackedBuff.class);
		for (TrackedBuff buff : TrackedBuff.values())
		{
			currentTicks.put(buff, client.getVarbitValue(buff.getVarbit()) * buff.getTicksPerUnit());
		}

		for (Map.Entry<TrackedBuff, Set<TrackedBuff>> entry : SUPPRESSED_BY.entrySet())
		{
			if (currentTicks.get(entry.getKey()) > 0)
			{
				for (TrackedBuff suppressed : entry.getValue())
				{
					currentTicks.put(suppressed, 0);
				}
			}
		}

		List<ActiveBuffDisplay> displays = new ArrayList<>();
		for (TrackedBuff buff : TrackedBuff.values())
		{
			int ticks = currentTicks.get(buff);

			if (ticks <= 0 || !isCategoryEnabled(buff.getCategory()))
			{
				peakTicks.remove(buff);
				continue;
			}

			Integer peak = peakTicks.get(buff);
			if (peak == null || ticks > peak)
			{
				peak = ticks;
				peakTicks.put(buff, peak);
			}

			double fraction = peak <= 0 ? 0 : (double) ticks / peak;
			int seconds = (int) Math.round(ticks * 0.6);
			displays.add(new ActiveBuffDisplay(buff, fraction, seconds));
		}

		this.activeDisplays = displays;
	}

	List<ActiveBuffDisplay> getActiveDisplays()
	{
		return activeDisplays;
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
		return map;
	}

	@Provides
	BuffRingsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BuffRingsConfig.class);
	}
}
