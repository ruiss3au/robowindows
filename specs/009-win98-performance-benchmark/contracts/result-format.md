# Benchmark result format

`RWBENCH.TXT` is ASCII, at most 4 KiB, LF or CRLF terminated, and contains
exactly one header followed by exactly three phases:

```text
RW98BENCH schema=1 workload=1 duration_ms=30000
phase=cpu elapsed_ms=N work=N throughput=N integrity=HHHHHHHH
phase=memory elapsed_ms=N work=N throughput=N integrity=HHHHHHHH
phase=gdi elapsed_ms=N work=N throughput=N integrity=HHHHHHHH
complete=1
```

All `N` values are unsigned decimal integers; integrity is eight uppercase hex
digits. Phase order is fixed. Unknown keys, duplicate phases, zero elapsed/work,
elapsed outside the specified tolerance, wrong versions, missing completion, and
trailing non-whitespace data are invalid. Throughput units are documented by the
workload: CPU operations/s, memory KiB/s, and GDI megapixels/s scaled by 1000.

The guest record does not contain host cycles or FPS. Those values come from the
matched host capture so a guest cannot accidentally report configured intent as
observed host behavior.
