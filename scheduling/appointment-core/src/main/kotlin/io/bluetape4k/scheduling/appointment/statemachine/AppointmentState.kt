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
 *       → PENDING_RESCHEDULE (재배정 대기)
 *         → RESCHEDULED (재배정 완료)
 *         → CANCELLED
 *       → CANCELLED
 *     → PENDING_RESCHEDULE
 *     → CANCELLED
 *   → CANCELLED
 * ```
 */
sealed class AppointmentState(
    val name: String,
) {
    companion object {
        val ACTIVE_STATUSES: List<AppointmentState> by lazy { listOf(REQUESTED, CONFIRMED) }
        val ACTIVE_STATUS_NAMES: List<String> by lazy { ACTIVE_STATUSES.map { it.name } }

        val PINNED_STATUSES: Set<AppointmentState> by lazy { setOf(CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED) }
        val PINNED_STATUS_NAMES: Set<String> by lazy { PINNED_STATUSES.map { it.name }.toSet() }

        fun fromName(name: String): AppointmentState = when (name) {
            "PENDING" -> PENDING
            "REQUESTED" -> REQUESTED
            "CONFIRMED" -> CONFIRMED
            "CHECKED_IN" -> CHECKED_IN
            "IN_PROGRESS" -> IN_PROGRESS
            "COMPLETED" -> COMPLETED
            "CANCELLED" -> CANCELLED
            "NO_SHOW" -> NO_SHOW
            "PENDING_RESCHEDULE" -> PENDING_RESCHEDULE
            "RESCHEDULED" -> RESCHEDULED
            else -> throw IllegalArgumentException("Unknown appointment status: $name")
        }
    }
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

    /** 재배정 대기 (임시휴진 등으로 인한 자동 전환) */
    data object PENDING_RESCHEDULE : AppointmentState("PENDING_RESCHEDULE")

    /** 재배정 완료 */
    data object RESCHEDULED : AppointmentState("RESCHEDULED")

    /** 취소 */
    data object CANCELLED : AppointmentState("CANCELLED")

    override fun toString(): String = name
}
