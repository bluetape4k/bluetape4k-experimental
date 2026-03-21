package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotNull
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
}
