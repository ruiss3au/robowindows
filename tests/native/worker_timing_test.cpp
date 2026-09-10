#include "robowindows_core_timing.h"
#include <cassert>
#include <iostream>
#include <thread>
struct Clock {
 static int64_t w,c;
 static int reads;
 static int64_t wall() { ++reads; return w; }
 static int64_t cpu() { ++reads; return c; }
};
int64_t Clock::w=0, Clock::c=0; int Clock::reads=0;
using Probe=RWTiming<Clock>;
int main() {
 Probe p;
 p.worker_begin(); p.event(RW_TRANSLATE); p.worker_end();
 { Probe::FrontScope off(p,false); }
 assert(Clock::reads==0 && p.take().worker_slices==0);
 p.configure(true);
 p.worker_begin();
 for (auto e : {RW_TRANSLATE,RW_SPECIAL,RW_INVALIDATED,RW_OPCODE,RW_SMC,RW_TRAP}) p.event(e);
 assert(p.take().translations==0); // Only completed worker slices publish.
 Clock::w=100000; Clock::c=40000; p.worker_end(); p.worker_end();
 auto s=p.take();
 assert(s.worker_slices==1 && s.worker_wall_total_us==100 && s.worker_cpu_total_us==40);
 assert(s.translations==1 && s.fallback_special==1 && s.fallback_invalidated==1);
 assert(s.fallback_opcode==1 && s.fallback_smc==1 && s.fallback_trap==1);
 assert(p.take().worker_slices==0);
 // Semaphore time is outside the begin/end pair, including pause/resume splits.
 Clock::w+=10000000; p.worker_begin(); Clock::w+=20000; Clock::c+=10000; p.worker_end();
 p.worker_begin(); Clock::w+=10000; Clock::c+=5000; p.worker_end();
 s=p.take(); assert(s.worker_slices==2 && s.worker_wall_total_us==30 && s.worker_wall_max_us==20);
 assert(s.worker_cpu_total_us==15 && s.worker_cpu_max_us==10);
 p.worker_begin(); p.event(RW_TRANSLATE); p.reset(); Clock::w+=1000; p.worker_end();
 s=p.take(); assert(s.worker_slices==0 && s.translations==0 && s.discarded_slices==1);
 p.worker_begin(); Clock::w+=1000; Clock::c+=1000; p.worker_end();
 assert(p.take().worker_slices==1);
 { Probe::FrontScope outer(p,false);
   Clock::w+=10000;
   { Probe::FrontScope inner(p,true); Clock::w+=5000; }
   Clock::w+=5000;
 }
 s=p.take(); assert(s.wait_calls==1 && s.wait_total_us==20 && s.mix_calls==1 && s.mix_total_us==5);
 { Probe::FrontScope old(p,false); p.reset(); Clock::w+=1000; }
 assert(p.take().wait_calls==0);
 Clock::w=-1; Clock::c=-1; p.worker_begin(); Clock::w=1000; Clock::c=1000; p.worker_end();
 s=p.take(); assert(s.worker_clock_errors==2 && s.worker_wall_total_us==0 && s.worker_cpu_total_us==0);
 { Probe::FrontScope backwards(p,false); Clock::w=0; }
 assert(p.take().frontend_clock_errors==1);
 p.worker_begin(); p.configure(false); p.worker_end();
 assert(p.take().worker_slices==0);
 int reads=Clock::reads; p.worker_begin(); p.event(RW_SMC); p.worker_end();
 assert(Clock::reads==reads);
 // One worker, concurrent frontend resets and snapshots: published generations
 // must never expose partial counters or a partly overwritten live slice.
 RWCoreTiming concurrent; concurrent.configure(true);
 std::atomic<bool> done{false};
 std::thread worker([&] {
   for(int i=0;i<10000;++i) {
     concurrent.worker_begin();
     for(int j=0;j<16;++j) concurrent.event(RW_TRANSLATE);
     concurrent.worker_end();
   }
   done=true;
 });
 unsigned snapshots=0;
 do {
   auto value=concurrent.take();
   assert(value.translations==16*value.worker_slices);
   assert(value.worker_wall_max_us<=value.worker_wall_total_us);
   if (++snapshots%37==0) concurrent.reset();
 } while(!done);
 worker.join();
 for(bool enabled : {false,true}) {
   RWCoreTiming probe; probe.configure(enabled);
   const auto begin=RWTimingClock::wall();
   for(int i=0;i<10000;++i) {
     probe.worker_begin();
     for(int j=0;j<64;++j) probe.event(RW_TRANSLATE);
     probe.worker_end();
   }
   const auto end=RWTimingClock::wall();
   auto value=probe.take();
   assert(value.translations==(enabled ? 640000u : 0u));
   std::cout<<"worker_calibration enabled="<<enabled<<" ns_per_slice_64_events="<<(end-begin)/10000<<"\n";
 }
 std::cout<<"Worker timing accounting/reset/concurrency checks passed\n";
}
