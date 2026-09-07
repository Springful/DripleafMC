# Changelog

## Versioning

`MAJOR.MINOR.PATCH`

| Position | Bumped when |
|---:|---|
| **MAJOR** | Something breaks. A config key is renamed or removed, a permission node changes, stored data needs migrating, or behaviour staff rely on changes shape. Read the notes before deploying one of these. |
| **MINOR** | Something is added. New commands, new screens, new config keys — all backwards compatible. New keys are merged into your existing files on start-up, with your edits preserved. |
| **PATCH** | Something is fixed. Bug fixes and wording only. No new features, no config changes. Always safe to drop in. |

---

## 1.4.1

The first release of the merged plugin. `DripleafCore` and the Rebirth Skripts
become one jar with two modules.

The version continues the numbering the server was already running rather than
restarting at zero — this replaces working production behaviour, it does not
introduce it.

### Menus are written once and drawn two ways

Every screen — shops, warps, homes, kits, rebirth, the admin panel — is one
`Screen` description handed to a router that picks the surface per player.

- **Dialogs are the default.** They scroll, take typed input, do not consume an
  inventory slot, and stay legible at any GUI scale.
- **Bedrock players get chest GUIs**, because Geyser clients do not render
  native dialogs reliably. Nothing is missing from the chest form: text fields
  become chat prompts, long lists paginate, locked entries still say why.
- **`/uimode`** switches a player between the two for *every* menu at once, with
  no restart and no config edit. It ships enabled — it is how a Bedrock player
  recovers from a menu that will not draw, and how staff compare the surfaces on
  a live server.
- **Bedrock detection** uses four signals in order of trust: Floodgate/Geyser,
  the `%bedrock%` placeholder, the `.` username prefix, and the Floodgate UUID
  shape. All configurable.

### Icons render correctly

Item icons were resolving against the block atlas, which is why every block in
the Server Shop rendered and every item showed the missing-texture checkerboard.
`IconService` now picks the atlas by material kind, `core/icons.yml` overrides
the cases where the sprite name does not match the material name, and start-up
logs `Icons: N resolved, M fell back (…)` so a bad icon is a warning rather than
a player report.

### Rebirth is configuration, not code

- All 25 tiers move to `rebirth/tiers.yml`. Nothing in Java hardcodes 25,
  iterates to 25, or assumes a maximum — adding tier 26 is a YAML block and a
  reload.
- `rebirth/unlocks.yml` pairs each unlock's display string with the command that
  grants it, in the same entry, so they cannot drift. They had: the executing
  Skript granted bonus keys at tiers 5, 10, 15 and 20 that the info menu never
  mentioned. The executing values win, and the info screen now reads from the
  same place.
- **Every** requirement is re-validated at confirmation, not just money.
- Statistics are read through `player.getStatistic(...)` instead of a
  PlaceholderAPI string parse per lore line.
- A player's tier is one integer in player data instead of up to 25 permission
  lookups per menu open. Existing `rebirth-N` groups are imported once, on join,
  idempotently. The group grant stays as a reward command so external plugins
  keep working — it just stops being the source of truth.
- Requirement lines show a progress bar and the real numbers; locked paths say
  *which* requirement is missing; costs are shown in full, never abbreviated.
- Tiers above 15 require typing the numeral to confirm.
- The tier browser paginates, so it scales past 25 automatically, and shows
  unreachable tiers as locked silhouettes rather than hiding them.

### Core module

- **One shop engine, three instances.** Server, shard and soul shops share a
  schema and a renderer; adding a fourth is a config file.
- **One money parser.** `1k`, `2.5m`, `1b`, `all`, `half` work on every amount
  argument, price and config value. Shards and souls reject fractional amounts
  rather than truncating them.
- **~70 utility commands**, every one individually toggleable. Anything
  EssentialsX also provides ships **disabled**, and a disabled command is never
  registered at all — so it cannot collide, rather than losing a race.
- Warps, homes with permission-driven limits, kits with restart-surviving
  cooldowns, teleport requests, `/back`, `/rtp`, and cached leaderboards.
- An admin panel where every mutating action shows before and after values and
  writes to `logs/admin.log`.

### Built for a server that has been crashing

- **One executor for the whole plugin.** No `CompletableFuture.supplyAsync` on
  the common pool, no per-feature pools, no HTTP clients, no per-join network
  calls. Repeating work is one task per concern regardless of player count. The
  live thread count is on the Diagnostics screen.
- **Atomic writes.** Every yml write goes to a temp file, is `fsync`ed, then
  `ATOMIC_MOVE`d over the target. Killing the process mid-write leaves the old
  file or the new one, never a torn one.
- **Every collection is bounded** and evicted on quit.
- **Reload validates before applying**, reports what changed and how long it
  took, and never re-registers commands or listeners.

### Known — needs a decision

- `/disposal` unlocks at **tier 10**, matching the executing Skript. The old
  info menu said 17. Either is defensible; the executing behaviour is the
  default.
- Chest shop, auction slot and player warp permission nodes in
  `rebirth/unlocks.yml` are **placeholders**. The plugins that own those limits
  were not confirmed. Replace them before going live; every affected line is
  flagged in the file.
