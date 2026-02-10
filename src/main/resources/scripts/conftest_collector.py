"""
Custom pytest plugin that emits JSON collection data to stderr.

Injected via: pytest -p conftest_collector --collect-only -q
Outputs a JSON blob wrapped in markers to stderr so the IDE can parse it.
"""
import json
import pytest
import sys


def pytest_collection_finish(session):
    """Hook that fires after collection; emits JSON to stdout."""
    data = {"tests": [], "fixtures": {}}

    for item in session.items:
        fixtures = list(item.fixturenames)
        data["tests"].append({
            "nodeid": item.nodeid,
            "module": item.module.__name__,
            "cls": item.cls.__name__ if item.cls else None,
            "name": item.name,
            "fixtures": fixtures,
        })

    fm = session._fixturemanager
    known_fixtures = set(fm._arg2fixturedefs.keys())
    for test in data["tests"]:
        test["fixtures"] = [f for f in test["fixtures"] if f in known_fixtures]
    for name, fixturedefs in fm._arg2fixturedefs.items():
        for fdef in fixturedefs:
            key = f"{fdef.baseid}::{name}" if fdef.baseid else name
            data["fixtures"][key] = {
                "name": name,
                "scope": fdef.scope,
                "baseid": fdef.baseid,
                "func_name": fdef.func.__name__,
                "module": fdef.func.__module__,
                "argnames": list(fdef.argnames),
                "autouse": getattr(fdef, "_autouse", False),
            }

    marker = "===PYTEST_COLLECTION_JSON==="
    print(f"{marker}{json.dumps(data)}{marker}", file=sys.stderr)
