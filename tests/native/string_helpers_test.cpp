#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <vector>

using Bit8u = uint8_t;
using Bit16u = uint16_t;
using Bit16s = int16_t;
using Bit32u = uint32_t;
using Bit32s = int32_t;
using PhysPt = uint32_t;
using Bitu = uintptr_t;
#define DRC_CALL_CONV
#define DRC_FC
static int CPU_Cycles;
static Bit16u reg_si, reg_di, reg_ax;
static Bit32u reg_esi, reg_edi, reg_eax;
static Bit8u reg_al;
static int fail_read, fail_write;
static std::vector<PhysPt> reads, writes;
template<class T> static bool read_checked(PhysPt address, T* value) {
    if (static_cast<int>(reads.size()) == fail_read) return true;
    reads.push_back(address);
    *value = static_cast<T>(0xa5a5a5a5);
    return false;
}
template<class T> static bool write_checked(PhysPt address, T value) {
    if (value != static_cast<T>(0xa5a5a5a5)) std::abort();
    if (static_cast<int>(writes.size()) == fail_write) return true;
    writes.push_back(address);
    return false;
}
#define ACCESS(s, T) \
static bool mem_read##s##_checked(PhysPt a, T* v) { return read_checked(a, v); } \
static bool mem_write##s##_checked(PhysPt a, T v) { return write_checked(a, v); }
ACCESS(b, Bit8u)
ACCESS(w, Bit16u)
ACCESS(d, Bit32u)
#include "string_helpers_under_test.h"

struct Helper {
    const char* name;
    int size;
    bool word, read, write;
    Bit32u (*run)(Bit32u, int);
};
static const Helper helpers[] = {
    {"dynrec_movsb_word", 1, true, true, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_movsb_word(n, d, 0x20000, 0x40000); }},
    {"dynrec_movsb_dword", 1, false, true, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_movsb_dword(n, d, 0x20000, 0x40000); }},
    {"dynrec_movsw_word", 2, true, true, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_movsw_word(n, d, 0x20000, 0x40000); }},
    {"dynrec_movsw_dword", 2, false, true, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_movsw_dword(n, d, 0x20000, 0x40000); }},
    {"dynrec_movsd_word", 4, true, true, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_movsd_word(n, d, 0x20000, 0x40000); }},
    {"dynrec_movsd_dword", 4, false, true, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_movsd_dword(n, d, 0x20000, 0x40000); }},
    {"dynrec_lodsb_word", 1, true, true, false,
     [](Bit32u n, int d) -> Bit32u { return dynrec_lodsb_word(n, d, 0x20000); }},
    {"dynrec_lodsb_dword", 1, false, true, false,
     [](Bit32u n, int d) -> Bit32u { return dynrec_lodsb_dword(n, d, 0x20000); }},
    {"dynrec_lodsw_word", 2, true, true, false,
     [](Bit32u n, int d) -> Bit32u { return dynrec_lodsw_word(n, d, 0x20000); }},
    {"dynrec_lodsw_dword", 2, false, true, false,
     [](Bit32u n, int d) -> Bit32u { return dynrec_lodsw_dword(n, d, 0x20000); }},
    {"dynrec_lodsd_word", 4, true, true, false,
     [](Bit32u n, int d) -> Bit32u { return dynrec_lodsd_word(n, d, 0x20000); }},
    {"dynrec_lodsd_dword", 4, false, true, false,
     [](Bit32u n, int d) -> Bit32u { return dynrec_lodsd_dword(n, d, 0x20000); }},
    {"dynrec_stosb_word", 1, true, false, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_stosb_word(n, d, 0x40000); }},
    {"dynrec_stosb_dword", 1, false, false, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_stosb_dword(n, d, 0x40000); }},
    {"dynrec_stosw_word", 2, true, false, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_stosw_word(n, d, 0x40000); }},
    {"dynrec_stosw_dword", 2, false, false, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_stosw_dword(n, d, 0x40000); }},
    {"dynrec_stosd_word", 4, true, false, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_stosd_word(n, d, 0x40000); }},
    {"dynrec_stosd_dword", 4, false, false, true,
     [](Bit32u n, int d) -> Bit32u { return dynrec_stosd_dword(n, d, 0x40000); }}
};
static int failures;
static void require(bool value, const Helper& h, const char* what) {
    if (!value) {
        if (failures < 30) std::cerr << h.name << ": " << what << '\n';
        ++failures;
    }
}
static Bit32u offset(const Helper& h, Bit32u start, int direction, unsigned n) {
    Bit32u value = start + direction * h.size * n;
    return h.word ? static_cast<Bit16u>(value) : value;
}
static void check(const Helper& h, int direction, Bit32u count, int budget,
                  int fault, bool write_fault, Bit32u start) {
    reg_si = reg_di = static_cast<Bit16u>(start);
    reg_esi = reg_edi = start;
    reg_al = 0xa5; reg_ax = 0xa5a5; reg_eax = 0xa5a5a5a5;
    const bool load = h.read && !h.write;
    if (load) { reg_al = 0; reg_ax = 0; reg_eax = 0; }
    CPU_Cycles = budget;
    dynrec_string_exception = 1; // Every call must clear stale exception state.
    reads.clear(); writes.clear();
    fail_read = write_fault ? -1 : fault;
    fail_write = write_fault ? fault : -1;
    const unsigned slice = std::min(count, static_cast<Bit32u>(std::max(0, budget)));
    const bool hits_fault = fault >= 0 && static_cast<unsigned>(fault) < slice;
    const unsigned done = hits_fault ? static_cast<unsigned>(fault) : slice;
    const auto left = h.run(count, direction);
    require(left == count - done, h, "remaining count");
    require(CPU_Cycles == budget - static_cast<int>(done), h, "completed element cycle charge");
    require(dynrec_string_exception == hits_fault, h, "exception state");
    if (load) {
        require(reg_al == (done && h.size == 1 ? 0xa5 : 0), h, "byte load/fault value");
        require(reg_ax == (done && h.size == 2 ? 0xa5a5 : 0), h, "word load/fault value");
        require(reg_eax == (done && h.size == 4 ? 0xa5a5a5a5 : 0), h, "dword load/fault value");
    }
    require((h.word ? reg_si : reg_esi) == offset(h, start, direction, h.read ? done : 0),
            h, "source progress");
    require((h.word ? reg_di : reg_edi) == offset(h, start, direction, h.write ? done : 0),
            h, "destination progress");
    const unsigned read_count = h.read ? done + (hits_fault && write_fault ? 1 : 0) : 0;
    require(reads.size() == read_count, h, "source access count");
    require(writes.size() == (h.write ? done : 0), h, "destination access count");
    for (unsigned i = 0; i < reads.size(); ++i)
        require(reads[i] == 0x20000 + offset(h, start, direction, i), h, "source ordering/wrap");
    for (unsigned i = 0; i < writes.size(); ++i)
        require(writes[i] == 0x40000 + offset(h, start, direction, i), h, "destination ordering/wrap");
    if (hits_fault) {
        fail_read = fail_write = -1;
        reads.clear(); writes.clear();
        CPU_Cycles = static_cast<int>(left) + 10;
        require(h.run(left, direction) == 0, h, "retry completes");
        require(CPU_Cycles == 10 && !dynrec_string_exception, h, "retry charges suffix only");
        require(reads.size() == (h.read ? left : 0), h, "retry source count");
        require(writes.size() == (h.write ? left : 0), h, "retry destination count");
        require((h.word ? reg_si : reg_esi) == offset(h, start, direction, h.read ? count : 0),
                h, "retry source progress");
        require((h.word ? reg_di : reg_edi) == offset(h, start, direction, h.write ? count : 0),
                h, "retry destination progress");
    }
}
int main() {
    for (const auto& h : helpers) check(h, 1, 4096, 20000, -1, false, 0);
    for (const auto& h : helpers) {
        for (int direction : {1, -1}) {
            for (Bit32u start : {0u, 0xfffcu, 0xfffffffcu}) {
                for (int budget : {INT32_MIN, -1, 0, 3, 8, 100}) {
                    check(h, direction, 8, budget, -1, false, start);
                    check(h, direction, 0, budget, 0, h.write, start);
                    for (int fault : {0, 2, 4, 7}) {
                        if (h.read) check(h, direction, 8, budget, fault, false, start);
                        if (h.write) check(h, direction, 8, budget, fault, true, start);
                    }
                }
            }
        }
        check(h, 1, 4096, 20000, -1, false, 0);
        check(h, 1, h.word ? 65535u : UINT32_MAX, 3, -1, false, 0);
    }
    if (failures) { std::cerr << failures << " REP helper assertions failed\n"; return 1; }
    std::cout << "All 18 production REP helpers: cycle/fault/retry/wrap checks passed\n";
}
