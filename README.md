# DripleafRebirth

A Paper **26.2** plugin replacement for the DripleafMC rebirth Skript. Same 25 tiers,
same Standard / Soul Ascension split, same broadcasts — but the ladder is data, not code,
and the progression logic lives in one engine that two front ends share.

Requires Java 25, Paper 26.2+, Vault. PlaceholderAPI and mcMMO are optional.

---

## Folder layout

```
DripleafRebirth/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/
    ├── resources/
    │   ├── paper-plugin.yml
    │   ├── config.yml        behaviour only
    │   ├── tiers.yml         costs + requirements + reward VALUES
    │   ├── rewards.yml       every command the plugin ever runs
    │   ├── lang.yml          every player-facing string
    │   ├── menus.yml         chest-GUI front end
    │   └── dialogs.yml       Paper Dialog front end
    └── java/net/dripleafmc/rebirth/
        ├── RebirthPlugin.java      wiring, reload, front-end selection
        ├── config/                 Settings, Lang, TierRegistry, RewardRegistry
        ├── tier/                   RebirthTier, PathSpec, RebirthPath
        ├── requirement/            Requirement + 6 implementations + factory
        ├── reward/                 RewardAction, RewardExecutor
        ├── core/                   RebirthService, CheckResult, RequirementState
        ├── data/                   Profile, ProfileStore (async flat file)
        ├── hook/                   Vault, PapiBridge, McMmoBridge, expansion
        ├── ui/                     Renderer + RebirthUI contract
        │   ├── menu/               MenuUI, RebirthHolder, MenuListener, Items
        │   └── dialog/             DialogUI
        ├── command/                RebirthCommand (Brigadier)
        └── util/                   Text, Numbers, Ctx
```

## The two versions

Both are compiled into the same jar and both drive the identical engine —
`RebirthService` is the only thing that can move a tier or spend money.
Pick one in `config.yml`:

```yaml
ui:
  mode: DIALOG   # or MENU
```

| | `MENU` | `DIALOG` |
|---|---|---|
| Config | `menus.yml` | `dialogs.yml` |
| Look | hopper GUI, three items | native 26.x dialog screen, buttons |
| Identity | custom `InventoryHolder` | one-use `DialogAction.customClick` callbacks |
| Bedrock | works | auto-falls back to `MENU` per player |

Keeping them as one jar rather than two builds means a fix to the tier engine
can't drift between versions. Switching is a config change and a `/rebirth reload`.

## Separation of concerns in config

- **tiers.yml** — how much and how many. Each tier has exactly three blocks:
  `cost:`, `requirements:`, `rewards:`. No commands appear here at all.
- **rewards.yml** — what actually runs. Named `reward-sets` (`core`, `standard`,
  `soul`, `milestone-*`), the `sacrifice:` block, and the two broadcasts.
  Tiers reference sets by name, so changing what a rebirth grants is one edit
  in one place instead of 25.
- **lang.yml** — all messages, plus the shared requirement/cost line templates
  that both front ends render.

Line syntax in a reward-set:

```yaml
- "console: lp user <player> parent add rebirth-<tier>"
- "player:  warp spawn"
- "op:      fly on"
- "message: <green>Welcome to Rebirth <tier_roman>!"
- "delay: 20"
```

## Internal placeholders

Registered as the `rebirth` expansion when PlaceholderAPI is present:

```
%rebirth_tier%              %rebirth_next%              %rebirth_can_standard%
%rebirth_tier_roman%        %rebirth_next_roman%        %rebirth_can_soul%
%rebirth_total%             %rebirth_next_cost%         %rebirth_progress%
%rebirth_last_path%         %rebirth_next_cost_soul%    %rebirth_cooldown%
%rebirth_maxed%             %rebirth_next_<value>%      %rebirth_multiplier%
%rebirth_requirement_<id>_progress|target|percent|met|name%
```

`%rebirth_next_<value>%` reads any key under a tier's `rewards.values:` —
`keys`, `cash`, `souls`, `multiplier`, or anything custom you add.

Inside the plugin's own yml files the shorter MiniMessage form is used instead:
`<tier>`, `<tier_roman>`, `<cost>`, `<balance>`, `<multiplier>`, `<keys>`,
`<cash>`, `<souls>`, `<player>`, `<path_name>`.

## What changed from the Skript, and why

| Skript | Here |
|---|---|
| 25-branch `if` chain rebuilt on every menu open | `tiers.yml` parsed once into immutable records |
| 25 `hasPermission` checks to find the current tier | one map lookup; the LuckPerms group is still granted as a reward command |
| Every statistic read through a PlaceholderAPI string parse | native `player.getStatistic(...)` |
| mcMMO power level via PAPI | native mcMMO API via a method handle, PAPI as fallback |
| Menus identified by stripped inventory title | custom `InventoryHolder` |
| Requirements re-evaluated per lore line | evaluated once into `CheckResult` |
| Cost, sacrifice and rewards hardcoded in the script | three separate config blocks |
| Rewards fired before the tier was persisted in some paths | strict order: validate → charge → sacrifice → persist → pay out |

## Commands

```
/rebirth                       open the menu or dialog
/rebirth reload                reload every yml
/rebirth info <player>
/rebirth set <player> <tier>
/rebirth force <player> <standard|soul>
```

Permissions: `dripleafrebirth.use`, `dripleafrebirth.admin`,
`dripleafrebirth.bypass.cooldown`.

## Migrating existing players

Tiers now live in `players.yml`. To import the LuckPerms groups you already
granted, run once per player:

```
/rebirth set <player> <their current rebirth-N number>
```

Or drop straight into `plugins/DripleafRebirth/players.yml`:

```yaml
players:
  0000-uuid-here:
    tier: 7
    total: 7
```

## Building

```
./gradlew build
```

Output lands in `build/libs/DripleafRebirth-1.0.0.jar`.
