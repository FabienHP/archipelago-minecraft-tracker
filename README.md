# Archipelago Advancement Tracker (Minecraft)

A **server-side** NeoForge mod that shows, during a Minecraft
[Archipelago](https://archipelago.gg) game, which advancements are doable with the items received so
far. Players keep a vanilla Minecraft client: there is nothing to install on their side.

It runs next to the randomizer mod ([NeoForgeAP](https://github.com/qixils/NeoForgeAP),
`aprandomizer`) and reads its state. It does not open a second connection to Archipelago.

Built for Minecraft 26.2, NeoForge 26.2.0.88 and `aprandomizer` 2.2.x.

This is a community project. It is not part of Archipelago or NeoForgeAP.

## What players see

- **An "Archipelago Tracker" tab in the advancements screen.** The advancements doable right now
  come first and are lit. Below them, region by region, are the ones still out of logic; hovering one
  lists the items it is waiting for (`Missing: Bucket, Progressive Tools 2`, where the number is the
  level needed). Each region says whether it is reachable and, for a shuffled structure, which
  dimension holds it. The "Progress" entry also lists what is missing to defeat the required bosses.
- **A chat message** when a received item makes new advancements doable.
- **The `/tracker` command**, which lists the doable advancements and brings the tab forward.

The mod's own texts are in English or French, following the language of each client.

## Installing

Put `aptracker-<version>.jar` in the `mods` folder of the server that Archipelago launches, next to
`Archipelago.jar`:

```
Minecraft AP Server Directory/NeoForge 26.2.0/mods/
```

To uninstall, delete that file.

## Building and testing

Gradle needs a JDK 17 or newer to start; it downloads the JDK 25 that Minecraft requires by itself.

| Command | What it does |
|---|---|
| `./gradlew :mod:build` | Builds the jar into `mod/build/libs/` |
| `./gradlew :core:test` | Tests the logic engine |
| `./gradlew :mod:runGameTestServer` | Starts a server with both mods and a mock player, then checks the tab as a client receives it |

The in-server test needs a `.apmc` file in `mod/run/gametest/APData/` (a folder git ignores).

## Layout

| Folder | Content |
|---|---|
| `core/` | The logic engine in plain Java, with no Minecraft dependency: a port of the apworld's `Rules.py` |
| `mod/` | The NeoForge mod: reading the randomizer's state, the tab, the chat message, the command |
| `reference/` | The apworld and the randomizer jar the project is built and tested against |
| `tools/golden/` | A Python script that records the results of the apworld's real logic |

`RandomizerState` is the only class that touches the randomizer's classes.

## How the logic is checked

The engine is compared with the apworld in two ways:

1. `ApworldLogicTest` replays the tests that ship **inside** the apworld (`test/test_*.py`), read
   straight from `reference/minecraft.apworld`.
2. `GoldenVectorsTest` compares the engine with results of the real Python logic, recorded by
   `tools/golden/generate_vectors.py` for 120 configurations (combat difficulty, structure compasses,
   death link, shuffled structures).

## Moving to a new apworld version

1. Replace `reference/minecraft.apworld`.
2. Copy its `data/*.json` files into `core/src/main/resources/aptracker/data/`.
3. Record the results again:
   ```
   .venv/Scripts/python tools/golden/generate_vectors.py
   ```
4. Run `./gradlew :core:test`. The failing tests point at the rules that changed; carry the changes
   over to `core/src/main/java/aptracker/core/logic/Rules.java`, which is written in the same order
   as `Rules.py`.

The script expects a checkout of [Archipelago](https://github.com/ArchipelagoMW/Archipelago) in
`Archipelago/` (ignored by git) and a Python 3.11 to 3.13 environment:

```
python -m venv .venv
.venv/Scripts/pip install PyYAML schema jellyfish platformdirs typing_extensions websockets orjson colorama jinja2 pathspec bsdiff4
```

## Known limits

- Until the server is connected to Archipelago (`/connect`), the received items are unknown: the tab
  says so and only shows what is doable without any item.
- The rule of "Overkill" depends on the `exclude_locations` list of the player's YAML, which neither
  the `.apmc` file nor the Archipelago server passes on. It is assumed to be empty.
- Advancements that need a boss to be defeated are judged on items only, as in the apworld: the
  tracker does not check that the number of advancements required to make the boss appear is reached.

## License

MIT, see [LICENSE](LICENSE). The logic rules and data files come from the Archipelago Minecraft
apworld; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
