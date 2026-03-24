package io.bluetape4k.graph.neo4j

import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.neo4j.driver.Driver
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.reactivestreams.ReactiveSession

/**
 * Neo4j Java Driver의 Reactive API를 Kotlin Coroutine으로 브릿지.
 *
 * `org.neo4j.driver.reactivestreams.ReactiveSession`이 반환하는
 * `org.reactivestreams.Publisher<T>`를 `kotlinx-coroutines-reactive`로 변환합니다.
 *
 * 소유권: 외부에서 주입된 [Driver]를 [close]에서 닫지 않습니다.
 *
 * @param driver Neo4j Java Driver (외부 소유)
 * @param database Neo4j 데이터베이스 이름 (기본: "neo4j")
 */
class Neo4jCoroutineSession(
    private val driver: Driver,
    private val database: String = "neo4j",
) : AutoCloseable {

    companion object : KLogging()

    /**
     * 읽기 전용 트랜잭션 실행.
     */
    suspend fun <T> read(block: suspend (ReactiveSession) -> Flow<T>): List<T> {
        val session = driver.session(ReactiveSession::class.java, sessionConfig())
        return try {
            block(session).toList()
        } finally {
            session.close<Void>().awaitFirstOrNull()
        }
    }

    /**
     * 쓰기 트랜잭션 실행.
     */
    suspend fun <T> write(block: suspend (ReactiveSession) -> Flow<T>): List<T> {
        val session = driver.session(ReactiveSession::class.java, sessionConfig())
        return try {
            block(session).toList()
        } finally {
            session.close<Void>().awaitFirstOrNull()
        }
    }

    /**
     * 읽기 쿼리를 Flow로 실행하여 Record 목록을 반환합니다.
     */
    suspend fun runReadQuery(cypher: String, params: Map<String, Any?> = emptyMap()): List<Record> {
        val session = driver.session(ReactiveSession::class.java, sessionConfig())
        return try {
            val result = session.run(Query(cypher, params)).awaitSingle()
            result.records().asFlow().toList()
        } finally {
            session.close<Void>().awaitFirstOrNull()
        }
    }

    /**
     * 쓰기 쿼리를 실행하고 Record 목록을 반환합니다.
     */
    suspend fun runWriteQuery(cypher: String, params: Map<String, Any?> = emptyMap()): List<Record> =
        runReadQuery(cypher, params)

    override fun close() {
        // driver는 외부 소유이므로 닫지 않음
    }

    private fun sessionConfig(): SessionConfig =
        SessionConfig.builder()
            .withDatabase(database)
            .build()
}
