package io.bluetape4k.scheduling.appointment.api.controller

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.scheduling.appointment.api.dto.ApiResponse
import io.bluetape4k.scheduling.appointment.api.dto.RescheduleCandidateResponse
import io.bluetape4k.scheduling.appointment.api.dto.toResponse
import io.bluetape4k.scheduling.appointment.model.tables.RescheduleCandidates
import io.bluetape4k.scheduling.appointment.repository.toRescheduleCandidateRecord
import io.bluetape4k.scheduling.appointment.service.ClosureRescheduleService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 휴진 및 재배정 관리 REST 컨트롤러.
 *
 * 병원 휴진, 의사 임시휴진 시 영향받는 예약을 재배정하고
 * 관리자가 수동으로 재배정 후보를 선택할 수 있는 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/appointments/{id}/reschedule")
class RescheduleController(
    private val closureRescheduleService: ClosureRescheduleService,
) {
    companion object : KLogging()

    /**
     * 병원 휴진으로 인한 일괄 재배정.
     *
     * 특정 날짜의 병원 휴진으로 영향받는 모든 예약을 조회하고
     * 각 예약에 대한 대체 슬롯 후보를 생성합니다.
     *
     * @param id 예약 ID (미사용, 요청 경로에만 사용)
     * @param clinicId 병원 ID
     * @param closureDate 휴진 날짜
     * @param searchDays 재배정 검색 기간 (기본값: 7일)
     * @return 예약별 재배정 후보 목록 (Map: appointmentId -> 후보 슬롯 목록)
     */
    @PostMapping("/closure")
    fun processClosureReschedule(
        @PathVariable id: Long,
        @RequestParam clinicId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) closureDate: LocalDate,
        @RequestParam(defaultValue = "7") searchDays: Int,
    ): ResponseEntity<ApiResponse<Map<Long, List<RescheduleCandidateResponse>>>> {
        log.debug { "POST /api/appointments/$id/reschedule/closure - clinic=$clinicId, date=$closureDate" }
        val result = closureRescheduleService.processClosureReschedule(clinicId, closureDate, searchDays)
        val response = result.mapValues { (_, candidates) ->
            candidates.map { it.toResponse() }
        }
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    /**
     * 재배정 후보 조회.
     *
     * 특정 예약에 대해 생성된 모든 재배정 후보를 조회합니다 (우선순위 정렬).
     *
     * @param id 원본 예약 ID
     * @return 재배정 후보 목록
     */
    @GetMapping("/candidates")
    fun getCandidates(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<List<RescheduleCandidateResponse>>> {
        log.debug { "GET /api/appointments/$id/reschedule/candidates" }
        val candidates = transaction {
            RescheduleCandidates
                .selectAll()
                .where { RescheduleCandidates.originalAppointmentId eq id }
                .orderBy(RescheduleCandidates.priority)
                .map { it.toRescheduleCandidateRecord().toResponse() }
        }
        return ResponseEntity.ok(ApiResponse.ok(candidates))
    }

    /**
     * 수동 재배정 확정.
     *
     * 제시된 후보 중 하나를 선택하여 실제로 예약을 재배정합니다.
     * 새로운 예약이 생성되고 원본 예약은 [AppointmentState.RESCHEDULED]로 변경됩니다.
     *
     * @param id 원본 예약 ID
     * @param candidateId 선택한 재배정 후보 ID
     * @return 생성된 새로운 예약 ID
     */
    @PostMapping("/confirm/{candidateId}")
    fun confirmReschedule(
        @PathVariable id: Long,
        @PathVariable candidateId: Long,
    ): ResponseEntity<ApiResponse<Long>> {
        log.debug { "POST /api/appointments/$id/reschedule/confirm/$candidateId" }
        val newAppointmentId = closureRescheduleService.confirmReschedule(candidateId)
        return ResponseEntity.ok(ApiResponse.ok(newAppointmentId))
    }

    /**
     * 자동 재배정.
     *
     * 가장 우수한 후보를 자동으로 선택하여 재배정합니다.
     * 적절한 후보가 없으면 null을 반환합니다.
     *
     * @param id 원본 예약 ID
     * @return 생성된 새로운 예약 ID (없으면 null)
     */
    @PostMapping("/auto")
    fun autoReschedule(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<Long?>> {
        log.debug { "POST /api/appointments/$id/reschedule/auto" }
        val newAppointmentId = closureRescheduleService.autoReschedule(id)
        return ResponseEntity.ok(ApiResponse.ok(newAppointmentId))
    }
}
