package io.bluetape4k.exposed.lettuce.domain

import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.map.WriteMode
import io.bluetape4k.exposed.lettuce.repository.AbstractJdbcLettuceRepository
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.RedisCodec
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ItemRepository(
    connection: StatefulRedisConnection<String, ItemDto>,
    writeMode: WriteMode = WriteMode.WRITE_THROUGH,
) : AbstractJdbcLettuceRepository<ItemEntity, Long, ItemDto>(connection) {

    override val entityClass: EntityClass<Long, ItemEntity> = ItemEntity

    override val codec: RedisCodec<String, ItemDto> =
        LettuceBinaryCodec(BinarySerializers.LZ4Fory)

    override val config: LettuceCacheConfig = LettuceCacheConfig(
        cacheName = "items",
        writeMode = writeMode,
    )

    override fun toValue(entity: ItemEntity): ItemDto =
        ItemDto(entity.id.value, entity.name, entity.price)

    override fun upsert(id: Long, value: ItemDto) {
        // transaction은 ExposedEntityMapWriter가 열어준다
        val entity = ItemEntity.findById(id)
        if (entity == null) {
            ItemEntity.new(id) {
                name = value.name
                price = value.price
            }
        } else {
            entity.name = value.name
            entity.price = value.price
        }
    }

    /** 테스트 편의: 직접 DB에 저장하고 ItemDto 반환 */
    fun createInDb(name: String, price: java.math.BigDecimal): ItemDto =
        transaction {
            val entity = ItemEntity.new {
                this.name = name
                this.price = price
            }
            toValue(entity)
        }
}
