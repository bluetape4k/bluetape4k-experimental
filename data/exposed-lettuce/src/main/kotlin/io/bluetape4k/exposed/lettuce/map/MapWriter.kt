package io.bluetape4k.exposed.lettuce.map

/**
 * 캐시 항목을 DB에 반영하는 인터페이스 (Write-Through / Write-Behind).
 *
 * @param K 키 타입
 * @param V 값 타입
 */
interface MapWriter<K: Any, V: Any> {
    /**
     * 단일 항목을 DB에 저장(upsert)한다.
     */
    fun write(key: K, value: V)

    /**
     * 단일 항목을 DB에서 삭제한다.
     */
    fun delete(key: K)

    /**
     * 여러 항목을 일괄 저장(upsert)한다.
     * 기본 구현은 [write]를 반복 호출한다.
     */
    fun writeAll(entries: Map<K, V>) {
        entries.forEach { (k, v) -> write(k, v) }
    }

    /**
     * 여러 항목을 일괄 삭제한다.
     * 기본 구현은 [delete]를 반복 호출한다.
     */
    fun deleteAll(keys: Iterable<K>) {
        keys.forEach { delete(it) }
    }
}
