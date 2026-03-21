package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.BreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicClosureRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicDefaultBreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorAbsenceRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorScheduleRecord
import io.bluetape4k.scheduling.appointment.model.dto.HolidayRecord
import io.bluetape4k.scheduling.appointment.model.dto.OperatingHoursRecord
import io.bluetape4k.scheduling.appointment.model.dto.EquipmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.RescheduleCandidateRecord
import io.bluetape4k.scheduling.appointment.model.dto.TreatmentEquipmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.TreatmentTypeRecord
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.model.tables.Equipments
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentEquipments
import io.bluetape4k.scheduling.appointment.model.tables.BreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.ClinicDefaultBreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.ClinicClosures
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.DoctorAbsences
import io.bluetape4k.scheduling.appointment.model.tables.DoctorSchedules
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.Holidays
import io.bluetape4k.scheduling.appointment.model.tables.OperatingHoursTable
import io.bluetape4k.scheduling.appointment.model.tables.RescheduleCandidates
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import org.jetbrains.exposed.v1.core.ResultRow

fun ResultRow.toClinicRecord() = ClinicRecord(
    id = this[Clinics.id].value,
    name = this[Clinics.name],
    slotDurationMinutes = this[Clinics.slotDurationMinutes],
    timezone = this[Clinics.timezone],
    locale = this[Clinics.locale],
    maxConcurrentPatients = this[Clinics.maxConcurrentPatients],
    openOnHolidays = this[Clinics.openOnHolidays],
)

fun ResultRow.toOperatingHoursRecord() = OperatingHoursRecord(
    id = this[OperatingHoursTable.id].value,
    clinicId = this[OperatingHoursTable.clinicId].value,
    dayOfWeek = this[OperatingHoursTable.dayOfWeek],
    openTime = this[OperatingHoursTable.openTime],
    closeTime = this[OperatingHoursTable.closeTime],
    isActive = this[OperatingHoursTable.isActive],
)

fun ResultRow.toClinicDefaultBreakTimeRecord() = ClinicDefaultBreakTimeRecord(
    id = this[ClinicDefaultBreakTimes.id].value,
    clinicId = this[ClinicDefaultBreakTimes.clinicId].value,
    name = this[ClinicDefaultBreakTimes.name],
    startTime = this[ClinicDefaultBreakTimes.startTime],
    endTime = this[ClinicDefaultBreakTimes.endTime],
)

fun ResultRow.toBreakTimeRecord() = BreakTimeRecord(
    id = this[BreakTimes.id].value,
    clinicId = this[BreakTimes.clinicId].value,
    dayOfWeek = this[BreakTimes.dayOfWeek],
    startTime = this[BreakTimes.startTime],
    endTime = this[BreakTimes.endTime],
)

fun ResultRow.toClinicClosureRecord() = ClinicClosureRecord(
    id = this[ClinicClosures.id].value,
    clinicId = this[ClinicClosures.clinicId].value,
    closureDate = this[ClinicClosures.closureDate],
    reason = this[ClinicClosures.reason],
    isFullDay = this[ClinicClosures.isFullDay],
    startTime = this[ClinicClosures.startTime],
    endTime = this[ClinicClosures.endTime],
)

fun ResultRow.toDoctorRecord() = DoctorRecord(
    id = this[Doctors.id].value,
    clinicId = this[Doctors.clinicId].value,
    name = this[Doctors.name],
    specialty = this[Doctors.specialty],
    providerType = this[Doctors.providerType],
    maxConcurrentPatients = this[Doctors.maxConcurrentPatients],
)

fun ResultRow.toDoctorScheduleRecord() = DoctorScheduleRecord(
    id = this[DoctorSchedules.id].value,
    doctorId = this[DoctorSchedules.doctorId].value,
    dayOfWeek = this[DoctorSchedules.dayOfWeek],
    startTime = this[DoctorSchedules.startTime],
    endTime = this[DoctorSchedules.endTime],
)

fun ResultRow.toDoctorAbsenceRecord() = DoctorAbsenceRecord(
    id = this[DoctorAbsences.id].value,
    doctorId = this[DoctorAbsences.doctorId].value,
    absenceDate = this[DoctorAbsences.absenceDate],
    startTime = this[DoctorAbsences.startTime],
    endTime = this[DoctorAbsences.endTime],
    reason = this[DoctorAbsences.reason],
)

fun ResultRow.toTreatmentTypeRecord() = TreatmentTypeRecord(
    id = this[TreatmentTypes.id].value,
    clinicId = this[TreatmentTypes.clinicId].value,
    name = this[TreatmentTypes.name],
    category = this[TreatmentTypes.category],
    defaultDurationMinutes = this[TreatmentTypes.defaultDurationMinutes],
    requiredProviderType = this[TreatmentTypes.requiredProviderType],
    consultationMethod = this[TreatmentTypes.consultationMethod],
    requiresEquipment = this[TreatmentTypes.requiresEquipment],
    maxConcurrentPatients = this[TreatmentTypes.maxConcurrentPatients],
)

fun ResultRow.toHolidayRecord() = HolidayRecord(
    id = this[Holidays.id].value,
    holidayDate = this[Holidays.holidayDate],
    name = this[Holidays.name],
    recurring = this[Holidays.recurring],
)

fun ResultRow.toAppointmentRecord() = AppointmentRecord(
    id = this[Appointments.id].value,
    clinicId = this[Appointments.clinicId].value,
    doctorId = this[Appointments.doctorId].value,
    treatmentTypeId = this[Appointments.treatmentTypeId].value,
    equipmentId = this[Appointments.equipmentId]?.value,
    consultationTopicId = this[Appointments.consultationTopicId]?.value,
    consultationMethod = this[Appointments.consultationMethod],
    rescheduleFromId = this[Appointments.rescheduleFromId],
    patientName = this[Appointments.patientName],
    patientPhone = this[Appointments.patientPhone],
    patientExternalId = this[Appointments.patientExternalId],
    appointmentDate = this[Appointments.appointmentDate],
    startTime = this[Appointments.startTime],
    endTime = this[Appointments.endTime],
    status = this[Appointments.status],
    createdAt = this[Appointments.createdAt],
    updatedAt = this[Appointments.updatedAt],
)

fun ResultRow.toEquipmentRecord() = EquipmentRecord(
    id = this[Equipments.id].value,
    clinicId = this[Equipments.clinicId].value,
    name = this[Equipments.name],
    usageDurationMinutes = this[Equipments.usageDurationMinutes],
    quantity = this[Equipments.quantity],
)

fun ResultRow.toTreatmentEquipmentRecord() = TreatmentEquipmentRecord(
    id = this[TreatmentEquipments.id].value,
    treatmentTypeId = this[TreatmentEquipments.treatmentTypeId].value,
    equipmentId = this[TreatmentEquipments.equipmentId].value,
)

fun ResultRow.toRescheduleCandidateRecord() = RescheduleCandidateRecord(
    id = this[RescheduleCandidates.id].value,
    originalAppointmentId = this[RescheduleCandidates.originalAppointmentId].value,
    candidateDate = this[RescheduleCandidates.candidateDate],
    startTime = this[RescheduleCandidates.startTime],
    endTime = this[RescheduleCandidates.endTime],
    doctorId = this[RescheduleCandidates.doctorId].value,
    priority = this[RescheduleCandidates.priority],
    selected = this[RescheduleCandidates.selected],
    createdAt = this[RescheduleCandidates.createdAt],
)
