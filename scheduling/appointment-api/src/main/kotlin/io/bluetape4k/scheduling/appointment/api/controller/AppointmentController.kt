package io.bluetape4k.scheduling.appointment.api.controller

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.scheduling.appointment.api.dto.ApiResponse
import io.bluetape4k.scheduling.appointment.api.dto.AppointmentResponse
import io.bluetape4k.scheduling.appointment.api.dto.CreateAppointmentRequest
import io.bluetape4k.scheduling.appointment.api.dto.UpdateStatusRequest
import io.bluetape4k.scheduling.appointment.api.dto.toResponse
import io.bluetape4k.scheduling.appointment.event.AppointmentDomainEvent
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStatus
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentEvent
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentStateMachine
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/appointments")
class AppointmentController(
    private val appointmentRepository: AppointmentRepository,
    private val stateMachine: AppointmentStateMachine,
    private val eventPublisher: ApplicationEventPublisher,
) {
    companion object : KLogging()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<ApiResponse<AppointmentResponse>> {
        log.debug { "GET /api/appointments/$id" }
        val record = transaction { appointmentRepository.findByIdOrNull(id) }
            ?: throw NoSuchElementException("Appointment not found: $id")
        return ResponseEntity.ok(ApiResponse.ok(record.toResponse()))
    }

    @PostMapping
    fun create(@RequestBody request: CreateAppointmentRequest): ResponseEntity<ApiResponse<AppointmentResponse>> {
        log.debug { "POST /api/appointments - patient=${request.patientName}" }
        val record = AppointmentRecord(
            clinicId = request.clinicId,
            doctorId = request.doctorId,
            treatmentTypeId = request.treatmentTypeId,
            equipmentId = request.equipmentId,
            patientName = request.patientName,
            patientPhone = request.patientPhone,
            appointmentDate = request.appointmentDate,
            startTime = request.startTime,
            endTime = request.endTime,
            status = AppointmentStatus.REQUESTED,
        )
        val saved = transaction { appointmentRepository.save(record) }
        eventPublisher.publishEvent(
            AppointmentDomainEvent.Created(
                appointmentId = saved.id!!,
                clinicId = saved.clinicId,
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(saved.toResponse()))
    }

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @RequestBody request: UpdateStatusRequest,
    ): ResponseEntity<ApiResponse<AppointmentResponse>> {
        log.debug { "PATCH /api/appointments/$id/status - target=${request.status}" }
        val record = transaction { appointmentRepository.findByIdOrNull(id) }
            ?: throw NoSuchElementException("Appointment not found: $id")

        val currentState = parseState(record.status)
        val event = parseEvent(request.status, request.reason)

        val nextState = runBlocking { stateMachine.transition(currentState, event) }

        transaction { appointmentRepository.updateStatus(id, nextState.name) }

        eventPublisher.publishEvent(
            AppointmentDomainEvent.StatusChanged(
                appointmentId = id,
                clinicId = record.clinicId,
                fromState = record.status,
                toState = nextState.name,
                reason = request.reason,
            )
        )

        val updated = transaction { appointmentRepository.findByIdOrNull(id) }!!
        return ResponseEntity.ok(ApiResponse.ok(updated.toResponse()))
    }

    @DeleteMapping("/{id}")
    fun cancel(@PathVariable id: Long): ResponseEntity<ApiResponse<AppointmentResponse>> {
        log.debug { "DELETE /api/appointments/$id" }
        val record = transaction { appointmentRepository.findByIdOrNull(id) }
            ?: throw NoSuchElementException("Appointment not found: $id")

        val currentState = parseState(record.status)
        val cancelEvent = AppointmentEvent.Cancel(reason = "Cancelled by user")

        runBlocking { stateMachine.transition(currentState, cancelEvent) }

        transaction { appointmentRepository.updateStatus(id, AppointmentStatus.CANCELLED) }

        eventPublisher.publishEvent(
            AppointmentDomainEvent.Cancelled(
                appointmentId = id,
                clinicId = record.clinicId,
                reason = "Cancelled by user",
            )
        )

        val updated = transaction { appointmentRepository.findByIdOrNull(id) }!!
        return ResponseEntity.ok(ApiResponse.ok(updated.toResponse()))
    }
}

internal fun parseState(status: String): AppointmentState = when (status) {
    "PENDING" -> AppointmentState.PENDING
    "REQUESTED" -> AppointmentState.REQUESTED
    "CONFIRMED" -> AppointmentState.CONFIRMED
    "CHECKED_IN" -> AppointmentState.CHECKED_IN
    "IN_PROGRESS" -> AppointmentState.IN_PROGRESS
    "COMPLETED" -> AppointmentState.COMPLETED
    "CANCELLED" -> AppointmentState.CANCELLED
    "NO_SHOW" -> AppointmentState.NO_SHOW
    "PENDING_RESCHEDULE" -> AppointmentState.PENDING_RESCHEDULE
    "RESCHEDULED" -> AppointmentState.RESCHEDULED
    else -> throw IllegalArgumentException("Unknown appointment status: $status")
}

internal fun parseEvent(targetStatus: String, reason: String? = null): AppointmentEvent = when (targetStatus) {
    "REQUESTED" -> AppointmentEvent.Request
    "CONFIRMED" -> AppointmentEvent.Confirm
    "CHECKED_IN" -> AppointmentEvent.CheckIn
    "IN_PROGRESS" -> AppointmentEvent.StartTreatment
    "COMPLETED" -> AppointmentEvent.Complete
    "CANCELLED" -> AppointmentEvent.Cancel(reason = reason ?: "Cancelled")
    "NO_SHOW" -> AppointmentEvent.MarkNoShow
    "PENDING_RESCHEDULE" -> AppointmentEvent.RequestReschedule(reason = reason ?: "Reschedule requested")
    "RESCHEDULED" -> AppointmentEvent.ConfirmReschedule
    "PENDING" -> AppointmentEvent.Reschedule
    else -> throw IllegalArgumentException("Unknown target status: $targetStatus")
}
