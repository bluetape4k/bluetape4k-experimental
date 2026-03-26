package io.bluetape4k.exposed.ignite3.dialect

import org.jetbrains.exposed.v1.core.vendors.DataTypeProvider
import java.util.UUID as JavaUUID

/**
 * Apache Ignite 3 DataTypeProvider
 *
 * Ignite 3 특이 사항:
 * - AUTO_INCREMENT 미지원 → INT/BIGINT 그대로 반환
 * - TEXT/BLOB 미지원 → VARCHAR(MAX)/VARBINARY 사용
 * - UUID 네이티브 지원 → UUID 타입 직접 사용
 * - DATETIME → TIMESTAMP
 */
object IgniteDataTypeProvider : DataTypeProvider() {

    // AUTO_INCREMENT 미지원 — plain INT/BIGINT 반환
    override fun integerAutoincType(): String = "INT"
    override fun uintegerAutoincType(): String = "BIGINT"
    override fun longAutoincType(): String = "BIGINT"
    override fun ulongAutoincType(): String = "NUMERIC(20)"

    // TEXT 미지원 → VARCHAR(65536)
    override fun textType(): String = "VARCHAR(65536)"
    override fun mediumTextType(): String = "VARCHAR(65536)"
    override fun largeTextType(): String = "VARCHAR(65536)"

    // BLOB 미지원 → VARBINARY
    override fun blobType(): String = "VARBINARY"

    // UUID 네이티브 지원
    override fun uuidType(): String = "UUID"

    override fun uuidToDB(value: JavaUUID): Any = value.toString()

    // DATETIME → TIMESTAMP
    override fun dateTimeType(): String = "TIMESTAMP"
    override fun timestampType(): String = "TIMESTAMP"

    // Binary
    override fun binaryType(): String = "VARBINARY"

    override fun binaryType(length: Int): String =
        if (length == Int.MAX_VALUE) "VARBINARY" else "VARBINARY($length)"

    // Hex — Ignite는 X'...' 형식 사용
    override fun hexToDb(hexString: String): String = "X'$hexString'"
}
