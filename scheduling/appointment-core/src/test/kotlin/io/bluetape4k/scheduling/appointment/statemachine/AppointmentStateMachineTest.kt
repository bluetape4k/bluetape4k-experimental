package io.bluetape4k.scheduling.appointment.statemachine

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppointmentStateMachineTest {
    private val stateMachine = AppointmentStateMachine()

    // ========================================
    // Valid transitions
    // ========================================

    @Test
    fun `PENDING에서 Request 이벤트로 REQUESTED로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.PENDING, AppointmentEvent.Request)
            next shouldBeEqualTo AppointmentState.REQUESTED
        }

    @Test
    fun `REQUESTED에서 Confirm 이벤트로 CONFIRMED로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.REQUESTED, AppointmentEvent.Confirm)
            next shouldBeEqualTo AppointmentState.CONFIRMED
        }

    @Test
    fun `CONFIRMED에서 CheckIn 이벤트로 CHECKED_IN으로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.CONFIRMED, AppointmentEvent.CheckIn)
            next shouldBeEqualTo AppointmentState.CHECKED_IN
        }

    @Test
    fun `CHECKED_IN에서 StartTreatment 이벤트로 IN_PROGRESS로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.CHECKED_IN, AppointmentEvent.StartTreatment)
            next shouldBeEqualTo AppointmentState.IN_PROGRESS
        }

    @Test
    fun `IN_PROGRESS에서 Complete 이벤트로 COMPLETED로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.IN_PROGRESS, AppointmentEvent.Complete)
            next shouldBeEqualTo AppointmentState.COMPLETED
        }

    @Test
    fun `CONFIRMED에서 MarkNoShow 이벤트로 NO_SHOW로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.CONFIRMED, AppointmentEvent.MarkNoShow)
            next shouldBeEqualTo AppointmentState.NO_SHOW
        }

    @Test
    fun `전체 정상 흐름 - PENDING부터 COMPLETED까지`() =
        runTest {
            var state: AppointmentState = AppointmentState.PENDING

            state = stateMachine.transition(state, AppointmentEvent.Request)
            state shouldBeEqualTo AppointmentState.REQUESTED

            state = stateMachine.transition(state, AppointmentEvent.Confirm)
            state shouldBeEqualTo AppointmentState.CONFIRMED

            state = stateMachine.transition(state, AppointmentEvent.CheckIn)
            state shouldBeEqualTo AppointmentState.CHECKED_IN

            state = stateMachine.transition(state, AppointmentEvent.StartTreatment)
            state shouldBeEqualTo AppointmentState.IN_PROGRESS

            state = stateMachine.transition(state, AppointmentEvent.Complete)
            state shouldBeEqualTo AppointmentState.COMPLETED
        }

    // ========================================
    // Cancel transitions
    // ========================================

    @Test
    fun `PENDING에서 Cancel 이벤트로 CANCELLED로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.PENDING, AppointmentEvent.Cancel("환자 요청"))
            next shouldBeEqualTo AppointmentState.CANCELLED
        }

    @Test
    fun `REQUESTED에서 Cancel 이벤트로 CANCELLED로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.REQUESTED, AppointmentEvent.Cancel("의사 부재"))
            next shouldBeEqualTo AppointmentState.CANCELLED
        }

    @Test
    fun `CONFIRMED에서 Cancel 이벤트로 CANCELLED로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.CONFIRMED, AppointmentEvent.Cancel("일정 변경"))
            next shouldBeEqualTo AppointmentState.CANCELLED
        }

    @Test
    fun `CHECKED_IN에서 Cancel 이벤트로 CANCELLED로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.CHECKED_IN, AppointmentEvent.Cancel("환자 거부"))
            next shouldBeEqualTo AppointmentState.CANCELLED
        }

    // ========================================
    // Reschedule transition
    // ========================================

    @Test
    fun `CONFIRMED에서 Reschedule 이벤트로 PENDING으로 전이`() =
        runTest {
            val next = stateMachine.transition(AppointmentState.CONFIRMED, AppointmentEvent.Reschedule)
            next shouldBeEqualTo AppointmentState.PENDING
        }

    // ========================================
    // Invalid transitions
    // ========================================

    @Test
    fun `PENDING에서 Complete 이벤트는 예외 발생`() =
        runTest {
            assertThrows<IllegalStateException> {
                stateMachine.transition(AppointmentState.PENDING, AppointmentEvent.Complete)
            }
        }

    @Test
    fun `COMPLETED에서 Cancel 이벤트는 예외 발생`() =
        runTest {
            assertThrows<IllegalStateException> {
                stateMachine.transition(AppointmentState.COMPLETED, AppointmentEvent.Cancel("취소 시도"))
            }
        }

    @Test
    fun `CANCELLED에서 Request 이벤트는 예외 발생`() =
        runTest {
            assertThrows<IllegalStateException> {
                stateMachine.transition(AppointmentState.CANCELLED, AppointmentEvent.Request)
            }
        }

    @Test
    fun `NO_SHOW에서 CheckIn 이벤트는 예외 발생`() =
        runTest {
            assertThrows<IllegalStateException> {
                stateMachine.transition(AppointmentState.NO_SHOW, AppointmentEvent.CheckIn)
            }
        }

    @Test
    fun `IN_PROGRESS에서 Cancel 이벤트는 예외 발생`() =
        runTest {
            assertThrows<IllegalStateException> {
                stateMachine.transition(AppointmentState.IN_PROGRESS, AppointmentEvent.Cancel("진료 중 취소 불가"))
            }
        }

    // ========================================
    // canTransition
    // ========================================

    @Test
    fun `canTransition - 유효한 전이는 true 반환`() {
        stateMachine.canTransition(AppointmentState.PENDING, AppointmentEvent.Request).shouldBeTrue()
        stateMachine.canTransition(AppointmentState.REQUESTED, AppointmentEvent.Confirm).shouldBeTrue()
        stateMachine.canTransition(AppointmentState.CONFIRMED, AppointmentEvent.CheckIn).shouldBeTrue()
        stateMachine.canTransition(AppointmentState.CONFIRMED, AppointmentEvent.Reschedule).shouldBeTrue()
        stateMachine.canTransition(AppointmentState.PENDING, AppointmentEvent.Cancel("이유")).shouldBeTrue()
    }

    @Test
    fun `canTransition - 유효하지 않은 전이는 false 반환`() {
        stateMachine.canTransition(AppointmentState.PENDING, AppointmentEvent.Complete).shouldBeFalse()
        stateMachine.canTransition(AppointmentState.COMPLETED, AppointmentEvent.Cancel("이유")).shouldBeFalse()
        stateMachine.canTransition(AppointmentState.CANCELLED, AppointmentEvent.Request).shouldBeFalse()
    }

    // ========================================
    // allowedEvents
    // ========================================

    @Test
    fun `allowedEvents - PENDING에서 허용된 이벤트`() {
        val allowed = stateMachine.allowedEvents(AppointmentState.PENDING)
        allowed shouldContain AppointmentEvent.Request::class.java
        allowed shouldContain AppointmentEvent.Cancel::class.java
        allowed shouldNotContain AppointmentEvent.Confirm::class.java
    }

    @Test
    fun `allowedEvents - CONFIRMED에서 허용된 이벤트`() {
        val allowed = stateMachine.allowedEvents(AppointmentState.CONFIRMED)
        allowed shouldContain AppointmentEvent.CheckIn::class.java
        allowed shouldContain AppointmentEvent.MarkNoShow::class.java
        allowed shouldContain AppointmentEvent.Cancel::class.java
        allowed shouldContain AppointmentEvent.Reschedule::class.java
        allowed shouldNotContain AppointmentEvent.Complete::class.java
    }

    @Test
    fun `allowedEvents - COMPLETED에서 허용된 이벤트 없음`() {
        val allowed = stateMachine.allowedEvents(AppointmentState.COMPLETED)
        allowed shouldBeEqualTo emptySet()
    }

    @Test
    fun `allowedEvents - CANCELLED에서 허용된 이벤트 없음`() {
        val allowed = stateMachine.allowedEvents(AppointmentState.CANCELLED)
        allowed shouldBeEqualTo emptySet()
    }

    // ========================================
    // onTransition callback
    // ========================================

    @Test
    fun `onTransition 콜백이 성공적인 전이 시 호출됨`() =
        runTest {
            val transitions = mutableListOf<Triple<AppointmentState, AppointmentEvent, AppointmentState>>()

            val sm =
                AppointmentStateMachine { from, event, to ->
                    transitions.add(Triple(from, event, to))
                }

            sm.transition(AppointmentState.PENDING, AppointmentEvent.Request)
            sm.transition(AppointmentState.REQUESTED, AppointmentEvent.Confirm)

            transitions.size shouldBeEqualTo 2
            transitions[0] shouldBeEqualTo
                Triple(
                    AppointmentState.PENDING,
                    AppointmentEvent.Request as AppointmentEvent,
                    AppointmentState.REQUESTED
                )
            transitions[1] shouldBeEqualTo
                Triple(
                    AppointmentState.REQUESTED,
                    AppointmentEvent.Confirm as AppointmentEvent,
                    AppointmentState.CONFIRMED
                )
        }

    @Test
    fun `onTransition 콜백은 실패한 전이 시 호출되지 않음`() =
        runTest {
            var callbackInvoked = false

            val sm =
                AppointmentStateMachine { _, _, _ ->
                    callbackInvoked = true
                }

            assertThrows<IllegalStateException> {
                sm.transition(AppointmentState.PENDING, AppointmentEvent.Complete)
            }

            callbackInvoked.shouldBeFalse()
        }
}
