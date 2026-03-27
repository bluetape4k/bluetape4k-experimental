package io.bluetape4k.exposed.postgis

import net.postgis.jdbc.geometry.Point
import net.postgis.jdbc.geometry.Polygon
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect

/**
 * PostGIS POINT 컬럼을 테이블에 등록한다.
 *
 * @param name 컬럼 이름
 * @return [Point] 타입의 [Column]
 */
fun Table.geoPoint(name: String): Column<Point> =
    registerColumn(name, GeoPointColumnType())

/**
 * PostGIS POLYGON 컬럼을 테이블에 등록한다.
 *
 * @param name 컬럼 이름
 * @return [Polygon] 타입의 [Column]
 */
fun Table.geoPolygon(name: String): Column<Polygon> =
    registerColumn(name, GeoPolygonColumnType())

/**
 * PostGIS `ST_Distance` 함수를 사용하여 두 geometry 간 거리를 계산한다.
 *
 * PostgreSQL(PostGIS) dialect 전용.
 *
 * @param other 비교 대상 geometry 컬럼
 * @return 거리 표현식
 * @throws IllegalStateException PostgreSQL이 아닌 dialect에서 호출 시
 */
fun Column<Point>.stDistance(other: Column<Point>): Expression<Double> {
    check(currentDialect is PostgreSQLDialect) {
        "stDistance 는 PostgreSQL(PostGIS) dialect 에서만 지원됩니다."
    }
    return StDistanceExpr(this, other)
}

/**
 * PostGIS `ST_DWithin` 함수를 사용하여 두 geometry가 지정 거리 이내인지 확인한다.
 *
 * PostgreSQL(PostGIS) dialect 전용.
 *
 * @param other 비교 대상 geometry 컬럼
 * @param distance 최대 거리 (도 단위, SRID 4326 기준)
 * @return 거리 조건 [Op]
 * @throws IllegalStateException PostgreSQL이 아닌 dialect에서 호출 시
 */
fun Column<Point>.stDWithin(other: Column<Point>, distance: Double): Op<Boolean> {
    check(currentDialect is PostgreSQLDialect) {
        "stDWithin 는 PostgreSQL(PostGIS) dialect 에서만 지원됩니다."
    }
    return StDWithinOp(this, other, distance)
}

/**
 * PostGIS `ST_Within` 함수를 사용하여 point가 polygon 내부에 있는지 확인한다.
 *
 * PostgreSQL(PostGIS) dialect 전용.
 *
 * @param polygon 폴리곤 컬럼
 * @return 포함 여부 [Op]
 * @throws IllegalStateException PostgreSQL이 아닌 dialect에서 호출 시
 */
fun Column<Point>.stWithin(polygon: Column<Polygon>): Op<Boolean> {
    check(currentDialect is PostgreSQLDialect) {
        "stWithin 는 PostgreSQL(PostGIS) dialect 에서만 지원됩니다."
    }
    return StWithinOp(this, polygon)
}

/**
 * PostGIS `ST_Distance(left, right)` SQL 표현식.
 */
class StDistanceExpr(
    private val left: Expression<Point>,
    private val right: Expression<Point>,
) : Expression<Double>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("ST_Distance(")
        queryBuilder.append(left)
        queryBuilder.append(", ")
        queryBuilder.append(right)
        queryBuilder.append(")")
    }
}

/**
 * PostGIS `ST_DWithin(left, right, distance)` SQL 표현식.
 */
class StDWithinOp(
    private val left: Expression<Point>,
    private val right: Expression<Point>,
    private val distance: Double,
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("ST_DWithin(")
        queryBuilder.append(left)
        queryBuilder.append(", ")
        queryBuilder.append(right)
        queryBuilder.append(", $distance)")
    }
}

/**
 * PostGIS `ST_Within(point, polygon)` SQL 표현식.
 */
class StWithinOp(
    private val point: Expression<Point>,
    private val polygon: Expression<Polygon>,
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("ST_Within(")
        queryBuilder.append(point)
        queryBuilder.append(", ")
        queryBuilder.append(polygon)
        queryBuilder.append(")")
    }
}
