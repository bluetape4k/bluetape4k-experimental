package io.bluetape4k.spring.data.exposed.r2dbc.repository

/**
 * 식별자를 갖는 도메인 객체 계약입니다.
 *
 * 신규 객체는 [id]가 `null`일 수 있으며, 저장 이후 식별자가 채워질 수 있습니다.
 */
interface HasIdentifier<ID : Any> {
    val id: ID?
}
