# The Menu System

> How every screen in DripleafCore is drawn twice from one piece of code, why
> Bedrock players get chest GUIs, and how to change any of it.

---

## What changed from the Skript version

The old system had two front ends that were two separate implementations. They
drifted, as two implementations of one thing always do: the chest menu and the
info menu disagreed about how many crate keys tier 10 granted, and only one of
them was right.

This version has **one** description of each screen and **two** renderers. A
change to a menu is a change in one place, and the two surfaces cannot disagree
about anything because there is nothing for them to disagree about.

---

## The model

Nothing in the plugin builds a dialog or an inventory directly. Everything
builds a `Screen` and hands it to the router:

```java
Screen screen = Screen.of("my-menu", Palette.brand("My Menu"))
        .line("<#AAAAAA>Some description.</#AAAAAA>")
        .button(ScreenButton.of("go", "<#55FF55>Do the thing</#55FF55>")
                .style(ButtonStyle.PRIMARY)
                .material(Material.LIME_DYE)
                .line("<#555555>➥ <#AAAAAA>What it does.</#AAAAAA>")
                .action(player -> doTheThing(player))
                .build())
        .build();

services.ui().open(player, screen);
```

That is the whole API. `UiService.open` decides the surface; neither the caller
nor the config knows or cares which one was chosen.

| `Screen` carries | Dialog renderer does | Chest renderer does |
|---|---|---|
| `title` | Dialog title | Inventory title |
| `body` lines | Body text, scrollable | Lore on a header item at the top |
| `heroItem` | An item body at 32×32 | The header item's icon |
| `buttons` | `ActionButton`s with tooltips | Items with lore |
| a **disabled** button | Still shown, reason in the tooltip | Still shown, greyed, reason in the lore and echoed to chat on click |
| `inputs` | Real text fields | A button that opens a chat prompt |
| more buttons than fit | Scrolls | Paginates, with ◀ ▶ in the bottom row |
| `layout` | Ignored | Chest size, filler, header slot |
| button `slot` hints | Ignored | Exact placement |

That last pair is how the rebirth path menu keeps its old hopper shape — slot 0
Standard, slot 2 the info book, slot 4 Soul Ascension — while becoming three
labelled buttons with full requirement breakdowns when drawn as a dialog.

### Why a screen is never cached

Dialog buttons use single-use `customClick` callbacks with a five-minute
lifetime, so a cached dialog would hold spent callbacks and quietly stop
working. Building a screen is a handful of small allocations; keeping one alive
across players is a correctness bug waiting to happen. Screens are built on
open, every time.

---

## Choosing a surface

Resolution order, per player, on every `open`:

1. **The player's own `/uimode` setting**, if `ui.allow-player-override` is on.
2. Otherwise the platform default: `ui.bedrock-mode` for a player detected as
   Bedrock, `ui.java-mode` for everyone else.

There is exactly one place in the codebase where this is decided, which is why
every screen honours the toggle for free.

### `/uimode`

| | |
|---|---|
| `/uimode` | Open the picker |
| `/uimode dialog` | Force dialogs for everything |
| `/uimode chest` | Force chest GUIs for everything |
| `/uimode auto` | Clear the override, back to the server default |
| `/uimode <player> <mode>` | Set it for someone else — `dripleaf.uimode.other` |

Aliases: `/ui`, `/menumode`. Permission: `dripleaf.uimode`, default `true`.

The setting is stored in the player's profile and survives a restart. The picker
is itself a `Screen`, so it draws on whichever surface you are currently on —
and switching redraws it immediately on the new one. You see the change land on
the screen you are looking at, which is the entire point of having a toggle
rather than a config key.

**This command ships enabled.** Everything else in `core/commands.yml` ships
disabled, and this is the exception on purpose: it is how a Bedrock player
recovers from a menu that will not draw for them, and they are the last people
able to read a wiki page about it.

---

## Bedrock detection

Four signals, checked in descending order of trustworthiness. The first that
gives a definite answer wins, and the answer is cached for the session — a
player does not change platform mid-login, and this is asked on every menu open.

```yaml
ui:
  bedrock-detection:
    # 1. Authoritative when installed. Nothing else is consulted if it answers.
    floodgate: true

    # 2. A placeholder the server already publishes. Blank to skip.
    placeholder: "%bedrock%"
    placeholder-true-values: [ "true", "yes", "1", "bedrock", "y" ]

    # 3. The prefix every Bedrock account on this network carries.
    username-prefix: "."

    # 4. Floodgate mints UUIDs whose high bits are zero.
    floodgate-uuid-shape: true
```

**Why four.** Floodgate is right and everything else is a heuristic — but
Floodgate's API moves between versions, the placeholder is what the rest of the
server already believes, and the username prefix works with nothing installed at
all. Any one of them failing leaves three.

The picker screen shows which signal answered for you, so "why does the plugin
think I'm on Bedrock?" is answerable in game rather than from logs. The same
information is on the admin **Diagnostics** screen, and
`%dripleaf_bedrock%` exposes the verdict to other plugins.

---

## menus.yml — every screen and button

Appearance lives in `menus.yml`; behaviour lives in the code. That split is
enforced by the types: a screen builder asks for a template by id, supplies an
action, and never writes a label, an icon or a slot in Java.

```yaml
screens:
  main-menu:
    title: "<gradient:#dce35b:#45b649><bold>Dripleaf</bold></gradient>"
    body: [ "<#AAAAAA>Everything, in one place.</#AAAAAA>" ]
    dialog:
      columns: 2          # 1-4 button columns
      button-width: 190
    chest:
      type: CHEST         # CHEST | HOPPER
      rows: 5             # 0 sizes to content
      decorated: true
      filler: BLACK_STAINED_GLASS_PANE
      header-slot: 4      # -1 for none
    buttons:
      homes:
        label: "<gradient:#dce35b:#45b649>Homes</gradient>"
        lore: [ "<#555555>➥ <#AAAAAA>Your saved locations.</#AAAAAA>" ]
        icon: RED_BED
        style: PRIMARY    # PRIMARY SOUL SHARD DANGER NEUTRAL LOCKED
        slot: 10          # chest slot; -1 auto-arranges
        permission: ""    # blank = everyone
        hidden: false     # true removes it entirely
        action: "homes"
```

`/dripleafcore reload menus` applies it.

### Actions

Buttons on config-driven screens carry an `action:`, which is what lets staff
add, remove and reorder entries with no code change:

| Action | Effect |
|---|---|
| `command:home` | Runs `/home` as the player |
| `console:give <player> diamond 1` | Runs on the console; `<player>` is substituted |
| `screen:my-menu` | Opens another screen defined in this file |
| `message:some.key` | Sends a `messages.yml` key |
| `close` | Closes the menu |
| `none` | Decorative |
| `shop`, `shardshop`, `soulshop`, `quickbuy`, `warps`, `homes`, `kits`, `settings`, `flight`, `admin` | A built-in screen, by name |

Building a second menu is a screen block plus a button pointing at it with
`action: "screen:my-menu"`. `/menu <id>` opens any screen by name for staff
holding `dripleaf.menu.other`.

### Placeholders

Each screen documents the placeholders available in its labels and lore at the
top of its block in the file — `<player>` on the main menu, `<item>`,
`<amount>`, `<price>` on Quick Buy slots, and so on. They resolve through
MiniMessage's tag resolver, so a value containing a `<` cannot inject
formatting.

---

## Configuration

```yaml
ui:
  java-mode: DIALOG            # DIALOG | CHEST
  bedrock-mode: CHEST          # DIALOG | CHEST
  allow-player-override: true  # false pins everyone to the two above
```

**Running the whole server on chest GUIs.** Set both to `CHEST`. Everything
still works; you lose scrolling and gain pagination.

**Running the whole server on dialogs, including Bedrock.** Set both to
`DIALOG`. Do this only after testing with real Bedrock clients — and leave
`allow-player-override` on so anyone it breaks for can rescue themselves with
`/uimode chest`.

**Pinning everyone.** Set `allow-player-override: false`. `/uimode` then
explains that the server has fixed it rather than silently doing nothing.

---

## Text input

The one thing a chest GUI genuinely cannot do is host a text field. Rather than
hiding features from Bedrock players, the chest renderer degrades every
`ScreenInput` into a **chat prompt**: the menu closes, the player is asked to
type, and the screen reopens with the value.

It is also the only input path that is reliable on Bedrock, which is why it is
this and not an anvil-rename trick.

Chat is intercepted for a player with a prompt open, so their answer never
reaches public chat — typing a shop search into global is a small
embarrassment, typing a rebirth confirmation numeral is a bigger one. `cancel`
backs out; the word is configurable at `ui.prompt-cancel-word`.

Used by: shop search, the admin player search, admin balance and tier edits,
warp creation, and the type-to-confirm on rebirth tiers above 15.

---

## The design language

Formalised so every new surface matches rather than drifting. Both renderers
draw from the same constants, so a lore block in the shop reads the same as one
in the rebirth menu.

### Palette

| Role | Colour |
|---|---|
| Dripleaf brand gradient | `#dce35b` → `#45b649` |
| Soul / ascension | `#AA00FF` → `#FF55FF` |
| Shard | `#00CFFF` → `#7FE9FF` |
| Success / met requirement | `#55FF55` |
| Failure / unmet requirement | `#FF5555` |
| Danger / sacrifice | `#FF3333`, `#800000` for headers |
| Warning | `#FFAA00` |
| Body text | `#DDDDDD` |
| Muted / secondary | `#AAAAAA` |
| Structural glyphs and separators | `#555555` |
| Deep muted / parenthetical | `#333333` |

Defined once in `Palette.java` and written literally in `messages.yml`. Changing
a colour in both rethemes the plugin.

### Glyphs

All in the default Minecraft font — **no resource pack is assumed**.

| Glyph | Meaning | Colour |
|---|---|---|
| `■` | Section header bullet | `#555555`, always |
| `➥` | Line item under a header | `#555555`, always |
| `✓` | Requirement met | `#55FF55` |
| `✗` | Requirement not met | `#FF5555` |
| `✦` | Decorative, soul context | soul gradient |
| `↪` | Call to action | context |
| `⚠` | Warning | `#FFAA00` |
| `┃` | Separator inside broadcast lines | `#555555` |
| `▰▱` | Progress bar, filled and empty | context |

### The three shapes

Every message in the plugin is one of these:

**Line** — prefix, then content. Command feedback.
```
Dripleaf » Sold 64× Diamond for 8,000.
```

**Panel** — a gradient rule, four-space-indented content, a closing rule, blank
lines either side. Broadcasts and rebirth confirmations.
```
──────────────────────────────────────
    REBIRTH XX
    You start again with 500,000 and a +140% sell bonus.
──────────────────────────────────────
```

**Lore block** — `■` header, then `➥` items. Every GUI item and dialog body.
```
■ Requirements
  ➥ ✓ mcMMO Power: ▰▰▰▰▰▰▰▰▰▰ 3,140 / 3,000 (100%)
  ➥ ✗ Deepslate Mined: ▰▰▰▰▰▰▱▱▱▱ 18,204 / 25,000 (73%)
```

### Sound

Every meaningful action gets one, configurable per event under `sounds:` in
`config.yml`. Format is `<key> [volume] [pitch]`; blank silences that event
without disabling the feature, which is the setting most servers actually want.

| Event | Default |
|---|---|
| Menu open | `ui.button.click` @ 1.2 |
| Purchase success | `entity.experience_orb.pickup` |
| Purchase denied | `block.note_block.bass` @ 0.5 |
| Warmup tick | `block.note_block.hat`, pitch rises with progress |
| Teleport complete | `entity.enderman.teleport` |
| Rebirth (standard) | `ui.toast.challenge_complete` |
| Rebirth (soul) | `ui.toast.challenge_complete` @ 0.7 |
| Admin action applied | `block.amethyst_block.chime` |

---

## Icons

Menu icons are atlas sprites, and getting the atlas wrong renders the
pink-and-black missing-texture checkerboard.

**What went wrong before.** Item textures live in `minecraft:items`; block
textures live in `minecraft:blocks`. Code that used the one-argument
`ObjectContents.sprite(…)` — or hardcoded `minecraft:blocks` — resolved
everything against the block atlas. `minecraft:block/sand` exists there and
rendered; `minecraft:item/diamond` does not, so it did not. That is exactly the
split the Server Shop showed: every block fine, every item broken.

**What happens now.** `IconService` picks the atlas by whether the material is a
block, and `core/icons.yml` is the escape hatch for the cases where the sprite
name does not match the material name — `grass_block` has `grass_block_top` and
`grass_block_side` and no plain one, and ores, doors and beds are the same
story. The file ships pre-populated with the known offenders.

Start-up logs one line: `Icons: 128 resolved, 2 fell back (…)`. That turns a
silent visual bug into a start-up warning, which is the difference between
finding it now and a player reporting it in three weeks.

When you find a new one: add four lines to `core/icons.yml`, run
`/dripleafcore reload`. No rebuild.

```yaml
overrides:
  SOME_MATERIAL:
    atlas: "minecraft:items"
    sprite: "minecraft:item/some_material"
```

> **F3+S** in game dumps the current atlas contents. Trust that over any
> documentation, including this page — the atlas an item's texture lives in has
> changed across recent versions.

---

## Adding a screen

1. Build a `Screen`. Use `Palette` and `Glyphs`; do not invent colours.
2. Put every string in `messages.yml` and read it through `MessageService`.
3. Give buttons a `ButtonStyle` — it carries both the colour and the chest
   material, so you cannot pick a purple gradient and a green dye by accident.
4. Disable buttons with `.locked(reason)` rather than omitting them. A visible
   goal drives progression; an empty grid does not.
5. Only reach for `ChestLayout` and slot hints if the chest form needs an exact
   arrangement.
6. Open it with `services.ui().open(player, screen)`.
7. **Test it both ways** with `/uimode dialog` and `/uimode chest`.

Step 7 is not optional. It takes ten seconds and it is the whole reason the
toggle exists.
