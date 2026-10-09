"""Moves the project to a newer NeoForgeAP release.

It does the mechanical part of an update: it downloads the randomizer jar and the apworld of the
release into reference/, copies the apworld's data files into the logic engine, and rewrites the
versions in gradle.properties (including the Minecraft and NeoForge versions when the release is
for a newer Minecraft). What is left for a person is whatever the tests then report.

Usage, from the repository root:
    python tools/update_upstream.py            move to the latest release, if it is newer
    python tools/update_upstream.py --check    only say whether a newer release exists

Only the standard library is used. Set GITHUB_TOKEN to raise GitHub's API rate limit.
"""
import argparse
import json
import os
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = "qixils/NeoForgeAP"
PROPERTIES = REPO_ROOT / "gradle.properties"
REFERENCE = REPO_ROOT / "reference"
DATA_DIR = REPO_ROOT / "core" / "src" / "main" / "resources" / "aptracker" / "data"
GAMETEST_APMC = REPO_ROOT / "mod" / "src" / "gametest" / "apmc" / "gametest.apmc"


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "archipelago-minecraft-tracker"})
    token = os.environ.get("GITHUB_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def read_property(text, key):
    match = re.search(rf"^{re.escape(key)}=(.*)$", text, re.MULTILINE)
    if match is None:
        raise SystemExit(f"'{key}' is missing from a gradle.properties file")
    return match.group(1).strip()


def write_property(text, key, value):
    read_property(text, key)
    return re.sub(rf"^{re.escape(key)}=.*$", lambda _: f"{key}={value}", text, flags=re.MULTILINE)


def numbers(version):
    """The leading numeric components of a version: "26.3.0.26-beta" gives (26, 3, 0, 26)."""
    return tuple(int(part) for part in re.match(r"\d+(?:\.\d+)*", version).group(0).split("."))


def next_minecraft(version):
    """The first version of the next Minecraft drop, used as the open end of the version ranges."""
    major, minor = numbers(version)[:2]
    return f"{major}.{minor + 1}"


def report(outputs):
    """Passes the result on to the following steps when run by GitHub Actions."""
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as stream:
            for key, value in outputs.items():
                stream.write(f"{key}={value}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="report whether a newer release exists, change nothing")
    parser.add_argument("--tag", help="the release to move to (default: the latest one)")
    parser.add_argument("--force", action="store_true", help="apply the release even when the project is already on it")
    args = parser.parse_args()

    properties = PROPERTIES.read_text(encoding="utf-8")
    current_tag = read_property(properties, "upstream_release")
    current_minecraft = read_property(properties, "minecraft_version")

    endpoint = f"releases/tags/{args.tag}" if args.tag else "releases/latest"
    release = json.loads(fetch(f"https://api.github.com/repos/{UPSTREAM}/{endpoint}"))
    tag = release["tag_name"]
    assets = {asset["name"]: asset["browser_download_url"] for asset in release["assets"]}
    jar_name = next((name for name in assets if re.fullmatch(r"aprandomizer-.+\.jar", name)), None)
    if jar_name is None or "minecraft.apworld" not in assets:
        raise SystemExit(f"NeoForgeAP {tag} does not have the expected files: {sorted(assets)}")

    upstream_properties = fetch(f"https://raw.githubusercontent.com/{UPSTREAM}/{tag}/gradle.properties").decode("utf-8")
    minecraft = read_property(upstream_properties, "minecraft_version")
    neoforge = read_property(upstream_properties, "neo_version")
    outputs = {"updated": "false", "tag": tag, "minecraft": minecraft}

    if tag == current_tag and not args.force:
        print(f"Up to date: the project is on NeoForgeAP {tag} (Minecraft {minecraft}).")
        report(outputs)
        return
    if numbers(minecraft) < numbers(current_minecraft):
        print(f"Ignored: NeoForgeAP {tag} is for Minecraft {minecraft}, older than the {current_minecraft} of this project.")
        report(outputs)
        return
    print(f"NeoForgeAP {tag} (Minecraft {minecraft}) is available; the project is on {current_tag} (Minecraft {current_minecraft}).")
    outputs["updated"] = "true"
    if args.check:
        report(outputs)
        return

    # Reference files.
    for old_jar in REFERENCE.glob("aprandomizer-*.jar"):
        old_jar.unlink()
    (REFERENCE / jar_name).write_bytes(fetch(assets[jar_name]))
    apworld_path = REFERENCE / "minecraft.apworld"
    apworld_path.write_bytes(fetch(assets["minecraft.apworld"]))
    print(f"Downloaded reference/{jar_name} and reference/minecraft.apworld.")

    # The apworld's tables, and the data version the randomizer accepts.
    with zipfile.ZipFile(apworld_path) as apworld:
        for name in apworld.namelist():
            if re.fullmatch(r"[^/]+/data/[^/]+\.json", name):
                (DATA_DIR / Path(name).name).write_bytes(apworld.read(name))
        init = next(name for name in apworld.namelist() if re.fullmatch(r"[^/]+/__init__\.py", name))
        client_version = re.search(r"^client_version\s*=\s*(\d+)", apworld.read(init).decode("utf-8"), re.MULTILINE)
    if client_version:
        fixture = GAMETEST_APMC.read_text(encoding="utf-8")
        fixture = re.sub(r'"client_version":\s*\d+', f'"client_version": {client_version.group(1)}', fixture)
        GAMETEST_APMC.write_text(fixture, encoding="utf-8", newline="\n")
    print("Copied the apworld's data files into core/.")

    # Versions.
    new_minecraft = numbers(minecraft)[:2] != numbers(current_minecraft)[:2]
    properties = write_property(properties, "upstream_release", tag)
    properties = write_property(properties, "aprandomizer_jar", f"reference/{jar_name}")
    properties = write_property(properties, "aprandomizer_version_range", "[%d.%d,)" % numbers(tag.lstrip("v"))[:2])
    properties = write_property(properties, "minecraft_version", minecraft)
    properties = write_property(properties, "minecraft_version_range", f"[{minecraft},{next_minecraft(minecraft)})")
    properties = write_property(properties, "neo_version", neoforge)
    properties = write_property(properties, "neo_version_range", f"[{neoforge},{next_minecraft(minecraft)})")
    if tag != current_tag:
        # A new Minecraft version is a new minor version of the tracker; anything else is a patch.
        major, minor, patch = numbers(read_property(properties, "mod_version"))[:3]
        version = f"{major}.{minor + 1}.0" if new_minecraft else f"{major}.{minor}.{patch + 1}"
        properties = write_property(properties, "mod_version", f"{version}+{minecraft}")
    PROPERTIES.write_text(properties, encoding="utf-8", newline="\n")
    outputs["version"] = read_property(properties, "mod_version")
    outputs["new_minecraft"] = "true" if new_minecraft else "false"
    print(f"gradle.properties now targets Minecraft {minecraft}, NeoForge {neoforge}; the tracker is {outputs['version']}.")

    print("Next: regenerate the recordings (tools/golden/generate_vectors.py), then run the tests.")
    if new_minecraft:
        print("This is a new Minecraft version: expect compile errors in mod/ where Minecraft's classes changed.")
    report(outputs)


if __name__ == "__main__":
    sys.exit(main())
