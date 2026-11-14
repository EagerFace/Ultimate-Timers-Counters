# Buff Rings

Shows a shrinking countdown ring (pie-chart style) over active potion/buff timers that the
game exposes an exact tick countdown for:

- Antifire / Super antifire
- Stamina potion (run-energy-drain immunity)
- Divine potions (super combat, bastion, battlemage, and the individual attack/strength/
  defence/ranging/magic variants)

Drinking a combo divine potion (e.g. divine super combat) only shows one ring instead of one
per sub-effect.

Each ring is drawn at its last-seen peak duration, so re-dosing mid-buff correctly resets the
countdown. Drag the overlay (default top-left) to reposition it in-game.

Not covered: Nightmare Zone / Chambers of Xeric overload potions. The game only exposes a
"refreshes remaining" dose counter for those, not a tick countdown, so a smooth ring would be
a guess rather than an accurate timer.
