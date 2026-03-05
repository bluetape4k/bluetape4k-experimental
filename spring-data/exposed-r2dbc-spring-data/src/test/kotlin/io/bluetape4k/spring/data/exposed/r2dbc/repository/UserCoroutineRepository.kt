package io.bluetape4k.spring.data.exposed.r2dbc.repository

import io.bluetape4k.spring.data.exposed.r2dbc.domain.UserEntity

/**
 * 테스트용 코루틴 기반 UserEntity Repository.
 * [CoroutineExposedRepository]의 기본 CRUD 메서드만 사용합니다.
 */
interface UserCoroutineRepository : CoroutineExposedRepository<UserEntity, Long>
