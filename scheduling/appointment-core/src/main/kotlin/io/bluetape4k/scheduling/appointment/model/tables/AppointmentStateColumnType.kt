package io.bluetape4k.scheduling.appointment.model.tables

import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/**
 * 예약 상태를 SQL VARCHAR(30) 컬럼에 저장/로드하는 커스텀 컬럼 타입.
 *
 * [AppointmentState]의 이름(PENDING, REQUESTED 등)을 문자열로 저장하고,
 * 조회할 때 자동으로 [AppointmentState]로 변환합니다.
 */
class AppointmentStateColumnType : ColumnType<AppointmentState>() {
    override fun sqlType(): String = "VARCHAR(30)"

    override fun valueFromDB(value: Any): AppointmentState = when (value) {
        is String -> AppointmentState.fromName(value)
        is AppointmentState -> value
        else -> error("Cannot convert $value to AppointmentState")
    }

    override fun notNullValueToDB(value: AppointmentState): Any = value.name
}

/**
 * 테이블에 [AppointmentState] 컬럼을 등록합니다.
 *
 * @param name 컬럼 이름
 * @return [AppointmentStateColumnType]으로 등록된 컬럼
 */
fun Table.appointmentState(name: String): Column<AppointmentState> =
    registerColumn(name, AppointmentStateColumnType())
