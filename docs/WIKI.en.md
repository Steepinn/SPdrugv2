# SPdrug — plugin wiki

**Russian:** [WIKI.md](WIKI.md) · [WIKI.html](WIKI.html)

Plugin for **Paper 1.21**: laboratory (synthesis), greenhouse (drying), field crops, drugs with effects, cigarettes. Source of truth — `src/main/resources/config.yml` and code in package `ru.spdrug`.

**Build version (Gradle):** see `build.gradle` (`version = …`).

---

## Contents

1. [Requirements and installation](#requirements-and-installation)
2. [Permissions](#permissions)
3. [Commands](#commands)
4. [Items and blocks](#items-and-blocks)
5. [Gameplay loop](#gameplay-loop)
6. [Laboratory](#laboratory)
7. [Greenhouse and drying](#greenhouse-and-drying)
8. [Field crops (seeds)](#field-crops-seeds)
9. [Drugs and cigarettes](#drugs-and-cigarettes)
10. [Configuration (section overview)](#configuration-section-overview)
11. [ID reference](#id-reference)
12. [Troubleshooting](#troubleshooting)

---

## Requirements and installation

- Server: **Paper** (API **1.21**, project uses `paper-api:1.21.1-R0.1-SNAPSHOT`).
- **Java 21** (see `build.gradle`).
- Build: from project root `gradlew build`, output jar: `build/libs/SPdrug-<version>.jar`.
- Place the jar in `plugins/`, restart the server or use your plugin manager.
- On first run, `plugins/SPdrug/config.yml` is created. After edits: `/spdrug reload` (requires plugin admin permission).

---

## Permissions

| Permission     | Purpose |
|----------------|---------|
| `spdrug.admin` | Item grants, `/spdrug reload` (default: OP only) |
| `spdrug.book`  | Get the book: `/spdrug book` or `/spdrug give book` (default: **everyone**, `default: true`) |

---

## Commands

All `/spdrug` subcommands except `book` require `spdrug.admin`. Most `give` commands work **for players only** (items go to inventory; overflow drops on the ground).

| Command | Description |
|---------|-------------|
| `/spdrug reload` | Reload `config.yml`, chemistry and drug recipes, re-register crafts. |
| `/spdrug book` | Give recipe book (needs `spdrug.book` or `spdrug.admin`). |
| `/spdrug give lab` | Laboratory placer. |
| `/spdrug give farm` | Greenhouse placer (composter + plot zone). |
| `/spdrug give book` | Same as book (needs `spdrug.book` or admin). |
| `/spdrug give seeds` | All special seeds (cannabis, coca, tobacco). |
| `/spdrug give raws` | All raw types from `items.herb-raw-types`. |
| `/spdrug give dried` | All dried batch types from `items.dried-batch-types`. |
| `/spdrug give chemicals` | All chemical components from `chemicals`. |
| `/spdrug give chemical <id>` | One chemical by id (e.g. `hc`, `me`). |
| `/spdrug give drugs` | All finished drugs from `drugs`. |
| `/spdrug give drug <id>` | One drug by id (e.g. `meth`, `cocaine`). |
| `/spdrug give all` | Full kit: placers, book, empty cigarette, seeds, raws, dried, chemicals, drugs. |

In-game hint: `/spdrug` with no args (players with book see a line about `/spdrug book`; admins see the full list).

Drug recipes and effects in detail: `docs/PREPARATION.html` (open in a browser).

---

## Items and blocks

Configured in `config.yml`: `blocks.*`, `item-materials.*`, text in `items.*`.

- **Laboratory** — placer item; right-click a solid block to place the lab block (default `SMOKER`). Opens synthesis GUI.
- **Greenhouse** — placer; places **composter** as plot center (`farm.horizontal-radius`, `farm.vertical-range`).
- **Growth watch** — right-click a grown field fern: progress and boost info.
- **Raw matter / dried batch** — plugin-tagged items; default materials raw `DRIED_KELP`, dried `NETHER_WART` (must match `recipe` for drugs with `first-slot-must-be-dried-batch`).
- **Drug** — right-click to use (effects, title, boss bar, bad trip chance, overdose on frequent use).
- **Cigarette** — empty crafted from paper; charge with tobacco product; loaded cigarette on RMB (short **regen** for `cigarette.effect-ticks`).

---

## Gameplay loop

Simplified chain:

1. Obtain **special seeds** (grass / server gameplay) or use admin commands.
2. Plant on **farmland** — a **fern** grows; **glowstone** **+2 blocks above** speeds growth and improves harvest when mature.
3. Break the mature plant → **raw** of the matching type (`cannabis` / `cocaine` / `tobacco`).
4. Inside greenhouse zone: right-click composter with raw in hand → **drying** (minigame, boss bar).
5. **Dried batch** + **chemistry** chain in the **laboratory** → final **drug** (or intermediate components).

Also: wheat in the greenhouse zone at full age drops plant matter typed as **cannabis** (default farm branch — see `SPdrugListener`).

---

## Laboratory

- Reagents go **left to right** in sequence slots; order must match the recipe.
- Matching uses **`recipe-keys`**: prefixes `CHEM:<id>` (tagged chemistry item) and `MAT:MATERIAL` (vanilla material).
- If `first-slot-must-be-dried-batch: true`, slot 1 must be the plugin’s **dried batch**, not a plain item of the same material. Optional `required-dried-type` (e.g. `cocaine`, `tobacco`) — PDC `dried_batch:<type>`.
- **`recipe`** — Bukkit materials per slot for type checks; length and order must match the GUI layout.
- After start: keep **stability** (LMB on lab, radius, boss bar). Failure or wrong formula can **destroy the laboratory** (explosion without breaking world blocks — see `laboratory.explosion-*`).
- Multiple **full sets** in one run multiply output; leftovers are returned.

---

## Greenhouse and drying

- Zone: square around composter, size from `farm.horizontal-radius` (e.g. 2 → side **5**).
- Drying: raw in main or off hand, right-click composter; timer and QTE use `messages.*` and `farm.drying-duration-ticks`.

---

## Field crops (seeds)

- Plant **only on farmland** (`FARMLAND` / tilled soil); otherwise `messages.field-herb-seed-need-farmland`.
- Plants can be **broken before maturity**: drops **1** special **seed** of the same type (no raw). When **mature**, **1** or **2** raw with glowstone boost.
- There are no separate untyped “hemp” / “dried batch” items: greenhouse wheat and fallback paths use **cannabis** as the base type; `/spdrug give raws|dried` only lists branches from `items.herb-raw-types` / `items.dried-batch-types`.
- Base grow time: `farm.field-herb-grow-duration-seconds`.
- Speed: `farm.field-herb-grow-speed-multiplier` — **higher** = **faster** (time divided by multiplier).
- Glowstone **Y+2** above the planted block: speed boost and (when mature) higher yield.

---

## Drugs and cigarettes

- Each drug in `drugs`: `positive-effects` / `negative-effects`, `bad-trip-chance`, optional overdose in `drug-use.overdose`.
- `drug-use`: title, boss bar, bad trip damage (`bad-trip-damage-min/max`), blindness flashes (`screen-flash-count`, `screen-flash-interval-ticks`, `0` = off).
- Cigarette: `cigarette.max-uses`, `cigarette.effect-ticks` — **REGENERATION I** duration per puff.

---

## Configuration (section overview)

| Section | Purpose |
|---------|---------|
| `general` | Boss bar radius, **`language`**: default **`en`**; use **`ru`** for Russian UI (`lang/ru.yml`, then `/spdrug reload`). |
| `blocks` | Blocks placed for lab and farm. |
| `item-materials` | Plugin item materials (raw, dried, cigarette, etc.). |
| `farm` | Greenhouse radius, drying, field crop growth; default **10%** special seed drop from grass (`grass-break-special-seed-chance-percent`). |
| `laboratory` | Synthesis duration, explosion settings. |
| `crafting` | Shapeless craft ingredients for placers and items. |
| `messages`, `gui`, `bossbar`, `items` | Text and GUI materials. |
| `drug-use` | Use effects, bad trip, overdose. |
| `cigarette` | Puffs and effect ticks. |
| `commands` | Command messages. |
| `chemicals` | Chemistry ids, display names, lab `recipe`. |
| `drugs` | Finished drugs: `recipe`, `recipe-keys`, effects. |

Color codes in strings: `&` prefix like classic Minecraft.

---

## ID reference

**Drugs (`drugs`) — top-level keys:**

`meth`, `lsd`, `cocaine`, `cannabis`, `mdma`, `heroin`, `amphetamine`, `nicotine`

**Chemistry (`chemicals`):**

`h`, `na`, `cl`, `p`, `pa`, `la`, `me`, `am`, `ec`, `hc`

Exact formulas are in `config.yml` per id.

---

## Troubleshooting

**Lab exploded despite correct-looking ingredients**

- Check **`recipe-keys`** and **`recipe`** together: length, order, first slot is plugin **dried batch**, not plain `NETHER_WART`.
- For cocaine and tobacco, **`required-dried-type`** must match the dried type (`cocaine`, `tobacco`).
- Chemistry must be **synthesized** (PDC-tagged item), not a bare snowball.

**Weird behaviour after updating the jar**

- Compare your `plugins/SPdrug/config.yml` with the jar/repo version: new keys (`field-herb-*`, `drug-use.bad-trip-*`, etc.) may be missing — defaults apply from code, but syncing the file is better.

**Recipe book is outdated**

- The book is generated when given; after config changes, get a new one (`/spdrug give book` or from `give all`).

---

## License and authors

Plugin **SPdrug**: **Steepin**, **SteepStudio**. **MIT** license — [`LICENSE`](../LICENSE) in the project root.

For balance tweaks, edit `config.yml` and run `/spdrug reload`.
