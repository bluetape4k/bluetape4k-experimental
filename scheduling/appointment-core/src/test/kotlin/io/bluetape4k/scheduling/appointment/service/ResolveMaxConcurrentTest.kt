package io.bluetape4k.scheduling.appointment.service

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class ResolveMaxConcurrentTest {
    @Test
    fun `treatmentMax가 있으면 treatmentMax 사용`() {
        resolveMaxConcurrent(clinicMax = 10, doctorMax = 5, treatmentMax = 3) shouldBeEqualTo 3
    }

    @Test
    fun `treatmentMax가 null이면 doctorMax 사용`() {
        resolveMaxConcurrent(clinicMax = 10, doctorMax = 5, treatmentMax = null) shouldBeEqualTo 5
    }

    @Test
    fun `treatmentMax와 doctorMax 모두 null이면 clinicMax 사용`() {
        resolveMaxConcurrent(clinicMax = 10, doctorMax = null, treatmentMax = null) shouldBeEqualTo 10
    }

    @Test
    fun `doctorMax만 null이고 treatmentMax가 있으면 treatmentMax 사용`() {
        resolveMaxConcurrent(clinicMax = 10, doctorMax = null, treatmentMax = 2) shouldBeEqualTo 2
    }

    @Test
    fun `모든 값이 같아도 treatmentMax 우선`() {
        resolveMaxConcurrent(clinicMax = 5, doctorMax = 5, treatmentMax = 5) shouldBeEqualTo 5
    }

    @Test
    fun `clinicMax만 사용되는 경우`() {
        resolveMaxConcurrent(clinicMax = 1, doctorMax = null, treatmentMax = null) shouldBeEqualTo 1
    }
}
