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

@RestController
@RequestMapping("/api/clinics/{clinicId}/slots")
class SlotController(
    private val slotCalculationService: SlotCalculationService,
) {
    companion object : KLogging()

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
