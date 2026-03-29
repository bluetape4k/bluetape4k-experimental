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
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStateHistoryRecord
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.bluetape4k.scheduling.appointment.repository.AppointmentStateHistoryRepository
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentEvent
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentStateMachine
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 예약 관리 REST 컨트롤러.
 *
 * 예약의 조회, 생성, 상태 변경, 취소 API를 제공합니다.
 * 모든 상태 변경은 [AppointmentStateMachine]을 통해 유효성 검증되며,
 * 변경 완료 시 [ApplicationEventPublisher]로 도메인 이벤트를 발행합니다.
 */
@RestController
@RequestMapping("/api/appointments")
class AppointmentController(
    private val appointmentRepository: AppointmentRepository,
    private val stateMachine: AppointmentStateMachine,
    private val eventPublisher: ApplicationEventPublisher,
    private val stateHistoryRepository: AppointmentStateHistoryRepository,
) {
    companion object : KLogging()

    /**
     * 기간별 예약 조회.
     *
     * @param clinicId 병원 ID
     * @param startDate 조회 시작 날짜
     * @param endDate 조회 종료 날짜
     * @return 기간 내 예약 목록
     */
    @GetMapping
    fun getByDateRange(
        @RequestParam clinicId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
    ): ResponseEntity<ApiResponse<List<AppointmentResponse>>> {
        log.debug { "GET /api/appointments?clinicId=$clinicId&startDate=$startDate&endDate=$endDate" }
        val records = transaction { appointmentRepository.findByClinicAndDateRange(clinicId, startDate..endDate) }
        return ResponseEntity.ok(ApiResponse.ok(records.map { it.toResponse() }))
    }

    /**
     * 예약 단건 조회.
     *
     * @param id 예약 ID
     * @return 예약 정보
     * @throws NoSuchElementException 예약이 없으면
     */
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<ApiResponse<AppointmentResponse>> {
        log.debug { "GET /api/appointments/$id" }
        val record = transaction { appointmentRepository.findByIdOrNull(id) }
            ?: throw NoSuchElementException("Appointment not found: $id")
        return ResponseEntity.ok(ApiResponse.ok(record.toResponse()))
    }

    /**
     * 예약 생성.
     *
     * 생성된 예약의 초기 상태는 [AppointmentState.REQUESTED]입니다.
     * 예약 생성 완료 시 [AppointmentDomainEvent.Created] 이벤트를 발행합니다.
     *
     * @param request 예약 생성 요청
     * @return 생성된 예약
     */
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
            status = AppointmentState.REQUESTED,
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

    /**
     * 예약 상태 변경.
     *
     * [AppointmentStateMachine]으로 유효한 전이만 허용합니다.
     * 상태 변경 이력은 [AppointmentStateHistory] 테이블에 기록됩니다.
     * 변경 완료 시 [AppointmentDomainEvent.StatusChanged] 이벤트를 발행합니다.
     *
     * @param id 예약 ID
     * @param request 변경 대상 상태와 사유
     * @return 업데이트된 예약
     * @throws IllegalStateException 허용되지 않은 상태 전이인 경우
     * @throws NoSuchElementException 예약이 없으면
     */
    @PatchMapping("/{id}/status")
    suspend fun updateStatus(
        @PathVariable id: Long,
        @RequestBody request: UpdateStatusRequest,
    ): ResponseEntity<ApiResponse<AppointmentResponse>> {
        log.debug { "PATCH /api/appointments/$id/status - target=${request.status}" }
        val record = transaction { appointmentRepository.findByIdOrNull(id) }
            ?: throw NoSuchElementException("Appointment not found: $id")

        val currentState = record.status
        val event = parseEvent(request.status, request.reason)

        val nextState = stateMachine.transition(currentState, event)

        transaction {
            appointmentRepository.updateStatus(id, nextState)
            stateHistoryRepository.save(
                AppointmentStateHistoryRecord(
                    appointmentId = id,
                    fromState = currentState,
                    toState = nextState,
                    reason = request.reason,
                )
            )
        }

        eventPublisher.publishEvent(
            AppointmentDomainEvent.StatusChanged(
                appointmentId = id,
                clinicId = record.clinicId,
                fromState = record.status.name,
                toState = nextState.name,
                reason = request.reason,
            )
        )

        val updated = transaction { appointmentRepository.findByIdOrNull(id) }!!
        return ResponseEntity.ok(ApiResponse.ok(updated.toResponse()))
    }

    /**
     * 예약 취소.
     *
     * 예약을 [AppointmentState.CANCELLED] 상태로 변경합니다.
     * 취소 이력은 [AppointmentStateHistory]에 기록되며,
     * [AppointmentDomainEvent.Cancelled] 이벤트를 발행합니다.
     *
     * @param id 예약 ID
     * @return 취소된 예약
     * @throws NoSuchElementException 예약이 없으면
     */
    @DeleteMapping("/{id}")
    suspend fun cancel(@PathVariable id: Long): ResponseEntity<ApiResponse<AppointmentResponse>> {
        log.debug { "DELETE /api/appointments/$id" }
        val record = transaction { appointmentRepository.findByIdOrNull(id) }
            ?: throw NoSuchElementException("Appointment not found: $id")

        val currentState = record.status
        val cancelEvent = AppointmentEvent.Cancel(reason = "Cancelled by user")

        stateMachine.transition(currentState, cancelEvent)

        transaction {
            appointmentRepository.updateStatus(id, AppointmentState.CANCELLED)
            stateHistoryRepository.save(
                AppointmentStateHistoryRecord(
                    appointmentId = id,
                    fromState = currentState,
                    toState = AppointmentState.CANCELLED,
                    reason = "Cancelled by user",
                )
            )
        }

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
