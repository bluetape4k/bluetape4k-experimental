package io.bluetape4k.scheduling.appointment.service.model

import java.time.Duration
import java.time.LocalTime

/**
 * 시간 범위를 나타내는 데이터 클래스.
 *
 * @property start 시작 시간 (inclusive)
 * @property end 종료 시간 (exclusive)
 */
data class TimeRange(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start < end) { "start($start) must be before end($end)" }
    }

    /**
     * 주어진 시간이 이 범위에 포함되는지 확인합니다.
     */
    fun contains(time: LocalTime): Boolean = time >= start && time < end

    /**
     * 다른 TimeRange와 겹치는지 확인합니다.
     */
    fun overlaps(other: TimeRange): Boolean = start < other.end && other.start < end

    /**
     * 이 범위의 Duration을 반환합니다.
     */
    fun duration(): Duration = Duration.between(start, end)
}

/**
 * base 시간 범위에서 exclusions를 빼고 남은 범위들을 반환합니다.
 * 겹치는 부분만 제거하고, 겹치지 않으면 base 그대로 반환합니다.
 */
fun subtractRanges(
    base: TimeRange,
    exclusions: List<TimeRange>,
): List<TimeRange> {
    var remaining = listOf(base)

    for (exclusion in exclusions) {
        remaining =
            remaining.flatMap { range ->
                subtractSingle(range, exclusion)
            }
    }

    return remaining
}

/**
 * 단일 범위에서 단일 exclusion을 빼는 내부 함수.
 */
private fun subtractSingle(
    range: TimeRange,
    exclusion: TimeRange,
): List<TimeRange> {
    // 겹치지 않으면 원래 범위 그대로
    if (!range.overlaps(exclusion)) {
        return listOf(range)
    }

    val result = mutableListOf<TimeRange>()

    // 왼쪽 잔여 부분
    if (exclusion.start > range.start) {
        result.add(TimeRange(range.start, exclusion.start))
    }

    // 오른쪽 잔여 부분
    if (exclusion.end < range.end) {
        result.add(TimeRange(exclusion.end, range.end))
    }

    return result
}

/**
 * 실제로 예약 가능한 시간 범위를 계산합니다.
 *
 * 1. clinic 범위와 doctor 범위의 교차(intersection) 계산
 * 2. breakTimes 제외
 * 3. partialClosures 제외
 * 4. doctorAbsences 제외
 */
fun computeEffectiveRanges(
    clinicOpen: LocalTime,
    clinicClose: LocalTime,
    doctorStart: LocalTime,
    doctorEnd: LocalTime,
    breakTimes: List<TimeRange> = emptyList(),
    partialClosures: List<TimeRange> = emptyList(),
    doctorAbsences: List<TimeRange> = emptyList(),
): List<TimeRange> {
    // 1) clinic과 doctor 범위의 교차
    val intersectStart = maxOf(clinicOpen, doctorStart)
    val intersectEnd = minOf(clinicClose, doctorEnd)

    // 교차 범위가 없으면 빈 리스트
    if (intersectStart >= intersectEnd) {
        return emptyList()
    }

    val base = TimeRange(intersectStart, intersectEnd)

    // 2) 모든 제외 항목을 합쳐서 순차적으로 제거
    val allExclusions = breakTimes + partialClosures + doctorAbsences

    return subtractRanges(base, allExclusions)
}
