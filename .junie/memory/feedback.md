[2025-12-01 20:28] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "auto-import behavior",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN relative import path does not include parent traversal THEN prefer and add relative import candidate"
}

[2025-12-01 20:29] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "auto-import behavior",
    "ERROR": "Relative import guarded by same-parent check is too strict",
    "NEW INSTRUCTION": "WHEN import can be expressed without parent traversal THEN add and prioritize relative import candidate over absolute options"
}

