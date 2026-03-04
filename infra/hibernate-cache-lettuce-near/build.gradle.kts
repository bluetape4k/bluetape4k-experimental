plugins {
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
}

dependencies {
    // 기존 near cache 모듈 재사용
    api(project(":infra-cache-lettuce-near"))

    // bluetape4k-io: BinarySerializers (Fory/Kryo 직렬화)
    api(Libs.bluetape4k_io)

    // bluetape4k-redis: LettuceBinaryCodec
    api(Libs.bluetape4k_redis)

    // Hibernate 7.x
    api(Libs.hibernate_core)

    // Serializer runtime dependencies (bluetape4k-io의 선택적 의존성)
    implementation(Libs.fory_kotlin)
    implementation(Libs.lz4_java)

    // Test
    testImplementation(Libs.hibernate_testing)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}

// JPA 엔티티 클래스 open 처리
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
