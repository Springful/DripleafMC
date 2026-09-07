# Core — Staff Guide

> Every command, permission and placeholder in the core module, and worked
> examples for the things you will actually change.

**Files you will touch**

| File | What lives in it |
|---|---|
| `core/commands.yml` | Which commands exist, and their cooldowns and warmups |
| `core/shops/*.yml` | The three shops |
| `core/warps.yml` | Server warps |
| `core/kits.yml` | Kits |
| `core/icons.yml` | Sprite overrides — the fix for missing-texture icons |
| `config.yml` | Behaviour: currencies, storage, teleport, sounds, menus |
| `messages.yml` | Every word a player reads |

After editing: **`/dripleafcore reload`**, or one of `core`, `shops`,
`messages`, `rebirth`. No restart, ever.

---

## What changed from the Skript version

| Before | Now |
|---|---|
| Utility commands spread across Essentials and Skript | One catalogue in `core/commands.yml`, each individually toggleable |
| A disabled feature still registered its command and raced Essentials | A disabled command is **never registered at all** |
| Shop items and prices in code | Three config files, one schema, one engine |
| Every item icon rendered as a missing-texture checkerboard | `IconService` picks the right atlas, with per-material overrides |
| `1000000` typed out everywhere | `1m`, `2.5k`, `all`, `half` — one parser, everywhere |
| Menus were chest GUIs only | Every screen drawn as a dialog **or** a chest GUI — see [UI.md](UI.md) |
| SQLite data corrupted by server crashes | Flat files with fsync + atomic rename |

---

## 1. Commands

**Everything below ships `enabled: false`.** EssentialsX provides most of it, and
two plugins racing to own `/fly` is a support ticket rather than a feature.
Because a disabled command is never registered, dropping this jar onto a running
server changes nothing until you opt in.

**To enable one:** open `core/commands.yml`, set `enabled: true`, disable the
Essentials equivalent, run `/dripleafcore reload core`.

The exceptions that ship **enabled**: `/uimode`, `/dripleafcore`, `/rebirth`,
`/rebirthinfo`, `/rebirthtop`.

### Menus

| Command | Aliases | Permission | CD | Warmup | What it does |
|---|---|---|---:|---:|---|
| `/uimode [mode]` | `/ui` `/menumode` | `dripleaf.uimode` | — | — | Switch between dialogs and chest GUIs |
| `/dripleafcore [reload\|admin]` | `/dcore` `/dlc` | — | — | — | Status, reload, admin panel |

### Teleportation

| Command | Aliases | Permission | CD | Warmup |
|---|---|---|---:|---:|
| `/spawn` | — | `dripleaf.spawn` | 30s | 5s |
| `/setspawn` | — | `dripleaf.spawn.set` | — | — |
| `/tpa <player>` | `/call` | `dripleaf.tpa` | 15s | — |
| `/tpahere <player>` | — | `dripleaf.tpahere` | 15s | — |
| `/tpaccept` | `/tpyes` | `dripleaf.tpa` | — | 5s |
| `/tpdeny` | `/tpno` | `dripleaf.tpa` | — | — |
| `/tpacancel` | — | `dripleaf.tpa` | — | — |
| `/back` | `/return` | `dripleaf.back` | 60s | 3s |
| `/rtp` | `/wild` | `dripleaf.rtp` | 300s | 5s |
| `/tp <player> [player]` | — | `dripleaf.tp.other` | — | — |
| `/tphere <player>` | — | `dripleaf.tp.here` | — | — |
| `/top` | — | `dripleaf.top` | 10s | — |

`/back` returns to the last death location or the last teleport origin,
whichever is more recent. The death behaviour is gated separately by
`dripleaf.back.ondeath` so it can be a rank perk. Requests expire after
`teleport.request-timeout-seconds` (default 60) and live in memory only.

### Homes

| Command | Permission | Notes |
|---|---|---|
| `/home [name]` | `dripleaf.home` | No name with one home goes straight there; with several, opens the picker |
| `/sethome [name]` | `dripleaf.home.set` | Defaults to `home` |
| `/delhome <name>` | `dripleaf.home.delete` | Confirmation screen |
| `/homes` | `dripleaf.home` | The picker, with world icon, coordinates and biome |

**Home limits are permission-driven**: `dripleaf.home.limit.<n>`, highest
matching node wins, with `homes.default-limit` in `config.yml` for players
holding none. That is what lets a rebirth tier grant home slots without the
rebirth module knowing anything about homes — it grants a node, and this reads
it.

### Warps

| Command | Permission | Notes |
|---|---|---|
| `/warp [name]` | `dripleaf.warp` | No argument opens the menu |
| `/warps` | `dripleaf.warp` | Same thing |
| `/setwarp <name> [category]` | `dripleaf.warp.set` | Uses your position |
| `/delwarp <name>` | `dripleaf.warp.delete` | Confirmation screen |

Warps a player cannot use are shown **greyed with the reason** rather than
hidden.

### Kits

| Command | Permission | Notes |
|---|---|---|
| `/kit [name]` | `dripleaf.kit` | No argument opens the menu |
| `/kits` | `dripleaf.kit` | Same thing |

Kit cooldowns persist across restarts, unlike command cooldowns. A daily kit
that resets because the server restarted is a daily kit nobody trusts.

### Economy

| Command | Aliases | Permission |
|---|---|---|
| `/balance [player]` | `/bal` `/money` | `dripleaf.balance` (+ `.other`) |
| `/baltop [page]` | — | `dripleaf.baltop` |
| `/pay <player> <amount>` | — | `dripleaf.pay` |
| `/shop` | `/store` | `dripleaf.shop` |
| `/shardshop` | — | `dripleaf.shop.shard` |
| `/soulshop` | — | `dripleaf.shop.soul` |
| `/sell [hand\|all\|inventory]` | — | `dripleaf.sell` |
| `/worth [item]` | — | `dripleaf.worth` |
| `/eco <give\|take\|set\|balance> <player> [amount]` | — | `dripleaf.eco.admin` |
| `/shards <give\|take\|set\|balance> <player> [amount]` | — | `dripleaf.shards.admin` |
| `/souls <give\|take\|set\|balance> <player> [amount]` | — | `dripleaf.souls.admin` |

Every amount goes through the parser in §8. `/baltop` is computed on a timer and
served from cache — never on request, because on a large player base that is a
main-thread stall.

### Chat and social

| Command | Aliases | Permission |
|---|---|---|
| `/msg <player> <message>` | `/w` `/tell` | `dripleaf.msg` |
| `/reply <message>` | `/r` | `dripleaf.msg` |
| `/ignore <player>` | — | `dripleaf.ignore` |
| `/socialspy` | `/ss` | `dripleaf.socialspy` |
| `/nick <name\|off> [player]` | — | `dripleaf.nick`, `.colour`, `.other` |
| `/realname <nick>` | — | `dripleaf.realname` |

Nicknames and ignore lists persist. Colour support is a separate node because it
is a Rebirth XX unlock; without it, tags are stripped rather than the command
being refused.

### Virtual workstations

| Command | Aliases | Permission |
|---|---|---|
| `/enderchest` | `/ec` | `dripleaf.enderchest` |
| `/workbench` | `/wb` `/craft` | `dripleaf.workbench` |
| `/anvil` | — | `dripleaf.anvil` |
| `/grindstone` | — | `dripleaf.grindstone` |
| `/cartography` | — | `dripleaf.cartography` |
| `/stonecutter` | — | `dripleaf.stonecutter` |
| `/loom` | — | `dripleaf.loom` |
| `/smithing` | — | `dripleaf.smithing` |

### Utility

| Command | Aliases | Permission | Notes |
|---|---|---|---|
| `/disposal` | `/trash` | `dripleaf.disposal` | Anything left in it is destroyed |
| `/hat` | — | `dripleaf.hat` | |
| `/repair [all]` | `/fix` | `dripleaf.repair`, `.all` | |
| `/condense` | `/compact` | `dripleaf.condense` | Walks the recipe registry, so datapack recipes work |
| `/recipe [item]` | — | `dripleaf.recipe` | Lists ingredients in chat |
| `/heal [player]` | — | `dripleaf.heal` | |
| `/feed [player]` | — | `dripleaf.feed` | |
| `/god [player]` | — | `dripleaf.god` | |
| `/fly [player]` | — | `dripleaf.fly` | Also honours `dripleaf.fly.claims` |
| `/speed <1-10> [player]` | — | `dripleaf.speed` | |
| `/vanish` | `/v` | `dripleaf.vanish` | `dripleaf.vanish.see` to see through it |
| `/invsee <player>` | — | `dripleaf.invsee` | |
| `/near [radius]` | — | `dripleaf.near` | |
| `/seen <player>` | — | `dripleaf.seen` | Profile read is off-thread |
| `/playtime [player]` | `/pt` | `dripleaf.playtime` | |
| `/ping [player]` | — | `dripleaf.ping` | |
| `/afk` | — | `dripleaf.afk` | Auto after `social.afk-after-seconds` |
| `/sudo <player> <command>` | — | `dripleaf.sudo` | Logged |
| `/broadcast <message>` | `/bc` | `dripleaf.broadcast` | MiniMessage |
| `/gm <0-3> [player]` | — | `dripleaf.gamemode` | |
| `/more` | — | `dripleaf.more` | |
| `/itemname <name>` | — | `dripleaf.itemname` | MiniMessage |
| `/itemlore <lore>` | — | `dripleaf.itemlore` | `\|` separates lines |

**`/fly` and claims.** A player holding `dripleaf.fly.claims` — the Rebirth XX
unlock — keeps flight while inside a GriefPrevention claim they have build trust
in. Everywhere else the normal rules apply, which is what makes the unlock mean
something. With GriefPrevention absent the check is always false and `/fly`
behaves normally.

---

## 2. Permissions

Convention: `dripleaf.<module>.<feature>[.<qualifier>]`

| Node | Grants |
|---|---|
| `dripleaf.*` | Everything |
| `dripleaf.admin` | The admin panel and every admin subcommand |
| `dripleaf.admin.reload` | Reload |
| `dripleaf.bypass.cooldown` | Every command cooldown |
| `dripleaf.bypass.cooldown.<command>` | One command's cooldown |
| `dripleaf.bypass.warmup` | Every warmup |
| `dripleaf.bypass.warmup.<command>` | One command's warmup |
| `dripleaf.bypass.cost` | Every command and warp cost |
| `dripleaf.home.limit.<n>` | Home limit; highest wins, `.*` is unlimited |
| `dripleaf.warp.<name>` | A specific restricted warp |
| `dripleaf.kit.<name>` | A specific kit |
| `dripleaf.shop` / `.shard` / `.soul` | Each shop |
| `dripleaf.uimode` | Change your own menu surface (default **true**) |
| `dripleaf.uimode.other` | Change someone else's |
| `dripleaf.rebirth` | `/rebirth` (default **true**) |
| `dripleaf.rebirth.admin` | Rebirth admin subcommands |
| `dripleaf.fly.claims` | Persistent flight in trusted claims |
| `dripleaf.nick.colour` | Coloured nicknames |
| `dripleaf.back.ondeath` | `/back` returns to your death location |
| `dripleaf.vanish.see` | See vanished players |
| `dripleaf.msg.bypassignore` | Message players who have ignored you |
| `dripleaf.balance.other` | See other players' balances |

Plus one node per command, as listed in §1.

---

## 3. Placeholders

Registered under `dripleaf` when PlaceholderAPI is present.

| Placeholder | Returns |
|---|---|
| `%dripleaf_balance%` | Money, abbreviated |
| `%dripleaf_balance_raw%` | Money, unformatted |
| `%dripleaf_shards%` | Shard balance |
| `%dripleaf_souls%` | Soul balance |
| `%dripleaf_homes_used%` / `%dripleaf_homes_max%` | Home counts |
| `%dripleaf_playtime%` | Formatted playtime |
| `%dripleaf_afk%` | `true` / `false` |
| `%dripleaf_ui_mode%` | `DIALOG` or `CHEST` for this player |
| `%dripleaf_bedrock%` | `true` / `false` — what the plugin decided |
| `%dripleaf_baltop_1%` … `_10%` | Leaderboard names |
| `%dripleaf_rebirthtop_1%` … `_10%` | Leaderboard names |

The rebirth placeholders are in [REBIRTH.md §10](REBIRTH.md#10-placeholders).

---

## 4. How to add a shop item

Open `core/shops/server-shop.yml`, copy any block under `items:`, edit it. The
key must be unique within the file.

```yaml
items:
  emerald:                    # unique key; also the transaction-log id
    material: EMERALD         # any Material
    category: gear            # must exist under categories:
    display: "Emerald"        # optional; defaults to a tidy material name
    buy: 800                  # omit or -1 to disable buying
    sell: 200                 # omit or -1 to disable selling

    # Everything below is optional.
    stack-sizes: [ 1, 16, 64 ]     # quantities offered in the buy screen
    permission: ""                 # required to see or buy it
    lore:
      - "Greener than the alternative."
    max-per-day: 0                 # 0 = unlimited
    commands: []                   # console commands run on purchase
    give-item: true                # defaults to false when commands are set
```

Reload with `/dripleafcore reload shops`.

**`give-item`** is how "buy a permission" entries work. An item with `commands:`
and no `give-item` runs the commands and hands over nothing — the material is
the icon and the command is the product. Set `give-item: true` to do both. This
is how the shard and soul shops sell home slots and unlocks.

### What the engine refuses to do quietly

| Mistake | What happens |
|---|---|
| No `buy` and no `sell` | Logged by key, item skipped |
| `sell` above `buy` | **Loud** start-up warning by name. Not corrected — you may have a reason. |
| Unknown `category` | Moved to an auto-generated **Uncategorised**, so the mistake is visible in game |
| Unknown `material` | Logged by key, item skipped. Never a stack trace. |

### The purchase flow

1. A confirmation screen with **exact** figures and the resulting balance.
2. A fresh balance check at confirm time — the check at menu-open time is not
   the check that matters.
3. An inventory space check **before** the transaction. A full inventory refuses
   the purchase; nothing is dropped on the ground.
4. Withdraw, give, log, sound, message.

Every transaction lands in `logs/transactions.log` with timestamp, UUID, item,
quantity, unit price, total and resulting balance. That is what makes
duplication bugs and staff disputes resolvable.

---

## 5. How to add a shop category

```yaml
categories:
  redstone:
    display: "Redstone"
    icon: REDSTONE            # any Material
    description: "Contraptions and their parts."
    order: 7                  # sort position; ties fall back to the key
```

Then set `category: redstone` on the items that belong in it and reload. A
category with no items is skipped rather than shown empty.

---

## 6. How to add a warp

**In game**, at the spot you want:

```
/setwarp mine worlds
```

That writes an entry into `core/warps.yml` with sensible defaults, which you
then edit for the display name, icon and cost.

**By hand:**

```yaml
warps:
  mine:
    display: "<gradient:#dce35b:#45b649>Mining World</gradient>"
    icon: DIAMOND_PICKAXE
    description:
      - "Fresh ore, resets weekly."
      - "Bring a pickaxe."
    location:
      world: resource         # must be a loaded world
      x: 128.5
      y: 64.0
      z: -256.5
      yaw: 90.0
      pitch: 0.0
    permission: ""            # blank = everyone
    cost: 0                   # supports k/m/b/t
    warmup: 5                 # seconds; cancels on movement
    cooldown: 30              # seconds
    category: worlds          # groups warps in the menu
    slot: -1                  # fixed chest slot, or -1 to auto-arrange
```

A warp whose world is not loaded is skipped with a named line at load rather
than throwing.

**Restricting a warp** is one line: set `permission` to a node, and grant that
node from a rebirth unlock. Players who lack it see the warp greyed with
*"Requires …"* rather than not seeing it at all.

---

## 7. How to add a kit

```yaml
kits:
  weekly:
    display: "<gradient:#dce35b:#45b649>Weekly Kit</gradient>"
    icon: CHEST
    description:
      - "A proper top-up, once a week."
    permission: "dripleaf.kit.weekly"   # blank = everyone
    cooldown: 604800                    # seconds; ignored if one-time
    one-time: false
    items:
      - ==: org.bukkit.inventory.ItemStack
        v: 4189
        type: DIAMOND
        amount: 8
    commands:
      - "lp user <player> permission set something true"
```

Items use Bukkit's own `ItemStack` serialisation, so enchantments, custom names,
lore and component data all round-trip. `v:` is the data version; copying it
from the shipped kits is fine.

Kit cooldowns are stored in player data and survive restarts.

---

## 8. Money suffixes

Every amount argument, config price and shop value goes through one parser.

| Input | Value |
|---|---|
| `1000`, `1,000` | 1 000 |
| `1k`, `1K` | 1 000 |
| `2.5k` | 2 500 |
| `1m` | 1 000 000 |
| `1b` | 1 000 000 000 |
| `1t` | 1 000 000 000 000 |
| `all` | The player's full balance |
| `half` | Half their balance |

Case-insensitive. `$` prefixes and `,`/`_` separators are stripped. Negatives,
`NaN`, `Infinity` and anything above `1e15` are rejected with a clear message.
`BigDecimal` internally, narrowed to `double` only at the Vault boundary.

**Shards and souls are integral.** `/souls give Steve 1.5` is rejected with a
message rather than silently truncated to 1.

**Two formats, and they are not interchangeable.** `formatExact` —
`1,250,000,000` — is used anywhere a player is about to spend money.
`format` — `1.25B` — is for scoreboards and lore lines. Never show an
abbreviated figure on a confirmation screen.

---

## 9. Currencies

| Currency | Backing | Whole numbers? | Placeholder |
|---|---|---|---|
| Money | Vault | No | `%dripleaf_balance%` |
| Shards | DripleafCore player data | Yes | `%dripleaf_shards%` |
| Souls | Configurable | Yes | `%dripleaf_souls%` |

```yaml
currencies:
  shards:
    mode: internal
  souls:
    mode: command       # internal | command
    give-command: "souls give <player> <amount>"
    take-command: "souls take <player> <amount>"
    balance-placeholder: "%excellenteconomy_balance_soul_token%"
```

**Souls ship in `command` mode.** They may already be live in ExcellentEconomy
with real player balances, and migrating those without an explicit decision is
not a plugin's call to make. Soul tokens are a secondary ExcellentEconomy
currency that **Vault cannot see**, so in command mode the balance is read
through the placeholder, never through Vault.

`internal` is the target state. Switching is a config edit and a reload — but
existing external balances do **not** come with it. Decide, then migrate
deliberately.

---

## 10. Cooldowns and warmups

Both are per-command, per-player, configured in `core/commands.yml`.

**Warmup** delays execution. It cancels if the player moves more than
`warmup.cancel-on-move-blocks` (default `0.5`, so turning on the spot is fine)
or, when `warmup.cancel-on-damage` is on, if they take damage. A progress bar
shows on the action bar with a note that rises in pitch as it counts.

**Cooldown** starts when a command **succeeds** — never when it is attempted and
fails, and never when a warmup is cancelled. Cooldowns are memory-only by
design: one that survives a restart punishes players for the server's problems.
Kit cooldowns are the deliberate exception and live in player data.

```yaml
commands:
  spawn:
    enabled: true
    cooldown: 30      # seconds after success
    warmup: 5         # seconds before executing
```

Bypass with `dripleaf.bypass.cooldown[.<id>]` and
`dripleaf.bypass.warmup[.<id>]`.

---

## 11. Retheming

Two files, no rebuild.

**`messages.yml`** holds every player-facing string, as MiniMessage. The palette
and glyph set are documented at the top of the file and in
[UI.md](UI.md#the-design-language). Edit, then `/dripleafcore reload messages`.

**`config.yml`** holds the sounds, one per event, as `<key> [volume] [pitch]`.
A blank value silences an event without disabling the feature — usually what you
actually want when a sound gets annoying.

If you change a colour, change it everywhere it appears. The palette is only
worth anything if it is consistent.

---

## 12. The admin panel

`/dripleafcore admin`, permission `dripleaf.admin`. It goes through the same UI
router as everything else, so it is a chest GUI for a Bedrock admin without a
second implementation.

| Screen | What it does |
|---|---|
| **Root** | Version, uptime, online count, TPS, module states |
| **Modules** | Reload core, or everything |
| **Players** | Search a player; view and edit balances, tier, homes, playtime |
| **Economy** | Shop and currency status, transaction log tail, leaderboard refresh |
| **Rebirth** | Tier browser and tier data reload |
| **Warps** | Create at your position, delete with confirmation |
| **Commands** | Every declared command with its enabled state, cooldown and warmup |
| **Diagnostics** | Icon resolution, hook status, config warnings, **thread count** |

Every mutating action goes through a confirmation showing the before and after
value, and writes a line to `logs/admin.log` with actor, action, target and
timestamp.

**Everything here is also reachable by command**, deliberately: dialogs are the
less reliable surface on Bedrock, and an admin who cannot open a panel must
still be able to do the job.

That thread count on Diagnostics is there on purpose. It should read **1**.

---

## 13. Reload

| Command | Effect |
|---|---|
| `/dripleafcore reload` | Everything |
| `/dripleafcore reload core` | Core module data only |
| `/dripleafcore reload rebirth` | Tier and unlock data only |
| `/dripleafcore reload shops` | All three shop configs |
| `/dripleafcore reload messages` | `messages.yml` only |

Permission: `dripleaf.admin.reload`.

- **Validated before applying.** A malformed file is reported and the old
  configuration stays live. A reload never leaves the server half-configured.
- **Reports what changed**: `Reloaded all: messages, 3 warps, 2 kits, 3 shops,
  25 tiers. (32ms)`
- **Closes open plugin menus** and tells those players why — they were built
  from configuration that no longer exists.
- **Never re-registers commands or listeners.** That path leaks. Registration
  happens once at enable; reload swaps data only. Ten reloads leave exactly the
  same number of handlers as zero.

`/dripleafcore` with no arguments prints version, module states, uptime and
exactly which optional integrations were found.

---

## 14. Troubleshooting

### A command does not register

- **It is disabled.** Everything except `/uimode`, `/dripleafcore` and the
  rebirth commands ships `enabled: false`. Check `core/commands.yml`; the admin
  **Commands** screen lists live state for all of them.
- **EssentialsX owns it.** Two plugins registering `/fly` means one of them
  loses. Disable the Essentials command in its config, then reload.
- **You reloaded.** Enabling a command in config and reloading does not register
  it — registration happens once, at enable. That is the same rule that stops
  reloads leaking. **Restart the server** after enabling a command.

The console prints `Registered N of M commands from core/commands.yml (K
disabled)` at start-up.

### Icons render as pink-and-black checkerboards

That is a sprite resolving against the wrong atlas. Add an override to
`core/icons.yml`:

```yaml
overrides:
  THE_MATERIAL:
    atlas: "minecraft:items"
    sprite: "minecraft:item/the_material"
```

Then `/dripleafcore reload`. Full explanation in
[UI.md](UI.md#icons); **F3+S** in game dumps the atlas contents, which is the
only authoritative list for the version you are running.

Start-up logs `Icons: N resolved, M fell back (…)`, so a broken icon is a
start-up warning rather than a player report three weeks later.

### A reload fails

The console names the file, the config path and the problem. The old
configuration is still live and the server is fine. Fix the line, reload again.

If it reports a count of warnings, those are values that fell back to defaults
rather than failing the load. They are listed on the admin **Diagnostics**
screen too.

### A shop item is missing

- **Unknown material** — logged by key at load.
- **No price** — an item with neither `buy` nor `sell` is skipped.
- **`permission:` is set** and the player lacks it. Locked items are shown
  greyed with a reason, so if it is genuinely absent it is one of the two above.
- **It is in Uncategorised** — its `category` does not match anything under
  `categories:`.

### Money commands say the economy is unavailable

Vault has no registered provider. `/dripleafcore` lists hook status. Money-priced
features refuse cleanly rather than handing out free goods.

### The server's thread count keeps climbing

Not from this plugin: it creates exactly one thread and the Diagnostics screen
proves it. That number is on that screen specifically so this can be ruled out
in seconds when the box gets into trouble.

### A player says a menu will not open

Get them to run `/uimode chest`. If that fixes it, the problem is dialog
rendering on their client — most likely Bedrock. Check
`ui.bedrock-detection` and see [UI.md](UI.md#bedrock-detection).
