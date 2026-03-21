package io.bluetape4k.scheduling.appointment.model.tables

object AppointmentStatus {
    const val REQUESTED = "REQUESTED"
    const val CONFIRMED = "CONFIRMED"
    const val CHECKED_IN = "CHECKED_IN"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    const val NO_SHOW = "NO_SHOW"
    const val PENDING_RESCHEDULE = "PENDING_RESCHEDULE"
    const val RESCHEDULED = "RESCHEDULED"

    val ACTIVE_STATUSES = listOf(REQUESTED, CONFIRMED)
}
