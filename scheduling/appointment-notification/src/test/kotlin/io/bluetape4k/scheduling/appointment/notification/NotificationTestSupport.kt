package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime

/**
 * Notification 테스트 공통 유틸리티.
 */
object NotificationTestSupport {

    fun connectH2(): Database = Database.connect(
        url = "jdbc:h2:mem:notification_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        driver = "org.h2.Driver",
    )

    fun createSchema() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Clinics,
                Doctors,
                TreatmentTypes,
                Appointments,
                NotificationHistoryTable,
            )
        }
    }

    fun insertSampleAppointment(
        clinicId: Long = insertSampleClinic(),
        doctorId: Long = insertSampleDoctor(),
        treatmentTypeId: Long = insertSampleTreatmentType(),
        patientName: String = "홍길동",
        patientPhone: String = "010-1234-5678",
        date: LocalDate = LocalDate.now().plusDays(1),
        status: String = "CONFIRMED",
    ): AppointmentRecord {
        val id = transaction {
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[Appointments.patientName] = patientName
                it[Appointments.patientPhone] = patientPhone
                it[Appointments.appointmentDate] = date
                it[Appointments.startTime] = LocalTime.of(9, 0)
                it[Appointments.endTime] = LocalTime.of(9, 30)
                it[Appointments.status] = status
            }[Appointments.id].value
        }
        return AppointmentRecord(
            id = id,
            clinicId = clinicId,
            doctorId = doctorId,
            treatmentTypeId = treatmentTypeId,
            patientName = patientName,
            patientPhone = patientPhone,
            appointmentDate = date,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(9, 30),
            status = status,
        )
    }

    private fun insertSampleClinic(): Long = transaction {
        Clinics.insert {
            it[Clinics.name] = "테스트 클리닉"
            it[Clinics.slotDurationMinutes] = 30
        }[Clinics.id].value
    }

    private fun insertSampleDoctor(): Long = transaction {
        Doctors.insert {
            it[Doctors.clinicId] = insertSampleClinic()
            it[Doctors.name] = "김의사"
            it[Doctors.specialty] = "일반내과"
        }[Doctors.id].value
    }

    private fun insertSampleTreatmentType(): Long = transaction {
        TreatmentTypes.insert {
            it[TreatmentTypes.clinicId] = insertSampleClinic()
            it[TreatmentTypes.name] = "일반진료"
            it[TreatmentTypes.defaultDurationMinutes] = 30
        }[TreatmentTypes.id].value
    }
}
