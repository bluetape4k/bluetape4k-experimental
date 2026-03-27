package io.bluetape4k.exposed.inet

import io.bluetape4k.testcontainers.database.PostgreSQLServer
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

object PgNetworkTable : LongIdTable("pg_networks") {
    val ip = inetAddress("ip")
    val network = cidr("network")
}

class InetPostgresTest {

    companion object {
        @JvmStatic
        val postgres = PostgreSQLServer.Launcher.postgres
    }

    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        db = Database.connect(
            url = postgres.jdbcUrl,
            driver = PostgreSQLServer.DRIVER_CLASS_NAME,
            user = postgres.username.orEmpty(),
            password = postgres.password.orEmpty(),
        )
        transaction(db) {
            SchemaUtils.create(PgNetworkTable)
        }
    }

    @AfterEach
    fun tearDown() {
        transaction(db) {
            SchemaUtils.drop(PgNetworkTable)
        }
    }

    @Test
    fun `PostgreSQL INET 네이티브 타입으로 IPv4 저장 및 조회`() {
        val addr = InetAddress.getByName("192.168.1.100")
        transaction(db) {
            PgNetworkTable.insert {
                it[ip] = addr
                it[network] = "192.168.1.0/24"
            }

            val row = PgNetworkTable.selectAll().single()
            val result = row[PgNetworkTable.ip]
            result.shouldNotBeNull()
            result.hostAddress shouldBeEqualTo "192.168.1.100"
        }
    }

    @Test
    fun `PostgreSQL INET 네이티브 타입으로 IPv6 저장 및 조회`() {
        val addr = InetAddress.getByName("2001:db8::1")
        transaction(db) {
            PgNetworkTable.insert {
                it[ip] = addr
                it[network] = "2001:db8::/32"
            }

            val row = PgNetworkTable.selectAll().single()
            val result = row[PgNetworkTable.ip]
            result.shouldNotBeNull()
            result shouldBeEqualTo addr
        }
    }

    @Test
    fun `PostgreSQL CIDR 네이티브 타입으로 네트워크 저장 및 조회`() {
        transaction(db) {
            PgNetworkTable.insert {
                it[ip] = InetAddress.getByName("10.1.1.5")
                it[network] = "10.0.0.0/8"
            }

            val row = PgNetworkTable.selectAll().single()
            val result = row[PgNetworkTable.network]
            result shouldBeEqualTo "10.0.0.0/8"
        }
    }
}
