package io.bluetape4k.exposed.inet

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
import java.net.InetAddress

object NetworkTable : LongIdTable("networks") {
    val ip = inetAddress("ip")
    val network = cidr("network")
}

class InetColumnTypeTest {

    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        db = Database.connect(
            url = "jdbc:h2:mem:inet_test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(db) {
            SchemaUtils.create(NetworkTable)
        }
    }

    @AfterEach
    fun tearDown() {
        transaction(db) {
            SchemaUtils.drop(NetworkTable)
        }
    }

    @Test
    fun `IPv4 주소 저장 및 조회`() {
        val addr = InetAddress.getByName("192.168.1.1")
        transaction(db) {
            NetworkTable.insert {
                it[ip] = addr
                it[network] = "192.168.0.0/24"
            }

            val row = NetworkTable.selectAll().single()
            val result = row[NetworkTable.ip]
            result.shouldNotBeNull()
            result.hostAddress shouldBeEqualTo "192.168.1.1"
        }
    }

    @Test
    fun `IPv6 주소 저장 및 조회`() {
        val addr = InetAddress.getByName("::1")
        transaction(db) {
            NetworkTable.insert {
                it[ip] = addr
                it[network] = "::1/128"
            }

            val row = NetworkTable.selectAll().single()
            val result = row[NetworkTable.ip]
            result.shouldNotBeNull()
            result shouldBeEqualTo addr
        }
    }

    @Test
    fun `CIDR 문자열 저장 및 조회`() {
        transaction(db) {
            NetworkTable.insert {
                it[ip] = InetAddress.getByName("10.0.0.1")
                it[network] = "10.0.0.0/8"
            }

            val row = NetworkTable.selectAll().single()
            val result = row[NetworkTable.network]
            result shouldBeEqualTo "10.0.0.0/8"
        }
    }
}
