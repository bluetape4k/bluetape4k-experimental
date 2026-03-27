# exposed-inet

Kotlin Exposed ORM 확장 — IP 주소(INET) 및 CIDR 네트워크 컬럼 타입 지원 모듈.

## 개요

PostgreSQL의 네이티브 `INET` / `CIDR` 타입과, H2 등 기타 DB의 `VARCHAR` fallback을 지원한다.

| 컬럼 타입            | PostgreSQL  | 기타 DB       | Kotlin 타입     |
|--------------------|-------------|--------------|----------------|
| `inetAddress()`    | `INET`      | `VARCHAR(45)` | `InetAddress`  |
| `cidr()`           | `CIDR`      | `VARCHAR(50)` | `String`       |

## 의존성

```kotlin
dependencies {
    implementation("io.bluetape4k:exposed-inet:<version>")
}
```

## 사용 예제

### 테이블 정의

```kotlin
import io.bluetape4k.exposed.inet.cidr
import io.bluetape4k.exposed.inet.inetAddress
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object NetworkTable : LongIdTable("networks") {
    val ip      = inetAddress("ip")
    val network = cidr("network")
}
```

### 데이터 저장 및 조회

```kotlin
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.net.InetAddress

// 저장
transaction {
    NetworkTable.insert {
        it[ip]      = InetAddress.getByName("192.168.1.1")
        it[network] = "192.168.0.0/24"
    }
}

// 조회
transaction {
    val row = NetworkTable.selectAll().single()
    val addr: InetAddress = row[NetworkTable.ip]
    val cidr: String      = row[NetworkTable.network]
}
```

### PostgreSQL 전용 — `<<` (isContainedBy) 연산자

PostgreSQL에서 INET 주소가 특정 CIDR 네트워크에 포함되는지 확인한다.

```kotlin
import io.bluetape4k.exposed.inet.isContainedBy
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.selectAll

// WHERE ip << '192.168.0.0/24'
val rows = NetworkTable
    .selectAll()
    .where { NetworkTable.ip.isContainedBy(stringLiteral("192.168.0.0/24")) }
    .toList()
```

> `isContainedBy`는 **PostgreSQL dialect 전용**이다. 다른 DB에서 호출하면 `IllegalStateException`이 발생한다.

## 지원 범위

- IPv4 주소 (`192.168.1.1`)
- IPv6 주소 (`2001:db8::1`, `::1`)
- CIDR 표기법 (`10.0.0.0/8`, `2001:db8::/32`)
- H2 인메모리 DB (테스트용 fallback)
- PostgreSQL Testcontainers 연동
