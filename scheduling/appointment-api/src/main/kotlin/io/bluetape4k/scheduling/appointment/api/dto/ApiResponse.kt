package io.bluetape4k.scheduling.appointment.api.dto

import java.io.Serializable

/**
 * 통일된 API 응답 형식.
 *
 * 모든 REST API 응답은 이 형식을 사용합니다.
 * 성공 시 `data`에 결과를, 실패 시 `error`에 오류 메시지를 포함합니다.
 *
 * @property success 요청 성공 여부
 * @property data 응답 데이터 (성공 시)
 * @property error 오류 메시지 (실패 시)
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        /**
         * 성공 응답을 생성합니다.
         *
         * @param data 응답 데이터
         * @return 성공 ApiResponse
         */
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        /**
         * 실패 응답을 생성합니다.
         *
         * @param message 오류 메시지
         * @return 실패 ApiResponse
         */
        fun <T> error(message: String): ApiResponse<T> = ApiResponse(success = false, error = message)
    }
}
