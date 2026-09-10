#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
source_file="$repo_dir/third_party/dosbox-pure/src/cpu/core_dynrec/operators.h"
[[ -f "$source_file" ]] || { echo 'REP helper checks require the pinned core checkout' >&2; exit 1; }
test_dir=$(mktemp -d)
trap 'rm -rf -- "$test_dir"' EXIT
sed -n '/^static Bit8u dynrec_string_exception;/,/^static void DRC_CALL_CONV dynrec_push_word/{
  /^static void DRC_CALL_CONV dynrec_push_word/q
  p
}' "$source_file" > "$test_dir/string_helpers_under_test.h"
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=undefined -fno-sanitize-recover=undefined \
  -I"$test_dir" "$repo_dir/tests/native/string_helpers_test.cpp" -o "$test_dir/string_helpers_test"
"$test_dir/string_helpers_test"
