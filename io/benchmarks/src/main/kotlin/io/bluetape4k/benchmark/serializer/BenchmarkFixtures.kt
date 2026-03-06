package io.bluetape4k.benchmark.serializer

enum class PayloadScale(
    val itemCount: Int,
    val tagCount: Int,
    val attributeCount: Int,
    val noteRepeat: Int,
) {
    SMALL(itemCount = 8, tagCount = 3, attributeCount = 2, noteRepeat = 1),
    MEDIUM(itemCount = 24, tagCount = 5, attributeCount = 3, noteRepeat = 3),
    LARGE(itemCount = 96, tagCount = 10, attributeCount = 5, noteRepeat = 8),
}

object BenchmarkFixtures {

    fun samplePayload(scale: PayloadScale = PayloadScale.MEDIUM): BenchmarkPayload =
        BenchmarkPayload().apply {
            orderId = 202603060000L + scale.ordinal + 1
            customerId = "customer-asia-seoul-${scale.name.lowercase()}-0001"
            status = "PAID"
            loyaltyTier = scale.ordinal + 1
            grandTotal = when (scale) {
                PayloadScale.SMALL -> 142.75
                PayloadScale.MEDIUM -> 1_842.75
                PayloadScale.LARGE -> 18_942.75
            }
            createdAtEpochMillis = 1_772_766_733_000L
            shippingAddress = BenchmarkAddress().apply {
                recipient = "Performance Test User"
                line1 = "100 Benchmark Avenue"
                city = "Seoul"
                zipCode = "04524"
                countryCode = "KR"
            }
            lineItems = MutableList(scale.itemCount) { index ->
                BenchmarkLineItem().apply {
                    sku = "SKU-${index.toString().padStart(4, '0')}"
                    quantity = (index % 5) + 1
                    unitPrice = 12.5 + index * 3.75
                    taxable = index % 2 == 0
                    attributes = MutableList(scale.attributeCount) { attributeIndex ->
                        when (attributeIndex) {
                            0 -> "category:${if (index % 3 == 0) "book" else "device"}"
                            1 -> "warehouse:${(index % 4) + 1}"
                            2 -> "campaign:spring-2026"
                            3 -> "fulfillment:${if (index % 2 == 0) "pickup" else "delivery"}"
                            else -> "batch:${scale.name.lowercase()}-${index}-${attributeIndex}"
                        }
                    }
                }
            }
            tags = MutableList(scale.tagCount) { tagIndex ->
                when (tagIndex) {
                    0 -> "priority"
                    1 -> "cross-border"
                    2 -> "fragile"
                    3 -> "recurring-customer"
                    4 -> "benchmark"
                    else -> "tag-${scale.name.lowercase()}-$tagIndex"
                }
            }
            status = "PAID-${scale.name}"
            customerId += "-${"x".repeat(scale.noteRepeat * 8)}"
        }

    fun fingerprint(payload: BenchmarkPayload): String =
        buildString {
            append(payload.orderId)
            append('|')
            append(payload.customerId)
            append('|')
            append(payload.status)
            append('|')
            append(payload.lineItems.size)
            append('|')
            append(payload.tags.joinToString(","))
            append('|')
            append(payload.lineItems.sumOf { it.quantity })
        }
}
