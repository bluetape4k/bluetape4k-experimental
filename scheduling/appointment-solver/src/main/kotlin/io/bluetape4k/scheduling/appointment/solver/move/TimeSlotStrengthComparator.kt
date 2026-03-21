package io.bluetape4k.scheduling.appointment.solver.move

import java.time.LocalTime
import java.util.Comparator

/**
 * 시간 슬롯 강도 비교기.
 *
 * Construction Heuristic에서 이른 시간 슬롯을 먼저 시도하도록 정렬합니다.
 * 이른 시간대 배치를 선호하여 오전 예약 집중 효과를 냅니다.
 *
 * Timefold는 강도가 높은(= compare 결과가 큰) 값을 먼저 시도합니다.
 * 이른 시간이 낮은 값이므로, 기본 오름차순 정렬([LocalTime.compareTo])을 그대로 사용하면
 * 늦은 시간이 먼저 시도됩니다. 필요에 따라 역순 정렬로 변경할 수 있습니다.
 */
class TimeSlotStrengthComparator : Comparator<LocalTime> {

    override fun compare(a: LocalTime, b: LocalTime): Int = a.compareTo(b)
}
