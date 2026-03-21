package io.bluetape4k.scheduling.appointment.api.config

import io.bluetape4k.scheduling.appointment.event.AppointmentEventLogs
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentNotes
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
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
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SchemaInitConfig {

    @Bean
    fun schemaInitializer(): ApplicationRunner =
        ApplicationRunner {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    Clinics,
                    OperatingHoursTable,
                    ClinicDefaultBreakTimes,
                    BreakTimes,
                    ClinicClosures,
                    Doctors,
                    DoctorSchedules,
                    DoctorAbsences,
                    TreatmentTypes,
                    Equipments,
                    TreatmentEquipments,
                    ConsultationTopics,
                    Holidays,
                    Appointments,
                    AppointmentNotes,
                    RescheduleCandidates,
                    AppointmentEventLogs,
                )
            }
        }
}
