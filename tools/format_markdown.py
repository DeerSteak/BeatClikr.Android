#!/usr/bin/env python3

from pathlib import Path
import re
import sys


FENCE = re.compile(r"^\s*(```|~~~)")
LIST_ITEM = re.compile(r"^(\s*(?:[-+*]|\d+[.)])\s+)(.*)$")
SPECIAL = re.compile(r"^\s*(?:#{1,6}\s|---+$|\*\*\*+$|___+$|<)")


def reflow_block(lines: list[str]) -> list[str]:
    if len(lines) <= 1 or any(line.endswith("  ") for line in lines):
        return lines
    if any(line.lstrip().startswith("|") for line in lines):
        return lines
    if all(line.startswith(">") for line in lines):
        text = " ".join(line.lstrip("> ").strip() for line in lines)
        return [f"> {text}"]
    if any(LIST_ITEM.match(line) for line in lines):
        output: list[str] = []
        for line in lines:
            match = LIST_ITEM.match(line)
            if match:
                output.append(f"{match.group(1)}{match.group(2).strip()}")
            elif output and line.startswith((" ", "\t")):
                output[-1] = f"{output[-1]} {line.strip()}"
            else:
                output.append(line)
        return output
    if any(SPECIAL.match(line) or line.startswith(("    ", "\t")) for line in lines):
        return lines
    return [" ".join(line.strip() for line in lines)]


def format_markdown(text: str) -> str:
    output: list[str] = []
    block: list[str] = []
    in_fence = False

    def flush() -> None:
        nonlocal block
        output.extend(reflow_block(block))
        block = []

    for line in text.splitlines():
        if FENCE.match(line):
            flush()
            output.append(line)
            in_fence = not in_fence
        elif in_fence:
            output.append(line)
        elif not line.strip():
            flush()
            output.append("")
        else:
            block.append(line)
    flush()
    return "\n".join(output).rstrip() + "\n"


def main() -> int:
    paths = [Path(value) for value in sys.argv[1:]]
    if not paths:
        print("Usage: format_markdown.py FILE...", file=sys.stderr)
        return 2
    for path in paths:
        path.write_text(format_markdown(path.read_text()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
