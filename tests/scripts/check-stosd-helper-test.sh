#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
source_file="$repo_dir/third_party/dosbox-pure/src/cpu/core_dynrec/operators.h"
if [[ ! -f "$source_file" ]]; then
  echo 'STOSD helper checks skipped: pinned core checkout unavailable'
  exit 0
fi
test_dir=$(mktemp -d)
trap 'rm -rf -- "$test_dir"' EXIT
{
  sed -n '/^static Bit8u dynrec_string_exception;/p' "$source_file"
  sed -n '/^static Bit16u DRC_CALL_CONV dynrec_stosd_word/,/^static void DRC_CALL_CONV dynrec_push_word/{
    /^static void DRC_CALL_CONV dynrec_push_word/q
    p
  }' "$source_file"
} > "$test_dir/stosd_helper_under_test.h"
test -s "$test_dir/stosd_helper_under_test.h"
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=undefined \
  -I"$test_dir" "$repo_dir/tests/native/stosd_helper_test.cpp" \
  -o "$test_dir/stosd_helper_test"
"$test_dir/stosd_helper_test"
