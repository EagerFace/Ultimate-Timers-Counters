# Ultimate Timers & Counters

Configurable countdown timer overlays for buffs, debuffs, cooldowns, and other timed effects, plus
count-based progress trackers like your current Slayer task.

- **Buffs** - antifire/super antifire, stamina, divine potions (including the individual
  attack/strength/defence/ranging/magic variants and bastion/battlemage), antipoison/antivenom,
  goading potion, prayer regeneration, thrall, Tombs of Amascut supplies, and more
- **Debuffs** - freeze spells, pickpocket stun, teleblock
- **Cooldowns** - dragonfire shield, heal group, spellbook swap, staff of the dead special, and
  other ability/item reuse cooldowns
- **Other timers** - boss respawn (requires the core "Boss Timers" plugin) and an AFK logout
  countdown
- **Counters** - a Slayer task kill counter that works independently of the core Slayer plugin's
  own infobox: it reads the kill count, task name, icon, points, master, and streak directly from
  the game, so it keeps working even with Slayer's infobox turned off

Drinking a combo potion (e.g. divine super combat) only shows one ring instead of one per
sub-effect, and a ring is drawn at its last-seen peak duration so re-dosing mid-buff correctly
resets the countdown.

## Requires RuneLite's core "Timers & Buffs" plugin to be enabled

Most timers are read from RuneLite's own bundled **Timers & Buffs** plugin via the shared
`InfoBoxManager`, rather than re-deriving each buff's remaining duration from scratch. If Timers &
Buffs is disabled, those rings won't appear - there's nothing to read. When a future RuneLite
update changes how a buff is tracked, Timers & Buffs gets updated right along with the client, and
this plugin's rings stay correct automatically.

The Slayer task counter and the AFK logout timer don't depend on Timers & Buffs at all - they read
directly from the game's own varps/varbits.

## Fully configurable layout and appearance

- Size, shape (square through circle via a corner-radius slider), border thickness, fill
  direction, and background/border colors
- Remaining-time text and an optional description label, each independently positioned (inside or
  outside the ring) with shared font/color/border/shadow settings
- Icon shown/hidden and independently positioned/sized
- Per-timer colors, or a single override color for everything at once
- Flashing and lingering (keep showing briefly after expiry) configured separately per category
  (buffs/debuffs/cooldowns/other timers)
- Drag a timer group to reposition it, drag one group onto another to merge them, or right-click
  a timer to detach it into its own group
