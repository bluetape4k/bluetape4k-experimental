package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotNull
import io.bluetape4k.scheduling.appointment.model.dto.EquipmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.TreatmentEquipmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.TreatmentTypeRecord
import io.bluetape4k.scheduling.appointment.model.tables.Equipments
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentEquipments
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

class TreatmentTypeRepository : LongJdbcRepository<TreatmentTypeRecord> {
    companion object : KLogging()

    override val table = TreatmentTypes
    override fun extractId(entity: TreatmentTypeRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): TreatmentTypeRecord = toTreatmentTypeRecord()

    fun findRequiredEquipmentIds(treatmentTypeId: Long): List<Long> =
        TreatmentEquipments
            .selectAll()
            .where { TreatmentEquipments.treatmentTypeId eq treatmentTypeId }
            .map { it[TreatmentEquipments.equipmentId].value }

    fun findEquipmentQuantities(equipmentIds: List<Long>): Map<Long, Int> =
        if (equipmentIds.isEmpty()) emptyMap()
        else Equipments
            .selectAll()
            .where { Equipments.id inList equipmentIds }
            .associate { it[Equipments.id].value to it[Equipments.quantity] }

    fun findByClinicId(clinicId: Long): List<TreatmentTypeRecord> =
        TreatmentTypes
            .selectAll()
            .where { TreatmentTypes.clinicId eq clinicId }
            .map { it.toTreatmentTypeRecord() }

    fun findEquipmentsByClinicId(clinicId: Long): List<EquipmentRecord> =
        Equipments
            .selectAll()
            .where { Equipments.clinicId eq clinicId }
            .map { it.toEquipmentRecord() }

    fun findAllTreatmentEquipments(clinicId: Long): List<TreatmentEquipmentRecord> {
        val treatmentIds = TreatmentTypes
            .selectAll()
            .where { TreatmentTypes.clinicId eq clinicId }
            .map { it[TreatmentTypes.id].value }

        if (treatmentIds.isEmpty()) return emptyList()

        return TreatmentEquipments
            .selectAll()
            .where { TreatmentEquipments.treatmentTypeId inList treatmentIds }
            .map { it.toTreatmentEquipmentRecord() }
    }
}
