# compileTestKotlin warning cleanup

## Context

`./gradlew compileTestKotlin --warning-mode all --rerun-tasks` showed two
repository-owned warning classes in the experimental benchmark modules:

- Deprecated `HasIdentifier` usage in the Exposed R2DBC repository benchmark.
- Deprecated `BinarySerializers.Jdk` usage in the serializer-compressor
  benchmark matrix.

## Decision

- Replace `HasIdentifier` with `Serializable` on the local benchmark DTO and
  keep `serialVersionUID` because bluetape4k data classes that implement
  `Serializable` should define it explicitly.
- Remove JDK serializer combinations from the compressor benchmark matrix
  instead of suppressing the warning. The upstream serializer is deprecated for
  RCE risk and the benchmark already keeps Kryo/Fory combinations for this
  matrix.

## Outcome

`compileTestKotlin` no longer reports the `HasIdentifier` or
`BinarySerializers.Jdk` deprecation warnings. The remaining warning-mode output
is Gradle 10 build-logic deprecation noise plus a runtime `sun.misc.Unsafe`
warning during benchmark tests; neither comes from touched Kotlin source.

## Future Guard

When warning cleanup touches benchmark matrices, do not keep deprecated
security-sensitive serializers only for historical comparison. Prefer removing
the deprecated axis and documenting the narrower supported benchmark scope.
