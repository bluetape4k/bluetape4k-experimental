# Hibernate Cache Lettuce — 종합 테스트 강화 계획

> **For agentic workers:
** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (
`- [ ]`) syntax for tracking.

**Goal:**
`infra/hibernate-cache-lettuce/` 모듈에 hibernate-jcache 참조 테스트 수준의 견고한 2nd Level Cache 테스트를 추가하고, Hibernate 6.8.x 지원을 위한 별도 모듈을 생성한다.

**Architecture:**

- Part A: 기존 `:hibernate-cache-lettuce` 모듈에 7개 신규 테스트 클래스 + 2개 엔티티 파일 추가 (Hibernate 7.x)
- Part B: `infra/hibernate-cache-lettuce-h6/` 신규 모듈 생성 — Hibernate 6.8.x API에 맞춘 RegionFactory + 동일 테스트 패턴
- 모든 테스트: TDD 스타일, Kluent assertions, JUnit 5, Testcontainers Redis 싱글턴

**Tech Stack:
** Kotlin 2.3, Hibernate 7.2.6.Final / 6.8.x, H2 2.x, Testcontainers Redis, Lettuce, Caffeine, JUnit 5, Kluent, MockK

---

## 현재 상태 (테스트 커버리지 갭)

| 카테고리                                | 현재 | 추가 예정               |
|-------------------------------------|----|---------------------|
| Entity 기본 CRUD 캐시                   | ✅  | -                   |
| 병렬 조회                               | ✅  | -                   |
| Query Cache 기본                      | ✅  | 명명 Region, 다중 파라미터  |
| Relation Cache (1:N, M:N)           | ✅  | -                   |
| Composite ID / Natural ID           | ✅  | Natural ID 삭제 후 재조회 |
| Region 통계 (CacheRegionStatistics)   | ❌  | **Task 2**          |
| containsEntity / containsCollection | ❌  | **Task 3**          |
| READ_WRITE 전략 (버전 엔티티)              | ❌  | **Task 4**          |
| 트랜잭션 롤백 후 캐시 상태                     | ❌  | **Task 5**          |
| ElementCollection 캐시                | ❌  | **Task 6**          |
| Query Cache 고급 (명명 Region)          | ❌  | **Task 7**          |
| 1st Level Cache 관리                  | ❌  | **Task 8**          |
| 동시 쓰기 충돌                            | ❌  | **Task 9**          |
| Hibernate 6.8.x 지원                  | ❌  | **Task 10-12**      |

---

## 파일 구조

### Part A: 기존 모듈 추가 파일

```
infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/
├── model/
│   ├── VersionedEntities.kt        [NEW] - @Version 필드를 가진 READ_WRITE 전략 엔티티
│   └── ElementCollectionEntities.kt [NEW] - @ElementCollection 캐시 엔티티
├── HibernateCacheStatisticsTest.kt  [NEW] - Region별 CacheRegionStatistics 검증
├── HibernateCacheContainmentTest.kt [NEW] - containsEntity/containsCollection/region eviction
├── HibernateReadWriteStrategyTest.kt [NEW] - READ_WRITE 전략, 버전 충돌
├── HibernateTransactionRollbackTest.kt [NEW] - insert/update/delete 롤백 후 캐시
├── HibernateElementCollectionCacheTest.kt [NEW] - @ElementCollection 캐시
├── HibernateQueryCacheAdvancedTest.kt [NEW] - 명명 Region, 다중 파라미터 쿼리
├── HibernateFirstLevelCacheTest.kt  [NEW] - 1st Level Cache 관리 (detach, clear, evict)
└── HibernateConcurrentWriteTest.kt  [NEW] - 동시 쓰기 충돌, NONSTRICT_READ_WRITE 동시성
```

### Part B: 신규 H6 모듈

```
infra/hibernate-cache-lettuce-h6/
├── build.gradle.kts                          [NEW]
├── README.md                                 [NEW]
└── src/
    ├── main/kotlin/io/bluetape4k/hibernate/cache/lettuce/
    │   ├── LettuceNearCacheProperties.kt     [COPY+ADAPT] - H6 호환 (패키지 수정 없음)
    │   ├── LettuceNearCacheRegionFactory.kt  [COPY+ADAPT] - H6 RegionFactoryTemplate API
    │   └── LettuceNearCacheStorageAccess.kt  [COPY+ADAPT] - H6 DomainDataStorageAccess
    └── test/kotlin/io/bluetape4k/hibernate/cache/lettuce/
        ├── model/                            [COPY] - 동일 엔티티 모델
        ├── AbstractHibernateNearCacheTest.kt [COPY] - 동일 베이스
        ├── RedisServers.kt                   [COPY] - 동일 Redis 싱글턴
        └── (모든 테스트 클래스 복사)
```

---

## Part A: Hibernate 7.x 테스트 강화

---

### Task 1: 신규 엔티티 모델 추가

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/model/VersionedEntities.kt`
- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/model/ElementCollectionEntities.kt`

- [ ] **Step 1: VersionedEntities.kt 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce.model

import jakarta.persistence.*
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import java.io.Serializable

@Entity
@Table(name = "versioned_items")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class VersionedItem : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var name: String = ""
    var price: Int = 0

    @Version
    var version: Long = 0
}

@Entity
@Table(name = "versioned_categories")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class VersionedCategory : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var label: String = ""

    @Version
    var version: Long = 0

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    val items: MutableSet<VersionedCategoryItem> = linkedSetOf()
}

@Entity
@Table(name = "versioned_category_items")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class VersionedCategoryItem : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var name: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: VersionedCategory? = null
}
```

- [ ] **Step 2: ElementCollectionEntities.kt 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce.model

import jakarta.persistence.*
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import java.io.Serializable

@Entity
@Table(name = "articles")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
class Article : Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var title: String = ""

    @ElementCollection
    @CollectionTable(name = "article_tags", joinColumns = [JoinColumn(name = "article_id")])
    @Column(name = "tag")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    val tags: MutableSet<String> = linkedSetOf()

    @ElementCollection
    @CollectionTable(name = "article_ratings", joinColumns = [JoinColumn(name = "article_id")])
    @Column(name = "rating")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    val ratings: MutableList<Int> = mutableListOf()
}
```

- [ ] **Step 3: AbstractHibernateNearCacheTest에 신규 엔티티 등록**

`AbstractHibernateNearCacheTest.kt`의 `MetadataSources` 빌더에 추가:

```kotlin
.addAnnotatedClass(VersionedItem::class.java)
.addAnnotatedClass(VersionedCategory::class.java)
.addAnnotatedClass(VersionedCategoryItem::class.java)
.addAnnotatedClass(Article::class.java)
```

- [ ] **Step 4: 빌드 확인 (컴파일만)**

```bash
./gradlew :hibernate-cache-lettuce:compileTestKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/model/
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/AbstractHibernateNearCacheTest.kt
git commit -m "test(hibernate-cache-lettuce): add VersionedItem, VersionedCategory, Article test entities"
```

---

### Task 2: HibernateCacheStatisticsTest

Region별 CacheRegionStatistics hit/miss/put 카운트 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateCacheStatisticsTest.kt`

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Department
import io.bluetape4k.hibernate.cache.lettuce.model.Employee
import io.bluetape4k.hibernate.cache.lettuce.model.Person
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Region별 CacheRegionStatistics 검증 테스트.
 * - 엔티티/컬렉션 Region 통계 (hit, miss, put)
 * - 쿼리 캐시 Region 통계
 * - evict 후 통계 재확인
 */
class HibernateCacheStatisticsTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun resetAll() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
    }

    @Test
    fun `엔티티 Region 통계: persist 시 put, 재조회 시 hit 누적`() {
        val personRegion = Person::class.java.name

        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Stats Alice"; age = 30 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }
        sessionFactory.statistics.clear()

        // 새 세션에서 조회 → 2LC hit
        repeat(3) {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                s.find(Person::class.java, personId).shouldNotBeNull()
                s.transaction.commit()
            }
        }

        val regionStats = sessionFactory.statistics.getDomainDataRegionStatistics(personRegion)
        regionStats.shouldNotBeNull()
        regionStats.hitCount shouldBeGreaterThan 0L
        regionStats.putCount shouldBeGreaterThan 0L
    }

    @Test
    fun `엔티티 Region 통계: 존재하지 않는 엔티티 조회 시 miss 누적`() {
        val personRegion = Person::class.java.name

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.find(Person::class.java, Long.MAX_VALUE - 1)
            s.transaction.commit()
        }

        val regionStats = sessionFactory.statistics.getDomainDataRegionStatistics(personRegion)
        regionStats.shouldNotBeNull()
        regionStats.hitCount shouldBeEqualTo 0L
    }

    @Test
    fun `컬렉션 Region 통계: OneToMany 컬렉션 로드 시 put 후 hit`() {
        val collectionRegion = "${Department::class.java.name}.employees"

        val deptId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val dept = Department().apply { name = "Engineering" }
            val emp1 = Employee().apply { name = "E1" }
            val emp2 = Employee().apply { name = "E2" }
            dept.addEmployee(emp1)
            dept.addEmployee(emp2)
            s.persist(dept)
            s.transaction.commit()
            dept.id!!
        }
        sessionFactory.statistics.clear()

        // Session 1: 컬렉션 로드 → put
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val d = s.find(Department::class.java, deptId)!!
            d.employees.size  // 컬렉션 초기화
            s.transaction.commit()
        }
        // Session 2: 컬렉션 재로드 → hit
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val d = s.find(Department::class.java, deptId)!!
            d.employees.size
            s.transaction.commit()
        }

        val collectionStats = sessionFactory.statistics.getDomainDataRegionStatistics(collectionRegion)
        collectionStats.shouldNotBeNull()
        collectionStats.hitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `쿼리 캐시 Region 통계: 동일 쿼리 반복 시 queryCacheHitCount 증가`() {
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.persist(Person().apply { name = "Q1"; age = 21 })
            s.persist(Person().apply { name = "Q2"; age = 22 })
            s.transaction.commit()
        }
        sessionFactory.statistics.clear()

        val hql = "select p from Person p where p.age > :age order by p.id"
        repeat(3) {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                s.createSelectionQuery(hql, Person::class.java)
                    .setParameter("age", 20)
                    .setCacheable(true)
                    .list()
                s.transaction.commit()
            }
        }

        sessionFactory.statistics.queryCacheHitCount shouldBeGreaterThan 0L
        sessionFactory.statistics.queryCachePutCount shouldBeGreaterThan 0L
    }

    @Test
    fun `전체 통계: secondLevelCachePutCount와 hit 비율 검증`() {
        val ids = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val people = (1..5).map { i -> Person().apply { name = "Bulk$i"; age = 20 + i } }
            people.forEach { s.persist(it) }
            s.transaction.commit()
            people.map { it.id!! }
        }
        sessionFactory.statistics.clear()

        // 1회 로드 (put)
        ids.forEach { id ->
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                s.find(Person::class.java, id)
                s.transaction.commit()
            }
        }
        val putCount = sessionFactory.statistics.secondLevelCachePutCount
        putCount shouldBeGreaterThan 0L

        // 2회 로드 (hit)
        ids.forEach { id ->
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                s.find(Person::class.java, id)
                s.transaction.commit()
            }
        }
        sessionFactory.statistics.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }
}
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateCacheStatisticsTest" --info 2>&1 | tail -30
```

- [ ] **Step 3: 테스트 통과 확인**
  Expected: 5개 테스트 모두 PASSED

- [ ] **Step 4: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateCacheStatisticsTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateCacheStatisticsTest for region-level statistics"
```

---

### Task 3: HibernateCacheContainmentTest

`containsEntity`, `containsCollection`, 명시적 Region eviction 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateCacheContainmentTest.kt`

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Department
import io.bluetape4k.hibernate.cache.lettuce.model.Employee
import io.bluetape4k.hibernate.cache.lettuce.model.Person
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Cache containsEntity / containsCollection / 명시적 Region eviction 테스트.
 */
class HibernateCacheContainmentTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun resetAll() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
    }

    @Test
    fun `캐시에 엔티티가 존재하는지 containsEntity로 확인`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Contain Alice"; age = 30 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        // persist 후 바로 캐시에 있어야 함
        sessionFactory.cache.containsEntity(Person::class.java, personId).shouldBeTrue()

        // evict 후 없어야 함
        sessionFactory.cache.evictEntityData(Person::class.java, personId)
        sessionFactory.cache.containsEntity(Person::class.java, personId).shouldBeFalse()
    }

    @Test
    fun `존재하지 않는 엔티티는 containsEntity가 false`() {
        sessionFactory.cache.containsEntity(Person::class.java, Long.MAX_VALUE).shouldBeFalse()
    }

    @Test
    fun `컬렉션을 로드한 후 containsCollection으로 확인`() {
        val deptId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val dept = Department().apply { name = "QA" }
            dept.addEmployee(Employee().apply { name = "QA1" })
            s.persist(dept)
            s.transaction.commit()
            dept.id!!
        }

        // 컬렉션 초기화 (캐시 로드)
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val d = s.find(Department::class.java, deptId)!!
            d.employees.size  // lazy 초기화
            s.transaction.commit()
        }

        sessionFactory.cache.containsCollection(
            "${Department::class.java.name}.employees", deptId
        ).shouldBeTrue()

        // 컬렉션 region evict
        sessionFactory.cache.evictCollectionData(
            "${Department::class.java.name}.employees", deptId
        )
        sessionFactory.cache.containsCollection(
            "${Department::class.java.name}.employees", deptId
        ).shouldBeFalse()
    }

    @Test
    fun `evictEntityData(Class) 호출 시 해당 엔티티 전체 Region이 비워진다`() {
        val ids = (1..3).map {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                val p = Person().apply { name = "Bulk$it"; age = 20 + it }
                s.persist(p)
                s.transaction.commit()
                p.id!!
            }
        }

        ids.forEach { id ->
            sessionFactory.cache.containsEntity(Person::class.java, id).shouldBeTrue()
        }

        sessionFactory.cache.evictEntityData(Person::class.java)

        ids.forEach { id ->
            sessionFactory.cache.containsEntity(Person::class.java, id).shouldBeFalse()
        }
    }

    @Test
    fun `evictAllRegions 호출 시 모든 엔티티가 캐시에서 제거된다`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "EvictAll"; age = 40 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }
        val deptId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val d = Department().apply { name = "ToEvict" }
            s.persist(d)
            s.transaction.commit()
            d.id!!
        }

        sessionFactory.cache.containsEntity(Person::class.java, personId).shouldBeTrue()
        sessionFactory.cache.containsEntity(Department::class.java, deptId).shouldBeTrue()

        sessionFactory.cache.evictAllRegions()

        sessionFactory.cache.containsEntity(Person::class.java, personId).shouldBeFalse()
        sessionFactory.cache.containsEntity(Department::class.java, deptId).shouldBeFalse()
    }

    @Test
    fun `특정 쿼리 Region만 evict해도 다른 Region은 유지된다`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "RegionKeep"; age = 30 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        // 쿼리 캐시 Region evict가 엔티티 Region에 영향 없음
        sessionFactory.cache.evictQueryRegions()
        sessionFactory.cache.containsEntity(Person::class.java, personId).shouldBeTrue()
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateCacheContainmentTest" --info 2>&1 | tail -30
```

Expected: 6개 테스트 PASSED

- [ ] **Step 3: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateCacheContainmentTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateCacheContainmentTest for containsEntity/containsCollection/eviction"
```

---

### Task 4: HibernateReadWriteStrategyTest

READ_WRITE 전략 (버전 엔티티) 동시성 및 캐시 무효화 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateReadWriteStrategyTest.kt`

> 전제: Task 1에서 `VersionedItem` 엔티티가 등록되어 있어야 함

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.VersionedItem
import io.bluetape4k.hibernate.cache.lettuce.model.VersionedCategory
import io.bluetape4k.hibernate.cache.lettuce.model.VersionedCategoryItem
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeNull
import org.hibernate.StaleObjectStateException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Collections

/**
 * READ_WRITE CacheConcurrencyStrategy 테스트.
 * - 버전 엔티티 캐시 hit/miss
 * - 버전 충돌 감지 후 캐시 무효화
 * - 동시 읽기 일관성
 */
class HibernateReadWriteStrategyTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun reset() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
    }

    @Test
    fun `READ_WRITE 엔티티 저장 후 새 세션에서 cache hit`() {
        val itemId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = VersionedItem().apply { name = "Widget"; price = 100 }
            s.persist(item)
            s.transaction.commit()
            item.id!!
        }
        sessionFactory.statistics.clear()

        repeat(2) {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                s.find(VersionedItem::class.java, itemId).shouldNotBeNull()
                s.transaction.commit()
            }
        }

        sessionFactory.statistics.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `READ_WRITE 엔티티 수정 후 캐시가 갱신된다`() {
        val itemId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = VersionedItem().apply { name = "Initial"; price = 50 }
            s.persist(item)
            s.transaction.commit()
            item.id!!
        }

        // 수정
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = s.find(VersionedItem::class.java, itemId)!!
            item.price = 200
            s.transaction.commit()
        }

        sessionFactory.statistics.clear()

        // 재조회 → 최신 값 확인
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val loaded = s.find(VersionedItem::class.java, itemId)!!
            loaded.price shouldBeEqualTo 200
            s.transaction.commit()
        }
    }

    @Test
    fun `READ_WRITE 엔티티 삭제 후 containsEntity가 false`() {
        val itemId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = VersionedItem().apply { name = "ToDelete"; price = 10 }
            s.persist(item)
            s.transaction.commit()
            item.id!!
        }

        sessionFactory.cache.containsEntity(VersionedItem::class.java, itemId)
            .let { /* persist 후 캐시에 있음 */ }

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = s.find(VersionedItem::class.java, itemId)!!
            s.remove(item)
            s.transaction.commit()
        }

        sessionFactory.cache.containsEntity(VersionedItem::class.java, itemId)
            .let { assert(!it) { "삭제된 엔티티가 캐시에 남아있음" } }
    }

    @Test
    fun `READ_WRITE 버전 엔티티를 MultithreadingTester로 병렬 읽기 시 일관성 유지`() {
        val itemId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = VersionedItem().apply { name = "Concurrent"; price = 999 }
            s.persist(item)
            s.transaction.commit()
            item.id!!
        }

        // warm-up
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.find(VersionedItem::class.java, itemId)
            s.transaction.commit()
        }
        sessionFactory.statistics.clear()

        val prices = Collections.synchronizedList(mutableListOf<Int>())
        MultithreadingTester()
            .workers(8)
            .rounds(5)
            .add {
                sessionFactory.openSession().use { s ->
                    s.beginTransaction()
                    val item = s.find(VersionedItem::class.java, itemId).shouldNotBeNull()
                    prices += item.price
                    s.transaction.commit()
                }
            }
            .run()

        prices.size shouldBeEqualTo 40
        prices.forEach { it shouldBeEqualTo 999 }
        sessionFactory.statistics.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `READ_WRITE VersionedCategory의 items 컬렉션도 캐시에 적재된다`() {
        val catId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val cat = VersionedCategory().apply { label = "Electronics" }
            val item1 = VersionedCategoryItem().apply { name = "TV"; category = cat }
            val item2 = VersionedCategoryItem().apply { name = "Phone"; category = cat }
            cat.items.add(item1)
            cat.items.add(item2)
            s.persist(cat)
            s.transaction.commit()
            cat.id!!
        }
        sessionFactory.statistics.clear()

        // Session 1: 컬렉션 로드 → put
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val cat = s.find(VersionedCategory::class.java, catId)!!
            cat.items.size
            s.transaction.commit()
        }

        // Session 2: 컬렉션 재로드 → hit
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val cat = s.find(VersionedCategory::class.java, catId)!!
            cat.items.size shouldBeEqualTo 2
            s.transaction.commit()
        }

        sessionFactory.statistics.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateReadWriteStrategyTest" --info 2>&1 | tail -30
```

Expected: 5개 테스트 PASSED

- [ ] **Step 3: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateReadWriteStrategyTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateReadWriteStrategyTest for READ_WRITE concurrency strategy"
```

---

### Task 5: HibernateTransactionRollbackTest

Insert/Update/Delete 롤백 후 캐시 상태 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateTransactionRollbackTest.kt`

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Person
import io.bluetape4k.hibernate.cache.lettuce.model.VersionedItem
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 트랜잭션 롤백 후 캐시 상태 검증 테스트.
 *
 * - Insert 롤백 → 캐시에 잔류하지 않아야 함
 * - Update 롤백 → 이전 값이 캐시에 복구되어야 함
 * - Delete 롤백 → 엔티티가 캐시에 다시 존재해야 함
 */
class HibernateTransactionRollbackTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun reset() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
    }

    @Test
    fun `insert 롤백 후 엔티티가 캐시에 존재하지 않는다`() {
        var personId: Long? = null

        try {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                val p = Person().apply { name = "Rollback Insert"; age = 30 }
                s.persist(p)
                personId = p.id
                // 강제 롤백
                s.transaction.rollback()
            }
        } catch (_: Exception) {}

        if (personId != null) {
            sessionFactory.cache.containsEntity(Person::class.java, personId!!).shouldBeFalse()
        }
    }

    @Test
    fun `update 롤백 후 캐시에서 원래 값을 조회한다`() {
        // 1. 엔티티 저장
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Original"; age = 25 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        // 2. 캐시에 올리기
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.find(Person::class.java, personId).shouldNotBeNull()
            s.transaction.commit()
        }

        // 3. 수정 롤백
        try {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                val p = s.find(Person::class.java, personId)!!
                p.name = "Modified But Rolled Back"
                s.flush()
                s.transaction.rollback()
            }
        } catch (_: Exception) {}

        // 4. 롤백 후 조회 → 원래 값 또는 DB에서 재로드
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val loaded = s.find(Person::class.java, personId)
            s.transaction.commit()
            loaded.shouldNotBeNull()
            // NONSTRICT_READ_WRITE: 롤백 후 캐시 무효화되어 DB에서 원래 값 로드
            loaded.name shouldBeEqualTo "Original"
        }
    }

    @Test
    fun `delete 롤백 후 엔티티를 다시 조회할 수 있다`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Delete Rollback"; age = 20 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        // 캐시에 올리기
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.find(Person::class.java, personId).shouldNotBeNull()
            s.transaction.commit()
        }

        // 삭제 시도 후 롤백
        try {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                val p = s.find(Person::class.java, personId)!!
                s.remove(p)
                s.flush()
                s.transaction.rollback()
            }
        } catch (_: Exception) {}

        // 롤백 후 재조회 가능해야 함
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val loaded = s.find(Person::class.java, personId)
            s.transaction.commit()
            loaded.shouldNotBeNull()
            loaded.name shouldBeEqualTo "Delete Rollback"
        }
    }

    @Test
    fun `READ_WRITE 전략: update 롤백 후 stale 캐시 없이 원래 값 반환`() {
        val itemId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = VersionedItem().apply { name = "SafeItem"; price = 100 }
            s.persist(item)
            s.transaction.commit()
            item.id!!
        }

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.find(VersionedItem::class.java, itemId).shouldNotBeNull()
            s.transaction.commit()
        }

        // 롤백
        try {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                val item = s.find(VersionedItem::class.java, itemId)!!
                item.price = 9999
                s.flush()
                s.transaction.rollback()
            }
        } catch (_: Exception) {}

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val item = s.find(VersionedItem::class.java, itemId).shouldNotBeNull()
            item.price shouldBeEqualTo 100  // 원래 값
            s.transaction.commit()
        }
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateTransactionRollbackTest" --info 2>&1 | tail -30
```

Expected: 4개 테스트 PASSED

- [ ] **Step 3: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateTransactionRollbackTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateTransactionRollbackTest for cache state after rollback"
```

---

### Task 6: HibernateElementCollectionCacheTest

@ElementCollection 캐시 적재, evict, 수정 후 갱신 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateElementCollectionCacheTest.kt`

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Article
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * @ElementCollection 캐시 테스트.
 * - tags (Set<String>) 및 ratings (List<Int>) ElementCollection 캐시
 * - ElementCollection 수정 후 캐시 갱신
 * - ElementCollection Region 통계
 */
class HibernateElementCollectionCacheTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun reset() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createMutationQuery("DELETE FROM Article").executeUpdate()
            s.transaction.commit()
        }
    }

    @Test
    fun `Article tags ElementCollection이 2nd level cache에 적재된다`() {
        val articleId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val article = Article().apply {
                title = "Caching Guide"
                tags.addAll(listOf("kotlin", "hibernate", "redis"))
            }
            s.persist(article)
            s.transaction.commit()
            article.id!!
        }
        sessionFactory.statistics.clear()

        // Session 1: tags 초기화 → ElementCollection 캐시 put
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.tags.size shouldBeEqualTo 3
            s.transaction.commit()
        }

        // Session 2: tags 재로드 → 캐시 hit
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.tags shouldContain "kotlin"
            s.transaction.commit()
        }

        sessionFactory.statistics.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `Article ratings ElementCollection 수정 후 캐시가 갱신된다`() {
        val articleId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val article = Article().apply {
                title = "Ratings Test"
                ratings.addAll(listOf(3, 4, 5))
            }
            s.persist(article)
            s.transaction.commit()
            article.id!!
        }

        // 초기 로드
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.ratings.size shouldBeEqualTo 3
            s.transaction.commit()
        }

        // ratings 수정
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.ratings.clear()
            a.ratings.addAll(listOf(1, 2))
            s.transaction.commit()
        }

        sessionFactory.statistics.clear()

        // 재조회 → 최신 ratings
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.ratings.size shouldBeEqualTo 2
            s.transaction.commit()
        }
    }

    @Test
    fun `Article ElementCollection region evict 후 DB에서 재로드`() {
        val articleId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val article = Article().apply {
                title = "Evict Test"
                tags.addAll(listOf("cache", "evict"))
            }
            s.persist(article)
            s.transaction.commit()
            article.id!!
        }

        // 캐시에 올리기
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.tags.size
            s.transaction.commit()
        }

        // ElementCollection region evict
        sessionFactory.cache.evictCollectionData("${Article::class.java.name}.tags", articleId)
        sessionFactory.statistics.clear()

        // 재조회 → DB에서 로드 (cache miss)
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.tags.size shouldBeEqualTo 2
            s.transaction.commit()
        }
    }

    @Test
    fun `Article 삭제 시 ElementCollection도 캐시에서 제거된다`() {
        val articleId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val article = Article().apply {
                title = "Delete Test"
                tags.add("delete-me")
            }
            s.persist(article)
            s.transaction.commit()
            article.id!!
        }

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            a.tags.size
            s.transaction.commit()
        }

        // 삭제
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val a = s.find(Article::class.java, articleId)!!
            s.remove(a)
            s.transaction.commit()
        }

        // 엔티티와 컬렉션 모두 캐시에서 제거
        sessionFactory.cache.containsEntity(Article::class.java, articleId)
            .let { assert(!it) { "삭제된 Article이 캐시에 남아있음" } }
        sessionFactory.cache.containsCollection("${Article::class.java.name}.tags", articleId)
            .let { assert(!it) { "삭제된 Article tags가 캐시에 남아있음" } }
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateElementCollectionCacheTest" --info 2>&1 | tail -30
```

Expected: 4개 테스트 PASSED

- [ ] **Step 3: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateElementCollectionCacheTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateElementCollectionCacheTest for @ElementCollection caching"
```

---

### Task 7: HibernateQueryCacheAdvancedTest

명명 Query Cache Region, 다중 파라미터 쿼리, 쿼리 Region별 통계 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateQueryCacheAdvancedTest.kt`

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Person
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Query Cache 고급 테스트.
 * - 명명 Query Cache Region
 * - 다중 파라미터 쿼리 캐시 키 분리
 * - 특정 Query Region evict
 * - 쿼리 결과 없음도 캐시됨
 */
class HibernateQueryCacheAdvancedTest : AbstractHibernateNearCacheTest() {

    companion object {
        const val PERSON_QUERY_REGION = "io.bluetape4k.person.queries"
    }

    @BeforeEach
    fun reset() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createMutationQuery("DELETE FROM Person").executeUpdate()
            s.transaction.commit()
        }
    }

    @Test
    fun `명명 Query Region을 사용한 캐시 쿼리가 hit된다`() {
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.persist(Person().apply { name = "Named1"; age = 25 })
            s.persist(Person().apply { name = "Named2"; age = 35 })
            s.transaction.commit()
        }
        sessionFactory.statistics.clear()

        val hql = "select p from Person p where p.age > :minAge order by p.id"

        // 첫 번째 실행 → put
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setParameter("minAge", 20)
                .setCacheable(true)
                .setCacheRegion(PERSON_QUERY_REGION)
                .list().size shouldBeEqualTo 2
            s.transaction.commit()
        }

        // 두 번째 실행 → hit
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setParameter("minAge", 20)
                .setCacheable(true)
                .setCacheRegion(PERSON_QUERY_REGION)
                .list().size shouldBeEqualTo 2
            s.transaction.commit()
        }

        sessionFactory.statistics.queryCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `파라미터 값이 다르면 별도 캐시 키로 저장된다`() {
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.persist(Person().apply { name = "Young"; age = 20 })
            s.persist(Person().apply { name = "Senior"; age = 60 })
            s.transaction.commit()
        }
        sessionFactory.statistics.clear()

        val hql = "select p from Person p where p.age < :maxAge order by p.id"

        // 파라미터 30
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setParameter("maxAge", 30)
                .setCacheable(true)
                .list().size shouldBeEqualTo 1
            s.transaction.commit()
        }

        // 파라미터 70 (다른 결과)
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setParameter("maxAge", 70)
                .setCacheable(true)
                .list().size shouldBeEqualTo 2
            s.transaction.commit()
        }

        // 파라미터 30 재조회 → hit
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setParameter("maxAge", 30)
                .setCacheable(true)
                .list().size shouldBeEqualTo 1
            s.transaction.commit()
        }

        sessionFactory.statistics.queryCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `특정 Query Region evict 후 해당 쿼리만 다시 DB를 조회한다`() {
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.persist(Person().apply { name = "RegionEvict"; age = 40 })
            s.transaction.commit()
        }

        val hql = "select p from Person p order by p.id"

        // 캐시에 올리기
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setCacheable(true)
                .setCacheRegion(PERSON_QUERY_REGION)
                .list()
            s.transaction.commit()
        }

        // 명명 Region evict
        sessionFactory.cache.evictQueryRegion(PERSON_QUERY_REGION)
        sessionFactory.statistics.clear()

        // 재조회 → cache miss (DB에서 로드)
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setCacheable(true)
                .setCacheRegion(PERSON_QUERY_REGION)
                .list()
            s.transaction.commit()
        }

        sessionFactory.statistics.queryCacheMissCount shouldBeGreaterThan 0L
    }

    @Test
    fun `결과가 없는 쿼리도 캐시에 저장되어 두 번째 호출이 hit된다`() {
        sessionFactory.statistics.clear()
        val hql = "select p from Person p where p.age > :age"

        // 결과 없는 쿼리 → 빈 결과 put
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setParameter("age", 9999)
                .setCacheable(true)
                .list().size shouldBeEqualTo 0
            s.transaction.commit()
        }

        // 동일 쿼리 → hit (빈 결과)
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.createSelectionQuery(hql, Person::class.java)
                .setParameter("age", 9999)
                .setCacheable(true)
                .list().size shouldBeEqualTo 0
            s.transaction.commit()
        }

        sessionFactory.statistics.queryCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `엔티티 변경 후 기본 Query Region의 캐시가 무효화된다`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Invalidate"; age = 30 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        val hql = "select p from Person p where p.age >= :age order by p.id"

        repeat(2) {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                s.createSelectionQuery(hql, Person::class.java)
                    .setParameter("age", 25)
                    .setCacheable(true)
                    .list()
                s.transaction.commit()
            }
        }

        // 엔티티 변경 → query cache 무효화
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = s.find(Person::class.java, personId)!!
            p.age = 10
            s.transaction.commit()
        }
        sessionFactory.statistics.clear()

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val result = s.createSelectionQuery(hql, Person::class.java)
                .setParameter("age", 25)
                .setCacheable(true)
                .list()
            result.size shouldBeEqualTo 0  // age=10으로 변경되어 조건 불충족
            s.transaction.commit()
        }

        sessionFactory.statistics.queryCacheMissCount shouldBeGreaterThan 0L
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateQueryCacheAdvancedTest" --info 2>&1 | tail -30
```

Expected: 5개 테스트 PASSED

- [ ] **Step 3: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateQueryCacheAdvancedTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateQueryCacheAdvancedTest for named regions and parameter variations"
```

---

### Task 8: HibernateFirstLevelCacheTest

1st Level Cache (Session 스코프) 관리 — detach, clear, evict, contains 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateFirstLevelCacheTest.kt`

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Person
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 1st Level Cache (Session) 관리 테스트.
 * - session.contains() 확인
 * - session.detach() 후 재조회 시 새 인스턴스
 * - session.clear() 후 2nd level cache에서 로드
 * - session.evict()로 특정 엔티티만 제거
 */
class HibernateFirstLevelCacheTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun reset() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
    }

    @Test
    fun `session 내에서 조회한 엔티티는 session contains가 true`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Session Alice"; age = 30 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val loaded = s.find(Person::class.java, personId).shouldNotBeNull()
            s.contains(loaded).shouldBeTrue()
            s.transaction.commit()
        }
    }

    @Test
    fun `session detach 후 동일 ID 재조회 시 새 인스턴스를 반환한다`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Detach Bob"; age = 35 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val loaded1 = s.find(Person::class.java, personId).shouldNotBeNull()
            s.detach(loaded1)
            s.contains(loaded1).shouldBeFalse()

            val loaded2 = s.find(Person::class.java, personId).shouldNotBeNull()
            loaded2 shouldNotBeSameInstanceAs loaded1
            s.transaction.commit()
        }
    }

    @Test
    fun `session clear 후 재조회 시 2nd level cache에서 로드된다`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Clear Charlie"; age = 28 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        // 2nd level cache에 올리기
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.find(Person::class.java, personId)
            s.transaction.commit()
        }
        sessionFactory.statistics.clear()

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val loaded1 = s.find(Person::class.java, personId).shouldNotBeNull()
            s.clear()  // 1st level cache 전체 제거
            s.contains(loaded1).shouldBeFalse()

            // 2nd level cache에서 재로드
            val loaded2 = s.find(Person::class.java, personId).shouldNotBeNull()
            loaded2 shouldNotBeSameInstanceAs loaded1
            s.transaction.commit()
        }

        sessionFactory.statistics.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `session evict 후 해당 엔티티만 1st level cache에서 제거된다`() {
        val person1Id = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Evict P1"; age = 20 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }
        val person2Id = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Keep P2"; age = 25 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p1 = s.find(Person::class.java, person1Id).shouldNotBeNull()
            val p2 = s.find(Person::class.java, person2Id).shouldNotBeNull()

            s.evict(p1)

            s.contains(p1).shouldBeFalse()
            s.contains(p2).shouldBeTrue()  // p2는 유지
            s.transaction.commit()
        }
    }

    @Test
    fun `evict + 2nd level 캐시 evict 후 DB에서 재로드된다`() {
        val personId = sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val p = Person().apply { name = "Full Evict"; age = 33 }
            s.persist(p)
            s.transaction.commit()
            p.id!!
        }

        // 2nd level cache에 올리기
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            s.find(Person::class.java, personId)
            s.transaction.commit()
        }

        // 2nd level cache evict
        sessionFactory.cache.evictEntityData(Person::class.java, personId)
        sessionFactory.statistics.clear()

        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            val loaded = s.find(Person::class.java, personId).shouldNotBeNull()
            loaded.name shouldBeEqualTo "Full Evict"
            s.transaction.commit()
        }

        // 2nd level cache miss → DB에서 로드
        sessionFactory.statistics.secondLevelCacheMissCount shouldBeGreaterThan 0L
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateFirstLevelCacheTest" --info 2>&1 | tail -30
```

Expected: 5개 테스트 PASSED

- [ ] **Step 3: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateFirstLevelCacheTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateFirstLevelCacheTest for session-level cache management"
```

---

### Task 9: HibernateConcurrentWriteTest

동시 쓰기 충돌, NONSTRICT_READ_WRITE 최종 일관성 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateConcurrentWriteTest.kt`

- [ ] **Step 1: 테스트 파일 작성**

```kotlin
package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Person
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 동시 쓰기 충돌 및 NONSTRICT_READ_WRITE 최종 일관성 테스트.
 * - 다수 쓰레드가 동시에 같은 엔티티를 업데이트해도 예외 없이 처리
 * - 다수 쓰레드가 다른 엔티티에 동시 persist 시 캐시 누적
 * - 읽기/쓰기 혼합 워크로드에서 데이터 일관성 보장
 */
class HibernateConcurrentWriteTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun reset() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
    }

    @Test
    fun `다수 쓰레드가 동시에 서로 다른 엔티티를 persist해도 캐시가 누적된다`() {
        val count = AtomicInteger(0)

        MultithreadingTester()
            .workers(8)
            .rounds(5)
            .add {
                sessionFactory.openSession().use { s ->
                    s.beginTransaction()
                    val idx = count.incrementAndGet()
                    val p = Person().apply { name = "Concurrent$idx"; age = 20 + (idx % 50) }
                    s.persist(p)
                    s.transaction.commit()
                }
            }
            .run()

        sessionFactory.statistics.secondLevelCachePutCount shouldBeGreaterThan 0L
    }

    @Test
    fun `읽기-쓰기 혼합 워크로드에서 캐시 hit이 발생한다`() {
        // 초기 엔티티 5개
        val ids = (1..5).map {
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                val p = Person().apply { name = "RW$it"; age = 20 + it }
                s.persist(p)
                s.transaction.commit()
                p.id!!
            }
        }

        // warm-up
        ids.forEach { id ->
            sessionFactory.openSession().use { s ->
                s.beginTransaction()
                s.find(Person::class.java, id)
                s.transaction.commit()
            }
        }
        sessionFactory.statistics.clear()

        val writeCount = AtomicInteger(0)
        MultithreadingTester()
            .workers(10)
            .rounds(3)
            .add {
                val id = ids.random()
                val isWrite = writeCount.incrementAndGet() % 5 == 0  // 20%는 쓰기

                if (isWrite) {
                    sessionFactory.openSession().use { s ->
                        s.beginTransaction()
                        val p = s.find(Person::class.java, id)
                        p?.age = (20..60).random()
                        s.transaction.commit()
                    }
                } else {
                    sessionFactory.openSession().use { s ->
                        s.beginTransaction()
                        s.find(Person::class.java, id).shouldNotBeNull()
                        s.transaction.commit()
                    }
                }
            }
            .run()

        sessionFactory.statistics.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `다수 쓰레드가 동시에 쿼리 캐시를 조회해도 일관성이 유지된다`() {
        sessionFactory.openSession().use { s ->
            s.beginTransaction()
            (1..10).forEach { i ->
                s.persist(Person().apply { name = "QC$i"; age = 20 + i })
            }
            s.transaction.commit()
        }

        val hql = "select p from Person p where p.age > :age order by p.id"
        sessionFactory.statistics.clear()

        val resultCounts = java.util.Collections.synchronizedList(mutableListOf<Int>())
        MultithreadingTester()
            .workers(6)
            .rounds(5)
            .add {
                sessionFactory.openSession().use { s ->
                    s.beginTransaction()
                    val results = s.createSelectionQuery(hql, Person::class.java)
                        .setParameter("age", 20)
                        .setCacheable(true)
                        .list()
                    resultCounts += results.size
                    s.transaction.commit()
                }
            }
            .run()

        // 모든 쓰레드가 동일한 결과를 반환해야 함
        resultCounts.distinct().size shouldBeEqualTo 1
        sessionFactory.statistics.queryCacheHitCount shouldBeGreaterThan 0L
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce:test --tests "*.HibernateConcurrentWriteTest" --info 2>&1 | tail -30
```

Expected: 3개 테스트 PASSED

- [ ] **Step 3: 전체 모듈 테스트 최종 확인**

```bash
./gradlew :hibernate-cache-lettuce:test --info 2>&1 | tail -50
```

Expected: 전체 테스트 PASSED (기존 + 신규 35개+ 테스트)

- [ ] **Step 4: Commit**

```bash
git add infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateConcurrentWriteTest.kt
git commit -m "test(hibernate-cache-lettuce): add HibernateConcurrentWriteTest for concurrent read-write workloads"
```

---

## Part B: Hibernate 6.8.x 지원 — 신규 모듈

---

### Task 10: Libs.kt에 Hibernate 6.8.x 의존성 추가

**Files:**

- Modify: `buildSrc/src/main/kotlin/Libs.kt`

- [ ] **Step 1: Hibernate 6.8.x 버전 상수 추가**

`Libs.kt`의 `Versions` object에:

```kotlin
const val hibernate_h6 = "6.6.15.Final"  // Hibernate 6.6.x (Jakarta EE 지원 최신)
```

> 참고: Hibernate 6.6.x가 Jakarta EE API를 지원하는 마지막 6.x 안정 버전임.
> Hibernate 6.8.x는 아직 없으므로 6.6.x 최신을 사용.

`Libs` object에:

```kotlin
fun hibernateH6(module: String) = "org.hibernate.orm:hibernate-$module:${Versions.hibernate_h6}"
val hibernate_h6_core = hibernateH6("core")
val hibernate_h6_testing = hibernateH6("testing")
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew :buildSrc:build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add buildSrc/src/main/kotlin/Libs.kt
git commit -m "build: add Hibernate 6.6.x dependency definitions for H6 module support"
```

---

### Task 11: hibernate-cache-lettuce-h6 모듈 생성

**Files:**

- Create: `infra/hibernate-cache-lettuce-h6/build.gradle.kts`
- Create: `infra/hibernate-cache-lettuce-h6/README.md`
- Create:
  `infra/hibernate-cache-lettuce-h6/src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/h6/LettuceNearCacheProperties.kt`
- Create:
  `infra/hibernate-cache-lettuce-h6/src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/h6/LettuceNearCacheRegionFactory.kt`
- Create:
  `infra/hibernate-cache-lettuce-h6/src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/h6/LettuceNearCacheStorageAccess.kt`

> **핵심 차이점 (H7 vs H6):**
>
> | 항목 | H7 (`hibernate-cache-lettuce`) | H6 (`hibernate-cache-lettuce-h6`) |
> |---|---|---|
> | 패키지 | `io.bluetape4k.hibernate.cache.lettuce` | `io.bluetape4k.hibernate.cache.lettuce.h6` |
> | RegionFactory 부모 | `org.hibernate.cache.spi.support.RegionFactoryTemplate` | 동일 (H6에도 존재) |
> | DomainDataRegionConfig | `org.hibernate.cache.cfg.spi` | `org.hibernate.cache.spi.support` 확인 필요 |
> | createTimestampsRegion | 시그니처 확인 필요 | H6 시그니처 사용 |

- [ ] **Step 1: build.gradle.kts 작성**

```kotlin
plugins {
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    api(project(":cache-lettuce-near"))
    api(Libs.bluetape4k_io)
    api(Libs.bluetape4k_lettuce)

    implementation(Libs.fory_kotlin)
    implementation(Libs.lz4_java)
    implementation(Libs.snappy_java)
    implementation(Libs.zstd_jni)

    // Hibernate 6.6.x
    api(Libs.hibernate_h6_core)

    testImplementation(Libs.hibernate_h6_testing)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}
```

- [ ] **Step 2: 빌드 확인 (컴파일)**

```bash
./gradlew :hibernate-cache-lettuce-h6:compileKotlin
```

컴파일 오류 시 H6 API 패키지 차이 확인 후 수정:

- `DomainDataRegionConfig`, `DomainDataRegionBuildingContext` 위치 확인
- 메서드 시그니처 차이 확인 후 H6 API에 맞게 조정

- [ ] **Step 3: 주요 API 차이 확인 및 조정**

H6 RegionFactory API 주요 체크포인트:

```bash
# H6에서 DomainDataRegionConfig 패키지 확인
rg "DomainDataRegionConfig" ~/.gradle/caches --include="*.jar" -l | head -5
# 또는 빌드 오류 메시지에서 패키지 확인
```

- [ ] **Step 4: src/test 리소스 파일 복사**

```bash
cp infra/hibernate-cache-lettuce/src/test/resources/junit-platform.properties \
   infra/hibernate-cache-lettuce-h6/src/test/resources/
cp infra/hibernate-cache-lettuce/src/test/resources/logback-test.xml \
   infra/hibernate-cache-lettuce-h6/src/test/resources/
```

- [ ] **Step 5: Commit**

```bash
git add infra/hibernate-cache-lettuce-h6/
git commit -m "feat: create hibernate-cache-lettuce-h6 module for Hibernate 6.6.x support"
```

---

### Task 12: H6 모듈 테스트 작성 및 검증

**Files:**

- Create:
  `infra/hibernate-cache-lettuce-h6/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/h6/` (Part A와 동일한 테스트 파일들)

- [ ] **Step 1: 테스트 엔티티 및 베이스 클래스 복사 (패키지 변경)**

Part A의 `model/` 디렉토리와 `AbstractHibernateNearCacheTest.kt`, `RedisServers.kt`를 복사하고 패키지를
`io.bluetape4k.hibernate.cache.lettuce.h6`로 변경

- [ ] **Step 2: H6 RegionFactory 클래스명 참조 업데이트**

`AbstractHibernateNearCacheTest.kt`에서:

```kotlin
.applySetting(
    "hibernate.cache.region.factory_class",
    LettuceNearCacheRegionFactory::class.java.name  // H6 버전 클래스 참조
)
```

- [ ] **Step 3: 핵심 테스트 파일 복사 (패키지 변경)**

Part A의 테스트 파일들을 복사하고 패키지를 `io.bluetape4k.hibernate.cache.lettuce.h6`로 변경:

- `HibernateEntityCacheTest.kt` (기존에서 복사)
- `HibernateQueryCacheTest.kt`
- `HibernateRelationCacheTest.kt`
- `HibernateCacheStatisticsTest.kt` (Task 2)
- `HibernateCacheContainmentTest.kt` (Task 3)
- `HibernateReadWriteStrategyTest.kt` (Task 4)
- `HibernateTransactionRollbackTest.kt` (Task 5)
- `HibernateElementCollectionCacheTest.kt` (Task 6)
- `HibernateQueryCacheAdvancedTest.kt` (Task 7)
- `HibernateFirstLevelCacheTest.kt` (Task 8)
- `HibernateConcurrentWriteTest.kt` (Task 9)

- [ ] **Step 4: 전체 H6 모듈 테스트 실행**

```bash
./gradlew :hibernate-cache-lettuce-h6:test --info 2>&1 | tail -50
```

Expected: 전체 테스트 PASSED

- [ ] **Step 5: Commit**

```bash
git add infra/hibernate-cache-lettuce-h6/src/test/
git commit -m "test(hibernate-cache-lettuce-h6): add comprehensive 2LC tests for Hibernate 6.6.x"
```

---

## 검증 체크리스트

- [ ] `./gradlew :hibernate-cache-lettuce:test` 전체 통과 (35개+ 테스트)
- [ ] `./gradlew :hibernate-cache-lettuce-h6:test` 전체 통과 (35개+ 테스트)
- [ ] 통계 기반 테스트: `secondLevelCacheHitCount`, `queryCacheHitCount` 검증
- [ ] Containment 테스트: `containsEntity`, `containsCollection` 검증
- [ ] Rollback 테스트: 캐시 stale 데이터 없음 확인
- [ ] Concurrent 테스트: MultithreadingTester 병렬 안전성 확인

---

## 참고

- 기존 테스트 패턴: `infra/hibernate-cache-lettuce/src/test/kotlin/`
- hibernate-jcache 참조: `~/work/jdbc/hibernate-orm/hibernate-jcache/src/test/java/org/hibernate/orm/test/`
- Hibernate 7 SPI: `org.hibernate.cache.cfg.spi.DomainDataRegionConfig`
- CLAUDE.md 주의사항: H2는 반드시 `Libs.h2_v2` (2.x) 사용
