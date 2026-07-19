# Common correctness traps

- Preserve `0`, empty strings, and `false`; use null-aware defaults instead of truthiness.
- Use timezone-aware date arithmetic and half-open timestamp ranges `[start, end)`.
- Name units in variables; epoch seconds and milliseconds are not interchangeable.
- Copy shared collections before mutation; know whether sort and reverse mutate.
- Await asynchronous work or mark intentional fire-and-forget calls explicitly.
- Store money as integer minor units or decimal values, never binary floats.
- Give numeric sorts a comparator and comparators a numeric result.
- Trace negative modulo behavior when values can be negative.
- Probe non-trivial regular expressions with a match, near-miss, and empty input.
- Parenthesize mixed boolean operators; name complex conditions.
