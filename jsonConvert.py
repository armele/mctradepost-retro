#!/usr/bin/env python3
"""
jsonConvert.py

Clone Minecraft block-state (or any JSON) files that match
    <start_pattern>*\.json
to new files whose names (and internal references) use
    <new_pattern>.

Usage
-----
python jsonConvert.py \
       --dir <path/to/folder> \
       --start stone_bricks \
       --new   cracked_stone_bricks

Examples: 
python jsonConvert.py --dir src/main/resources/assets/mctradepost/blockstates  --start mixed_stone --new endethyst_brick
python jsonConvert.py --dir src/main/resources/assets/mctradepost/models/block --start mixed_stone --new endethyst_brick
python jsonConvert.py --dir src/main/resources/assets/mctradepost/models/item  --start mixed_stone --new endethyst_brick
python jsonConvert.py --dir src/main/resources/data/mctradepost/loot_tables/blocks  --start mixed_stone --new endethyst_brick
python jsonConvert.py --dir src/main/resources/data/mctradepost/recipe  --start mixed_stone --new endethyst_brick


For every file such as  stone_bricks.json  or  stone_bricks_slab.json
found in <path/to/folder>, the script will create a copy named
cracked_stone_bricks.json, cracked_stone_bricks_slab.json, …,
and inside each copy it will replace every occurrence of the text
“stone_bricks” with “cracked_stone_bricks”.
"""

import argparse
import sys
from pathlib import Path
from typing import Iterable


def find_blockstate_files(directory: Path, start_pattern: str) -> Iterable[Path]:
    """
    Yield every *.json Path in *directory* whose name starts with *start_pattern*.
    """
    yield from directory.glob(f"{start_pattern}*.json")


def clone_file(src: Path, start_pattern: str, new_pattern: str) -> None:
    """
    Copy *src* to a new file whose name (and internal contents)
    uses *new_pattern* instead of *start_pattern*.
    """
    dst_name = src.name.replace(start_pattern, new_pattern, 1)
    dst_path = src.with_name(dst_name)

    # Read, transform, write
    try:
        text = src.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        print(f"⚠️  Cannot decode {src}: {exc}", file=sys.stderr)
        return

    new_text = text.replace(start_pattern, new_pattern)

    if dst_path.exists():
        print(f"⚠️  Skipping existing file: {dst_path}")
        return

    dst_path.write_text(new_text, encoding="utf-8")
    print(f"✓ {src.name} → {dst_path.name}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Clone block-state JSON files.")
    parser.add_argument(
        "--dir",
        default=".",
        type=Path,
        help="Directory to search (default: current directory)",
    )
    parser.add_argument("--start", required=True, help="Original pattern (prefix)")
    parser.add_argument("--new", required=True, help="Replacement pattern (prefix)")
    args = parser.parse_args()

    directory: Path = args.dir.expanduser().resolve()
    if not directory.is_dir():
        sys.exit(f"❌ Directory does not exist: {directory}")

    files = list(find_blockstate_files(directory, args.start))
    if not files:
        sys.exit("No matching files found – nothing to do.")

    for src in files:
        clone_file(src, args.start, args.new)


if __name__ == "__main__":
    main()
