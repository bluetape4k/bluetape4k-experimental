package io.bluetape4k.ulid

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.ulid.utils.MaxRandomPart
import io.bluetape4k.ulid.utils.MaxTimestampPart
import io.bluetape4k.ulid.utils.MinRandomPart
import io.bluetape4k.ulid.utils.PastTimestampPart
import io.bluetape4k.ulid.utils.partsOf
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeInRange
import org.amshove.kluent.shouldBeLessOrEqualTo

abstract class AbstractULIDTest {

    companion object: KLogging() {
        const val REPEAT_SIZE = 5
    }

    protected fun assertValidParts(ulidStr: String) {
        log.debug { "random ULID=$ulidStr" }
        ulidStr.length shouldBeEqualTo 26
        val (timePart, randomPart) = partsOf(ulidStr)
        timePart shouldBeGreaterThan PastTimestampPart
        timePart shouldBeLessOrEqualTo MaxTimestampPart
        randomPart shouldBeInRange (MinRandomPart..MaxRandomPart)
    }
}
