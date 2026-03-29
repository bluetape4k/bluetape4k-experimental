package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime

data class ClinicClosureRecord(
    val id: Long? = null,
    val clinicId: Long,
    val closureDate: LocalDate,
    val reason: String? = null,
    val isFullDay: Boolean = true,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
