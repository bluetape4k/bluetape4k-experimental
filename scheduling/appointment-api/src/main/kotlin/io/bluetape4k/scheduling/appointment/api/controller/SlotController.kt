package io.bluetape4k.scheduling.appointment.api.controller

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.scheduling.appointment.api.dto.ApiResponse
import io.bluetape4k.scheduling.appointment.api.dto.SlotResponse
import io.bluetape4k.scheduling.appointment.api.dto.toResponse
import io.bluetape4k.scheduling.appointment.service.SlotCalculationService
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 가용 슬롯 조회 REST 컨트롤러.
 *
 * 주어진 병원, 의사, 진료 유형, 날짜에 대해
 * 예약 가능한 시간대 목록을 반환합니다.
 */
@RestController
@RequestMapping("/api/clinics/{clinicId}/slots")
class SlotController(
    private val slotCalculationService: SlotCalculationService,
) {
    companion object : KLogging()

    /**
     * 가용 슬롯 조회.
     *
     * 병원의 운영 시간, 의사의 스케줄, 기존 예약, 장비 가용성을 고려하여
     * 예약 가능한 슬롯을 반환합니다.
     *
     * @param clinicId 병원 ID
     * @param doctorId 의사 ID
     * @param treatmentTypeId 진료 유형 ID
     * @param date 조회 날짜
     * @param requestedDurationMinutes 요청한 진료 시간 (분, optional)
     * @return 가용 슬롯 목록
     */
    @GetMapping
    fun getAvailableSlots(
        @PathVariable clinicId: Long,
        @RequestParam doctorId: Long,
        @RequestParam treatmentTypeId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestParam(required = false) requestedDurationMinutes: Int? = null,
    ): ResponseEntity<ApiResponse<List<SlotResponse>>> {
        log.debug { "GET /api/clinics/$clinicId/slots - doctor=$doctorId, treatment=$treatmentTypeId, date=$date" }
        val query = SlotQuery(
            clinicId = clinicId,
            doctorId = doctorId,
            treatmentTypeId = treatmentTypeId,
            date = date,
            requestedDurationMinutes = requestedDurationMinutes,
        )
        val slots = slotCalculationService.findAvailableSlots(query).map { it.toResponse() }
        return ResponseEntity.ok(ApiResponse.ok(slots))
    }
}
