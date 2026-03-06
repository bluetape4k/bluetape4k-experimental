package io.bluetape4k.examples.benchmark.dto

data class CreateAuthorRequest(
    val name: String = "",
    val email: String = "",
    val books: List<BookDto> = emptyList(),
)
