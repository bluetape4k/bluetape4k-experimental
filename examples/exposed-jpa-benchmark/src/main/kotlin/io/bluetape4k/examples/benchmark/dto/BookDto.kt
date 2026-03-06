package io.bluetape4k.examples.benchmark.dto

import java.math.BigDecimal

data class BookDto(
    val id: Long? = null,
    val title: String = "",
    val isbn: String = "",
    val price: BigDecimal = BigDecimal.ZERO,
)
