package io.bluetape4k.exposed.lettuce.map

/**
 * Lettuce 기반 캐시 맵의 쓰기 전략.
 */
enum class WriteMode {
    /** 캐시에만 기록하고 DB에는 쓰지 않는다 (read-only cache). */
    READ_ONLY,

    /** DB 쓰기 후 캐시를 즉시 갱신한다 (Write-Through). */
    WRITE_THROUGH,

    /** DB 쓰기를 비동기로 처리하고 캐시를 먼저 갱신한다 (Write-Behind). */
    WRITE_BEHIND,
}
