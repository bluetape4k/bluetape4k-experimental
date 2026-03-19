package io.bluetape4k.scheduling.appointment.statemachine

/**
 * 예약 상태 머신.
 *
 * [transitions] Map 으로 허용된 상태 전이를 정의하고,
 * [transition] 함수로 현재 상태와 이벤트에 따라 다음 상태를 반환합니다.
 */
class AppointmentStateMachine(
    private val onTransition: (
        suspend (
            from: AppointmentState,
            event: AppointmentEvent,
            to: AppointmentState,
        ) -> Unit
    )? = null,
) {
    /**
     * 허용된 상태 전이 정의.
     * Key: (현재 상태, 이벤트 클래스) → Value: 다음 상태
     *
     * [AppointmentEvent.Cancel]은 모든 cancellable 상태에서 허용됩니다.
     */
    private val transitions: Map<Pair<AppointmentState, Class<out AppointmentEvent>>, AppointmentState> =
        buildMap {
            // PENDING → REQUESTED
            put(AppointmentState.PENDING to AppointmentEvent.Request::class.java, AppointmentState.REQUESTED)
            // PENDING → CANCELLED
            put(AppointmentState.PENDING to AppointmentEvent.Cancel::class.java, AppointmentState.CANCELLED)

            // REQUESTED → CONFIRMED
            put(AppointmentState.REQUESTED to AppointmentEvent.Confirm::class.java, AppointmentState.CONFIRMED)
            // REQUESTED → CANCELLED
            put(AppointmentState.REQUESTED to AppointmentEvent.Cancel::class.java, AppointmentState.CANCELLED)

            // CONFIRMED → CHECKED_IN
            put(AppointmentState.CONFIRMED to AppointmentEvent.CheckIn::class.java, AppointmentState.CHECKED_IN)
            // CONFIRMED → NO_SHOW
            put(AppointmentState.CONFIRMED to AppointmentEvent.MarkNoShow::class.java, AppointmentState.NO_SHOW)
            // CONFIRMED → CANCELLED
            put(AppointmentState.CONFIRMED to AppointmentEvent.Cancel::class.java, AppointmentState.CANCELLED)
            // CONFIRMED → PENDING (재예약)
            put(AppointmentState.CONFIRMED to AppointmentEvent.Reschedule::class.java, AppointmentState.PENDING)

            // CHECKED_IN → IN_PROGRESS
            put(
                AppointmentState.CHECKED_IN to AppointmentEvent.StartTreatment::class.java,
                AppointmentState.IN_PROGRESS
            )
            // CHECKED_IN → CANCELLED
            put(AppointmentState.CHECKED_IN to AppointmentEvent.Cancel::class.java, AppointmentState.CANCELLED)

            // IN_PROGRESS → COMPLETED
            put(AppointmentState.IN_PROGRESS to AppointmentEvent.Complete::class.java, AppointmentState.COMPLETED)
        }

    /**
     * 현재 상태에서 이벤트를 처리하여 다음 상태로 전이합니다.
     *
     * @param currentState 현재 상태
     * @param event 발생한 이벤트
     * @return 전이된 다음 상태
     * @throws IllegalStateException 허용되지 않은 전이인 경우
     */
    suspend fun transition(
        currentState: AppointmentState,
        event: AppointmentEvent,
    ): AppointmentState {
        val key = currentState to event::class.java
        val nextState =
            transitions[key]
                ?: throw IllegalStateException(
                    "Invalid transition: $currentState + $event. Allowed events: ${allowedEvents(currentState)}"
                )

        onTransition?.invoke(currentState, event, nextState)
        return nextState
    }

    /**
     * 현재 상태에서 해당 이벤트로 전이 가능한지 확인합니다.
     */
    fun canTransition(
        currentState: AppointmentState,
        event: AppointmentEvent,
    ): Boolean {
        val key = currentState to event::class.java
        return transitions.containsKey(key)
    }

    /**
     * 현재 상태에서 허용된 이벤트 클래스 목록을 반환합니다.
     */
    fun allowedEvents(currentState: AppointmentState): Set<Class<out AppointmentEvent>> =
        transitions.keys
            .filter { it.first == currentState }
            .map { it.second }
            .toSet()
}
