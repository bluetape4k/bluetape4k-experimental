package io.bluetape4k.scheduling.appointment.service.model

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalTime

class TimeRangeTest {
    // ========================================
    // TimeRange 기본 동작
    // ========================================

    @Test
    fun `start가 end보다 이후이면 예외 발생`() {
        assertThrows<IllegalArgumentException> {
            TimeRange(LocalTime.of(17, 0), LocalTime.of(9, 0))
        }
    }

    @Test
    fun `start와 end가 같으면 예외 발생`() {
        assertThrows<IllegalArgumentException> {
            TimeRange(LocalTime.of(9, 0), LocalTime.of(9, 0))
        }
    }

    @Test
    fun `contains - 범위 내 시간은 true`() {
        val range = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))

        range.contains(LocalTime.of(9, 0)).shouldBeTrue()
        range.contains(LocalTime.of(12, 0)).shouldBeTrue()
        range.contains(LocalTime.of(16, 59)).shouldBeTrue()
    }

    @Test
    fun `contains - 범위 밖 시간은 false`() {
        val range = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))

        range.contains(LocalTime.of(8, 59)).shouldBeFalse()
        range.contains(LocalTime.of(17, 0)).shouldBeFalse() // end는 exclusive
    }

    @Test
    fun `overlaps - 겹치는 범위는 true`() {
        val range = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))

        range.overlaps(TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 0))).shouldBeTrue()
        range.overlaps(TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0))).shouldBeTrue()
        range.overlaps(TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0))).shouldBeTrue()
    }

    @Test
    fun `overlaps - 겹치지 않는 범위는 false`() {
        val range = TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0))

        range.overlaps(TimeRange(LocalTime.of(12, 0), LocalTime.of(13, 0))).shouldBeFalse()
        range.overlaps(TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0))).shouldBeFalse()
    }

    @Test
    fun `duration - 정확한 Duration 반환`() {
        val range = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
        range.duration() shouldBeEqualTo Duration.ofHours(8)
    }

    // ========================================
    // subtractRanges
    // ========================================

    @Test
    fun `인접 범위 제외 - 겹치지 않으므로 base 유지`() {
        val base = TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0))
        val exclusion = TimeRange(LocalTime.of(12, 0), LocalTime.of(13, 0))

        val result = subtractRanges(base, listOf(exclusion))

        result shouldBeEqualTo listOf(base)
    }

    @Test
    fun `겹치는 제외 - 중간 부분 제거`() {
        val base = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
        val exclusion = TimeRange(LocalTime.of(11, 0), LocalTime.of(14, 0))

        val result = subtractRanges(base, listOf(exclusion))

        result shouldBeEqualTo
            listOf(
                TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))
            )
    }

    @Test
    fun `빈 결과 - base 전체가 제외됨`() {
        val base = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
        val exclusion = TimeRange(LocalTime.of(8, 0), LocalTime.of(18, 0))

        val result = subtractRanges(base, listOf(exclusion))

        result shouldBeEqualTo emptyList()
    }

    @Test
    fun `base 시작 부분이 제외됨`() {
        val base = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
        val exclusion = TimeRange(LocalTime.of(8, 0), LocalTime.of(11, 0))

        val result = subtractRanges(base, listOf(exclusion))

        result shouldBeEqualTo
            listOf(
                TimeRange(LocalTime.of(11, 0), LocalTime.of(17, 0))
            )
    }

    @Test
    fun `base 끝 부분이 제외됨`() {
        val base = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
        val exclusion = TimeRange(LocalTime.of(15, 0), LocalTime.of(18, 0))

        val result = subtractRanges(base, listOf(exclusion))

        result shouldBeEqualTo
            listOf(
                TimeRange(LocalTime.of(9, 0), LocalTime.of(15, 0))
            )
    }

    @Test
    fun `다중 제외 - 여러 구간 제거`() {
        val base = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
        val exclusions =
            listOf(
                TimeRange(LocalTime.of(10, 0), LocalTime.of(11, 0)),
                TimeRange(LocalTime.of(13, 0), LocalTime.of(14, 0))
            )

        val result = subtractRanges(base, exclusions)

        result shouldBeEqualTo
            listOf(
                TimeRange(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                TimeRange(LocalTime.of(11, 0), LocalTime.of(13, 0)),
                TimeRange(LocalTime.of(14, 0), LocalTime.of(17, 0))
            )
    }

    @Test
    fun `제외 목록이 비어있으면 base 그대로 반환`() {
        val base = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))

        val result = subtractRanges(base, emptyList())

        result shouldBeEqualTo listOf(base)
    }

    // ========================================
    // computeEffectiveRanges
    // ========================================

    @Test
    fun `clinic과 doctor 교차 + break 제외`() {
        val result =
            computeEffectiveRanges(
                clinicOpen = LocalTime.of(8, 0),
                clinicClose = LocalTime.of(18, 0),
                doctorStart = LocalTime.of(9, 0),
                doctorEnd = LocalTime.of(17, 0),
                breakTimes =
                    listOf(
                        TimeRange(LocalTime.of(12, 0), LocalTime.of(13, 0))
                    )
            )

        // 교차: 09:00-17:00, break 12:00-13:00 제외
        result shouldBeEqualTo
            listOf(
                TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                TimeRange(LocalTime.of(13, 0), LocalTime.of(17, 0))
            )
    }

    @Test
    fun `clinic과 doctor 범위가 겹치지 않으면 빈 리스트`() {
        val result =
            computeEffectiveRanges(
                clinicOpen = LocalTime.of(8, 0),
                clinicClose = LocalTime.of(12, 0),
                doctorStart = LocalTime.of(13, 0),
                doctorEnd = LocalTime.of(17, 0)
            )

        result shouldBeEqualTo emptyList()
    }

    @Test
    fun `모든 제외 항목 적용`() {
        val result =
            computeEffectiveRanges(
                clinicOpen = LocalTime.of(8, 0),
                clinicClose = LocalTime.of(20, 0),
                doctorStart = LocalTime.of(9, 0),
                doctorEnd = LocalTime.of(18, 0),
                breakTimes =
                    listOf(
                        TimeRange(LocalTime.of(12, 0), LocalTime.of(13, 0))
                    ),
                partialClosures =
                    listOf(
                        TimeRange(LocalTime.of(15, 0), LocalTime.of(16, 0))
                    ),
                doctorAbsences =
                    listOf(
                        TimeRange(LocalTime.of(10, 0), LocalTime.of(11, 0))
                    )
            )

        // 교차: 09:00-18:00
        // break 12:00-13:00 제외 → [09:00-12:00, 13:00-18:00]
        // partialClosure 15:00-16:00 제외 → [09:00-12:00, 13:00-15:00, 16:00-18:00]
        // doctorAbsence 10:00-11:00 제외 → [09:00-10:00, 11:00-12:00, 13:00-15:00, 16:00-18:00]
        result shouldBeEqualTo
            listOf(
                TimeRange(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                TimeRange(LocalTime.of(11, 0), LocalTime.of(12, 0)),
                TimeRange(LocalTime.of(13, 0), LocalTime.of(15, 0)),
                TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0))
            )
    }

    @Test
    fun `제외 항목 없으면 교차 범위 그대로 반환`() {
        val result =
            computeEffectiveRanges(
                clinicOpen = LocalTime.of(9, 0),
                clinicClose = LocalTime.of(17, 0),
                doctorStart = LocalTime.of(9, 0),
                doctorEnd = LocalTime.of(17, 0)
            )

        result shouldBeEqualTo
            listOf(
                TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
            )
    }
}
