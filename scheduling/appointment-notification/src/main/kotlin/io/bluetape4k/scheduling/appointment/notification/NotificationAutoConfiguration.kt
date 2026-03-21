package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.redis.lettuce.leader.LettuceLeaderGroupElection
import io.bluetape4k.redis.lettuce.leader.leaderGroupElection
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 알림 모듈 Auto-Configuration.
 *
 * `scheduling.notification.enabled=true` (기본값)일 때 활성화됩니다.
 * [NotificationChannel] 빈이 없으면 [DummyNotificationChannel]을 등록합니다.
 * Redis가 있으면 리더 선출 + Resilience4j 장애 격리를 적용합니다.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "scheduling.notification",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(
    NotificationProperties::class,
    NotificationResilienceProperties::class,
)
@EnableScheduling
class NotificationAutoConfiguration {

    companion object : KLogging()

    @Bean
    fun notificationSchemaInitializer(): ApplicationRunner = ApplicationRunner {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(NotificationHistoryTable)
        }
    }

    @Bean
    fun notificationHistoryRepository(): NotificationHistoryRepository =
        NotificationHistoryRepository()

    @Bean
    @ConditionalOnMissingBean(NotificationChannel::class)
    fun dummyNotificationChannel(
        historyRepository: NotificationHistoryRepository,
    ): NotificationChannel =
        DummyNotificationChannel(historyRepository)

    /**
     * Resilience4j 데코레이터.
     * 외부 알림 서비스 호출 시 CircuitBreaker + Retry + Bulkhead 적용.
     */
    @Bean
    fun resilientNotificationChannel(
        notificationChannel: NotificationChannel,
        resilienceProperties: NotificationResilienceProperties,
    ): ResilientNotificationChannel {
        log.info { "Resilience4j 적용: CircuitBreaker + Retry + Bulkhead" }
        return ResilientNotificationChannel.create(notificationChannel, resilienceProperties)
    }

    /**
     * Redis가 있을 때 리더 선출 빈 등록.
     * HA 환경에서 스케줄러가 1개 인스턴스에서만 실행되도록 합니다.
     */
    @Bean
    @ConditionalOnClass(RedisClient::class)
    @ConditionalOnBean(StatefulRedisConnection::class)
    fun notificationLeaderElection(
        connection: StatefulRedisConnection<String, String>,
    ): LettuceLeaderGroupElection {
        log.info { "HA 리더 선출 활성화: LettuceLeaderGroupElection" }
        return connection.leaderGroupElection()
    }

    @Bean
    fun notificationEventListener(
        resilientNotificationChannel: ResilientNotificationChannel,
        appointmentRepository: AppointmentRepository,
        properties: NotificationProperties,
    ): NotificationEventListener =
        NotificationEventListener(resilientNotificationChannel, appointmentRepository, properties)

    @Bean
    fun appointmentReminderScheduler(
        resilientNotificationChannel: ResilientNotificationChannel,
        appointmentRepository: AppointmentRepository,
        historyRepository: NotificationHistoryRepository,
        properties: NotificationProperties,
        leaderElection: LettuceLeaderGroupElection?,
    ): AppointmentReminderScheduler =
        AppointmentReminderScheduler(
            resilientNotificationChannel,
            appointmentRepository,
            historyRepository,
            properties,
            leaderElection,
        )
}
