#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
# Compile real pinned function bodies against minimal host mocks. The source
# assertions also delimit hooks that must not expand into linked execution.
python3 - "$repo_dir" "$work_dir" <<'PY'
from pathlib import Path
import subprocess, sys
root, out = map(Path, sys.argv[1:])
pure=root/'third_party/dosbox-pure'
def body(path, signature):
    source=(pure/path).read_text()
    start=source.index(signature)
    opening=source.index('{',start)
    depth=1
    for end in range(opening+1,len(source)):
        depth += (source[end]=='{')-(source[end]=='}')
        if not depth: return source[opening+1:end]
    raise AssertionError(signature)
dyn=(pure/'src/cpu/core_dynrec.cpp').read_text()
assert dyn.count('rw_core_timing.cache().lookup(')==1
assert dyn.index('FindCacheBlock(ip_point&4095)') < dyn.index('rw_core_timing.cache().lookup(') < dyn.index('run_block:')
assert dyn.count('rw_core_timing.event(RW_TRANSLATE)')==1
assert 'rw_core_timing.event(RW_TRANSLATE);\n\t\t\t\tRWCoreTiming::Cache::TranslationScope' in dyn
assert 'block=CreateCacheBlock(chandler,ip_point,32);\n\t\t\t\ttiming.complete();' in dyn
basic=(pure/'src/cpu/core_dynrec/decoder_basic.h').read_text()
assert basic.count('RW_CLEAR_CODE_SIZE')==2 and basic.count('RW_CLEAR_PAGE_PRESSURE')==1
assert 'RWCoreTiming::Cache::PublicationScope' not in (pure/'src/cpu/core_dynrec/risc_armv8le.h').read_text()
arm='src/cpu/core_dynrec/risc_armv8le.h'
assert subprocess.check_output(['git','-C',str(pure),'show','HEAD:'+arm])==(pure/arm).read_bytes()
for name,sig,path in [
    ('publication','static void dyn_closeblock(void)','src/cpu/core_dynrec/decoder_basic.h'),
    ('clear','void CacheBlockDynRec::Clear(void)','src/cpu/dyn_cache.h'),
    ('invalidate','bool InvalidateRange(Bitu start,Bitu end)','src/cpu/dyn_cache.h'),
    ('release','void ClearRelease(void)','src/cpu/dyn_cache.h'),
    ('reclaim','static CacheBlockDynRec * cache_openblock(void)','src/cpu/dyn_cache.h')]:
    (out/(name+'.inc')).write_text(body(path,sig))
PY
g++ -std=c++11 -O2 -Wall -Wextra -Werror -fsanitize=undefined -fno-sanitize-recover=undefined -pthread \
  -I"$repo_dir/third_party/dosbox-pure/include" -I"$work_dir" \
  "$repo_dir/tests/native/cache_hooks_test.cpp" -o "$work_dir/check"
"$work_dir/check"
