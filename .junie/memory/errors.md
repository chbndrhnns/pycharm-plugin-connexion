[2025-12-31 21:26] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml semantic errors: unknown language id 'Python'",
    "ROOT CAUSE": "plugin.xml validation requires Python language dependency; unresolved without Python plugin declared.",
    "PROJECT NOTE": "Add Python plugin dependency in plugin.xml, e.g., <depends>Pythonid</depends>, to resolve language=\"Python\" usages.",
    "NEW INSTRUCTION": "WHEN plugin.xml validation flags unknown Python language THEN declare <depends>Pythonid</depends> in plugin.xml"
}

[2026-01-01 09:58] - Updated by Junie - Error analysis
{
    "TYPE": "permission",
    "TOOL": "WrapTestInClassIntention",
    "ERROR": "PSI changed outside WriteCommandAction/CommandProcessor",
    "ROOT CAUSE": "Refactoring invokes PSI replace/insert without wrapping in a write command.",
    "PROJECT NOTE": "Wrap all PSI mutations in WriteCommandAction.runWriteCommandAction(project) and commit documents before/after changes.",
    "NEW INSTRUCTION": "WHEN performing PSI modifications in intention or handler THEN wrap logic in WriteCommandAction"
}

