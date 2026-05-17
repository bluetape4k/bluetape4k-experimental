# Formal bluetape4k Release References

Context: Central Portal release preparation requires repositories that consume
`bluetape4k-*` artifacts to use formal release coordinates instead of
`-SNAPSHOT` coordinates.

Decision: Use released bluetape4k BOM coordinates through BOM-named version
aliases. Do not import `bluetape4k-dependencies` before the final aggregator BOM
has been released. Exposed artifacts use `bluetape4k-exposed-bom`.

Outcome: The repository can participate in cross-repository BOM catalog checks
without blocking `bluetape4k-dependencies` release validation.

Verification: `rg -n 'bluetape4k.*SNAPSHOT' gradle/libs.versions.toml`.

Future guard: Do not reintroduce `bluetape4k-*` snapshot references before a
formal release train unless the repository is explicitly excluded from release
validation.
