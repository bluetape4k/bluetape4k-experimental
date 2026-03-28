package io.bluetape4k.exposed.ignite3.dialect

import org.jetbrains.exposed.v1.jdbc.vendors.DatabaseDialectMetadata

/**
 * Apache Ignite 3용 Exposed JDBC metadata 구현.
 *
 * 현재 Ignite 3는 기본 [DatabaseDialectMetadata] 동작으로 필요한 메타데이터 조회를 처리한다.
 * 별도 vendor 식별과 dialect registration 경로를 제공하기 위해 전용 타입을 둔다.
 */
class IgniteDialectMetadata : DatabaseDialectMetadata()
