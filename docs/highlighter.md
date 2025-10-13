### Short answer
You can’t call `DaemonCodeAnalyzerImpl#getHighlights` from a plugin because it’s `@TestOnly` and the whole `impl` package is not part of the open API. There is no public API that returns a list of `HighlightInfo`s.

### What you can use from public API
If your goal is to access the highlights currently shown in the editor (what users see on the screen and in the error stripe), you can read them through the editor’s markup models. That is the supported, public way:

- Get the document’s markup model via `DocumentMarkupModel.forDocument(document, project, /* create= */ false)`.
- Iterate `RangeHighlighter`s in the desired range.
- Optionally filter by layer or by tooltip/attributes if you need to approximate severity.

Example:
```java
Editor editor = /* your editor */;
Project project = editor.getProject();
Document document = editor.getDocument();
MarkupModel model = DocumentMarkupModel.forDocument(document, project, false);

if (model instanceof MarkupModelEx mm) {
  int start = 0;
  int end = document.getTextLength();
  List<RangeHighlighter> result = new ArrayList<>();
  mm.processRangeHighlightersOverlappingWith(start, end, h -> {
    // Filter if you care only about daemon highlights (as opposed to your own)
    // Common heuristics:
    //  - Layer close to HighlighterLayer.ERROR/WARNING
    //  - Has error stripe mark or tooltip
    //  - HighlighterTargetArea.EXACT_RANGE
    if (h.getTargetArea() == HighlighterTargetArea.EXACT_RANGE && h.getErrorStripeTooltip() != null) {
      result.add(h);
    }
    return true; // continue
  });

  // Now `result` holds the highlighters that correspond to visible highlights
  // You can inspect: h.getStartOffset(), h.getEndOffset(), h.getTextAttributes(...),
  // h.getErrorStripeMarkColor(), h.getErrorStripeTooltip(), etc.
}
```
Notes:
- `RangeHighlighter` is a stable, public abstraction. Avoid relying on `HighlightInfo` or `HighlightInfo.HIGHLIGHT_INFO_KEY` — both live in `impl` and can change without notice.
- If you need a severity-like notion, you can approximate using:
  - `h.getLayer()` (compare to `HighlighterLayer.ERROR`, `HighlighterLayer.WARNING`, etc.)
  - `h.getErrorStripeMarkColor()` and `h.isThinErrorStripeMark()`
  - The tooltip object’s type/content (many inspections set a structured tooltip)

### Ensuring highlights are available
The daemon runs asynchronously. There’s no public API to “block until all highlighting is done”. Typical approaches:
- If you operate in response to user actions after the editor has been visible for a bit, highlights are usually already present.
- If you must trigger highlighting, you can call `DaemonCodeAnalyzer.getInstance(project).restart(psiFile)` and later read the markup model, but you cannot wait synchronously using public API. Consider designing your feature to react to updates (e.g., via editor repaint/error-stripe changes) instead of waiting.

### Alternatives depending on your goal
- If you actually need results of a specific inspection, prefer running that inspection via the inspections API and consuming its `ProblemDescriptor`s, instead of scraping daemon highlights.
- For file-level (gutter/header) problems, `WolfTheProblemSolver` exposes whether a file has problems, but not the per-range details.

### Summary
- `DaemonCodeAnalyzerImpl#getHighlights` is test-only; there’s no public equivalent returning `HighlightInfo`.
- Public, supported way: query the editor’s `MarkupModel`/`MarkupModelEx` for `RangeHighlighter`s and use their public properties (offsets, attributes, tooltip, stripe mark) to get the highlights users see.