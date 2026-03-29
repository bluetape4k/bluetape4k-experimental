package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable
import java.time.LocalDate

data class HolidayRecord(
    val id: Long? = null,
    val holidayDate: LocalDate,
    val name: String,
    val recurring: Boolean = false,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
