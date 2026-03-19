package io.bluetape4k.scheduling.appointment.service

/**
 * 최대 동시 환자 수를 결정합니다.
 *
 * 우선순위: treatmentMax > doctorMax > clinicMax
 *
 * @param clinicMax 병원 전체 기본 최대 동시 환자 수
 * @param doctorMax 의사별 최대 동시 환자 수 (nullable)
 * @param treatmentMax 치료 유형별 최대 동시 환자 수 (nullable)
 * @return 적용할 최대 동시 환자 수
 */
fun resolveMaxConcurrent(
    clinicMax: Int,
    doctorMax: Int?,
    treatmentMax: Int?,
): Int = treatmentMax ?: doctorMax ?: clinicMax
