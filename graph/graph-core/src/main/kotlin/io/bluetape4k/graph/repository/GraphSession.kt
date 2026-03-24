package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스 세션 관리 (동기 방식).
 *
 * 소유권: 외부에서 주입된 Database/Driver를 [close]에서 닫지 않는다.
 * 연결 풀/드라이버 생명주기는 Spring 컨테이너 또는 호출자가 관리한다.
 */
interface GraphSession : AutoCloseable {
    fun createGraph(name: String)
    fun dropGraph(name: String)
    fun graphExists(name: String): Boolean
}
