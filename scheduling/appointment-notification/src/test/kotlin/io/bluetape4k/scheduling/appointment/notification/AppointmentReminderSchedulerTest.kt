package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalTime

/**
 * [AppointmentReminderScheduler] 테스트.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppointmentReminderSchedulerTest {

    private val notificationChannel = mockk<NotificationChannel>(relaxed = true)
    private val appointmentRepository = AppointmentRepository()
    private val historyRepository = NotificationHistoryRepository()
    private val properties = NotificationProperties()

    private lateinit var scheduler: AppointmentReminderScheduler

    @BeforeAll
    fun setup() {
        NotificationTestSupport.connectH2()
        NotificationTestSupport.createSchema()
    }

    @BeforeEach
    fun cleanup() {
        scheduler = AppointmentReminderScheduler(
            notificationChannel = notificationChannel,
            appointmentRepository = appointmentRepository,
            historyRepository = historyRepository,
            properties = properties,
            leaderElection = null,
        )
        clearMocks(notificationChannel)
        transaction {
            NotificationHistoryTable.deleteAll()
            Appointments.deleteAll()
            TreatmentTypes.deleteAll()
            Doctors.deleteAll()
            Clinics.deleteAll()
        }
    }

    @Test
    fun `다음날 확정 예약은 clinic id 와 무관하게 리마인더를 발송한다`() {
        val appointment = insertConfirmedAppointment(date = LocalDate.now().plusDays(1))

        scheduler.checkReminders()

        verify(exactly = 1) {
            notificationChannel.sendReminder(appointment, ReminderType.DAY_BEFORE)
        }
    }

    @Test
    fun `성공 이력이 있으면 중복 리마인더를 발송하지 않는다`() {
        val appointment = insertConfirmedAppointment(date = LocalDate.now().plusDays(1))
        transaction {
            historyRepository.save(
                NotificationHistoryRecord(
                    appointmentId = appointment.id!!,
                    channelType = "DUMMY",
                    eventType = NotificationEventType.REMINDER_DAY_BEFORE,
                    payloadJson = "{}",
                ),
            )
        }

        scheduler.checkReminders()

        verify(exactly = 0) {
            notificationChannel.sendReminder(any(), ReminderType.DAY_BEFORE)
        }
    }

    private fun insertConfirmedAppointment(date: LocalDate): AppointmentRecord =
        transaction {
            val clinicId = Clinics.insertAndGetId {
                it[name] = "테스트 클리닉"
                it[slotDurationMinutes] = 30
            }.value

            val doctorId = Doctors.insertAndGetId {
                it[Doctors.clinicId] = clinicId
                it[name] = "김의사"
            }.value

            val treatmentTypeId = TreatmentTypes.insertAndGetId {
                it[TreatmentTypes.clinicId] = clinicId
                it[name] = "일반진료"
                it[defaultDurationMinutes] = 30
            }.value

            val appointmentId = Appointments.insertAndGetId {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[patientName] = "홍길동"
                it[patientPhone] = "010-1234-5678"
                it[appointmentDate] = date
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(9, 30)
                it[status] = "CONFIRMED"
            }.value

            appointmentRepository.findByIdOrNull(appointmentId)!!
        }
}
