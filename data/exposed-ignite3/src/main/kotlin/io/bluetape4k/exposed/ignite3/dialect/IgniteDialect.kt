package io.bluetape4k.exposed.ignite3.dialect

import io.bluetape4k.logging.KotlinLogging
import io.bluetape4k.logging.warn
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnDiff
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.core.vendors.VendorDialect

private val log = KotlinLogging.logger {}

/**
 * Apache Ignite 3 Dialect for JetBrains Exposed ORM
 *
 * Ignite 3 제약사항:
 * - PRIMARY KEY 필수
 * - AUTO_INCREMENT 미지원 → UUID/Snowflake ID 사용 권장
 * - FOREIGN KEY 미지원
 * - TEXT/BLOB 미지원 → VARCHAR(65536)/VARBINARY 사용
 * - UNIQUE → CREATE UNIQUE INDEX 사용 (ALTER TABLE ADD CONSTRAINT 아님)
 * - Sequence 미지원
 */
class IgniteDialect : VendorDialect(
    name = "Ignite",
    dataTypeProvider = IgniteDataTypeProvider,
    functionProvider = IgniteFunctionProvider,
) {
    companion object : DialectNameProvider("Ignite")

    // Ignite 3는 다중 생성 키(RETURNING) 미지원
    override val supportsMultipleGeneratedKeys: Boolean = false

    // Sequence 미지원
    override val supportsCreateSequence: Boolean = false

    // FK 미지원
    override val supportsOnUpdate: Boolean = false
    override val supportsSetDefaultReferenceOption: Boolean = false
    override val supportsRestrictReferenceOption: Boolean = false

    // Schema DDL은 Ignite 3에서 사용 가능하지만 기본 PUBLIC 스키마 사용
    override val supportsCreateSchema: Boolean = true

    // Column 타입 변경은 Ignite 3에서 제한적 지원
    override val supportsColumnTypeChange: Boolean = false

    /**
     * UNIQUE 인덱스: Ignite 3는 ALTER TABLE ADD CONSTRAINT 대신 CREATE UNIQUE INDEX 사용
     * 일반 인덱스: CREATE INDEX
     */
    @OptIn(InternalApi::class)
    override fun createIndex(index: Index): String {
        val t = currentTransaction()
        val quotedTableName = t.identity(index.table)
        val quotedIndexName = t.db.identifierManager.cutIfNecessaryAndQuote(index.indexName)

        val fieldsList = index.columns.joinToString(prefix = "(", postfix = ")") {
            t.identity(it)
        }

        return if (index.unique) {
            "CREATE UNIQUE INDEX $quotedIndexName ON $quotedTableName $fieldsList"
        } else {
            "CREATE INDEX $quotedIndexName ON $quotedTableName $fieldsList"
        }
    }

    /**
     * DROP INDEX: Ignite 3는 ALTER TABLE DROP CONSTRAINT 대신 DROP INDEX 사용
     */
    override fun dropIndex(
        tableName: String,
        indexName: String,
        isUnique: Boolean,
        isPartialOrFunctional: Boolean,
    ): String {
        val quotedIndexName = identifierManager.cutIfNecessaryAndQuote(indexName)
        return "DROP INDEX $quotedIndexName"
    }

    /**
     * ALTER COLUMN: Ignite 3는 컬럼 타입 변경이 제한적이므로 빈 목록 반환
     */
    @OptIn(InternalApi::class)
    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> {
        log.warn { "Ignite 3 does not support modifyColumn. Skipping: ${column.name}" }
        return emptyList()
    }

    /**
     * ADD PRIMARY KEY: Ignite 3 표준 SQL 문법 사용
     */
    @OptIn(InternalApi::class)
    override fun addPrimaryKey(table: Table, pkName: String?, vararg pkColumns: Column<*>): String {
        val transaction = currentTransaction()
        val columns = pkColumns.joinToString(prefix = "(", postfix = ")") { transaction.identity(it) }
        return "ALTER TABLE ${transaction.identity(table)} ADD PRIMARY KEY $columns"
    }
}
