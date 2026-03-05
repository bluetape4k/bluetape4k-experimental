package io.bluetape4k.examples.exposed.webflux.repository

import io.bluetape4k.examples.exposed.webflux.domain.ProductEntity
import io.bluetape4k.spring.data.exposed.r2dbc.repository.CoroutineExposedRepository

interface ProductCoroutineRepository : CoroutineExposedRepository<ProductEntity, Long>
