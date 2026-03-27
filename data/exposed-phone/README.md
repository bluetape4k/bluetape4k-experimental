# exposed-phone

## 개요
전화번호를 E.164 형식으로 자동 정규화하여 저장하는 Exposed 커스텀 컬럼 타입 모듈.
Google libphonenumber 기반으로 국제 전화번호를 파싱하고 표준화합니다.

## 주요 기능
- `PhoneNumberColumnType` — `PhoneNumber` 객체를 VARCHAR(20)으로 저장
- `PhoneNumberStringColumnType` — String을 E.164로 정규화 후 VARCHAR(20)으로 저장
- `Table.phoneNumber(name, defaultRegion)` factory 확장 함수
- `Table.phoneNumberString(name, defaultRegion)` factory 확장 함수

## 사용 예제
```kotlin
object Contacts : Table("contacts") {
    val phone = phoneNumber("phone")              // PhoneNumber 타입
    val phoneStr = phoneNumberString("phone_str") // E.164 String 타입
}
```

## 의존성
```kotlin
testImplementation(project(":exposed-phone"))
```
