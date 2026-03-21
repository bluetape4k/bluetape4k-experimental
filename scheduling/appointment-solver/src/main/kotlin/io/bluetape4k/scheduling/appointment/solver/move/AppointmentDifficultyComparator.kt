package io.bluetape4k.scheduling.appointment.solver.move

import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import java.util.Comparator

/**
 * Planning Entity 난이도 비교기.
 *
 * Construction Heuristic의 First Fit Decreasing 전략에서 배치하기 어려운 예약을
 * 먼저 배치하여 초기 해 품질을 향상시킵니다.
 *
 * 난이도 기준 (높을수록 먼저 배치):
 * 1. 장비 필요 여부 — 특수 장비가 필요한 예약은 제약이 많아 더 어려움
 * 2. 진료 시간 — 긴 진료는 가용 시간 슬롯이 적어 더 어려움
 * 3. 요청 날짜 유무 — requestedDate가 지정된 예약은 날짜 선택이 제한됨
 *
 * Timefold는 Comparator 결과에서 **뒤에 오는 것을 먼저 배치**합니다
 * (정렬 후 역순으로 처리). 따라서 어려운 항목이 compare 결과에서 크게 나와야 합니다.
 */
class AppointmentDifficultyComparator : Comparator<AppointmentPlanning> {

    override fun compare(a: AppointmentPlanning, b: AppointmentPlanning): Int =
        compareValuesBy(
            a, b,
            { if (it.requiresEquipment) 1 else 0 },
            { it.durationMinutes },
            { if (it.requestedDate != null) 1 else 0 },
        )
}
