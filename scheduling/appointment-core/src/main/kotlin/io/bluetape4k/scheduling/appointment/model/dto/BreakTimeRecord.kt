package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

data class BreakTimeRecord(
    val id: Long? = null,
    val clinicId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
