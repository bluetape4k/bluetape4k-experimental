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

@RestController
@RequestMapping("/api/appointments/{id}/reschedule")
class RescheduleController(
    private val closureRescheduleService: ClosureRescheduleService,
) {
    companion object : KLogging()

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

    @PostMapping("/confirm/{candidateId}")
    fun confirmReschedule(
        @PathVariable id: Long,
        @PathVariable candidateId: Long,
    ): ResponseEntity<ApiResponse<Long>> {
        log.debug { "POST /api/appointments/$id/reschedule/confirm/$candidateId" }
        val newAppointmentId = closureRescheduleService.confirmReschedule(candidateId)
        return ResponseEntity.ok(ApiResponse.ok(newAppointmentId))
    }

    @PostMapping("/auto")
    fun autoReschedule(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<Long?>> {
        log.debug { "POST /api/appointments/$id/reschedule/auto" }
        val newAppointmentId = closureRescheduleService.autoReschedule(id)
        return ResponseEntity.ok(ApiResponse.ok(newAppointmentId))
    }
}
