package io.bluetape4k.scheduling.appointment.timezone

import io.bluetape4k.scheduling.appointment.model.dto.ClinicRecord
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.repository.ClinicRepository
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * [ClinicTimezoneService] 테스트.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClinicTimezoneServiceTest {

    private val clinicRepository = ClinicRepository()
    private val service = ClinicTimezoneService(clinicRepository)

    private var seoulClinicId = 0L
    private var nyClinicId = 0L
    private var tokyoClinicId = 0L

    @BeforeAll
    fun setup() {
        Database.connect("jdbc:h2:mem:timezone_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "org.h2.Driver")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Clinics)

            seoulClinicId = Clinics.insert {
                it[name] = "서울 클리닉"
                it[slotDurationMinutes] = 30
                it[timezone] = "Asia/Seoul"
                it[locale] = "ko-KR"
            }[Clinics.id].value

            nyClinicId = Clinics.insert {
                it[name] = "NY Clinic"
                it[slotDurationMinutes] = 30
                it[timezone] = "America/New_York"
                it[locale] = "en-US"
            }[Clinics.id].value

            tokyoClinicId = Clinics.insert {
                it[name] = "東京クリニック"
                it[slotDurationMinutes] = 30
                it[timezone] = "Asia/Tokyo"
                it[locale] = "ja-JP"
            }[Clinics.id].value
        }
    }

    @Test
    fun `toClinicTime - 서울 타임존 변환`() {
        val result = service.toClinicTime(seoulClinicId, LocalDate.of(2026, 3, 21), LocalTime.of(9, 0))

        result.shouldNotBeNull()
        result.zone.shouldBeEqualTo(ZoneId.of("Asia/Seoul"))
        result.hour.shouldBeEqualTo(9)
    }

    @Test
    fun `utcToClinicLocal - UTC를 서울 시간으로 변환`() {
        // UTC 00:00 → 서울 09:00 (KST = UTC+9)
        val utcTime = LocalDateTime.of(2026, 3, 21, 0, 0)
        val localTime = service.utcToClinicLocal(seoulClinicId, utcTime)

        localTime.hour.shouldBeEqualTo(9)
    }

    @Test
    fun `utcToClinicLocal - UTC를 뉴욕 시간으로 변환`() {
        // UTC 12:00 → NY 08:00 (EDT = UTC-4, 3월은 서머타임)
        val utcTime = LocalDateTime.of(2026, 3, 21, 12, 0)
        val localTime = service.utcToClinicLocal(nyClinicId, utcTime)

        localTime.hour.shouldBeEqualTo(8) // EDT
    }

    @Test
    fun `clinicLocalToUtc - 서울 시간을 UTC로 변환`() {
        // 서울 09:00 → UTC 00:00
        val seoulTime = LocalDateTime.of(2026, 3, 21, 9, 0)
        val utcTime = service.clinicLocalToUtc(seoulClinicId, seoulTime)

        utcTime.hour.shouldBeEqualTo(0)
    }

    @Test
    fun `getZoneId - 클리닉별 ZoneId 반환`() {
        service.getZoneId(seoulClinicId).shouldBeEqualTo(ZoneId.of("Asia/Seoul"))
        service.getZoneId(nyClinicId).shouldBeEqualTo(ZoneId.of("America/New_York"))
        service.getZoneId(tokyoClinicId).shouldBeEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun `getLocale - 클리닉별 Locale 반환`() {
        service.getLocale(seoulClinicId).shouldBeEqualTo(Locale.forLanguageTag("ko-KR"))
        service.getLocale(nyClinicId).shouldBeEqualTo(Locale.forLanguageTag("en-US"))
        service.getLocale(tokyoClinicId).shouldBeEqualTo(Locale.forLanguageTag("ja-JP"))
    }

    @Test
    fun `formatDate - locale별 날짜 포맷`() {
        val date = LocalDate.of(2026, 3, 21)

        val koDate = service.formatDate(seoulClinicId, date)
        val enDate = service.formatDate(nyClinicId, date)
        val jaDate = service.formatDate(tokyoClinicId, date)

        koDate.shouldNotBeNull()
        enDate.shouldNotBeNull()
        jaDate.shouldNotBeNull()
        // 각 locale별로 다른 포맷이어야 함
    }

    @Test
    fun `nowAtClinic - 클리닉 현재 시간`() {
        val now = service.nowAtClinic(seoulClinicId)

        now.zone.shouldBeEqualTo(ZoneId.of("Asia/Seoul"))
    }
}
