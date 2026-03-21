package io.bluetape4k.scheduling.appointment.solver.domain

import java.time.LocalTime

/**
 * 영업시간 내에서 slotDurationMinutes 간격으로 이산 시간 슬롯 목록을 생성합니다.
 *
 * @param openTime 영업 시작 시간
 * @param closeTime 영업 종료 시간
 * @param slotDurationMinutes 슬롯 간격 (분)
 * @return 이산 시간 슬롯 목록
 */
fun generateTimeSlots(
    openTime: LocalTime,
    closeTime: LocalTime,
    slotDurationMinutes: Int,
): List<LocalTime> {
    val slots = mutableListOf<LocalTime>()
    var current = openTime
    while (current < closeTime) {
        slots.add(current)
        current = current.plusMinutes(slotDurationMinutes.toLong())
    }
    return slots
}
