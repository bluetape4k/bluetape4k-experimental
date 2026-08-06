pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        // https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
        id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
    }
}

val baseProjectName = "bluetape4k"

val bluetape4kDependenciesCatalogRef = providers.gradleProperty("bluetape4kDependenciesCatalogRef")
    .orElse(providers.environmentVariable("BLUETAPE4K_DEPENDENCIES_CATALOG_REF"))
    .orElse("3d2fb6e0087a6bbef5418aee8024bba9dd527e26")
    .get()
require(bluetape4kDependenciesCatalogRef.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}"))) {
    "bluetape4k-dependencies catalog ref must be an immutable Git commit SHA: " +
        bluetape4kDependenciesCatalogRef
}
val bluetape4kDependenciesCatalogCacheKey = bluetape4kDependenciesCatalogRef.replace(Regex("[^A-Za-z0-9._-]"), "_")

fun catalogSha256(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

fun expectedCatalogSha256(checksumFile: File): String? =
    checksumFile.takeIf(File::isFile)
        ?.readText()
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

fun catalogChecksumMatches(catalogFile: File, checksumFile: File): Boolean =
    catalogFile.isFile &&
        !java.nio.file.Files.isSymbolicLink(catalogFile.toPath()) &&
        expectedCatalogSha256(checksumFile)?.let { it == catalogSha256(catalogFile) } == true

val catalogConnectTimeoutMillis = 10_000
val catalogReadTimeoutMillis = 30_000
val catalogMaxBytes = 2L * 1024 * 1024
val catalogChecksumMaxBytes = 1024L

fun downloadCatalogFile(url: String, target: File, maxBytes: Long) {
    val connection = uri(url).toURL().openConnection()
    connection.connectTimeout = catalogConnectTimeoutMillis
    connection.readTimeout = catalogReadTimeoutMillis
    require(connection.contentLengthLong < 0 || connection.contentLengthLong <= maxBytes) {
        "Catalog download exceeds allowed size: $url"
    }
    connection.getInputStream().buffered().use { input ->
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                require(copied <= maxBytes) { "Catalog download exceeds allowed size: $url" }
                output.write(buffer, 0, count)
            }
        }
    }
}

fun validateCatalogStructure(catalogFile: File) {
    val catalogText = catalogFile.readText()
    val requiredMarkers = listOf(
        "# <shared-version-source-of-truth by scripts/sync-shared-versions.py>",
        "# </shared-version-source-of-truth>",
        "[versions]",
        "[libraries]",
    )
    require(requiredMarkers.all(catalogText::contains)) {
        "Invalid bluetape4k-dependencies catalog structure: $catalogFile"
    }
}

fun resolveBluetape4kDependenciesCatalogFile(): File {
    providers.gradleProperty("bluetape4kDependenciesCatalogPath")
        .orElse(providers.environmentVariable("BLUETAPE4K_DEPENDENCIES_CATALOG_PATH"))
        .orNull
        ?.let(::file)
        ?.let { catalogFile ->
            require(catalogFile.isFile && !java.nio.file.Files.isSymbolicLink(catalogFile.toPath())) {
                "Explicit bluetape4k-dependencies catalog must be a regular non-symlink file: $catalogFile"
            }
            validateCatalogStructure(catalogFile)
            return catalogFile
        }

   val catalogFile = file(".gradle/bluetape4k-dependencies/$bluetape4kDependenciesCatalogCacheKey/libs.versions.toml")
    val checksumFile = file(".gradle/bluetape4k-dependencies/$bluetape4kDependenciesCatalogCacheKey/libs.versions.toml.sha256")
    if (!catalogChecksumMatches(catalogFile, checksumFile)) {
        require(catalogFile.parentFile.mkdirs() || catalogFile.parentFile.isDirectory) {
            "Cannot create bluetape4k-dependencies catalog cache: ${catalogFile.parentFile}"
        }
        val catalogBaseUrl =
            "https://raw.githubusercontent.com/bluetape4k/bluetape4k-dependencies/" +
                "$bluetape4kDependenciesCatalogRef/gradle"
        val catalogTempFile = File.createTempFile("libs.versions-", ".toml.tmp", catalogFile.parentFile)
        val checksumTempFile = File.createTempFile("libs.versions-", ".sha256.tmp", catalogFile.parentFile)
        try {
            downloadCatalogFile("$catalogBaseUrl/libs.versions.toml", catalogTempFile, catalogMaxBytes)
            downloadCatalogFile(
                "$catalogBaseUrl/libs.versions.toml.sha256",
                checksumTempFile,
                catalogChecksumMaxBytes,
            )
            val expectedChecksum = requireNotNull(expectedCatalogSha256(checksumTempFile)) {
                "Invalid bluetape4k-dependencies catalog checksum: $checksumTempFile"
            }
            require(catalogSha256(catalogTempFile) == expectedChecksum) {
                "bluetape4k-dependencies catalog checksum mismatch for ref $bluetape4kDependenciesCatalogRef"
            }
            validateCatalogStructure(catalogTempFile)
            java.nio.file.Files.move(
                checksumTempFile.toPath(),
                checksumFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            java.nio.file.Files.move(
                catalogTempFile.toPath(),
                catalogFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            catalogTempFile.delete()
            checksumTempFile.delete()
        }
    }
    validateCatalogStructure(catalogFile)
    return catalogFile
}

val bluetape4kDependenciesCatalogFile = resolveBluetape4kDependenciesCatalogFile()

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
    versionCatalogs {
        create("bt4k") {
            from(files(bluetape4kDependenciesCatalogFile))
        }
    }
}

rootProject.name = "$baseProjectName-experimental"

//val bluetape4kProjectsDir = file("../bluetape4k-projects")
//if (bluetape4kProjectsDir.exists()) {
//    includeBuild(bluetape4kProjectsDir) {
//        dependencySubstitution {
//            substitute(module("io.github.bluetape4k:bluetape4k-cache-lettuce"))
//                .using(project(":bluetape4k-cache-lettuce"))
//        }
//    }
//}

include("shared")

includeModules("kotlin", false, false)
includeModules("coroutines", false, false)
includeModules("ai", false, false)
includeModules("data", false, false)
includeModules("io", false, false)
includeModules("examples", false, false)

fun includeModules(baseDir: String, withProjectName: Boolean = true, withBaseDir: Boolean = true) {
    files("$rootDir/$baseDir").files
        .filter { it.isDirectory }
        .forEach { moduleDir ->
            moduleDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && File(it, "build.gradle.kts").exists() }
                ?.forEach { dir ->
                    val basePath = baseDir.replace("/", "-")
                    val projectName = when {
                        !withProjectName && !withBaseDir -> dir.name
                        withProjectName && !withBaseDir -> baseProjectName + "-" + dir.name
                        withProjectName                 -> baseProjectName + "-" + basePath + "-" + dir.name
                        else                             -> basePath + "-" + dir.name
                    }

                    include(projectName)
                    project(":$projectName").projectDir = dir
                }
        }
}
