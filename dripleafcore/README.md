# DripleafCore

Economy, shop, shards, homes and a dialog-first UI layer for **play.dripleafmc.net**.

Written from scratch — no decompiled jars, no bundled third-party code, nothing you
can't read. Every menu in the plugin is built from one neutral model and rendered
either as a Paper Dialog or as a chest menu, so no feature exists on only one path
and Bedrock players never hit a dead end.

---

## Requirements

- **Java 21**
- **Paper 1.21.7+** (the Dialog API lands in 1.21.7; below that everything falls back to chest menus)
- **Vault** + an economy provider (ExcellentEconomy on your box)
- Optional: PlaceholderAPI, Floodgate

## Building

```bash
gradle build
# -> build/libs/DripleafCore-1.0.0.jar
```

There is no Gradle wrapper checked in; use your own Gradle 8.x, or open the folder in
IntelliJ and let it import the Gradle project.

`sqlite-jdbc` and `mysql-connector-j` are declared in `plugin.yml` under `libraries:`,
so Paper downloads them at first startup. Nothing is shaded into the jar.

---

## What's in it

| Command | What it does |
|---|---|
| `/shop` | Category dialog → item list → quantity slider with a live total |
| `/quickbuy` (`/qb`) | Saved one-click purchase presets; `/qb clear` empties the panel |
| `/sell` | 54-slot holding chest; drag items in, close to sell |
| `/sell hand` | Sells the held stack |
| `/worth [item]` | Price for the held/named item, or the full price book |
| `/sellhistory` | Your last 50 sales with totals and timestamps |
| `/sellmulti` | Your current sell multiplier |
| `/shards [player]` | Shard balance |
| `/shardshop` | Spend shards on gear and boosters |
| `/home [name]` `/homes` `/sethome` `/delhome` | Homes, with a dialog list, a name form and a delete confirmation |
| `/rtp [region]` | Region picker, async chunk search, warmup |
| `/tpa` `/tpaccept` `/tpdeny` | Requests, with an accept/deny dialog for the target |
| `/settings` | Toggles: dialogs on/off, TPA, action-bar receipts, public stats |
| `/stats [player]` | Full stat card |
| `/bounty [player] [amount]` | Bounty list, target picker, amount slider |
| `/leaderboard` | Shards, kills, playtime, blocks broken, money earned |
| `/rules` `/dlhelp` | Rules and the main menu |
| `/dripleafcore reload` | Reloads configs (not commands) |

Every one of those opens a dialog on Java 1.21.7+ and a chest menu otherwise.

### Commands you already own

`commands.yml` turns any command off completely rather than shadowing another plugin.
`HOME` and `SPAWN` are the ones to watch: Essentials and EssentialsSpawn already own
those on your server. `SPAWN` ships **off**. If you want DripleafCore's homes, disable
Essentials' home commands first — two plugins registering `/home` is a coin flip.

---

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | Storage, UI, shards, sell, homes, RTP, TPA, combat, bounty, rules |
| `worth.yml` | **The single price table.** Shop, `/sell`, wands and lore all read it |
| `shop.yml` | Shop categories and buy prices (falls back to `worth.yml`) — **also the entire Quick Buy catalogue** |
| `shard-shop.yml` | What shards buy |
| `messages.yml` | Every message, MiniMessage format |
| `commands.yml` | Per-command on/off |

Sell price = `worth.yml` buy price × `settings.sell-ratio` (0.65) × player multiplier.

---

## Permissions

| Node | Grants |
|---|---|
| `dripleaf.shop.use` | Access to `/shop` — **grant this at Rebirth 2** |
| `dripleaf.rebirth.<1-10>` | Sell multiplier, +5% per level (configurable) |
| `dripleaf.home.slot.<1-10>` | Home slots; highest held node wins, default 2 |
| `dripleaf.teleport.instant` | Skips teleport warmups |
| `dripleaf.rtp.nocooldown` | Skips the RTP cooldown |
| `dripleaf.combat.bypass` | Never combat-tagged |
| `dripleaf.stats.other` | See private stat cards |
| `dripleaf.admin` | `/dripleafcore reload` |

`/sell` and `/worth` are deliberately open to everyone — a new player needs a way to
turn loot into coins on day one, even with the shop still locked.

---

## Placeholders

`%dripleaf_shards%` `%dripleaf_shards_short%` `%dripleaf_money%` `%dripleaf_money_short%`
`%dripleaf_kills%` `%dripleaf_deaths%` `%dripleaf_kdr%` `%dripleaf_killstreak%`
`%dripleaf_best_killstreak%` `%dripleaf_mobs_killed%` `%dripleaf_blocks_broken%`
`%dripleaf_blocks_placed%` `%dripleaf_money_made%` `%dripleaf_money_spent%`
`%dripleaf_playtime%` `%dripleaf_booster%` `%dripleaf_booster_time%` `%dripleaf_homes%`
`%dripleaf_home_slots%` `%dripleaf_sell_multiplier%` `%dripleaf_combat%` `%dripleaf_bounty%`

Feed these to TAB rather than having DripleafCore draw a tab list of its own.

---

## How it stays cheap

These are deliberate choices, not accidents — worth knowing before you change them.

- **No `PlayerMoveEvent` anywhere.** Move fires several times per player per tick. Shard
  activity is sampled inside the two-minute earn task by comparing positions; teleport
  warmups sample position in their own short-lived task. Idle servers pay nothing.
- **No scheduler for combat tags.** Tags carry an expiry and are checked lazily.
- **Batched writes.** Dirty profiles only, one JDBC batch per flush, one IO thread.
  Nothing touches the database on the main thread except schema creation at boot.
- **Homes and bounties live in memory.** `/home` never queries the database.
- **`EnumMap` price table.** Lookup is an array index off the material ordinal, which
  matters when `/sell` walks a double chest in one tick.
- **Configs parsed once** into records and primitive fields; nothing calls `getString()`
  inside an event handler.
- **Dialog callbacks are bounded** — they expire after `ui.callback-lifetime-minutes`
  rather than pinning handlers for a session.
- **Async chunk loading** for RTP, with block reads bounced back to the main thread.

## Quick Buy

A per-player panel of saved purchases. An empty slot reads "Click to choose an item to
buy"; picking one opens **Choose Item**, then **How many to buy?**, then the slot holds
that preset with its running total. Left-click buys instantly, right-click clears it
(dialog viewers get a small Buy / Change amount / Remove menu instead, since dialogs
have no right-click).

The picker is built from `shop.yml` and nothing else — not the material registry — so a
preset can never point at something the server doesn't sell. If you later remove an item
from the shop, existing presets for it show as a red barrier and clear on click rather
than silently buying nothing.

Presets load with the player's profile at pre-login and are written through on change,
so clicking one never waits on the database.

## Known gaps

- **Not compiled yet.** The sandbox this was written in can't reach Maven Central or
  repo.papermc.io, so the tree has been parse-checked but not type-checked against
  paper-api. Expect a first `gradle build` to surface a handful of signature fixes —
  the Dialog API is marked experimental and moves between point releases. Everything
  touching it is funnelled through `ui/DialogRenderer.java`, so that's the one file to
  fix if the API has shifted.
- Quick Buy has no drag-and-drop; presets are set through the picker, not by dropping an
  item into a slot.
- Shard tools (the 9-block pickaxe, instant-tree axe, chest sell-axe) aren't built yet —
  they're not dialog features and they need GriefPrevention claim checks on every
  multi-block break. Next increment.
- `/stats` covers online players only. Offline lookups need an async profile fetch path.
- The rotating Billford-style trade isn't in this drop.
