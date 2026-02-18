#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

platform_type="$(rg -n "^platformType=" "$project_root/gradle.properties" | head -n1 | sed 's/.*=//')"
platform_version="$(rg -n "^platformVersion=" "$project_root/gradle.properties" | head -n1 | sed 's/.*=//')"

if [[ -z "$platform_type" || -z "$platform_version" ]]; then
  echo "Missing platformType/platformVersion in gradle.properties" >&2
  exit 1
fi

config_options_dir_default="$project_root/build/idea-sandbox/${platform_type}-${platform_version}/config_integrationTest/options"
config_options_dir="${IDE_CONFIG_OPTIONS_DIR:-$config_options_dir_default}"

sdk_name="Python (project venv)"

test_project="$project_root/src/integrationTest/resources/testProjects/simpleProject"
venv_dir="$test_project/.venv"
python_bin=""

if [[ -x "$venv_dir/bin/python" ]]; then
  python_bin="$venv_dir/bin/python"
else
  if command -v python3 >/dev/null 2>&1; then
    python3 -m venv "$venv_dir"
    python_bin="$venv_dir/bin/python"
  elif command -v python >/dev/null 2>&1; then
    python -m venv "$venv_dir"
    python_bin="$venv_dir/bin/python"
  else
    echo "python3 or python is required to create a venv" >&2
    exit 1
  fi
fi

python_version="$($python_bin -c 'import sys; print("Python %d.%d.%d" % sys.version_info[:3])')"

mkdir -p "$config_options_dir"

sdk_uuid="$($python_bin - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

cat >"$config_options_dir/jdk.table.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<application>
  <component name="ProjectJdkTable">
    <jdk version="2">
      <name value="$sdk_name" />
      <type value="Python SDK" />
      <version value="$python_version" />
      <homePath value="$python_bin" />
      <roots>
        <classPath>
          <root type="composite" />
        </classPath>
        <sourcePath>
          <root type="composite" />
        </sourcePath>
      </roots>
      <additional SDK_UUID="$sdk_uuid">
        <setting name="FLAVOR_ID" value="VirtualEnvSdkFlavor" />
        <setting name="FLAVOR_DATA" value="{}" />
      </additional>
    </jdk>
  </component>
</application>
EOF

echo "Wrote $config_options_dir/jdk.table.xml for $sdk_name -> $python_bin"
