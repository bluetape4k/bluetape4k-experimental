# compileTestKotlin 경고 정리

## 배경

`./gradlew compileTestKotlin --warning-mode all --rerun-tasks`에서 실험
벤치마크 모듈의 저장소 소유 코드에 해당하는 경고 두 종류가 나타났다.

- Exposed R2DBC repository benchmark에서 Deprecated `HasIdentifier` 사용.
- serializer-compressor benchmark matrix에서 Deprecated
  `BinarySerializers.Jdk` 사용.

## 결정

- 로컬 benchmark DTO의 `HasIdentifier`를 `Serializable`로 바꾸고
  `serialVersionUID`를 유지한다. `Serializable`을 구현하는 bluetape4k data
  class는 이를 명시적으로 정의해야 하기 때문이다.
- 경고를 억제하는 대신 compressor benchmark matrix에서 JDK serializer
  조합을 제거한다. upstream serializer는 RCE 위험으로 deprecated되었고,
  이 benchmark는 이미 이 matrix에 Kryo/Fory 조합을 유지한다.

## 결과

`compileTestKotlin`은 더 이상 `HasIdentifier` 또는
`BinarySerializers.Jdk` deprecation warning을 보고하지 않는다. 남은
warning-mode 출력은 Gradle 10 build-logic deprecation noise와 벤치마크 테스트
중 발생하는 runtime `sun.misc.Unsafe` warning이며, 둘 다 변경한 Kotlin
소스에서 발생하지 않는다.

## 향후 지침

경고 정리 대상이 benchmark matrix일 때 historical comparison만을 위해
deprecated된 보안 민감 serializer를 유지하지 않는다. deprecated된 축을
제거하고 더 좁아진 지원 benchmark 범위를 문서화한다.
