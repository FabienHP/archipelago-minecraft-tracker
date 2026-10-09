"""Generates the reference results the Java logic port is checked against.

The script loads the Minecraft apworld in a real Archipelago checkout, builds worlds the way
Universal Tracker does (slot data passed through ``re_gen_passthrough``), and records what the
apworld's own rules consider reachable for many inventories. ``GoldenVectorsTest`` then replays
every inventory against the Java port.

Usage, from the repository root:
    .venv/Scripts/python tools/golden/generate_vectors.py
"""
import argparse
import gzip
import itertools
import json
import os
import random
import shutil
import sys
import unittest  # noqa: F401  Archipelago only honours AP_TEST_WORLDS when a test framework is loaded
from argparse import Namespace
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

GEN_STEPS = ("generate_early", "create_regions", "create_items", "set_rules", "connect_entrances",
             "generate_basic", "pre_fill")
EVENTS = ("Blaze Rods", "Ender Dragon", "Wither")

# The structure placement of the seed this project was first played on.
PLAYED_STRUCTURES = {
    "Overworld Structure 1": "Village",
    "Overworld Structure 2": "Nether Fortress",
    "Nether Structure 1": "Pillager Outpost",
    "Nether Structure 2": "End City",
    "The End Structure": "Bastion Remnant",
}


def to_hex(flags):
    """Packs booleans into a hex string, first flag in the lowest bit."""
    value = sum(1 << index for index, flag in enumerate(flags) if flag)
    return format(value, "x")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--archipelago", default=REPO_ROOT / "Archipelago", type=Path)
    parser.add_argument("--apworld", default=REPO_ROOT / "reference" / "minecraft.apworld", type=Path)
    parser.add_argument("--output", type=Path,
                        default=REPO_ROOT / "core" / "src" / "test" / "resources" / "golden" / "vectors.json.gz")
    parser.add_argument("--seed", default=20261009, type=int)
    parser.add_argument("--orders", default=2, type=int, help="item orders replayed per configuration")
    args = parser.parse_args()

    archipelago = args.archipelago.resolve()
    apworld = args.apworld.resolve()
    output = args.output.resolve()

    # Archipelago loads custom worlds from <checkout>/custom_worlds when run from source.
    custom_worlds = archipelago / "custom_worlds"
    custom_worlds.mkdir(exist_ok=True)
    shutil.copyfile(apworld, custom_worlds / "minecraft.apworld")

    os.environ["AP_TEST_WORLDS"] = "minecraft"
    os.environ["SKIP_REQUIREMENTS_UPDATE"] = "1"
    os.chdir(archipelago)
    sys.path.insert(0, str(archipelago))

    from BaseClasses import CollectionState, ItemClassification, MultiWorld
    from worlds import AutoWorld, failed_world_loads
    from worlds.AutoWorld import call_all

    world_type = AutoWorld.AutoWorldRegister.world_types.get("Minecraft")
    if world_type is None:
        raise SystemExit(f"The Minecraft apworld did not load: {failed_world_loads.get('minecraft', 'not found')}")

    from worlds.minecraft import Constants

    rng = random.Random(args.seed)
    region_info = Constants.region_info
    exits = [exit_name for exit_name, _ in region_info["default_connections"]]
    structures = [structure for _, structure in region_info["default_connections"]]
    illegal = region_info["illegal_connections"]
    location_names = Constants.location_info["all_locations"]
    entrance_names = [exit_name for _, region_exits in region_info["regions"] for exit_name in region_exits]

    def is_legal(placement):
        return all(exit_name not in illegal.get(structure, []) for exit_name, structure in placement.items())

    legal_placements = [dict(zip(exits, order)) for order in itertools.permutations(structures)]
    legal_placements = [placement for placement in legal_placements if is_legal(placement)]

    def placements_for_combo():
        """Vanilla, the played seed, the village in each other dimension, and one at random."""
        village_in_nether = [p for p in legal_placements if p["Nether Structure 1"] == "Village"]
        village_in_end = [p for p in legal_placements if p["The End Structure"] == "Village"]
        return [dict(region_info["default_connections"]), PLAYED_STRUCTURES, rng.choice(village_in_nether),
                rng.choice(village_in_end), rng.choice(legal_placements)]

    def build_world(slot_data):
        multiworld = MultiWorld(1)
        multiworld.game[1] = "Minecraft"
        multiworld.player_name = {1: "Tester"}
        multiworld.set_seed(args.seed)
        multiworld.seed_name = "golden"
        multiworld.re_gen_passthrough = {"Minecraft": slot_data}
        options = Namespace()
        for name, option in world_type.options_dataclass.type_hints.items():
            setattr(options, name, {1: option.from_any(option.default)})
        multiworld.set_options(options)
        multiworld.state = CollectionState(multiworld)
        for step in GEN_STEPS:
            call_all(multiworld, step)
        return multiworld

    def evaluate(multiworld, item_names):
        world = multiworld.worlds[1]
        state = CollectionState(multiworld)
        for name in item_names:
            item = world.create_item(name)
            item.classification = ItemClassification.progression
            state.collect(item, prevent_sweep=True)
        state.sweep_for_advancements()
        return {
            "locations": to_hex(multiworld.get_location(name, 1).can_reach(state) for name in location_names),
            "entrances": to_hex(multiworld.get_entrance(name, 1).can_reach(state) for name in entrance_names),
            "events": to_hex(state.has(name, 1) for name in EVENTS),
        }

    configurations = []
    state_count = 0
    combos = itertools.product((0, 1, 2), (0, 1), (False, True), (0, 1))
    for combat_difficulty, structure_compasses, death_link, include_hard in combos:
        for placement in placements_for_combo():
            slot_data = {
                "structures": placement,
                "advancement_goal": 40,
                "egg_shards_required": 0,
                "egg_shards_available": 0,
                "bosses_to_defeat": 1,
                "shuffle_structures": 1,
                "structure_compasses": structure_compasses,
                "combat_difficulty": combat_difficulty,
                "include_hard_advancements": include_hard,
                "include_unreasonable_advancements": 0,
                "include_postgame_advancements": 0,
                "death_link": death_link,
                "immediate_respawn": True,
            }
            multiworld = build_world(slot_data)

            # Every copy of every item the rules can ask for, received one at a time in a random order.
            pool = [item.name for item in multiworld.itempool if item.name in Constants.item_info["progression_items"]]
            orders = []
            for _ in range(args.orders):
                order = pool[:]
                rng.shuffle(order)
                states = [evaluate(multiworld, order[:count]) for count in range(len(order) + 1)]
                state_count += len(states)
                orders.append({"items": order, "states": states})
            configurations.append({"slot_data": slot_data, "orders": orders})

    document = {
        "comment": "Generated by tools/golden/generate_vectors.py from the real apworld. Do not edit.",
        "locations": location_names,
        "entrances": entrance_names,
        "events": list(EVENTS),
        "configurations": configurations,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.GzipFile(output, "wb", mtime=0) as stream:
        stream.write(json.dumps(document, separators=(",", ":")).encode("utf-8"))
    print(f"{len(configurations)} configurations, {state_count} inventories -> {output}")


if __name__ == "__main__":
    main()
