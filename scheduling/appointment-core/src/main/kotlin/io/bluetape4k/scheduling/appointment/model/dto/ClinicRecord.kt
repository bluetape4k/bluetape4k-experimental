package io.bluetape4k.scheduling.appointment.model.dto

data class ClinicRecord(
    val id: Long? = null,
    val name: String,
    val slotDurationMinutes: Int = 30,
    val timezone: String = "UTC",
    val locale: String = "ko-KR",
    val maxConcurrentPatients: Int = 1,
    val openOnHolidays: Boolean = false,
)
