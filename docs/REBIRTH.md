# Rebirth — Staff Guide

> Everything you need to run, change and extend the rebirth system without a
> developer. Written for someone who has never opened a config file before.

**Files you will touch**

| File | What lives in it |
|---|---|
| `rebirth/tiers.yml` | Costs, requirements and reward *values* |
| `rebirth/unlocks.yml` | What each tier unlocks, and the command that grants it |
| `rebirth/rebirth.yml` | Behaviour — sacrifices, visibility, confirmation, broadcasts |
| `messages.yml` | Every word a player reads |

After editing any of them: **`/dripleafcore reload rebirth`**. No restart.

---

## What changed from the Skript version

| The Skript did | This does |
|---|---|
| Hardcoded 25 tiers in a function with pipe-delimited strings | `tiers.yml`, parsed once into immutable records. Nothing in the code knows the number 25. |
| Rebuilt a 25-branch `if` chain on every menu open | One map lookup |
| Ran up to 25 `hasPermission` checks to find the current tier | Reads one integer from player data |
| Read every statistic through a PlaceholderAPI string parse, per lore line | `player.getStatistic(...)`, evaluated once per screen |
| Re-checked only *money* at confirmation | Re-checks **every** requirement at confirmation |
| Kept the executing list and the info menu as two separate lists — which drifted | One list. Display and grant are the same config entry. |
| Identified menus by their stripped inventory title | A custom `InventoryHolder` |
| Had one front end | One screen description, drawn as a dialog or a chest GUI — see [UI.md](UI.md) |

**The drift was real.** The executing Skript granted 2 keys at tier 5, 3 at
tier 10, 3 at tier 15 and 4 at tier 20. The info menu told players 1, 1, 2 and
3. The executing behaviour is what players actually received, so that is what
the table below uses, and the info screen now reads from the same place.

---

## 1. How to add a new rebirth tier

A complete worked example. We are adding **tier 26**.

### Step 1 — open the file

`plugins/DripleafCore/rebirth/tiers.yml`

### Step 2 — copy the last block

Scroll to the bottom. Copy everything from `  25:` to the end, paste it below,
and change the `25:` to `26:`. Indentation matters in YAML: `26:` sits two
spaces in, and everything under it sits four.

### Step 3 — edit it

```yaml
  # ── Tier 26 — XXVI ─────────────────────────────────────────────
  26:
    numeral: "XXVI"                  # shown in menus; any text
    cost: 200m                       # money; k / m / b / t all work
    soul-cost-multiplier: 2.0        # Soul path costs 400m

    requirements:                    # ALL must be met
      mcmmo-power: 3900
      stat:
        type: MINE_BLOCK             # a Bukkit Statistic — see §6
        material: ANCIENT_DEBRIS     # what was mined
        amount: 2000
        display: "Ancient Debris Mined"   # what players see

    rewards:
      cash: 1.5m                     # starting money after the wipe
      keys: 5                        # crate keys
      multiplier: 215                # +215% sell and jobs money
      souls: 6                       # SOUL PATH ONLY

    unlocks:                         # display only — see §2 below
      - "Unlock: +100 chest shops (600 max)"
      - "Unlock: +1 player warp (26 max)"

    commands:                        # console, on completion
      - "lp user <player> parent add rebirth-26"
      - "excellentcrates key give <player> rebirth <keys>"

    soul-commands: []                # extra commands, Soul path only
```

### Step 4 — add its unlocks

Open `rebirth/unlocks.yml` and add a matching block. Each entry pairs the text
players see with the command that actually grants it, so the two can never drift:

```yaml
  26:
    - display: "Unlock: +100 chest shops (600 max)"
      commands:
        - "lp user <player> permission set shop.limit.600 true"
    - display: "Unlock: +1 player warp (26 max)"
      commands:
        - "lp user <player> permission set playerwarps.limit.26 true"
```

### Step 5 — reload

```
/dripleafcore reload rebirth
```

That is it. No restart, no code change, no other file. The console tells you how
many tiers loaded; if it says 25, something in your block did not parse and the
console line above it says exactly what and where.

> **Tiers must be consecutive from 1.** Jumping from 25 to 27 is reported at
> load, because a gap means a player can reach a tier they can never leave.

**Substitutions available in any command:** `<player>` `<tier>` `<numeral>`
`<keys>` `<cash>` `<souls>` `<multiplier>` `<cost>`

---

## 2. How to change an existing tier's requirements

Edit the tier's `requirements:` block and reload. That is all.

> ### ⚠ Lowering a requirement does not reach backwards
>
> A player who already passed tier 12 stays at tier 12. Lowering tier 12's
> requirement makes it easier for everyone who has not reached it yet, and does
> nothing at all for anyone who has.
>
> The same is true upwards: raising a requirement never demotes anyone. A
> player's tier is stored, not recomputed, precisely so that changing the ladder
> cannot retroactively take something away from someone who earned it.
>
> If you genuinely need to move a specific player, use
> `/rebirth admin set <player> <tier>`. It is logged.

**Removing a requirement**: delete its lines. Deleting the whole
`requirements:` block leaves a tier gated on cost alone, which is valid.

**Raising a cost mid-season**: safe. Players see the new figure the moment they
reopen the menu, and the balance check at confirmation always uses the live
number.

---

## 3. How to add a new requirement type

The four that ship:

```yaml
requirements:
  mcmmo-power: 1500              # mcMMO power level
  playtime: 40                   # hours played, from DripleafCore's tracking
  permission: "some.node"        # holding a permission node
  stat:                          # any Bukkit statistic — see §6
    type: KILL_ENTITY
    entity: BLAZE
    amount: 500
    display: "Blazes Killed"
```

Anything beyond that needs a small code change, and it is genuinely small: one
class and one `case` label.

1. Create a class in `rebirth/requirement/` implementing `Requirement`:

```java
public final class QuestRequirement implements Requirement {

    private final int quests;

    public QuestRequirement(int quests) {
        this.quests = quests;
    }

    @Override public String type()    { return "quests"; }
    @Override public String display() { return "Quests Completed"; }
    @Override public double target()  { return quests; }
    @Override public Material icon()  { return Material.WRITTEN_BOOK; }

    @Override
    public double progress(Player player) {
        return yourQuestPlugin.completedCount(player);
    }
}
```

2. Add a `case` to `RequirementFactory.build`:

```java
case "quests" -> {
    int amount = node.integer(key, 0, 0, 100_000);
    if (amount > 0) {
        out.add(new QuestRequirement(amount));
    }
}
```

3. Use it: `quests: 25`

Progress bars, the "still needed" text on locked buttons, the tier browser and
the placeholders all pick it up with no further work — they read `progress()`
and `target()` and nothing else.

---

## 4. The tier table

Costs and requirements exactly as they stand. Soul cost is the standard cost
times `soul-cost-multiplier`, currently `2.0` on every tier.

| # | Numeral | Standard cost | Soul cost | mcMMO | Requirement | Amount | Cash | Keys | Multiplier | Souls |
|---:|---|---:|---:|---:|---|---:|---:|---:|---:|---:|
| 1 | I | 90k | 180k | 150 | Stone mined | 2,500 | 10k | 1 | +4% | 1 |
| 2 | II | 225k | 450k | 300 | Zombies | 625 | 15k | 1 | +8% | 1 |
| 3 | III | 450k | 900k | 450 | Oak logs | 1,250 | 20k | 1 | +12% | 1 |
| 4 | IV | 675k | 1.35m | 600 | Fish caught | 125 | 25k | 1 | +16% | 1 |
| 5 | V | 1.125m | 2.25m | 750 | Deepslate | 2,500 | 50k | 2 | +20% | 1 |
| 6 | VI | 1.4875m | 2.975m | 900 | Skeletons | 625 | 60k | 1 | +26% | 2 |
| 7 | VII | 2.125m | 4.25m | 1050 | Netherrack | 6,250 | 70k | 1 | +32% | 2 |
| 8 | VIII | 2.7625m | 5.525m | 1200 | Creepers | 375 | 80k | 1 | +38% | 2 |
| 9 | IX | 3.4m | 6.8m | 1350 | Obsidian | 250 | 90k | 1 | +44% | 2 |
| 10 | X | 4.25m | 8.5m | 1500 | Quartz ore | 6,250 | 150k | 3 | +50% | 2 |
| 11 | XI | 6.375m | 12.75m | 1650 | Blazes | 625 | 160k | 2 | +58% | 3 |
| 12 | XII | 8.5m | 17m | 1800 | End stone | 6,250 | 170k | 2 | +66% | 3 |
| 13 | XIII | 10.625m | 21.25m | 1950 | Nether gold | 3,750 | 180k | 2 | +74% | 3 |
| 14 | XIV | 12.75m | 25.5m | 2100 | Endermen | 1,250 | 190k | 2 | +82% | 3 |
| 15 | XV | 16m | 32m | 2250 | Ancient debris | 125 | 300k | 3 | +90% | 3 |
| 16 | XVI | 20m | 40m | 2400 | Wither skeletons | 375 | 320k | 2 | +100% | 4 |
| 17 | XVII | 26m | 52m | 2550 | Fish caught | 625 | 340k | 2 | +110% | 4 |
| 18 | XVIII | 32m | 64m | 2700 | Obsidian | 1,250 | 360k | 2 | +120% | 4 |
| 19 | XIX | 40m | 80m | 2850 | Ancient debris | 375 | 380k | 2 | +130% | 4 |
| 20 | XX | 48.75m | 97.5m | 3000 | Deepslate | 25,000 | 500k | 4 | +140% | 4 |
| 21 | XXI | 60m | 120m | 3150 | Stone mined | 62,500 | 550k | 3 | +152% | 5 |
| 22 | XXII | 75m | 150m | 3300 | Zombies | 6,250 | 600k | 3 | +164% | 5 |
| 23 | XXIII | 93.75m | 187.5m | 3450 | Oak logs | 12,500 | 650k | 3 | +176% | 5 |
| 24 | XXIV | 112.5m | 225m | 3600 | Creepers | 2,500 | 700k | 3 | +188% | 5 |
| 25 | XXV | 150m | 300m | 3750 | Wither skeletons | 1,250 | 1m | 5 | +200% | 5 |

---

## 5. The unlock table

| Tier | Unlocks |
|---:|---|
| 1 | `/recipe`; +5 chest shops (15); +1 player warp (1) |
| 2 | Server shop access; +3 auction slots (8); +5 chest shops (20); +1 warp (2) |
| 3 | `/craft`; +10 chest shops (30); +1 warp (3) |
| 4 | +10 chest shops (40); +1 warp (4) |
| 5 | Silver chat tag; aura particles; +10 chest shops (50); +1 warp (5) |
| 6 | +4 auction slots (12); +10 chest shops (60); +1 warp (6) |
| 7 | `/condense`; +10 chest shops (70); +1 warp (7) |
| 8 | +10 chest shops (80); +1 warp (8) |
| 9 | +10 chest shops (90); +1 warp (9) |
| 10 | `/disposal` **(see below)**; global login broadcast; +10 chest shops (100); +1 warp (10) |
| 11 | `/enderchest`; +20 chest shops (120); +1 warp (11) |
| 12 | +20 chest shops (140); +1 warp (12) |
| 13 | +6 auction slots (18); +20 chest shops (160); +1 warp (13) |
| 14 | +20 chest shops (180); +1 warp (14) |
| 15 | `/hat`; 1× iron golem spawner; +20 chest shops (200); +1 warp (15) |
| 16 | +20 chest shops (220); +1 warp (16) |
| 17 | +20 chest shops (240); +1 warp (17) |
| 18 | +4 auction slots (22); +20 chest shops (260); +1 warp (18) |
| 19 | +20 chest shops (280); +1 warp (19) |
| 20 | Custom prefix; custom name colours; infinite fly in claims; +3 auction slots (25 max); +20 chest shops (300); +1 warp (20) |
| 21 | +25 chest shops (325); +1 warp (21) |
| 22 | +25 chest shops (350); +1 warp (22) |
| 23 | +25 chest shops (375); +1 warp (23) |
| 24 | +25 chest shops (400); +1 warp (24) |
| 25 | "The Overlord" title; +100 chest shops (500 max); +1 warp (25 max) |

### ⚠ Two things in this table need your decision

**`/disposal` is listed at tier 10.** The executing Skript unlocked it at 10;
the old info menu said 17. Either is defensible and it is a real design
decision, so the default is the behaviour that was actually running. To move it
to 17, cut the `/disposal` entry out of `unlocks.yml` under `10:` and paste it
under `17:`, then reload.

**The chest shop, auction slot and player warp permission nodes are
placeholders.** The plugins that own those limits were never confirmed, so
`unlocks.yml` names them plausibly (`shop.limit.15`, `auction.limit.8`,
`playerwarps.limit.1`) rather than correctly. Find the real node in each
plugin's documentation and replace them before going live. Every affected line
is flagged in the file itself.

Until they are replaced, those unlocks display correctly to players and grant
nothing.

---

## 6. Statistic reference

`type:` is any [Bukkit `Statistic`](https://jd.papermc.io/paper/1.21/org/bukkit/Statistic.html).
Which qualifier it needs depends on its kind:

| Statistic kind | Needs | Example |
|---|---|---|
| `BLOCK` / `ITEM` | `material:` | `MINE_BLOCK` + `material: STONE` |
| `ENTITY` | `entity:` | `KILL_ENTITY` + `entity: ZOMBIE` |
| `UNTYPED` | nothing | `FISH_CAUGHT` |

Get it wrong and the requirement is skipped with a named line in the console at
load — never a stack trace, and never a broken menu.

### The combinations the shipped tiers use

| Requirement | `type:` | Qualifier |
|---|---|---|
| Stone mined | `MINE_BLOCK` | `material: STONE` |
| Deepslate | `MINE_BLOCK` | `material: DEEPSLATE` |
| Oak logs | `MINE_BLOCK` | `material: OAK_LOG` |
| Netherrack | `MINE_BLOCK` | `material: NETHERRACK` |
| Obsidian | `MINE_BLOCK` | `material: OBSIDIAN` |
| Quartz ore | `MINE_BLOCK` | `material: NETHER_QUARTZ_ORE` |
| End stone | `MINE_BLOCK` | `material: END_STONE` |
| Nether gold | `MINE_BLOCK` | `material: NETHER_GOLD_ORE` |
| Ancient debris | `MINE_BLOCK` | `material: ANCIENT_DEBRIS` |
| Zombies | `KILL_ENTITY` | `entity: ZOMBIE` |
| Skeletons | `KILL_ENTITY` | `entity: SKELETON` |
| Creepers | `KILL_ENTITY` | `entity: CREEPER` |
| Blazes | `KILL_ENTITY` | `entity: BLAZE` |
| Endermen | `KILL_ENTITY` | `entity: ENDERMAN` |
| Wither skeletons | `KILL_ENTITY` | `entity: WITHER_SKELETON` |
| Fish caught | `FISH_CAUGHT` | — |

Other useful ones: `MINE_BLOCK` for any block, `CRAFT_ITEM`, `USE_ITEM`,
`BREAK_ITEM`, `DAMAGE_DEALT`, `WALK_ONE_CM`, `PLAY_ONE_MINUTE`, `DEATHS`.

> Statistics are per-player and vanilla-tracked. They are **not** reset by a
> rebirth — a tier requiring 2,500 stone mined stays satisfied forever once it
> has been. That is deliberate: requirements gate *reaching* a tier, and the
> sacrifice is what makes rebirthing cost something.

---

## 7. Behaviour settings

`rebirth/rebirth.yml`:

```yaml
sacrifice:
  money: true                       # wipe the balance
  mcmmo: true                       # reset every skill
  mcmmo-command: "mmoedit <player> all 0"

tier-source: data                   # data | permission
permission-format: "group.rebirth-<tier>"
migrate-from-luckperms: true

info-visibility-block: 5            # tiers revealed in blocks of five
type-to-confirm-above: 15           # above this, type the numeral to confirm
cooldown-seconds: 0                 # 0 = no cooldown between rebirths
requirement-cache-millis: 2000      # never affects the confirmation check

broadcast:
  enabled: true
  standard: true
  soul: true
```

**`tier-source`.** `data` reads an integer from player data and is free.
`permission` walks the LuckPerms groups downwards and costs up to one lookup per
tier, per menu open, per player — it exists to match the old Skript exactly if
you need that, and it is not the default for a reason.

The LuckPerms group grant stays as a reward command in `tiers.yml`, so external
plugins keying off `group.rebirth-N` keep working. It simply stops being the
source of truth.

**`migrate-from-luckperms`.** On join, a player with no stored tier who holds a
`rebirth-N` group has that tier imported into player data, once, and logged. It
is idempotent by construction: a player with a stored tier is never re-read.

**`info-visibility-block`.** A player at tier *T* sees tiers up to
`(floor(T / block) + 1) × block`:

| Current tier | Visible |
|---|---|
| 0–4 | 1–5 |
| 5–9 | 1–10 |
| 10–14 | 1–15 |
| 15–19 | 1–20 |
| 20–24 | 1–25 |

Beyond that, tiers show as grey silhouettes reading *"Reach Rebirth XXV to
reveal this tier"*. They are never hidden — a visible locked goal is
motivating, an empty grid is not.

**`type-to-confirm-above`.** Above this tier, confirming requires typing the
tier's numeral. At 48 million an accidental click is hours of play. Set it to
`0` to require it always, or above your highest tier to never require it.

---

## 8. What happens when a player rebirths

The order is fixed and is not negotiable:

1. **Re-validate every requirement** — not just money.
2. **Charge the cost.**
3. **Apply sacrifices** — balance to zero, mcMMO reset.
4. **Persist the tier**, then run `commands`, then `soul-commands` if soul.
5. **Inject starting cash.**
6. **Grant keys and souls.**
7. **Run the unlock commands** for the tier just reached.
8. **Broadcast, sound, panel.**

The tier is persisted *before* the payout, deliberately: a crash at step 5 loses
rewards, which staff can hand back. A crash after a payout that never persisted
loses the tier, which they cannot reconstruct.

If any step throws, the full state — player, tier, path, and which stage it
reached — is written to `logs/rebirth.log` before the error propagates. A
half-applied rebirth is the worst possible outcome and staff need enough to
rebuild it by hand.

---

## 9. Commands

| Command | Permission | What it does |
|---|---|---|
| `/rebirth` | `dripleaf.rebirth` | The path menu |
| `/rebirth info` · `/rebirthinfo` | `dripleaf.rebirth` | The tier browser |
| `/rebirth stats [player]` | `dripleaf.rebirth` | Lifetime summary |
| `/rebirthtop [page]` | `dripleaf.rebirth` | Leaderboard, cached |
| `/rebirth admin set <player> <tier>` | `dripleaf.rebirth.admin` | Set a tier, no rewards |
| `/rebirth admin grant <player> <tier>` | `dripleaf.rebirth.admin` | Set a tier **and** run its rewards |
| `/rebirth admin reset <player>` | `dripleaf.rebirth.admin` | Wipe rebirth progress |
| `/rebirth admin reload` | `dripleaf.rebirth.admin` | Reload tier and unlock data |

Aliases: `/prestige`, `/ascend`, `/rbinfo`, `/rbtop`.

Every admin action is written to `logs/admin.log` with actor, action, target and
the before and after values. All of them are also reachable from
`/dripleafcore admin → Rebirth`.

---

## 10. Placeholders

Registered under `dripleaf` when PlaceholderAPI is present.

| Placeholder | Returns |
|---|---|
| `%dripleaf_rebirth%` | Current tier number |
| `%dripleaf_rebirth_numeral%` | Current numeral, e.g. `XII` |
| `%dripleaf_rebirth_next%` | Next tier number |
| `%dripleaf_rebirth_next_numeral%` | Next numeral |
| `%dripleaf_rebirth_max%` | Highest tier defined |
| `%dripleaf_rebirth_maxed%` | `true` / `false` |
| `%dripleaf_rebirth_multiplier%` | Sell bonus, e.g. `+140%` |
| `%dripleaf_rebirth_multiplier_raw%` | As a factor, e.g. `2.40` |
| `%dripleaf_rebirth_progress%` | Mean percent across the next tier's requirements |
| `%dripleaf_rebirth_cost%` | Next tier's Standard cost, in full |
| `%dripleaf_rebirth_cost_soul%` | Next tier's Soul cost, in full |
| `%dripleaf_rebirth_requirement%` | Next tier's statistic requirement, display form |
| `%dripleaf_rebirth_requirement_progress%` | e.g. `1,847 / 2,500` |
| `%dripleaf_rebirth_can_standard%` | `true` / `false` |
| `%dripleaf_rebirth_can_soul%` | `true` / `false` |
| `%dripleaf_rebirth_cooldown%` | Time until the next rebirth is allowed |
| `%dripleaf_rebirthtop_1%` … `_10%` | Leaderboard names |

Every one returns something sensible for an offline or unknown player rather
than erroring — a scoreboard that throws spams the console once per tick per
player.

---

## 11. Troubleshooting

### A tier is not showing in the browser

- **Above the visibility block.** Expected: a player at tier 3 sees 1–5. Check
  `info-visibility-block`.
- **It did not load.** The console at start-up says how many tiers loaded. If
  the number is short, the line above it names the file, the path and the
  problem.
- **Tiers are not consecutive.** A gap after tier *N* means everything after it
  is unreachable. The console reports this at load.

### A requirement can never be satisfied

- **Wrong qualifier for the statistic kind.** `KILL_ENTITY` with `material:`
  instead of `entity:` is skipped at load with a named line. See §6.
- **mcMMO is not installed.** `McMmoBridge` reports 0, so any `mcmmo-power`
  requirement above 0 never passes. `/dripleafcore` lists which hooks were
  found.
- **The statistic is not what you think.** `MINE_BLOCK` counts blocks *broken*,
  not items collected. Check the player's own statistics screen in game.

### Rewards were not granted

Read `logs/rebirth.log`. Every completion logs `OK` with the tier, path, cost,
keys and souls. A failure logs `FAIL` with the stage it reached.

- **`FAIL` at `commands`** — a reward command referenced a plugin that is not
  installed. Check `tiers.yml` and `unlocks.yml` for the tier.
- **`OK` but the player has nothing** — the commands ran and the target plugin
  refused them. Run one by hand from the console with the player's name in place
  of `<player>` and read what it says.
- **Keys specifically** — `excellentcrates key give <player> rebirth <keys>`
  assumes a crate named `rebirth`. Check that it exists.

### A player is stuck between tiers

Almost always a half-applied rebirth. `logs/rebirth.log` says exactly which step
completed.

```
/rebirth admin set <player> <tier>      put them where they should be
/rebirth admin grant <player> <tier>    re-run that tier's rewards
```

`grant` re-runs commands, so use it only when the log shows the payout did not
happen — otherwise they get the rewards twice.

### The info menu and reality disagree

They cannot any more: `unlocks.yml` holds the display string and the granting
command in the same entry. If they look wrong, they are wrong *together*, and
fixing the entry fixes both.

### Everything looks right but nothing responds

Check which menu surface you are on. `/uimode` shows it, and switching with
`/uimode chest` is the fastest way to rule out a client-side dialog problem.
See [UI.md](UI.md).
