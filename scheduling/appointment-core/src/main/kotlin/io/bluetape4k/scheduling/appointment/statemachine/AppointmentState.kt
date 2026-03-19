package io.bluetape4k.scheduling.appointment.statemachine

/**
 * 예약 상태를 나타내는 sealed class.
 *
 * 상태 전이 흐름:
 * ```
 * PENDING (가예약/미확정)
 *   → REQUESTED (예약요청)
 *     → CONFIRMED (확정)
 *       → CHECKED_IN (내원확인)
 *         → IN_PROGRESS (진료중)
 *           → COMPLETED (진료완료)
 *         → CANCELLED
 *       → NO_SHOW (미내원)
 *       → CANCELLED
 *     → CANCELLED
 *   → CANCELLED
 * ```
 */
sealed class AppointmentState(
    val name: String,
) {
    /** 가예약/미확정 */
    data object PENDING : AppointmentState("PENDING")

    /** 예약요청 */
    data object REQUESTED : AppointmentState("REQUESTED")

    /** 확정 */
    data object CONFIRMED : AppointmentState("CONFIRMED")

    /** 내원확인 */
    data object CHECKED_IN : AppointmentState("CHECKED_IN")

    /** 진료중 */
    data object IN_PROGRESS : AppointmentState("IN_PROGRESS")

    /** 진료완료 */
    data object COMPLETED : AppointmentState("COMPLETED")

    /** 미내원 */
    data object NO_SHOW : AppointmentState("NO_SHOW")

    /** 취소 */
    data object CANCELLED : AppointmentState("CANCELLED")

    override fun toString(): String = name
}
