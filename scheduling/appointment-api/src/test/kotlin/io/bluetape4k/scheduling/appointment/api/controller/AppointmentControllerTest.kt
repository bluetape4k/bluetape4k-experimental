package io.bluetape4k.scheduling.appointment.api.controller

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.event.AppointmentEventLogs
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStateHistory
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import io.bluetape4k.scheduling.appointment.model.tables.BreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.ClinicClosures
import io.bluetape4k.scheduling.appointment.model.tables.ClinicDefaultBreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.ConsultationTopics
import io.bluetape4k.scheduling.appointment.model.tables.DoctorAbsences
import io.bluetape4k.scheduling.appointment.model.tables.DoctorSchedules
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.Equipments
import io.bluetape4k.scheduling.appointment.model.tables.Holidays
import io.bluetape4k.scheduling.appointment.model.tables.OperatingHoursTable
import io.bluetape4k.scheduling.appointment.model.tables.RescheduleCandidates
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentEquipments
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentNotes
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.DayOfWeek

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentControllerTest {

    companion object : KLogging()

    @Autowired
    private lateinit var mockMvc: MockMvc

    private var clinicId: Long = 0
    private var doctorId: Long = 0
    private var treatmentTypeId: Long = 0

    @BeforeEach
    fun setup() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Clinics, OperatingHoursTable, ClinicDefaultBreakTimes, BreakTimes, ClinicClosures,
                Doctors, DoctorSchedules, DoctorAbsences,
                TreatmentTypes, Equipments, TreatmentEquipments,
                ConsultationTopics, Holidays,
                Appointments, AppointmentNotes, AppointmentStateHistory,
                RescheduleCandidates, AppointmentEventLogs,
            )

            // Clean up in reverse FK order
            AppointmentEventLogs.deleteAll()
            AppointmentStateHistory.deleteAll()
            RescheduleCandidates.deleteAll()
            AppointmentNotes.deleteAll()
            Appointments.deleteAll()
            TreatmentEquipments.deleteAll()
            Equipments.deleteAll()
            ConsultationTopics.deleteAll()
            TreatmentTypes.deleteAll()
            DoctorAbsences.deleteAll()
            DoctorSchedules.deleteAll()
            Doctors.deleteAll()
            Holidays.deleteAll()
            ClinicClosures.deleteAll()
            BreakTimes.deleteAll()
            ClinicDefaultBreakTimes.deleteAll()
            OperatingHoursTable.deleteAll()
            Clinics.deleteAll()

            // Insert test data
            clinicId = Clinics.insertAndGetId {
                it[name] = "Test Clinic"
                it[slotDurationMinutes] = 30
                it[timezone] = "Asia/Seoul"
                it[maxConcurrentPatients] = 3
                it[openOnHolidays] = false
            }.value

            doctorId = Doctors.insertAndGetId {
                it[Doctors.clinicId] = this@AppointmentControllerTest.clinicId
                it[name] = "Dr. Test"
                it[specialty] = "General"
                it[providerType] = "DOCTOR"
                it[maxConcurrentPatients] = 1
            }.value

            treatmentTypeId = TreatmentTypes.insertAndGetId {
                it[TreatmentTypes.clinicId] = this@AppointmentControllerTest.clinicId
                it[name] = "General Checkup"
                it[category] = "GENERAL"
                it[defaultDurationMinutes] = 30
                it[requiredProviderType] = "DOCTOR"
                it[requiresEquipment] = false
                it[maxConcurrentPatients] = 1
            }.value

            OperatingHoursTable.insertAndGetId {
                it[OperatingHoursTable.clinicId] = this@AppointmentControllerTest.clinicId
                it[dayOfWeek] = DayOfWeek.MONDAY
                it[openTime] = java.time.LocalTime.of(9, 0)
                it[closeTime] = java.time.LocalTime.of(18, 0)
                it[isActive] = true
            }

            DoctorSchedules.insertAndGetId {
                it[DoctorSchedules.doctorId] = this@AppointmentControllerTest.doctorId
                it[dayOfWeek] = DayOfWeek.MONDAY
                it[startTime] = java.time.LocalTime.of(9, 0)
                it[endTime] = java.time.LocalTime.of(18, 0)
            }
        }
    }

    @Test
    fun `POST - create appointment`() {
        val body = """
            {
                "clinicId": $clinicId,
                "doctorId": $doctorId,
                "treatmentTypeId": $treatmentTypeId,
                "patientName": "John Doe",
                "patientPhone": "010-1234-5678",
                "appointmentDate": "2026-04-06",
                "startTime": "10:00",
                "endTime": "10:30"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.patientName").value("John Doe"))
            .andExpect(jsonPath("$.data.status").value("REQUESTED"))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
    }

    @Test
    fun `GET - find appointment by id`() {
        val appointmentId = createTestAppointment()

        mockMvc.perform(get("/api/appointments/$appointmentId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(appointmentId))
            .andExpect(jsonPath("$.data.patientName").value("Jane Doe"))
    }

    @Test
    fun `GET - return 404 for non-existent appointment`() {
        mockMvc.perform(get("/api/appointments/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `PATCH - update appointment status`() {
        val appointmentId = createTestAppointment()

        val body = """{"status": "CONFIRMED"}"""

        val result = mockMvc.perform(
            patch("/api/appointments/$appointmentId/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
    }

    @Test
    fun `PATCH - reject invalid status transition`() {
        val appointmentId = createTestAppointment()

        // REQUESTED -> COMPLETED is not a valid transition
        val body = """{"status": "COMPLETED"}"""

        val result = mockMvc.perform(
            patch("/api/appointments/$appointmentId/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `DELETE - cancel appointment`() {
        val appointmentId = createTestAppointment()

        val result = mockMvc.perform(delete("/api/appointments/$appointmentId"))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
    }

    private fun createTestAppointment(): Long =
        transaction {
            Appointments.insertAndGetId {
                it[Appointments.clinicId] = this@AppointmentControllerTest.clinicId
                it[Appointments.doctorId] = this@AppointmentControllerTest.doctorId
                it[Appointments.treatmentTypeId] = this@AppointmentControllerTest.treatmentTypeId
                it[patientName] = "Jane Doe"
                it[patientPhone] = "010-9876-5432"
                it[appointmentDate] = java.time.LocalDate.of(2026, 4, 6)
                it[startTime] = java.time.LocalTime.of(11, 0)
                it[endTime] = java.time.LocalTime.of(11, 30)
                it[Appointments.status] = AppointmentState.REQUESTED
            }.value
        }
}
