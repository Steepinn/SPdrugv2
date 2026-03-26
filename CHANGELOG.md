# Changelog

All notable changes to **SPdrug** are documented in this file.

## 1.3.5

- Default **drug `display-name`** values in `config.yml` are **English** (Mephedrone, LSD, Cocaine, Cannabis, Heroin, Amphetamine, Tobacco product; MDMA unchanged).
- **`farm.grass-break-special-seed-chance-percent`** default is **10** (was 20).

## 1.3.0

### Localization
- Added `general.language` in `config.yml` (`ru` / `en`).
- Bundled `lang/en.yml` and `lang/ru.yml` — UI strings load from the language file first, then fall back to `config.yml`.
- `/spdrug reload` now reloads locale data after the config is re-read.

### Farm & field tuning (`config.yml`)
- **Field fern** (special seeds): `field-herb-grow-duration-seconds` and `field-herb-grow-speed-multiplier` are documented; they control time-to-mature for plugin ferns (glowstone boost unchanged).
- **Greenhouse wheat**: `greenhouse-wheat-grow-speed-multiplier` — speeds vanilla wheat growth inside greenhouse zones (Paper `BlockGrowEvent`).
- **Grass drops**: `grass-break-special-seed-chance-percent` (0–100) replaces the hardcoded chance for special seeds from short/tall grass.
- **Seed type weights**: `grass-break-special-seed-weights` (`cannabis`, `cocaine`, `tobacco`) — weighted roll when a special seed drops.

### Recipe book & vanilla recipes
- Written **SPdrug recipe book**: separate page for **growth watch** (shapeless ingredients + note about any order).
- **Vanilla recipe book**: players **discover** plugin crafting recipes on join and after `/spdrug reload` (`discoverRecipe`) — growth watch, lab/farm placers, cigarettes where applicable.

### Commands & messages
- Admin help, give feedback, book/give player-only messages, and field-herb action bar lines use the locale system where applicable.

---

## 1.2.11 (and earlier bundled work)

- Initial merge of locale infrastructure, extended `config.yml` keys, and Gradle `1.2.11` artifact; superseded by **1.3.0** for a single release that includes book + discover + farm tuning in one build.
