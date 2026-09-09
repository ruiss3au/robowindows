#!/usr/bin/env bash
# Fast publication-hygiene checks over tracked source.
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

failed=0
while IFS= read -r -d '' path; do
  # A tracked file deleted in the worktree remains in `git ls-files --cached`
  # until commit; deletion itself must not make the hygiene check unreadable.
  [[ -e $path ]] || continue
  case ${path,,} in
    *.iso|*.img|*.img.gz|*.ima|*.vhd|*.vhdx|*.qcow2|*.key|*.keystore|*.jks|*.apk)
      echo "Prohibited tracked artifact: $path" >&2; failed=1 ;;
  esac
  size=$(wc -c <"$path")
  if ((size > 5 * 1024 * 1024)); then
    echo "Unexpected tracked file larger than 5 MiB: $path ($size bytes)" >&2
    failed=1
  fi
done < <(git ls-files --cached --others --exclude-standard -z)

if git ls-files | rg -q '^(build|artifacts|backups|third_party)/'; then
  echo "Generated or private top-level output is tracked." >&2
  failed=1
fi
if rg -n --hidden --glob '!.git/**' --glob '!backups/**' --glob '!artifacts/**' \
    --glob '!third_party/**' \
    '(BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|product[ _-]?key[[:space:]]*[:=][[:space:]]*[A-Za-z0-9-]{10,})' .; then
  echo "Possible credential or private key found." >&2
  failed=1
fi
if rg -n --pcre2 --glob '*.md' --glob '*.sh' --glob '!backups/**' \
    --glob '!artifacts/**' --glob '!third_party/**' \
    'adb\s+-s\s+(?!["$])[A-Za-z0-9._:-]{8,}' .; then
  echo "A literal ADB device serial may be tracked; use ROBOWINDOWS_DEVICE_SERIAL." >&2
  failed=1
fi

git diff --check
for script in scripts/*.sh scripts/lib/*.sh; do
  bash -n "$script"
done
((failed == 0)) || exit 1
echo "Repository hygiene checks passed"
