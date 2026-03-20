package io.bluetape4k.scheduling.appointment.service

import io.bluetape4k.scheduling.appointment.model.tables.AppointmentNotes
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.model.tables.BreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.ClinicClosures
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.ConsultationMethod
import io.bluetape4k.scheduling.appointment.model.tables.DoctorAbsences
import io.bluetape4k.scheduling.appointment.model.tables.DoctorSchedules
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.ProviderType
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentCategory
import io.bluetape4k.scheduling.appointment.model.tables.Equipments
import io.bluetape4k.scheduling.appointment.model.tables.OperatingHoursTable
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentEquipments
import io.bluetape4k.scheduling.appointment.model.tables.ConsultationTopics
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SlotCalculationServiceTest {
    companion object {
        private lateinit var db: Database
        private val service = SlotCalculationService()

        // 월요일 날짜
        private val MONDAY = LocalDate.of(2026, 3, 23)

        @JvmStatic
        @BeforeAll
        fun setup() {
            db = Database.connect("jdbc:h2:mem:slot_test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
            transaction {
                SchemaUtils.create(
                    Clinics,
                    OperatingHoursTable,
                    BreakTimes,
                    ClinicClosures,
                    Doctors,
                    DoctorSchedules,
                    DoctorAbsences,
                    Equipments,
                    TreatmentTypes,
                    TreatmentEquipments,
                    ConsultationTopics,
                    Appointments,
                    AppointmentNotes
                )
            }
        }
    }

    @BeforeEach
    fun cleanUp() {
        transaction {
            AppointmentNotes.deleteAll()
            Appointments.deleteAll()
            TreatmentEquipments.deleteAll()
            ConsultationTopics.deleteAll()
            TreatmentTypes.deleteAll()
            Equipments.deleteAll()
            DoctorAbsences.deleteAll()
            DoctorSchedules.deleteAll()
            Doctors.deleteAll()
            ClinicClosures.deleteAll()
            BreakTimes.deleteAll()
            OperatingHoursTable.deleteAll()
            Clinics.deleteAll()
        }
    }

    /**
     * 기본 데이터를 삽입하고 (clinicId, doctorId, treatmentTypeId) 반환
     */
    private fun insertBaseData(
        clinicOpen: LocalTime = LocalTime.of(9, 0),
        clinicClose: LocalTime = LocalTime.of(18, 0),
        slotDurationMinutes: Int = 30,
        maxConcurrentPatients: Int = 1,
        doctorStart: LocalTime = LocalTime.of(9, 0),
        doctorEnd: LocalTime = LocalTime.of(18, 0),
        treatmentDurationMinutes: Int = 30,
        requiresEquipment: Boolean = false,
        doctorMaxConcurrent: Int? = null,
        treatmentMaxConcurrent: Int? = null,
        providerType: String = ProviderType.DOCTOR,
        treatmentCategory: String = TreatmentCategory.TREATMENT,
        requiredProviderType: String = ProviderType.DOCTOR,
        consultationMethod: String? = null,
    ): Triple<Long, Long, Long> =
        transaction {
            val clinicId =
                Clinics
                    .insert {
                        it[name] = "Test Clinic"
                        it[Clinics.slotDurationMinutes] = slotDurationMinutes
                        it[Clinics.maxConcurrentPatients] = maxConcurrentPatients
                    }[Clinics.id]
                    .value

            OperatingHoursTable.insert {
                it[OperatingHoursTable.clinicId] = clinicId
                it[dayOfWeek] = DayOfWeek.MONDAY
                it[openTime] = clinicOpen
                it[closeTime] = clinicClose
                it[isActive] = true
            }

            val doctorId =
                Doctors
                    .insert {
                        it[Doctors.clinicId] = clinicId
                        it[name] = "Dr. Kim"
                        it[Doctors.providerType] = providerType
                        it[Doctors.maxConcurrentPatients] = doctorMaxConcurrent
                    }[Doctors.id]
                    .value

            DoctorSchedules.insert {
                it[DoctorSchedules.doctorId] = doctorId
                it[dayOfWeek] = DayOfWeek.MONDAY
                it[startTime] = doctorStart
                it[endTime] = doctorEnd
            }

            val treatmentTypeId =
                TreatmentTypes
                    .insert {
                        it[TreatmentTypes.clinicId] = clinicId
                        it[name] = "General Checkup"
                        it[category] = treatmentCategory
                        it[defaultDurationMinutes] = treatmentDurationMinutes
                        it[TreatmentTypes.requiredProviderType] = requiredProviderType
                        it[TreatmentTypes.consultationMethod] = consultationMethod
                        it[TreatmentTypes.requiresEquipment] = requiresEquipment
                        it[TreatmentTypes.maxConcurrentPatients] = treatmentMaxConcurrent
                    }[TreatmentTypes.id]
                    .value

            Triple(clinicId, doctorId, treatmentTypeId)
        }

    @Test
    fun `1 - 기본 슬롯 생성 - 09_00-18_00, 30분 슬롯, 제외 없음 - 18개`() {
        val (clinicId, doctorId, treatmentTypeId) = insertBaseData()

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00~18:00, 30분 간격, 30분 duration → 18 slots (09:00, 09:30, ..., 17:30)
        assertEquals(18, slots.size)
        assertEquals(LocalTime.of(9, 0), slots.first().startTime)
        assertEquals(LocalTime.of(17, 30), slots.last().startTime)
    }

    @Test
    fun `2 - 점심시간 제외 12_00-13_00 - 16개`() {
        val (clinicId, doctorId, treatmentTypeId) = insertBaseData()

        transaction {
            BreakTimes.insert {
                it[BreakTimes.clinicId] = clinicId
                it[dayOfWeek] = DayOfWeek.MONDAY
                it[startTime] = LocalTime.of(12, 0)
                it[endTime] = LocalTime.of(13, 0)
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00~12:00 → 6 slots, 13:00~18:00 → 10 slots = 16
        assertEquals(16, slots.size)
        // 12:00, 12:30 슬롯이 없어야 함
        assertTrue(slots.none { it.startTime == LocalTime.of(12, 0) })
        assertTrue(slots.none { it.startTime == LocalTime.of(12, 30) })
    }

    @Test
    fun `3 - 종일 휴진 - 빈 리스트`() {
        val (clinicId, doctorId, treatmentTypeId) = insertBaseData()

        transaction {
            ClinicClosures.insert {
                it[ClinicClosures.clinicId] = clinicId
                it[closureDate] = MONDAY
                it[reason] = "Holiday"
                it[isFullDay] = true
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `4 - 부분 휴진 13_00-15_00 - 감소된 슬롯`() {
        val (clinicId, doctorId, treatmentTypeId) = insertBaseData()

        transaction {
            ClinicClosures.insert {
                it[ClinicClosures.clinicId] = clinicId
                it[closureDate] = MONDAY
                it[reason] = "Partial closure"
                it[isFullDay] = false
                it[startTime] = LocalTime.of(13, 0)
                it[endTime] = LocalTime.of(15, 0)
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00~13:00 → 8 slots, 15:00~18:00 → 6 slots = 14
        assertEquals(14, slots.size)
        assertTrue(slots.none { it.startTime >= LocalTime.of(13, 0) && it.startTime < LocalTime.of(15, 0) })
    }

    @Test
    fun `5 - 의사 스케줄이 운영시간보다 짧음 10_00-16_00 - 교차 결과`() {
        val (clinicId, doctorId, treatmentTypeId) =
            insertBaseData(
                doctorStart = LocalTime.of(10, 0),
                doctorEnd = LocalTime.of(16, 0)
            )

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 교차: 10:00~16:00 → 12 slots
        assertEquals(12, slots.size)
        assertEquals(LocalTime.of(10, 0), slots.first().startTime)
        assertEquals(LocalTime.of(15, 30), slots.last().startTime)
    }

    @Test
    fun `6 - 의사 종일 부재 - 빈 리스트`() {
        val (clinicId, doctorId, treatmentTypeId) = insertBaseData()

        transaction {
            DoctorAbsences.insert {
                it[DoctorAbsences.doctorId] = doctorId
                it[absenceDate] = MONDAY
                it[startTime] = null
                it[endTime] = null
                it[reason] = "Sick leave"
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `7 - 의사 부분 부재 14_00-16_00 - 감소된 슬롯`() {
        val (clinicId, doctorId, treatmentTypeId) = insertBaseData()

        transaction {
            DoctorAbsences.insert {
                it[DoctorAbsences.doctorId] = doctorId
                it[absenceDate] = MONDAY
                it[startTime] = LocalTime.of(14, 0)
                it[endTime] = LocalTime.of(16, 0)
                it[reason] = "Meeting"
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00~14:00 → 10 slots, 16:00~18:00 → 4 slots = 14
        assertEquals(14, slots.size)
        assertTrue(slots.none { it.startTime >= LocalTime.of(14, 0) && it.startTime < LocalTime.of(16, 0) })
    }

    @Test
    fun `8 - 동시 수용 3, 기존 예약 2명 - remainingCapacity 1`() {
        val (clinicId, doctorId, treatmentTypeId) =
            insertBaseData(
                maxConcurrentPatients = 3
            )

        // 09:00~09:30에 2개 예약 추가
        transaction {
            repeat(2) { i ->
                Appointments.insert {
                    it[Appointments.clinicId] = clinicId
                    it[Appointments.doctorId] = doctorId
                    it[Appointments.treatmentTypeId] = treatmentTypeId
                    it[patientName] = "Patient $i"
                    it[appointmentDate] = MONDAY
                    it[startTime] = LocalTime.of(9, 0)
                    it[endTime] = LocalTime.of(9, 30)
                    it[status] = "CONFIRMED"
                }
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        val slot0900 = slots.first { it.startTime == LocalTime.of(9, 0) }
        assertEquals(1, slot0900.remainingCapacity)

        // 다른 슬롯은 capacity 3
        val slot0930 = slots.first { it.startTime == LocalTime.of(9, 30) }
        assertEquals(3, slot0930.remainingCapacity)
    }

    @Test
    fun `9 - 동시 수용 가득 - 해당 슬롯 제외`() {
        val (clinicId, doctorId, treatmentTypeId) =
            insertBaseData(
                maxConcurrentPatients = 1
            )

        // 09:00~09:30에 1개 예약 (capacity=1이므로 가득)
        transaction {
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[patientName] = "Patient Full"
                it[appointmentDate] = MONDAY
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(9, 30)
                it[status] = "CONFIRMED"
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00 슬롯은 제외되어야 함 → 17개
        assertEquals(17, slots.size)
        assertTrue(slots.none { it.startTime == LocalTime.of(9, 0) })
    }

    @Test
    fun `10 - 장비 필요하고 전부 사용 중 - 해당 슬롯 제외`() {
        val (clinicId, doctorId, treatmentTypeId) =
            insertBaseData(
                requiresEquipment = true
            )

        // 장비 추가 (quantity=1)
        val equipmentId =
            transaction {
                val eqId =
                    Equipments
                        .insert {
                            it[Equipments.clinicId] = clinicId
                            it[name] = "X-Ray Machine"
                            it[usageDurationMinutes] = 30
                            it[quantity] = 1
                        }[Equipments.id]
                        .value

                TreatmentEquipments.insert {
                    it[TreatmentEquipments.treatmentTypeId] = treatmentTypeId
                    it[TreatmentEquipments.equipmentId] = eqId
                }

                eqId
            }

        // 09:00~09:30에 해당 장비 사용하는 예약
        transaction {
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[Appointments.equipmentId] = equipmentId
                it[patientName] = "Patient Equip"
                it[appointmentDate] = MONDAY
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(9, 30)
                it[status] = "CONFIRMED"
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00 슬롯은 장비 부족으로 제외
        assertTrue(slots.none { it.startTime == LocalTime.of(9, 0) })
        assertEquals(17, slots.size)
    }

    @Test
    fun `11 - 장비 수량 2, 1개 사용 중 - 슬롯 가용`() {
        val (clinicId, doctorId, treatmentTypeId) =
            insertBaseData(
                maxConcurrentPatients = 2,
                requiresEquipment = true
            )

        // 장비 추가 (quantity=2)
        val equipmentId =
            transaction {
                val eqId =
                    Equipments
                        .insert {
                            it[Equipments.clinicId] = clinicId
                            it[name] = "X-Ray Machine"
                            it[usageDurationMinutes] = 30
                            it[quantity] = 2
                        }[Equipments.id]
                        .value

                TreatmentEquipments.insert {
                    it[TreatmentEquipments.treatmentTypeId] = treatmentTypeId
                    it[TreatmentEquipments.equipmentId] = eqId
                }

                eqId
            }

        // 09:00~09:30에 1개 사용 중
        transaction {
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[Appointments.equipmentId] = equipmentId
                it[patientName] = "Patient Equip"
                it[appointmentDate] = MONDAY
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(9, 30)
                it[status] = "CONFIRMED"
            }
        }

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00 슬롯 여전히 가용 (quantity 2, used 1)
        val slot0900 = slots.firstOrNull { it.startTime == LocalTime.of(9, 0) }
        assertTrue(slot0900 != null)
        assertTrue(slot0900!!.equipmentIds.contains(equipmentId))
    }

    @Test
    fun `12 - 진료 시간 60분이 슬롯 단위 30분보다 큼 - 후보 감소`() {
        val (clinicId, doctorId, treatmentTypeId) =
            insertBaseData(
                treatmentDurationMinutes = 60
            )

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        // 09:00~18:00, 30분 간격으로 시작, 60분 duration
        // 시작 가능: 09:00, 09:30, ..., 17:00 (17:00+60=18:00 OK, 17:30+60=18:30 > 18:00 NG)
        // → 17개 슬롯
        assertEquals(17, slots.size)
        assertEquals(LocalTime.of(9, 0), slots.first().startTime)
        assertEquals(LocalTime.of(17, 0), slots.last().startTime)
        // 각 슬롯의 endTime은 startTime + 60분
        slots.forEach { slot ->
            assertEquals(slot.startTime.plusMinutes(60), slot.endTime)
        }
    }

    @Test
    fun `13 - 의사가 상담 진료 유형에 배정되면 빈 슬롯 반환`() {
        // DOCTOR가 CONSULTATION(CONSULTANT 필요) 진료에 배정 → provider type 불일치
        val (clinicId, doctorId, treatmentTypeId) =
            insertBaseData(
                providerType = ProviderType.DOCTOR,
                treatmentCategory = TreatmentCategory.CONSULTATION,
                requiredProviderType = ProviderType.CONSULTANT,
                consultationMethod = ConsultationMethod.IN_PERSON
            )

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, doctorId, treatmentTypeId, MONDAY)
            )

        assertTrue(slots.isEmpty(), "의사는 상담 진료를 수행할 수 없어야 합니다")
    }

    @Test
    fun `14 - 전문상담사가 상담 진료 유형에 배정되면 슬롯 정상 반환`() {
        // CONSULTANT가 CONSULTATION 진료에 배정 → provider type 일치
        val (clinicId, consultantId, treatmentTypeId) =
            insertBaseData(
                providerType = ProviderType.CONSULTANT,
                treatmentCategory = TreatmentCategory.CONSULTATION,
                requiredProviderType = ProviderType.CONSULTANT,
                consultationMethod = ConsultationMethod.IN_PERSON
            )

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, consultantId, treatmentTypeId, MONDAY)
            )

        assertEquals(18, slots.size, "상담사는 상담 슬롯을 정상적으로 제공해야 합니다")
    }

    @Test
    fun `15 - 전문상담사가 진료 유형에 배정되면 빈 슬롯 반환`() {
        // CONSULTANT가 TREATMENT(DOCTOR 필요) 진료에 배정 → provider type 불일치
        val (clinicId, consultantId, treatmentTypeId) =
            insertBaseData(
                providerType = ProviderType.CONSULTANT,
                treatmentCategory = TreatmentCategory.TREATMENT,
                requiredProviderType = ProviderType.DOCTOR
            )

        val slots =
            service.findAvailableSlots(
                SlotQuery(clinicId, consultantId, treatmentTypeId, MONDAY)
            )

        assertTrue(slots.isEmpty(), "상담사는 진료를 수행할 수 없어야 합니다")
    }

    @Test
    fun `16 - 전화 상담은 장비 불필요, 영상통화는 장비 필요`() {
        // 전화 상담 - 장비 불필요
        val (clinicId1, consultantId1, phoneConsultationId) =
            insertBaseData(
                providerType = ProviderType.CONSULTANT,
                treatmentCategory = TreatmentCategory.CONSULTATION,
                requiredProviderType = ProviderType.CONSULTANT,
                consultationMethod = ConsultationMethod.PHONE,
                requiresEquipment = false
            )

        val phoneSlots =
            service.findAvailableSlots(
                SlotQuery(clinicId1, consultantId1, phoneConsultationId, MONDAY)
            )

        assertEquals(18, phoneSlots.size, "전화 상담은 장비 없이 가능해야 합니다")
        phoneSlots.forEach { slot ->
            assertTrue(slot.equipmentIds.isEmpty())
        }
    }
}
