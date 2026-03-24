package io.bluetape4k.graph.examples.linkedin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.examples.linkedin.service.LinkedInGraphService
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LinkedInGraphTest {

    companion object {
        private val server = PostgreSQLAgeServer.instance
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var ops: AgeGraphOperations
    private lateinit var service: LinkedInGraphService

    @BeforeAll
    fun startServer() {
        server.start()
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        database = Database.connect(dataSource)
        ops = AgeGraphOperations(database, "linkedin_test")
        service = LinkedInGraphService(ops, "linkedin_test")
    }

    @AfterAll
    fun stopServer() {
        dataSource.close()
        server.stop()
    }

    @BeforeEach
    fun setupGraph() {
        if (ops.graphExists("linkedin_test")) {
            ops.dropGraph("linkedin_test")
        }
        service.initialize()
    }

    @Test
    fun `사람 추가 및 인맥 연결`() {
        val alice = service.addPerson("Alice", "Software Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Product Manager", "StartupXYZ", "Busan")

        alice.id.shouldNotBeNull()
        bob.id.shouldNotBeNull()

        service.connect(alice.id, bob.id, since = "2023-01-01", strength = 8)

        val connections = service.getDirectConnections(alice.id)
        connections.shouldNotBeEmpty()
    }

    @Test
    fun `최단 인맥 경로 탐색 - 6단계 분리`() {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")
        val dave = service.addPerson("Dave", "CTO", "D", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)
        service.connect(carol.id, dave.id)

        val path = service.findConnectionPath(alice.id, dave.id)

        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
    }

    @Test
    fun `2촌 인맥 탐색`() {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)

        val secondDegree = service.getConnectionsWithinDegree(alice.id, degree = 2)
        secondDegree.shouldNotBeEmpty()
    }

    @Test
    fun `회사 추가 및 재직자 조회`() {
        val alice = service.addPerson("Alice", "Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Designer", "TechCorp", "Seoul")
        val techCorp = service.addCompany("TechCorp", "Technology", "Seoul")

        service.addWorkExperience(alice.id, techCorp.id, "Software Engineer", isCurrent = true)
        service.addWorkExperience(bob.id, techCorp.id, "Designer", isCurrent = true)

        val employees = service.findEmployees(techCorp.id)
        employees.shouldNotBeEmpty()
    }

    @Test
    fun `팔로우 관계 생성`() {
        val alice = service.addPerson("Alice", "Influencer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Fan", "B", "Seoul")

        service.follow(bob.id, alice.id)

        val followers = ops.neighbors(
            alice.id,
            NeighborOptions(edgeLabel = "FOLLOWS", direction = Direction.INCOMING, maxDepth = 1)
        )
        followers.shouldNotBeEmpty()
    }
}
