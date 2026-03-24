package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스 세션 관리 (코루틴 방식).
 *
 * 소유권: 외부에서 주입된 Database/Driver를 [close]에서 닫지 않는다.
 * 연결 풀/드라이버 생명주기는 Spring 컨테이너 또는 호출자가 관리한다.
 *
 * @see GraphSession 동기(blocking) 방식
 */
interface GraphSuspendSession : AutoCloseable {
    suspend fun createGraph(name: String)
    suspend fun dropGraph(name: String)
    suspend fun graphExists(name: String): Boolean
}
