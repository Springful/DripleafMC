<div align="center">

# DripleafCore

**One jar. Two modules. Every menu drawn twice.**

Core server utilities and the Rebirth progression system for **DripleafMC**,
merged into a single Paper plugin.

`Paper 26.2` · `Java 25` · `v1.5.0`

</div>

---

## What this is

DripleafCore replaces two Skript files and a pile of overlapping plugins with
one jar containing two modules that share a foundation:

| Module | What it owns |
|---|---|
| **core** | currencies, three shops, warps, homes, kits, teleportation, ~70 utility commands, the admin panel |
| **rebirth** | 25 configurable tiers, two ascension paths, requirements, rewards, unlocks, leaderboards |

They talk to each other through a four-interface API and nothing else, which is
what makes `/dripleafcore reload` a data swap rather than a re-wiring exercise —
and what would let a third module be added later without touching either.

---

## The headline: every menu, drawn two ways

Every screen in this plugin — shops, warps, homes, kits, rebirth, the admin
panel — is written **once**, as a `Screen`, and handed to a router that picks
how to draw it for the player in front of it.

<table>
<tr><th></th><th>Dialogs</th><th>Chest GUIs</th></tr>
<tr>
  <td><b>Who gets it</b></td>
  <td>Java players, by default</td>
  <td>Bedrock players via Geyser, by default</td>
</tr>
<tr>
  <td><b>Why</b></td>
  <td>Scrolls, takes typed input, doesn't eat an inventory slot, legible at any GUI scale</td>
  <td>Renders reliably on Bedrock, where native dialogs do not</td>
</tr>
<tr>
  <td><b>Text input</b></td>
  <td>A real text field</td>
  <td>A chat prompt — the menu closes, you type, it reopens</td>
</tr>
<tr>
  <td><b>Long lists</b></td>
  <td>Scroll</td>
  <td>Paginate</td>
</tr>
</table>

**Dialogs are the house default.** Chest GUIs are not a lesser fallback — they
are a complete second rendering of the same screen, and nothing is missing from
them.

### The toggle

```
/uimode              open the picker
/uimode dialog       force dialogs for everything
/uimode chest        force chest GUIs for everything
/uimode auto         hand yourself back to the server default
/uimode <player> <mode>    set it for someone else  (dripleaf.uimode.other)
```

Set it and **every** menu in the plugin redraws on that surface immediately —
no restart, no config edit, no relog. The picker itself redraws on the surface
you just chose, so you see the change land on the screen you are looking at.

This is how you answer "which of these actually works for my players?" on a
live server instead of guessing. It ships **enabled**, deliberately: it is also
how a Bedrock player recovers from a menu that will not draw for them.

### How a Bedrock player is recognised

Four signals, in descending order of trust. The first that answers wins, and the
answer is cached for the session.

1. **Floodgate or Geyser**, if either is installed. Authoritative.
2. **The `%bedrock%` placeholder** — configurable, because the server already
   publishes this and a plugin that disagrees with the scoreboard is a support
   ticket.
3. **The `.` username prefix** every Bedrock account on this network carries.
   Cheap, offline-safe, and still works when Floodgate's API moves.
4. **The Floodgate UUID shape** (high bits zero), as a last structural check.

All four are configurable under `ui.bedrock-detection` in `config.yml`.

📖 **[docs/UI.md](docs/UI.md)** — the full picture, including how to add a
screen that renders correctly both ways.

---

## Documentation

These are deliverables, not afterthoughts. They are written so staff can operate
the plugin without a developer in the room.

| | |
|---|---|
| 📗 **[docs/CORE.md](docs/CORE.md)** | Every command, permission and placeholder. How to add a shop item, a category, a warp, a kit. Money suffixes. Retheming. Icon troubleshooting. |
| 📕 **[docs/REBIRTH.md](docs/REBIRTH.md)** | How to add tier 26, worked end to end. The full tier and unlock tables. Statistic reference. Troubleshooting. |
| 🎨 **[docs/UI.md](docs/UI.md)** | Dialogs vs chest GUIs, Bedrock detection, the toggle, and the design language. |
| 📋 **[CHANGELOG.md](CHANGELOG.md)** | Versions, and what the numbers mean. |

---

## Layout

```
DripleafCore/
├── pom.xml
├── .github/workflows/build.yml       mvn clean package, uploads the jar
├── docs/                             CORE.md · REBIRTH.md · UI.md
└── src/main/
    ├── java/net/dripleaf/core/
    │   ├── DripleafCore.java         bootstrap; owns nothing
    │   ├── DripleafPlaceholders.java the %dripleaf_…% expansion
    │   ├── api/                      the four interfaces modules share
    │   ├── common/                   the shared foundations
    │   │   ├── config/    validated loading, defaults merged on upgrade
    │   │   ├── storage/   flat files, atomic writes, fsync
    │   │   ├── text/      MiniMessage, palette, glyphs, progress bars
    │   │   ├── icon/      Material → atlas sprite, with overrides
    │   │   ├── ui/        Screen, the two renderers, the router  ★
    │   │   ├── cooldown/  cooldowns and warmups
    │   │   ├── money/     one parser, one formatter
    │   │   ├── command/   the shared command base and registry
    │   │   ├── sound/     configurable per event
    │   │   ├── scheduler/ ONE executor for the whole plugin
    │   │   ├── log/       transactions · admin · rebirth
    │   │   └── hook/      Vault · PAPI · LuckPerms · mcMMO · GP · Crates · Floodgate
    │   ├── core/                     the CORE module
    │   └── rebirth/                  the REBIRTH module
    └── resources/
        ├── paper-plugin.yml
        ├── config.yml                behaviour only
        ├── messages.yml              every player-facing string
        ├── core/
        │   ├── commands.yml          which commands exist, and their timings
        │   ├── warps.yml · kits.yml · icons.yml
        │   └── shops/                server · shard · soul
        └── rebirth/
            ├── rebirth.yml           behaviour
            ├── tiers.yml             costs, requirements, reward values
            └── unlocks.yml           what each tier grants, and how
```

★ is where the dialog/chest split lives.

---

## Three rules the code holds itself to

**1. One thread.** The whole plugin gets a single shared executor. Not a style
preference — this host has hard-crashed with `OutOfMemoryError: unable to create
native thread` while heap was healthy, because the ceiling being hit is the
container's process limit shared across 85 plugins. So: no
`CompletableFuture.supplyAsync` on the common pool, no per-feature pools, no
HTTP clients, no per-join network calls. Repeating work is one task per concern
— warmups, autosave, leaderboards, teleport expiry — regardless of player count.
The live thread and task count are on the admin **Diagnostics** screen.

**2. Atomic writes.** Every yml write goes to a temp file, is `fsync`ed, and is
then `ATOMIC_MOVE`d over the target. Killing the server mid-write leaves either
the old file or the new one, never a torn one. The `force(true)` is the part
people skip, and skipping it is the same corruption with extra steps.

**3. Nothing hardcodes 25.** Tier count and tier content are pure config.
Adding tier 26 is a YAML block and a reload.

---

## Building

```bash
mvn clean package
```

Output: `target/DripleafCore-1.5.0.jar`. Requires **JDK 25**; the `paper.version`
property in `pom.xml` should track the Paper build the server actually runs.

GitHub Actions builds every push and uploads the jar as an artifact.

---

## Installing

1. Drop the jar in `plugins/`.
2. Start once. Every config is written out with its documentation in the file.
3. **Remove `DripleafRebirth.sk` and `DripleafRebirthInfo.sk` from
   `plugins/Skript/scripts/` in the same deploy**, or the rebirth commands will
   double-register.
4. Back up `plugins/DripleafCore/`, the LuckPerms database and the
   ExcellentEconomy data before going any further.

Every core command ships **disabled** — anything EssentialsX also provides would
otherwise collide with it, and a disabled command here is never registered at
all rather than losing a race. Turn them on one at a time in
`core/commands.yml`, after disabling the Essentials equivalent, and run
`/dripleafcore reload core`.

The exceptions, which ship enabled: `/uimode`, `/dripleafcore`, `/rebirth`,
`/rebirthinfo` and `/rebirthtop`.

### Optional integrations

All soft. The plugin enables cleanly with every one of them absent — it logs a
line, disables the dependent feature, and keeps running.

`Vault` · `PlaceholderAPI` · `LuckPerms` · `mcMMO` · `GriefPrevention` ·
`ExcellentCrates` · `Floodgate` / `Geyser`

`/dripleafcore` prints exactly which ones were found.

---

## Two things that need a decision

Both have a working default, so nothing is blocked — but both are real design
choices that were made for you rather than by you:

- **`/disposal` unlocks at tier 10.** The executing Skript said 10; the info
  menu said 17. The executing behaviour wins by default. See
  [docs/REBIRTH.md](docs/REBIRTH.md#the-unlock-table).
- **Chest shop, auction slot and player warp permission nodes are
  placeholders.** The plugins that own those limits were not confirmed, so
  `rebirth/unlocks.yml` names them plausibly rather than correctly. Replace them
  before going live — they are all flagged in the file.

---

<div align="center">
<sub>DripleafMC · <a href="https://dripleafmc.net">dripleafmc.net</a></sub>
</div>
