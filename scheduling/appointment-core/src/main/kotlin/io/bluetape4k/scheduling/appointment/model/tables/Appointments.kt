package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp

object Appointments : LongIdTable("scheduling_appointments") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val doctorId = reference("doctor_id", Doctors, onDelete = ReferenceOption.CASCADE)
    val treatmentTypeId = reference("treatment_type_id", TreatmentTypes, onDelete = ReferenceOption.CASCADE)
    val equipmentId = optReference("equipment_id", Equipments, onDelete = ReferenceOption.SET_NULL)
    val patientName = varchar("patient_name", 255)
    val patientPhone = varchar("patient_phone", 50).nullable()
    val patientExternalId = varchar("patient_external_id", 255).nullable()
    val appointmentDate = date("appointment_date")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val consultationTopicId = optReference("consultation_topic_id", ConsultationTopics, onDelete = ReferenceOption.SET_NULL)
    val consultationMethod = varchar("consultation_method", 30).nullable()
    val rescheduleFromId = long("reschedule_from_id").nullable()
    val status = varchar("status", 30).default("REQUESTED")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}
