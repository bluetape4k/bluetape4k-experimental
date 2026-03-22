package io.bluetape4k.scheduling.appointment.solver.domain

/**
 * 장비 정보 Problem Fact.
 */
data class EquipmentFact(
    val id: Long,
    val usageDurationMinutes: Int,
    val quantity: Int,
)
