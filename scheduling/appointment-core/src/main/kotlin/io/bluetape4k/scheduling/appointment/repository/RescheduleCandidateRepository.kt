package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotNull
import io.bluetape4k.scheduling.appointment.model.dto.RescheduleCandidateRecord
import io.bluetape4k.scheduling.appointment.model.tables.RescheduleCandidates
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class RescheduleCandidateRepository : LongJdbcRepository<RescheduleCandidateRecord> {
    companion object : KLogging()

    override val table = RescheduleCandidates
    override fun extractId(entity: RescheduleCandidateRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): RescheduleCandidateRecord = toRescheduleCandidateRecord()

    fun save(record: RescheduleCandidateRecord): RescheduleCandidateRecord {
        val id = RescheduleCandidates.insertAndGetId {
            it[originalAppointmentId] = record.originalAppointmentId
            it[candidateDate] = record.candidateDate
            it[startTime] = record.startTime
            it[endTime] = record.endTime
            it[doctorId] = record.doctorId
            it[priority] = record.priority
        }.value
        return record.copy(id = id)
    }

    fun findBestCandidate(originalAppointmentId: Long): RescheduleCandidateRecord? =
        RescheduleCandidates
            .selectAll()
            .where {
                (RescheduleCandidates.originalAppointmentId eq originalAppointmentId) and
                    (RescheduleCandidates.selected eq false)
            }.orderBy(RescheduleCandidates.priority)
            .firstOrNull()?.toRescheduleCandidateRecord()

    fun markSelected(candidateId: Long): Int =
        RescheduleCandidates.update(where = { RescheduleCandidates.id eq candidateId }) {
            it[selected] = true
        }
}
