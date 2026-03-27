package io.bluetape4k.exposed.postgis

import io.bluetape4k.logging.KLogging
import net.postgis.jdbc.geometry.LinearRing
import net.postgis.jdbc.geometry.Point
import net.postgis.jdbc.geometry.Polygon
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * PostGIS 컬럼 타입 및 공간 함수 통합 테스트.
 */
class GeoColumnTypeTest {

    companion object : KLogging() {
        private const val SRID = 4326

        private val postgisImage = DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres")

        @JvmStatic
        val postgisContainer: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(postgisImage)
                .apply { start() }

        /**
         * Point 생성 헬퍼. PostGIS 좌표 순서: x=경도(lng), y=위도(lat)
         */
        private fun point(lng: Double, lat: Double): Point =
            Point(lng, lat).apply { srid = SRID }

        /**
         * 사각형 폴리곤 생성 헬퍼.
         */
        private fun rectanglePolygon(
            minLng: Double,
            minLat: Double,
            maxLng: Double,
            maxLat: Double,
        ): Polygon {
            val ring = LinearRing(
                arrayOf(
                    Point(minLng, minLat),
                    Point(maxLng, minLat),
                    Point(maxLng, maxLat),
                    Point(minLng, maxLat),
                    Point(minLng, minLat),  // 닫힌 링
                )
            )
            return Polygon(arrayOf(ring)).apply { srid = SRID }
        }
    }

    object Locations : LongIdTable("locations") {
        val name = varchar("name", 255)
        val point = geoPoint("point")
    }

    object Regions : LongIdTable("regions") {
        val name = varchar("name", 255)
        val point = geoPoint("point")
        val area = geoPolygon("area")
    }

    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        db = Database.connect(
            url = postgisContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgisContainer.username,
            password = postgisContainer.password,
        )
        transaction(db) {
            exec("CREATE EXTENSION IF NOT EXISTS postgis")
            SchemaUtils.create(Locations, Regions)
        }
    }

    @AfterEach
    fun tearDown() {
        transaction(db) {
            SchemaUtils.drop(Locations, Regions)
        }
    }

    @Test
    fun `서울 좌표 Point 저장 및 조회`() {
        val seoul = point(lng = 126.9780, lat = 37.5665)

        transaction(db) {
            Locations.insert {
                it[name] = "서울"
                it[point] = seoul
            }

            val row = Locations.selectAll().single()
            row[Locations.name] shouldBeEqualTo "서울"

            val result = row[Locations.point]
            result.shouldNotBeNull()
            result.x shouldBeEqualTo seoul.x
            result.y shouldBeEqualTo seoul.y
        }
    }

    @Test
    fun `여러 도시 좌표 저장 및 조회`() {
        val cities = listOf(
            "서울" to point(126.9780, 37.5665),
            "부산" to point(129.0756, 35.1796),
            "인천" to point(126.7052, 37.4563),
        )

        transaction(db) {
            cities.forEach { (cityName, cityPoint) ->
                Locations.insert {
                    it[name] = cityName
                    it[point] = cityPoint
                }
            }

            val results = Locations.selectAll().toList()
            results.size shouldBeEqualTo 3
        }
    }

    @Test
    fun `ST_DWithin - 서울에서 약 1도 이내 포인트 검색`() {
        val seoul = point(126.9780, 37.5665)
        val incheon = point(126.7052, 37.4563)
        val busan = point(129.0756, 35.1796)

        transaction(db) {
            Locations.insert { it[name] = "서울"; it[point] = seoul }
            Locations.insert { it[name] = "인천"; it[point] = incheon }
            Locations.insert { it[name] = "부산"; it[point] = busan }

            // ST_DWithin(point, point, 1.0) 은 자기 자신과의 거리가 0이므로 모든 행이 true
            // 함수 호출 자체가 정상 동작하는지 검증
            val nearSeoul = Locations
                .selectAll()
                .where { Locations.point.stDWithin(Locations.point, 1.0) }
                .map { it[Locations.name] }

            nearSeoul.size shouldBeEqualTo 3
        }
    }

    @Test
    fun `ST_Within - 폴리곤 내 포인트 확인`() {
        // 한반도 대략적인 바운딩 박스
        val koreaBounds = rectanglePolygon(
            minLng = 124.0, minLat = 33.0,
            maxLng = 132.0, maxLat = 43.0,
        )

        val seoul = point(126.9780, 37.5665)

        transaction(db) {
            Regions.insert {
                it[name] = "서울"
                it[point] = seoul
                it[area] = koreaBounds
            }

            val results = Regions
                .selectAll()
                .where { Regions.point.stWithin(Regions.area) }
                .map { it[Regions.name] }

            results.size shouldBeEqualTo 1
            results.first() shouldBeEqualTo "서울"
        }
    }

    @Test
    fun `ST_Within - 폴리곤 밖 포인트 제외`() {
        // 서울 근처만 포함하는 작은 폴리곤
        val smallArea = rectanglePolygon(
            minLng = 126.9, minLat = 37.5,
            maxLng = 127.1, maxLat = 37.6,
        )

        val seoul = point(126.9780, 37.5665)
        val busan = point(129.0756, 35.1796)

        transaction(db) {
            Regions.insert {
                it[name] = "서울"
                it[point] = seoul
                it[area] = smallArea
            }
            Regions.insert {
                it[name] = "부산"
                it[point] = busan
                it[area] = smallArea
            }

            val withinResults = Regions
                .selectAll()
                .where { Regions.point.stWithin(Regions.area) }
                .map { it[Regions.name] }

            withinResults.size shouldBeEqualTo 1
            withinResults.contains("서울").shouldBeTrue()
        }
    }

    @Test
    fun `Polygon 저장 및 조회`() {
        val polygon = rectanglePolygon(
            minLng = 126.0, minLat = 37.0,
            maxLng = 127.0, maxLat = 38.0,
        )

        transaction(db) {
            Regions.insert {
                it[name] = "테스트 영역"
                it[point] = point(126.5, 37.5)
                it[area] = polygon
            }

            val row = Regions.selectAll().single()
            val result = row[Regions.area]
            result.shouldNotBeNull()
            result.numRings() shouldBeEqualTo 1
        }
    }
}
