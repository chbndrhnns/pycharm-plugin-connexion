#!/usr/bin/env bash
#
# Regenerates the pytest collector test data used by PytestOutputParserIntegrationTest.
#
# Prerequisites: uv (https://docs.astral.sh/uv/)
#
# What it does:
#   1. Creates a temporary Python 3.13 venv with pytest installed
#   2. Runs `pytest --collect-only -q` on the sample test files in scripts/pytest-collector-testdata/
#   3. Runs the same command with our conftest_collector.py plugin to capture JSON output
#   4. Saves both outputs to src/test/resources/testData/
#
# Usage:
#   ./scripts/regenerate-pytest-collector-testdata.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TESTDATA_SRC="$ROOT_DIR/scripts/pytest-collector-testdata"
TESTDATA_DST="$ROOT_DIR/src/test/resources/testData"
COLLECTOR_SCRIPT="$ROOT_DIR/src/main/resources/scripts"
TMPDIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMPDIR"
}
trap cleanup EXIT

echo "==> Creating temporary Python 3.13 venv in $TMPDIR..."
uv venv --python 3.13 "$TMPDIR/.venv" 2>&1 | tail -1
uv pip install pytest --python "$TMPDIR/.venv/bin/python" 2>&1 | tail -1

PYTHON="$TMPDIR/.venv/bin/python"

# Copy sample test files into the temp dir so pytest paths are clean
cp -r "$TESTDATA_SRC" "$TMPDIR/tests"

echo "==> Running pytest --collect-only -q (quiet output)..."
"$PYTHON" -m pytest --collect-only -q --no-header "$TMPDIR/tests/" \
  > "$TESTDATA_DST/pytest-collect-quiet-output.txt" 2>&1 || true

echo "==> Running pytest --collect-only -q with conftest_collector.py (JSON output)..."
PYTHONPATH="$COLLECTOR_SCRIPT" "$PYTHON" -m pytest \
  --collect-only -q --no-header \
  -p conftest_collector \
  "$TMPDIR/tests/" \
  > /dev/null \
  2> "$TESTDATA_DST/pytest-collect-json-output.txt" || true

# Verify outputs
QUIET_LINES=$(wc -l < "$TESTDATA_DST/pytest-collect-quiet-output.txt" | tr -d ' ')
JSON_SIZE=$(wc -c < "$TESTDATA_DST/pytest-collect-json-output.txt" | tr -d ' ')

echo "==> Generated:"
echo "    pytest-collect-quiet-output.txt  ($QUIET_LINES lines)"
echo "    pytest-collect-json-output.txt   ($JSON_SIZE bytes)"

if [ "$QUIET_LINES" -lt 3 ]; then
  echo "ERROR: Quiet output looks too small" >&2
  exit 1
fi
if [ "$JSON_SIZE" -lt 100 ]; then
  echo "ERROR: JSON output looks too small" >&2
  exit 1
fi

echo "==> Done. Test data regenerated successfully."
