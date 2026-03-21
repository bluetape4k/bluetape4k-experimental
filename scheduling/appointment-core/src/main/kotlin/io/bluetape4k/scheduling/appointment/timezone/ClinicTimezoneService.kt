package io.bluetape4k.scheduling.appointment.timezone

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.ClinicRecord
import io.bluetape4k.scheduling.appointment.repository.ClinicRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 클리닉별 타임존 변환 서비스.
 *
 * 내부 저장은 UTC 기준 LocalDate/LocalTime이며,
 * 외부 표시 시 클리닉의 timezone으로 변환합니다.
 */
class ClinicTimezoneService(
    private val clinicRepository: ClinicRepository,
) {
    companion object : KLogging()

    /**
     * 클리닉 timezone으로 변환된 [ZonedDateTime]을 반환합니다.
     */
    fun toClinicTime(clinicId: Long, date: LocalDate, time: LocalTime): ZonedDateTime {
        val clinic = getClinic(clinicId)
        val zoneId = ZoneId.of(clinic.timezone)
        return ZonedDateTime.of(date, time, zoneId)
    }

    /**
     * UTC 기준 LocalDateTime을 클리닉 timezone으로 변환합니다.
     */
    fun utcToClinicLocal(clinicId: Long, utcDateTime: LocalDateTime): LocalDateTime {
        val clinic = getClinic(clinicId)
        val utcZoned = utcDateTime.atZone(ZoneId.of("UTC"))
        val clinicZoned = utcZoned.withZoneSameInstant(ZoneId.of(clinic.timezone))
        return clinicZoned.toLocalDateTime()
    }

    /**
     * 클리닉 로컬 시간을 UTC로 변환합니다.
     */
    fun clinicLocalToUtc(clinicId: Long, localDateTime: LocalDateTime): LocalDateTime {
        val clinic = getClinic(clinicId)
        val clinicZoned = localDateTime.atZone(ZoneId.of(clinic.timezone))
        val utcZoned = clinicZoned.withZoneSameInstant(ZoneId.of("UTC"))
        return utcZoned.toLocalDateTime()
    }

    /**
     * 클리닉 locale에 맞는 날짜 포맷 문자열을 반환합니다.
     */
    fun formatDate(clinicId: Long, date: LocalDate): String {
        val clinic = getClinic(clinicId)
        val locale = Locale.forLanguageTag(clinic.locale)
        val formatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
        return date.format(formatter)
    }

    /**
     * 클리닉 locale에 맞는 시간 포맷 문자열을 반환합니다.
     */
    fun formatTime(clinicId: Long, time: LocalTime): String {
        val clinic = getClinic(clinicId)
        val locale = Locale.forLanguageTag(clinic.locale)
        val formatter = DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale)
        return time.format(formatter)
    }

    /**
     * 클리닉의 현재 로컬 시간을 반환합니다.
     */
    fun nowAtClinic(clinicId: Long): ZonedDateTime {
        val clinic = getClinic(clinicId)
        return ZonedDateTime.now(ZoneId.of(clinic.timezone))
    }

    /**
     * 클리닉의 [ZoneId]를 반환합니다.
     */
    fun getZoneId(clinicId: Long): ZoneId {
        val clinic = getClinic(clinicId)
        return ZoneId.of(clinic.timezone)
    }

    /**
     * 클리닉의 [Locale]을 반환합니다.
     */
    fun getLocale(clinicId: Long): Locale {
        val clinic = getClinic(clinicId)
        return Locale.forLanguageTag(clinic.locale)
    }

    private fun getClinic(clinicId: Long): ClinicRecord =
        transaction { clinicRepository.findByIdOrNull(clinicId) }
            ?: throw NoSuchElementException("Clinic not found: $clinicId")
}
