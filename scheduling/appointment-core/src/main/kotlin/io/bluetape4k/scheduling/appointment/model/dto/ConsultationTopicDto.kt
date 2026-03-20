package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier

data class ConsultationTopicDto(
    override val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val description: String? = null,
    val defaultDurationMinutes: Int = 30,
) : HasIdentifier<Long>
