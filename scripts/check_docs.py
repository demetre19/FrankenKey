#!/usr/bin/env python3
"""Doc-repo toolchain checks for FrankenKey.

The repo ships APKs + PRD/plan documents — no compiled source tree. The
test/lint/build checks therefore assert document and manifest integrity:
non-empty plan artifacts, parseable JSON manifests, and the presence of
the crew plan the night manager consumes.
"""
import glob
import json
import sys


def _plan_docs() -> list[str]:
    return (glob.glob("plan/**/*.md", recursive=True)
            + glob.glob("PRD-*.md")
            + glob.glob("plan/**/*.crew.json"))


def test() -> int:
    files = _plan_docs()
    assert files, "no plan/PRD docs found"
    stubs = [f for f in files
             if f.endswith(".md") and len(open(f).read().strip()) < 40]
    assert not stubs, f"stub docs: {stubs}"
    print(f"{len(files)} plan artifacts non-empty")
    return 0


def lint() -> int:
    json.load(open(".prd/profile.json"))
    plans = glob.glob("plan/**/*.crew.json", recursive=True)
    for f in plans:
        json.load(open(f))
    print(f"profile + {len(plans)} crew plans parse")
    return 0


def build() -> int:
    plans = glob.glob("plan/**/*.crew.json", recursive=True)
    assert plans, "no crew plan present"
    print(f"{len(plans)} crew plan(s) present")
    return 0


if __name__ == "__main__":
    check = sys.argv[1] if len(sys.argv) > 1 else "test"
    sys.exit({"test": test, "lint": lint, "build": build}[check]())
