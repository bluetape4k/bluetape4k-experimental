package io.bluetape4k.spring.boot.autoconfigure.cache.lettuce

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class LettuceNearCacheAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(LettuceNearCacheHibernateAutoConfiguration::class.java)
        )

    @Test
    fun `HibernatePropertiesCustomizer가 기본 설정으로 등록된다`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(HibernatePropertiesCustomizer::class.java)
        }
    }

    @Test
    fun `enabled=false이면 Bean이 등록되지 않는다`() {
        contextRunner
            .withPropertyValues("bluetape4k.cache.lettuce-near.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(HibernatePropertiesCustomizer::class.java)
            }
    }

    @Test
    fun `custom redisUri가 Hibernate properties에 반영된다`() {
        contextRunner
            .withPropertyValues("bluetape4k.cache.lettuce-near.redis-uri=redis://myredis:6380")
            .run { context ->
                val customizer = context.getBean(HibernatePropertiesCustomizer::class.java)
                val props = mutableMapOf<String, Any>()
                customizer.customize(props)

                assertThat(props["hibernate.cache.lettuce.redis_uri"])
                    .isEqualTo("redis://myredis:6380")
                assertThat(props["hibernate.cache.region.factory_class"])
                    .isEqualTo("io.bluetape4k.hibernate.cache.lettuce.LettuceNearCacheRegionFactory")
                assertThat(props["hibernate.cache.use_second_level_cache"])
                    .isEqualTo("true")
            }
    }

    @Test
    fun `metrics enabled이면 statistics 설정이 반영된다`() {
        contextRunner
            .withPropertyValues(
                "bluetape4k.cache.lettuce-near.metrics.enabled=true",
                "bluetape4k.cache.lettuce-near.metrics.enable-caffeine-stats=true",
            )
            .run { context ->
                val customizer = context.getBean(HibernatePropertiesCustomizer::class.java)
                val props = mutableMapOf<String, Any>()
                customizer.customize(props)

                assertThat(props["hibernate.generate_statistics"]).isEqualTo("true")
                assertThat(props["hibernate.cache.lettuce.local.record_stats"]).isEqualTo("true")
            }
    }

    @Test
    fun `region별 TTL 설정이 Hibernate properties에 반영된다`() {
        contextRunner
            .withPropertyValues(
                "bluetape4k.cache.lettuce-near.redis-ttl.default=60s",
                // 점이 포함된 Map 키는 브라켓 표기법 사용
                "bluetape4k.cache.lettuce-near.redis-ttl.regions[product]=300s",
            )
            .run { context ->
                val customizer = context.getBean(HibernatePropertiesCustomizer::class.java)
                val props = mutableMapOf<String, Any>()
                customizer.customize(props)

                assertThat(props["hibernate.cache.lettuce.redis_ttl.default"]).isEqualTo("60s")
                assertThat(props["hibernate.cache.lettuce.redis_ttl.product"]).isEqualTo("300s")
            }
    }

    @Test
    fun `LettuceNearCacheSpringProperties 기본값이 올바르게 설정된다`() {
        contextRunner.run { context ->
            val props = context.getBean(LettuceNearCacheSpringProperties::class.java)
            assertThat(props.enabled).isTrue()
            assertThat(props.redisUri).isEqualTo("redis://localhost:6379")
            assertThat(props.codec).isEqualTo("lz4fory")
            assertThat(props.useResp3).isTrue()
            assertThat(props.local.maxSize).isEqualTo(10_000L)
            assertThat(props.metrics.enabled).isTrue()
        }
    }
}
