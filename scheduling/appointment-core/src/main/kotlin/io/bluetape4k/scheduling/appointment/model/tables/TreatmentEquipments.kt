package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object TreatmentEquipments : LongIdTable("scheduling_treatment_equipments") {
    val treatmentTypeId = reference("treatment_type_id", TreatmentTypes, onDelete = ReferenceOption.CASCADE)
    val equipmentId = reference("equipment_id", Equipments, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex(treatmentTypeId, equipmentId)
        // 장비별 역방향 조회 (장비에 연결된 치료 유형 탐색)
        index("idx_treatment_equipments_equipment_id", false, equipmentId)
    }
}
