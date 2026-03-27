package io.bluetape4k.exposed.phone

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

object ContactTable : LongIdTable("contacts") {
    val phone = phoneNumber("phone")
    val phoneStr = phoneNumberString("phone_str")
}

class PhoneNumberColumnTypeTest {

    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        db = Database.connect(
            url = "jdbc:h2:mem:phone_test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(db) {
            SchemaUtils.create(ContactTable)
        }
    }

    @AfterEach
    fun tearDown() {
        transaction(db) {
            SchemaUtils.drop(ContactTable)
        }
    }

    @Test
    fun `한국 번호 E164 정규화 저장 및 조회`() {
        transaction(db) {
            ContactTable.insert {
                it[phone] = PhoneNumberUtil.getInstance().parse("010-1234-5678", "KR")
                it[phoneStr] = "010-1234-5678"
            }

            val row = ContactTable.selectAll().single()
            val phoneNumber = row[ContactTable.phone]
            phoneNumber.shouldNotBeNull()

            val e164 = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
            e164 shouldBeEqualTo "+821012345678"
        }
    }

    @Test
    fun `미국 번호 파싱 및 저장`() {
        transaction(db) {
            ContactTable.insert {
                it[phone] = PhoneNumberUtil.getInstance().parse("+1-650-253-0000", "US")
                it[phoneStr] = "+1-650-253-0000"
            }

            val row = ContactTable.selectAll().single()
            val phoneNumber = row[ContactTable.phone]
            phoneNumber.shouldNotBeNull()

            val e164 = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
            e164 shouldBeEqualTo "+16502530000"
        }
    }

    @Test
    fun `잘못된 번호 입력 시 예외 발생`() {
        // PhoneNumberStringColumnType.notNullValueToDB() 에서 파싱 → IllegalArgumentException 발생
        assertFailsWith<IllegalArgumentException> {
            transaction(db) {
                ContactTable.insert {
                    it[phone] = PhoneNumberUtil.getInstance().parse("+821012345678", "KR")
                    it[phoneStr] = "invalid-number"
                }
            }
        }
    }

    @Test
    fun `phoneNumberString 컬럼 E164 정규화`() {
        transaction(db) {
            ContactTable.insert {
                it[phone] = PhoneNumberUtil.getInstance().parse("010-9999-8888", "KR")
                it[phoneStr] = "010-9999-8888"
            }

            val row = ContactTable.selectAll().single()
            val phoneStr = row[ContactTable.phoneStr]
            phoneStr shouldBeEqualTo "+821099998888"
        }
    }
}
