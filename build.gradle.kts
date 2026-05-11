plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.liveklass"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Testcontainers BOM 을 Spring Boot 가 고정한 버전(1.19.x)보다 올린다 — 1.19.x 의 docker-java 3.3.x 는
    // 최신 Docker Desktop 소켓과 호환성 문제가 있어 1.20.x(docker-java 3.4.x)로 상향.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.6"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Testcontainers 의 Docker 연결 설정이 환경변수로 주어지면 테스트 JVM 에 그대로 전달
    // (예: 로컬 Docker Desktop 호환 위해 DOCKER_HOST=unix://~/Library/Containers/com.docker.docker/Data/docker.raw.sock,
    //  DOCKER_API_VERSION=1.43 등을 export 후 실행). CI(GitHub Actions) 의 기본 Docker 에서는 불필요.
    listOf("DOCKER_HOST", "DOCKER_API_VERSION", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH", "TESTCONTAINERS_RYUK_DISABLED")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
}
