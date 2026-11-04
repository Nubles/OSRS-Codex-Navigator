# Codex Navigator

**An account compass for Old School RuneScape.** Pin anything you want to reach (a quest, a diary tier, the quest cape or a level) and Codex Navigator charts the whole way there from your real progress, prerequisites first, down to the single next step and about how much play is left.

Part of the **Codex** suite of RuneLite plugins, which share one look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/1-compass.png" width="225" alt="Compass: your focus, its road and the best quests to do next">
  <img src="docs/screenshots/2-goals.png" width="225" alt="Goals and the next session">
  <img src="docs/screenshots/4-atlas-diaries.png" width="225" alt="Atlas: diary tiers as seals">
</p>

## The panel

- **Crest.** One ring for your whole account, with ledgers for the quest cape (counted as the cape counts quests), diaries and combat achievement tasks.
- **Compass.** Your focus goal and its next step, then quests in progress, the **best next** quests (those on the way to your goals first, then by how much each opens per hour), quests you can start today with a pie or potion (**Boost and go**), quests held back by only a little training (**Almost there**), the diary tiers within reach and how fast you really train.
- **The road.** Under your focus, the way there drawn as a road on an old map: every quest a stone, gold behind you, a glowing stone for the next one and a flag at the goal. Click a stone to open its quest. **Save road card** or **Copy** makes a picture of it to share.
- **Goals.** Pin any quest, diary tier, the quest cape or a level. The first pin is your focus. Each goal shows its progress, next step and the play left. A finished goal tells you in the chatbox and offers the next in line to pin. With several goals pinned, their paths merge into one route, shared steps once. **Next session** fills one to four hours from your goals.
- **Level goals.** "Aiming for a level?" opens a skill grid that offers the levels worth aiming for: the next ones that open a quest or diary tier, the next round ten, and 99.
- **Atlas.** Search and filter every quest, see each area's diary tiers as seals, and track combat achievement tiers.
- **Fastest path.** On the path page of any quest, diary tier or the quest cape, Navigator solves the quickest order to it from where your account stands: every quest that can be started comes first, since the experience it rewards only ever shortens training later, and when none can, it opens the quest that costs the least training next, takes quest points from the quickest quests outside the goal when a quest asks for more, and trains a diary's own levels last. It shows the total ("~306 h in all", quests and training apart), how much quest experience the order banks and the hours that saves against training every level up front, the **critical path** drawn as one spine (the chain of steps that wait on each other, with its longest wait), the skills that **gate** the goal most with what each opens, and the numbered order with each quest's reward. **Set as goal board** makes the goal your focus; the goal board other plugins read then carries its fastest order.
- **Quest prep.** Under your focus, the next quest on its way made ready, the way Quest Helper lists a quest's items but checked against your own account: what still stands in the way and about how long each takes (a quest, a level, or "train to 51, then a summer pie"), the levels a pie or potion covers on the day, and every item the quest's OSRS Wiki page says to bring, marked **with you**, **in bank**, **to get** or **in quest** (the quest hands it out). A kind of thing ("Food", "Any bow") is listed but never ticked, and without a bank seen the card says so instead of calling items missing. A quest's own page carries the same card. Item lists are bundled for 56 quests so far, the free quests and those most paths cross; the rest say the list is not bundled yet.
- **Path pages.** Open any goal for every level, quest point and combat level it still needs, every quest on the way as a timeline (done, in progress, ready, or blocked and by what), and what finishing it leads to.
- **Your pace.** Navigator learns how fast you really train each skill from the experience you gain while it runs, and times every estimate with it.
- **In-game plaque.** A small overlay with your focus and its next step; with nothing pinned it suggests the best quest to start.
- **Skills tab bars.** Every skill a pinned goal still needs gets a slim gold bar in the game's own skills tab, running to the highest level your goals ask for and notched where lower goals fall. A small gold diamond marks a skill that is the next step of a goal: nothing else on its path can be done first. Rest the pointer on one for a note beside the tab naming each goal that needs it, its level and the experience left (or "boost" where a pie or potion covers it).

<p align="center">
  <img src="docs/screenshots/10-road.png" width="225" alt="The road to Desert Treasure II, with a date">
  <img src="docs/screenshots/6-detail-quest.png" width="225" alt="A quest's path page">
  <img src="docs/screenshots/8-detail-cape.png" width="225" alt="The quest cape's path page">
  <img src="docs/screenshots/12-fastest-cape.png" width="225" alt="The fastest path to the quest cape">
</p>

<p align="center">
  <img src="docs/screenshots/11-road-card.png" width="300" alt="Road card">
  <img src="docs/screenshots/9-overlay.png" width="300" alt="Focus overlay">
</p>

<p align="center">
  <img src="docs/screenshots/14-quest-prep.png" width="225" alt="Quest prep: what blocks the next quest and what to bring">
  <img src="docs/screenshots/15-quest-prep-unseen.png" width="225" alt="Quest prep with the bank not yet seen">
</p>

<p align="center">
  <img src="docs/screenshots/13-skills-tab.png" width="476" alt="Goal bars in the skills tab, with the hover note">
</p>

## Good to know

- **Diaries.** The game tells plugins which diary tiers are complete, not which of their tasks you have done. Once every level and quest a tier needs is met, Navigator says **Tasks** rather than a percentage it cannot measure.
- **Free worlds.** On a free world only free quests are suggested and diaries are left out.
- **Time estimates.** Times assume steady, ordinary training and a typical first play-through of each quest, until Navigator has seen 15 minutes of your own training in a skill. They are for comparing one choice with another, so they read as "about".
- **Quest experience.** The fastest path counts the set experience quests reward, from the OSRS Wiki's quest experience rewards tables, for Agility, Attack, Construction, Cooking, Crafting, Defence, Farming, Firemaking, Fishing, Fletching, Herblore and Hitpoints. Lamps and rewards you choose a skill for are left out, and so far the other skills' rewards are too, which only ever makes an estimate a little long.
- **Boosts.** A level counts as boostable only where Quest Helper or the quest's OSRS Wiki page marks it so, and only for plain, repeatable boosts (pies, potions, the dwarven stout). Ironmen are only offered boosts they can make or buy in a shop.
- **Logged out.** Goals you pin while logged out join your account's goals at your next login.

## Settings that matter

| Setting | Default | What it does |
| --- | --- | --- |
| Show focus in game | On | The in-game plaque with your focus and its next step. |
| Suggest with no focus | On | With nothing pinned, the plaque suggests the best quest to start. |
| Announce finished goals | On | A chatbox line, seen only by you, the moment a pinned goal is complete. |
| Goal bars in skills tab | On | A gold bar on each skill a pinned goal needs, toward that level, with a hover note. |
| Other skills to next level | Off | Skills no goal needs show a quieter bar toward their next level. |
| Include miniquests | On | Lists and suggests miniquests alongside quests. |
| Learn my pace | On | Times training by how fast you really train each skill. Off uses steady rates. |
| Hardcore care | On | On a one-life account, marks quests with a deadly fight or a Wilderness trip and suggests them after safer ones. |
| Name on cards | On | Your character's name on the road card. |

## With other Codex plugins

Navigator works on its own. With these installed it knows more, and only ever reads what they save:

- **[Codex Session Chronicle](https://github.com/Nubles/OSRS-Codex-Session-Chronicle):** how much you have played over the last two weeks, which turns each goal's hours into a day on the calendar ("Around 3 Nov at your 40 min a day"). Without it, no dates are shown.
- **[Codex Guardian](https://github.com/Nubles/OSRS-Codex-Guardian):** an account you marked one-life there gets Hardcore care, as hardcore ironmen and Hardcore World accounts do anyway.
- **[Codex Bank Steward](https://github.com/Nubles/OSRS-Codex-Bank-Steward):** the bank as it last saw it (its shared `bankV1`), so quest prep can say what is in the bank before you open it this session. Without it, prep checks the bank once you open it.

Your pinned goals are shared the other way: Bank Steward, Annals, Plunder Pilot, Pest Control Pilot and Minigame Pilot read them to point their own suggestions at what you are working toward.

## Privacy and rules

Navigator only reads what the client already shows you (quest states, diary and combat achievement progress, levels, and your pack, worn items and bank for quest prep, kept in memory only) and draws its own panel and overlay. It does not click, change menus, send chat or touch the network; its one chatbox line is seen only by you. Pinned goals and your learned pace are saved per account in your RuneLite profile and never leave your computer. A damaged save loses only the entries that cannot be read, and a save it can read only in part (damaged, or from a newer version) is copied aside unchanged before anything is saved over it.

## For other plugins

Codex Navigator saves two keys per RuneScape profile in the `codexnavigator` config group, which any plugin can read with `ConfigManager.getRSProfileConfiguration("codexnavigator", key)`. Both shapes are stable: fields may be added, never renamed or removed.

- **`goals`**: the pinned goals in order, focus first, as a JSON list of keys: `"quest:<name>"` (names as in RuneLite's `Quest` enum), `"diary:<Area> <Tier>"` such as `"diary:Ardougne Easy"`, `"cape:Quest point cape"`, and `"skill:<Skill> <level>"` such as `"skill:Agility 70"`. Readers should keep keys of a kind they do not know.
- **`goalBoard`**: the same goals with what they still need, written whenever that changes while logged in:

```json
{
  "version": 1,
  "focus": "quest:Regicide",
  "goals": [
    {
      "key": "quest:Regicide",
      "type": "quest",
      "title": "Regicide",
      "complete": false,
      "progress": 0.42,
      "next": "Start Plague City",
      "hoursLeft": 4.5,
      "skills": { "Agility": 56, "Ranged": 25 },
      "quests": ["Plague City", "Biohazard", "Underground Pass", "Regicide"],
      "fastest": ["Start Plague City", "Start Biohazard", "Train Ranged to 25", "Start Underground Pass", "Train Agility to 56", "Start Regicide"],
      "fastestHours": 7.5,
      "gatedBy": { "Agility": 3.0, "Ranged": 0.5 }
    }
  ]
}
```

`type` is `quest`, `diary`, `cape` or `skill` (a level goal: its `skills` holds the one level, its `quests` is empty). `skills` lists only unmet levels, by skill name. `quests` lists unfinished quests on the way, prerequisites first. `progress` is rounded to two decimals and `hoursLeft` to the half hour. `next` is absent once a goal is complete, and `focus` is absent with nothing pinned. The focus goal alone, when it is a quest, a diary tier or the cape and not yet complete, also carries `fastest` (the fastest order as plain steps, quest rewards banked before training), `fastestHours` (to the half hour) and `gatedBy` (hours of training per gating skill, most first); every other goal leaves them out.

## Data

Quest and diary requirements are bundled in `src/main/resources/com/codex/navigator/codex-data.json`. They are generated from the [Fate Locked](https://github.com/Nubles/OSRS-Fate-Locked) dataset, with names mapped to RuneLite's `Quest` enum so progress can be read from the game. To regenerate the data, see `tools/build-codex-data.py`.

What each quest asks you to bring comes from the "Items required" and "Recommended" rows of each quest's OSRS Wiki page, kept in `tools/quest-items-source.py`; `tools/build-quest-items.py` matches every name to RuneLite's item ids and writes `quest-items.json`. Quest Helper's item lists were looked at for ideas only; none of its data or code is used for them.

Which skill requirements can be boosted comes from [Quest Helper](https://github.com/Zoinkwiz/quest-helper) (BSD 2-Clause, see `THIRD_PARTY_NOTICES.md`) plus four the OSRS Wiki marks boostable that it does not, and is bundled as `boosts.json`; `tools/build-boosts.py` rebuilds it.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```

The `docs/screenshots` images are rendered from the real panel code with a sample account.
