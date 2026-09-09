# Benchmark result format

`RWBENCH.TXT` is ASCII, at most 4 KiB, LF or CRLF terminated, and contains
exactly one header followed by exactly three phases:

```text
RW98BENCH schema=2 workload=2 duration_ms=30000
phase=cpu elapsed_ms=N work=N throughput=N integrity=HHHHHHHH
phase=memory elapsed_ms=N work=N throughput=N integrity=HHHHHHHH
phase=gdi pixels_per_rect=4096 elapsed_ms=N work=N throughput=N frames=N fps=N integrity=HHHHHHHH
complete=1
```

All `N` values are unsigned decimal integers; integrity is eight uppercase hex
digits. Phase order is fixed. Unknown keys, duplicate phases, zero elapsed/work,
elapsed outside the specified tolerance, wrong versions, missing completion, and
trailing non-whitespace data are invalid. Throughput units are documented by the
workload: CPU operations/ms, memory KiB/ms, and off-screen 64×64 GDI rectangles/s.
`frames` counts only the rate-limited 320×200 preview copies and `fps` equals
`frames * 1000 / elapsed_ms`; it is not RoboWindows' host presentation rate.

The guest record does not contain host cycles or host FPS. Those values come from
the matched host capture so a guest cannot accidentally report configured intent
as observed host behavior. Guest preview FPS and host presented FPS remain
separate fields.
