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
static Bit16u reg_di;
static Bit32u reg_edi, reg_eax;
static PhysPt fail_at;
static std::vector<PhysPt> writes;
static bool mem_writed_checked(PhysPt address, Bit32u value) {
    if (value != 0x1234abcd) std::abort();
    if (address == fail_at) return true;
    writes.push_back(address);
    return false;
}

// Extracted from the actual patched dependency by the shell test, not a copy.
#include "stosd_helper_under_test.h"

static void require(bool condition, const char* message) {
    if (!condition) { std::cerr << message << '\n'; std::exit(1); }
}

static void reset(int cycles, PhysPt fault) {
    CPU_Cycles = cycles;
    fail_at = fault;
    reg_eax = 0x1234abcd;
    writes.clear();
}

int main() {
    for (bool word : {false, true}) {
        for (int direction : {1, -1}) {
            const PhysPt base = 0x30000;
            const uint32_t start = direction > 0 ? 0x1ff0 : 0x200c;
            const uint32_t fault = start + direction * 16;
            // Both full-count and cycle-limited helper slices must report the
            // faulting iteration plus all work deferred to later slices.
            for (int cycles : {100, 6}) {
                reset(cycles, base + fault);
                reg_di = static_cast<Bit16u>(start);
                reg_edi = start;
                const auto left = word ? dynrec_stosd_word(8, direction, base)
                                       : dynrec_stosd_dword(8, direction, base);
                require(left == 4, "fault remaining count");
                require(dynrec_string_exception == 1, "fault reported");
                require((word ? reg_di : reg_edi) == fault, "fault destination");
                require(writes.size() == 4, "only completed prefix written");
                for (unsigned i = 0; i < 4; ++i)
                    require(writes[i] == base + start + direction * 4 * i,
                            "prefix write ordering");
                reset(100, UINT32_MAX);
                const auto remaining = word ? dynrec_stosd_word(4, direction, base)
                                            : dynrec_stosd_dword(4, direction, base);
                require(remaining == 0 && dynrec_string_exception == 0,
                        "retry completes and clears exception flag");
                require(writes.size() == 4, "retry writes only suffix");
            }
            reset(3, UINT32_MAX);
            reg_di = static_cast<Bit16u>(start);
            reg_edi = start;
            const auto left = word ? dynrec_stosd_word(8, direction, base)
                                   : dynrec_stosd_dword(8, direction, base);
            require(left == 5 && writes.size() == 3 && CPU_Cycles == 0,
                    "cycle-limited progress");
            reset(100, base + start);
            reg_di = static_cast<Bit16u>(start);
            reg_edi = start;
            const auto zero = word ? dynrec_stosd_word(0, direction, base)
                                   : dynrec_stosd_dword(0, direction, base);
            require(zero == 0 && writes.empty() && !dynrec_string_exception,
                    "zero count must not access memory");
        }
    }
    reset(100, UINT32_MAX);
    reg_di = 0xfffc;
    require(dynrec_stosd_word(2, 1, 0x30000) == 0, "16-bit wrapping result");
    require(reg_di == 4 && writes == std::vector<PhysPt>({0x3fffc, 0x30000}),
            "16-bit destination wraps");
    reset(100, UINT32_MAX);
    reg_di = 0;
    require(dynrec_stosd_word(2, -1, 0x30000) == 0, "16-bit backward result");
    require(reg_di == 0xfff8 && writes == std::vector<PhysPt>({0x30000, 0x3fffc}),
            "16-bit destination wraps backward");
    std::cout << "Patched STOSD helper checks passed\n";
}
