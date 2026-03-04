package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.hibernate.cache.lettuce.model.Person
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Hibernate Entity 2nd Level Cache 통합 테스트.
 *
 * - persist → session close → new session load → 2nd level cache hit 확인
 * - evict → reload → DB miss 확인
 * - Hibernate statistics를 통해 캐시 동작 검증
 */
class HibernateEntityCacheTest : AbstractHibernateNearCacheTest() {

    @BeforeEach
    fun clearCacheAndStats() {
        sessionFactory.cache.evictAllRegions()
        sessionFactory.statistics.clear()
    }

    @Test
    fun `Entity 저장 후 새 세션에서 재조회 시 2nd level cache hit`() {
        // 1. Session 1: Entity 저장 → 2nd level cache 적재
        val personId = sessionFactory.openSession().use { session ->
            session.beginTransaction()
            val p = Person().apply { name = "Alice"; age = 30 }
            session.persist(p)
            session.transaction.commit()
            p.id!!
        }

        sessionFactory.statistics.clear()

        // 2. Session 2: 1st level cache miss → 2nd level cache hit
        sessionFactory.openSession().use { session ->
            session.beginTransaction()
            val loaded = session.get(Person::class.java, personId)
            session.transaction.commit()
            loaded.shouldNotBeNull()
            loaded.name shouldBeEqualTo "Alice"
        }

        // 3. Session 3: 또 조회 → 2nd level cache hit
        sessionFactory.openSession().use { session ->
            session.beginTransaction()
            session.get(Person::class.java, personId).shouldNotBeNull()
            session.transaction.commit()
        }

        val stats = sessionFactory.statistics
        stats.secondLevelCacheHitCount shouldBeGreaterThan 0L
    }

    @Test
    fun `캐시에 없는 Entity 조회 시 2nd level cache miss`() {
        sessionFactory.statistics.clear()

        sessionFactory.openSession().use { session ->
            session.beginTransaction()
            session.get(Person::class.java, Long.MAX_VALUE).shouldBeNull()
            session.transaction.commit()
        }

        // 존재하지 않는 엔티티이므로 miss (put도 없음)
        val stats = sessionFactory.statistics
        stats.secondLevelCacheHitCount shouldBeEqualTo 0L
    }

    @Test
    fun `캐시 evict 후 재조회 시 DB에서 로드된다`() {
        // 1. Entity 저장
        val personId = sessionFactory.openSession().use { session ->
            session.beginTransaction()
            val p = Person().apply { name = "Bob"; age = 25 }
            session.persist(p)
            session.transaction.commit()
            p.id!!
        }

        // 2. Session 2: 2nd level cache에 올리기
        sessionFactory.openSession().use { session ->
            session.beginTransaction()
            session.get(Person::class.java, personId)
            session.transaction.commit()
        }

        // 3. 2nd level cache evict
        sessionFactory.cache.evictEntityData(Person::class.java, personId)
        sessionFactory.statistics.clear()

        // 4. Session 3: 재조회 → cache miss → DB에서 로드
        sessionFactory.openSession().use { session ->
            session.beginTransaction()
            val loaded = session.get(Person::class.java, personId)
            session.transaction.commit()
            loaded.shouldNotBeNull()
            loaded.name shouldBeEqualTo "Bob"
        }

        val stats = sessionFactory.statistics
        stats.secondLevelCacheMissCount shouldBeGreaterThan 0L
    }

    @Test
    fun `Entity 수정 후 캐시가 갱신된다`() {
        // 1. Entity 저장
        val personId = sessionFactory.openSession().use { session ->
            session.beginTransaction()
            val p = Person().apply { name = "Charlie"; age = 20 }
            session.persist(p)
            session.transaction.commit()
            p.id!!
        }

        // 2. Entity 수정
        sessionFactory.openSession().use { session ->
            session.beginTransaction()
            val p = session.get(Person::class.java, personId)
            p!!.name = "Charlie Updated"
            session.transaction.commit()
        }

        sessionFactory.statistics.clear()

        // 3. 수정 후 재조회 → 최신 값 확인
        sessionFactory.openSession().use { session ->
            session.beginTransaction()
            val loaded = session.get(Person::class.java, personId)
            session.transaction.commit()
            loaded.shouldNotBeNull()
            loaded.name shouldBeEqualTo "Charlie Updated"
        }
    }
}
