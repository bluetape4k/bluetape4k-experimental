package io.bluetape4k.scheduling.appointment.statemachine

/**
 * 예약 상태 전이를 트리거하는 이벤트.
 */
sealed class AppointmentEvent {
    /** 예약 요청 (PENDING → REQUESTED) */
    data object Request : AppointmentEvent()

    /** 예약 확정 (REQUESTED → CONFIRMED) */
    data object Confirm : AppointmentEvent()

    /** 내원 확인 (CONFIRMED → CHECKED_IN) */
    data object CheckIn : AppointmentEvent()

    /** 진료 시작 (CHECKED_IN → IN_PROGRESS) */
    data object StartTreatment : AppointmentEvent()

    /** 진료 완료 (IN_PROGRESS → COMPLETED) */
    data object Complete : AppointmentEvent()

    /** 취소 (cancellable 상태 → CANCELLED) */
    data class Cancel(
        val reason: String,
    ) : AppointmentEvent()

    /** 미내원 처리 (CONFIRMED → NO_SHOW) */
    data object MarkNoShow : AppointmentEvent()

    /** 재예약 (CONFIRMED → PENDING) */
    data object Reschedule : AppointmentEvent()

    /** 재배정 요청 — 임시휴진 등 (REQUESTED/CONFIRMED → PENDING_RESCHEDULE) */
    data class RequestReschedule(
        val reason: String,
    ) : AppointmentEvent()

    /** 재배정 확정 (PENDING_RESCHEDULE → RESCHEDULED) */
    data object ConfirmReschedule : AppointmentEvent()

    override fun toString(): String = this::class.simpleName ?: "Unknown"
}
