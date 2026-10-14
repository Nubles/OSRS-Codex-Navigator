# Codex Navigator

**An account compass for Old School RuneScape.** Codex Navigator reads your quests, achievement diaries, levels and combat achievement progress straight from the client. Pin anything you want to reach and it charts the whole path there, prerequisites first, down to the single next step.

It is the first plugin in the **Codex** suite: a family of RuneLite plugins that share one design language and one local account core.

<p align="center">
  <img src="docs/screenshots/1-compass.png" width="225" alt="Compass view">
  <img src="docs/screenshots/8-detail-cape.png" width="225" alt="Quest cape path">
  <img src="docs/screenshots/4-atlas-diaries.png" width="225" alt="Diary atlas">
</p>

## What it does

- **Account crest.** One ring for your whole account, plus ledgers for quests, diaries and combat achievement tasks.
- **Compass.** Your focus goal and its next step, followed by quests in progress, the quests worth starting right now (ranked by how much they unlock), quests held back by only a few levels, and your closest diaries.
- **Goals.** Pin any quest, diary tier or the quest cape. The first pin is your focus; every goal shows its progress and next step.
- **Atlas.** Search and filter every quest, see each area's diary tiers as seals, and track combat achievement tiers.
- **Path pages.** Open any goal to see every level it needs with live bars, and every quest on the way as a timeline: done, in progress, ready, or blocked and by what.
- **In-game plaque.** A small overlay with your focus and its next step. With nothing pinned it suggests the best quest to start.

<p align="center">
  <img src="docs/screenshots/9-overlay.png" width="300" alt="Focus overlay">
</p>

## Rules posture

Codex Navigator only reads client state you can already see (quest states, varbits, levels) and draws its own panel and overlay. It does not click, move items, change menus, send chat or touch the network. Nothing leaves your computer, and pinned goals are saved per account in your RuneLite profile.

## Data

Quest and diary requirements are bundled in `src/main/resources/com/codex/navigator/codex-data.json`. They are generated from the [Fate Locked](https://github.com/Nubles/OSRS-Fate-Locked) dataset, with names mapped to RuneLite's `Quest` enum so progress can be read from the game. To regenerate the data, see `tools/build-codex-data.py`.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```

The `docs/screenshots` images are rendered from the real panel code with a sample account.
