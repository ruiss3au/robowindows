# Tasks

- [x] T001 Record requirements, independent experimental rollout and rollback.
- [x] T002 Implement schema-8 preference, Display drafts and policy boundaries.
- [x] T003 Implement presenter-owned EGL backend, pacing and visible fallback.
- [x] T004 Extend telemetry/capture consumers and host regression tests.
- [x] T005 Run hygiene, host tests, pins and Android build.
- [x] T006 Verify stopped installation and disposable CPU/graphics/lifecycle gates.
- [x] T007 Run short timer-and-tone performance matrix; record measured results.
- [ ] T008 With user coordination, run Normal then DynRec Windows GPU smoke and
  clean shutdown; leave omitted thermal/long-term claims explicitly unverified.
  Normal completed with clean shutdown but failed audio/timing acceptance;
  DynRec also failed the separately authorized post-REP desktop check (T010).
  Further trials are on hold pending diagnosis, not automatically queued.
- [x] T009 Run the user-authorized same-build Normal/Software Windows comparison,
  record workload comparability and audio/timing evidence, and verify clean shutdown.
  Capture completed and also failed audio/timing acceptance; workload equivalence
  is unconfirmed. Clean Windows shutdown was verified; task completion records
  the diagnostic result, not an audio/timing acceptance pass.
- [x] T010 After the verified REP correction, run the separately authorized
  DynRec fixed-20k/GPU desktop-only smoke on the clean experimental copy. Capture
  startup and a coordinated three-minute window-opening interval separately,
  record human responsiveness/sound observations, and verify normal Windows
  shutdown and unchanged stable-machine state. No AoE2 or longer campaign.
  Ended early for recurring underruns/deadline resets; shutdown was clean.
  Completion records the diagnostic failure, not a full-duration quality pass.
