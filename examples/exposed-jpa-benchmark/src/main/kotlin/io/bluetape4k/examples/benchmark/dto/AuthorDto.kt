package io.bluetape4k.examples.benchmark.dto

data class AuthorDto(
    val id: Long? = null,
    val name: String = "",
    val email: String = "",
    val books: List<BookDto> = emptyList(),
)
