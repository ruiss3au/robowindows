#include "robowindows_core_timing.h"
#include <cassert>
#include <cstdlib>
#include <iostream>
using Bitu=unsigned; using Bits=int; using Bit32u=unsigned;
const unsigned DYN_HASH_SHIFT=4, CACHE_MAXSIZE=64;
RWCoreTiming rw_core_timing;
struct Handler;
struct CacheBlockDynRec {
 struct Hash { unsigned index=1; CacheBlockDynRec* next=nullptr; } hash;
 struct Link { CacheBlockDynRec *from=nullptr,*to=nullptr,*next=nullptr; } link[2];
 struct Page { Handler* handler=nullptr; unsigned start=0,end=0; } page;
 struct Storage { unsigned size=64; CacheBlockDynRec* next=nullptr; unsigned char* start=nullptr; void* wmapmask=nullptr; } cache;
 CacheBlockDynRec* crossblock=nullptr;
 void Clear();
};
struct Handler {
 unsigned write_map[4096]={}, phys_page=0, active_blocks=0, releases=0;
 CacheBlockDynRec* hash_map[258]={};
 void DelCacheBlock(CacheBlockDynRec*) { for(auto& b:write_map) b=0; }
 void Release() { ++releases; }
 bool InvalidateRange(Bitu start,Bitu end);
 void ClearRelease();
};
CacheBlockDynRec link_blocks[2];
struct Cache {
 void* wmapmask=nullptr;
 struct { CacheBlockDynRec* active=nullptr; } block;
 unsigned char* pos=nullptr;
} cache;
static unsigned reg_eip=0, cs=0;
static unsigned SegPhys(unsigned) { return 0; }
static unsigned PAGING_GetPhysicalPage(unsigned) { return 0; }
static void cache_addunusedblock(CacheBlockDynRec*) {}
static void no_log(const char*) { assert(false); }
#define LOG(a,b) no_log
void CacheBlockDynRec::Clear() {
#include "clear.inc"
}
bool Handler::InvalidateRange(Bitu start,Bitu end) {
#include "invalidate.inc"
}
void Handler::ClearRelease() {
#include "release.inc"
}
static CacheBlockDynRec* cache_openblock() {
#include "reclaim.inc"
}
static unsigned close_stage=0;
static void dyn_fill_blocks() { assert(close_stage++==0); }
static void cache_block_before_close() { assert(close_stage++==1); }
static void cache_closeblock() { assert(close_stage++==2); }
static void cache_block_closing(unsigned char*,unsigned) { assert(close_stage++==3); }
struct { CacheBlockDynRec* block=nullptr; } decode;
static void dyn_closeblock() {
#include "publication.inc"
}
static void prepare(CacheBlockDynRec& b,Handler& h) {
 b=CacheBlockDynRec{}; b.page.handler=&h;
 b.link[0].to=&link_blocks[0]; b.link[1].to=&link_blocks[1];
}
int main() {
 rw_core_timing.configure(true,true); rw_core_timing.worker_begin();
 Handler h; CacheBlockDynRec a,b;
 prepare(a,h); prepare(b,h); a.crossblock=&b; b.crossblock=&a;
 h.write_map[0]=1; h.hash_map[1]=&a;
 h.InvalidateRange(0,0); // Real Clear recursively clears its cross-page partner.
 assert(a.page.handler==nullptr && b.page.handler==nullptr);
 h.InvalidateRange(0,0); // No intersecting block is not a clear operation.
 prepare(a,h); h.hash_map[0]=&a; h.active_blocks=1;
 { RWCoreTiming::Cache::ReasonScope r(rw_core_timing.cache(),RW_CLEAR_CODE_SIZE);
   h.ClearRelease(); }
 assert(h.releases==1);
 prepare(a,h); h.ClearRelease(); assert(h.releases==2);
 prepare(a,h); cache.block.active=&a;
 assert(cache_openblock()==&a);
 prepare(a,h); decode.block=&a;
 rw_core_timing.event(RW_TRANSLATE);
 { RWCoreTiming::Cache::TranslationScope t(rw_core_timing.cache()); dyn_closeblock(); t.complete(); }
 assert(close_stage==4);
 rw_core_timing.worker_end();
 RWCacheSnapshot c; auto w=rw_core_timing.take(&c);
 assert(c.invalidate_calls==2 && c.clear_code_write==2 && c.clear_calls==5);
 assert(c.clear_code_size==1 && c.clear_page_release==1 && c.clear_cache_reclaim==1);
 assert(c.samples_completed==1 && c.publication_scopes==1 && c.translation_attempts==w.translations);
 assert(c.clock_errors==0 && c.scope_errors==0);
 std::cout<<"Pinned clear/invalidation/release/reclaim/publication hooks passed; ARM source unchanged\n";
}
