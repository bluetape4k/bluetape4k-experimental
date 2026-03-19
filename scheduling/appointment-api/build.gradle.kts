// Phase 3+ stub — 소스 없음
plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":appointment-core"))
    api(project(":appointment-event"))
}
