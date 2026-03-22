package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

class TableSchemaTest {
    private val allTables =
        arrayOf(
            Holidays,
            Clinics,
            ClinicDefaultBreakTimes,
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
            AppointmentNotes,
            RescheduleCandidates
        )

    @Test
    fun `should create all tables without errors`() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

        transaction {
            SchemaUtils.create(*allTables)
        }
    }

    @Test
    fun `should drop and recreate all tables`() {
        Database.connect("jdbc:h2:mem:test_drop;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

        transaction {
            SchemaUtils.create(*allTables)
            SchemaUtils.drop(*allTables)
            SchemaUtils.create(*allTables)
        }
    }
}
