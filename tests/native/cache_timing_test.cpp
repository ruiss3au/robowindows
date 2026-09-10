#include "robowindows_cache_timing.h"
#include "robowindows_core_timing.h"
#include <cassert>
#include <iostream>
#include <thread>
struct Clock {
 static int64_t w, c;
 static unsigned reads;
 static int64_t wall() { ++reads; return w; }
 static int64_t cpu() { ++reads; return c; }
};
int64_t Clock::w=0, Clock::c=0; unsigned Clock::reads=0;
using Probe=RWCacheTiming<Clock>;
static void work(Probe& p, unsigned publications=1) {
 Probe::TranslationScope t(p);
 for(unsigned i=0;i<publications;++i) {
  Probe::PublicationScope pub(p);
  Clock::w+=300; Clock::c+=200;
 }
 Clock::w+=1000; Clock::c+=500;
 t.complete();
}
int main() {
 Probe p;
 p.begin(false); work(p); p.lookup(true); p.clear(); p.finish();
 assert(Clock::reads==0 && p.totals().snapshot().translation_attempts==0);
 for(unsigned count : {0u,1u,64u,65u,193u,194u,512u}) {
  p.begin(true); unsigned reads=Clock::reads;
  for(unsigned i=0;i<count;++i) work(p,5);
  p.finish(); auto s=p.totals().snapshot();
  unsigned selected=count ? std::min(4u,1u+(count-1)/64) : 0;
  assert(s.translation_attempts==count && s.samples_completed==selected);
  assert(s.samples_started==selected && s.samples_discarded==0);
  assert(s.translation_attempts==s.stride_skipped+s.cap_skipped+s.samples_started);
  assert(s.publication_scopes==4*selected && s.publication_skipped==selected);
  assert(Clock::reads-reads==selected*20 && Clock::reads-reads<=80);
  assert(s.publication_cpu_sample_us<=s.translation_cpu_sample_us);
 }
 p.begin(true);
 { Probe::TranslationScope outer(p);
   work(p); // Nested attempts are not independently sampled.
   { Probe::PublicationScope pub(p); Probe::PublicationScope inner(p);
     Clock::w+=1000; Clock::c+=1000; }
   outer.complete(); }
 p.finish(); auto s=p.totals().snapshot();
 assert(s.translation_attempts==2 && s.nested_attempts==1 && s.samples_completed==1);
 assert(s.publication_scopes==2 && s.scope_errors==0);
 p.begin(true);
 p.lookup(true); p.lookup(false);
 for(auto reason : {RW_CLEAR_CODE_WRITE,RW_CLEAR_CACHE_RECLAIM,RW_CLEAR_CODE_SIZE,
                    RW_CLEAR_PAGE_PRESSURE,RW_CLEAR_PAGE_RELEASE,RW_CLEAR_OTHER}) {
  Probe::ReasonScope r(p,reason); p.clear();
 }
 { Probe::ReasonScope specific(p,RW_CLEAR_CODE_WRITE);
   { Probe::ReasonScope generic(p,RW_CLEAR_PAGE_RELEASE); p.clear(); }
   { Probe::ReasonScope nested(p,RW_CLEAR_CACHE_RECLAIM); p.clear(); }
   p.clear(); }
 p.clear(); p.invalidate(); p.finish(); s=p.totals().snapshot();
 assert(s.lookup_hits==1 && s.lookup_misses==1 && s.invalidate_calls==1);
 assert(s.clear_calls==10 && s.clear_code_write==3 && s.clear_cache_reclaim==2);
 assert(s.clear_other==2 && s.clear_page_release==1);
 // Discard at a worker boundary; a stale destructor must not read any clocks.
 p.begin(true);
 { Probe::TranslationScope stale(p);
   { Probe::PublicationScope pub(p); Clock::w+=1000; Clock::c+=1000; }
   p.finish(); s=p.totals().snapshot();
   assert(s.samples_started==1 && s.samples_discarded==1 && s.publication_scopes==0);
   p.begin(true); unsigned reads=Clock::reads;
   work(p); // Still nested in the surviving old stack frame.
   assert(Clock::reads==reads); stale.complete(); }
 work(p); p.finish(); s=p.totals().snapshot();
 assert(s.translation_attempts==2 && s.nested_attempts==1 && s.samples_completed==1);
 p.begin(true);
 try { Probe::TranslationScope abort(p); throw 1; } catch(int) {}
 p.finish(); s=p.totals().snapshot(); assert(s.samples_discarded==1);
 p.begin(true); Clock::w=-1;
 work(p); p.finish(); s=p.totals().snapshot();
 assert(s.clock_errors>0 && s.samples_discarded==1 && s.translation_wall_sample_us==0);
 Clock::w=0; Clock::c=0;
 p.begin(true);
 { Probe::TranslationScope t(p); Clock::w=-1; t.complete(); }
 p.finish(); assert(p.totals().snapshot().clock_errors>0);
 Clock::w=0;
 p.begin(true); { Probe::TranslationScope t(p); t.complete(); }
 p.finish(); assert(p.totals().snapshot().translation_wall_sample_us==0);
 p.begin(false); auto reads=Clock::reads; work(p); p.finish(); assert(Clock::reads==reads);
 // Do not truncate each tiny sample or each worker publication before drain.
 RWTiming<Clock> joint; joint.configure(true,true);
 for(unsigned i=0;i<3;++i) {
  joint.worker_begin(); joint.event(RW_TRANSLATE);
  { Probe::TranslationScope t(joint.cache());
    { Probe::PublicationScope pub(joint.cache()); Clock::w+=400; Clock::c+=400; }
    t.complete(); }
  joint.worker_end();
 }
 RWCacheSnapshot c; auto w=joint.take(&c);
 assert(w.translations==3 && c.translation_attempts==3);
 assert(c.translation_cpu_sample_us==1 && c.publication_cpu_sample_us==1);
 assert(c.translation_cpu_sample_max_us==0 && c.samples_completed==3);
 joint.worker_begin(); joint.event(RW_TRANSLATE);
 { Probe::TranslationScope t(joint.cache()); joint.reset(); t.complete(); }
 joint.worker_end(); w=joint.take(&c);
 assert(w.discarded_slices==1 && c.translation_attempts==0);
 // Publication guard can outlive its slice, including a disabled slice.
 p.begin(true);
 { Probe::TranslationScope t(p);
   { Probe::PublicationScope pub(p); p.finish();
     assert(p.totals().snapshot().samples_discarded==1);
     p.begin(false); reads=Clock::reads; }
   t.complete(); assert(Clock::reads==reads); }
 p.finish(); p.begin(true); work(p); p.finish();
 assert(p.totals().snapshot().samples_completed==1);
 // Failed publication clocks poison the whole sample, not just the subset.
 p.begin(true);
 { Probe::TranslationScope t(p);
   { Probe::PublicationScope pub(p); Clock::c=-1; }
   Clock::c=100000; t.complete(); }
 p.finish(); s=p.totals().snapshot();
 assert(s.clock_errors==1 && s.samples_discarded==1 && s.publication_scopes==0);
 // Atomically drained cache and worker records stay matched across resets.
 RWCoreTiming concurrent; concurrent.configure(true,true);
 std::atomic<bool> done{false};
 std::thread thread([&] {
  for(unsigned i=0;i<5000;++i) {
   concurrent.worker_begin();
   for(unsigned j=0;j<16;++j) {
    concurrent.event(RW_TRANSLATE);
    RWCoreTiming::Cache::TranslationScope t(concurrent.cache()); t.complete();
   }
   concurrent.worker_end();
  }
  done=true;
 });
 unsigned drains=0;
 do {
  w=concurrent.take(&c);
  assert(w.translations==c.translation_attempts && w.translations==16*w.worker_slices);
  assert(c.samples_started==c.samples_completed+c.samples_discarded);
  if(++drains%37==0) concurrent.reset();
 } while(!done);
 thread.join();
 for(int mode=0;mode<4;++mode) {
  auto calibration=RWCacheCalibrate(mode);
  assert(calibration.clock_errors==0 && calibration.accounting_errors==0);
  assert(calibration.mean_ns>0);
  std::cout<<"cache_calibration mode="<<mode<<" mean_ns="<<calibration.mean_ns<<"\n";
 }
 std::cout<<"Cache sampling/caps/nesting/reasons/discard/clock checks passed\n";
}
