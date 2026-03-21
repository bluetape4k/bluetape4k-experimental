package io.bluetape4k.scheduling.appointment.api.dto

data class UpdateStatusRequest(
    val status: String,
    val reason: String? = null,
)
