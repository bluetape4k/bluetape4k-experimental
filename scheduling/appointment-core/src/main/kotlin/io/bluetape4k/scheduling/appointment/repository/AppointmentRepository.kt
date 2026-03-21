package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStatus
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.support.requireNotNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.LocalTime

class AppointmentRepository : LongJdbcRepository<AppointmentRecord> {
    companion object : KLogging()

    override val table = Appointments
    override fun extractId(entity: AppointmentRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): AppointmentRecord = toAppointmentRecord()

    fun countOverlapping(
        doctorId: Long,
        date: LocalDate,
        slotStart: LocalTime,
        slotEnd: LocalTime,
    ): Int =
        Appointments
            .selectAll()
            .where {
                (Appointments.doctorId eq doctorId) and
                    (Appointments.appointmentDate eq date) and
                    (Appointments.startTime less slotEnd) and
                    (Appointments.endTime greater slotStart) and
                    (Appointments.status neq AppointmentStatus.CANCELLED) and
                    (Appointments.status neq AppointmentStatus.NO_SHOW)
            }.count()
            .toInt()

    fun countEquipmentUsage(
        equipmentId: Long,
        date: LocalDate,
        slotStart: LocalTime,
        slotEnd: LocalTime,
    ): Int =
        Appointments
            .selectAll()
            .where {
                (Appointments.equipmentId eq equipmentId) and
                    (Appointments.appointmentDate eq date) and
                    (Appointments.startTime less slotEnd) and
                    (Appointments.endTime greater slotStart) and
                    (Appointments.status neq AppointmentStatus.CANCELLED) and
                    (Appointments.status neq AppointmentStatus.NO_SHOW)
            }.count()
            .toInt()

    fun findActiveByClinicAndDate(
        clinicId: Long,
        date: LocalDate,
        activeStatuses: List<String> = AppointmentStatus.ACTIVE_STATUSES,
    ): List<AppointmentRecord> =
        Appointments
            .selectAll()
            .where {
                (Appointments.clinicId eq clinicId) and
                    (Appointments.appointmentDate eq date) and
                    (Appointments.status inList activeStatuses)
            }.map { it.toAppointmentRecord() }

    fun updateStatusByClinicAndDate(
        clinicId: Long,
        date: LocalDate,
        fromStatuses: List<String>,
        toStatus: String,
    ): Int =
        Appointments.update(
            where = {
                (Appointments.clinicId eq clinicId) and
                    (Appointments.appointmentDate eq date) and
                    (Appointments.status inList fromStatuses)
            }
        ) {
            it[status] = toStatus
        }

    fun save(record: AppointmentRecord): AppointmentRecord {
        val id = Appointments.insertAndGetId {
            it[clinicId] = record.clinicId
            it[doctorId] = record.doctorId
            it[treatmentTypeId] = record.treatmentTypeId
            it[equipmentId] = record.equipmentId
            it[consultationTopicId] = record.consultationTopicId
            it[consultationMethod] = record.consultationMethod
            it[rescheduleFromId] = record.rescheduleFromId
            it[patientName] = record.patientName
            it[patientPhone] = record.patientPhone
            it[patientExternalId] = record.patientExternalId
            it[appointmentDate] = record.appointmentDate
            it[startTime] = record.startTime
            it[endTime] = record.endTime
            it[status] = record.status
        }.value
        return record.copy(id = id)
    }

    fun updateStatus(appointmentId: Long, newStatus: String): Int =
        Appointments.update(where = { Appointments.id eq appointmentId }) {
            it[status] = newStatus
        }

    fun findByClinicAndDateRange(clinicId: Long, dateRange: ClosedRange<LocalDate>): List<AppointmentRecord> =
        Appointments
            .selectAll()
            .where {
                (Appointments.clinicId eq clinicId) and
                    (Appointments.appointmentDate greaterEq dateRange.start) and
                    (Appointments.appointmentDate lessEq dateRange.endInclusive) and
                    (Appointments.status neq AppointmentStatus.CANCELLED) and
                    (Appointments.status neq AppointmentStatus.NO_SHOW)
            }.map { it.toAppointmentRecord() }
}
