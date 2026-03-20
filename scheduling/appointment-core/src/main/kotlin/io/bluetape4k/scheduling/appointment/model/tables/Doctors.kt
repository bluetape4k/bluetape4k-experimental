package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * 진료 제공자(의사, 전문상담사 등) 유형.
 */
object ProviderType {
    const val DOCTOR = "DOCTOR"
    const val CONSULTANT = "CONSULTANT"
}

object Doctors : LongIdTable("scheduling_doctors") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val specialty = varchar("specialty", 255).nullable()
    val providerType = varchar("provider_type", 30).default(ProviderType.DOCTOR)
    val maxConcurrentPatients = integer("max_concurrent_patients").nullable()
}
