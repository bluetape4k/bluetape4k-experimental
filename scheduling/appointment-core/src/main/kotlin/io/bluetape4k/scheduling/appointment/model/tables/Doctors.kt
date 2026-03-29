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

/**
 * 의사/전문상담사 정보 테이블.
 *
 * 각 병원의 진료 제공자 정보를 저장합니다.
 * 진료 유형과 제공자 유형의 매칭, 동시 환자 수 제한 등의 비즈니스 로직을 지원합니다.
 */
object Doctors : LongIdTable("scheduling_doctors") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val specialty = varchar("specialty", 255).nullable()
    val providerType = varchar("provider_type", 30).default(ProviderType.DOCTOR)
    val maxConcurrentPatients = integer("max_concurrent_patients").nullable()

    init {
        // 병원별 의사 목록 조회
        index("idx_doctors_clinic_id", false, clinicId)
    }
}
