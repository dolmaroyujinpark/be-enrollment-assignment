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

    // --- Testcontainers ↔ Docker 연결 호환 (특히 macOS Docker Desktop) ---
    // 1) docker-java 가 기본으로 보내는 Docker API 버전(1.32)은 최신 Docker Desktop(min 1.40)에서 거부됨 → 고정.
    systemProperty("api.version", "1.43")
    // 2) 헬퍼 컨테이너(Ryuk)에 Docker 소켓을 bind-mount 할 때 쓸 소켓 경로. Docker Desktop 의 내부 데이터 디렉토리
    //    (...Data/docker.raw.sock)는 VM 이 마운트 못 하므로 표준 경로로 강제. Linux/CI 에서도 동일 경로라 무해.
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    // 3) macOS Docker Desktop: 자동 감지되는 ~/.docker/run/docker.sock 은 CLI 프록시라 docker-java 가 못 씀(빈 400 응답)
    //    → 실제 엔진 소켓 docker.raw.sock 으로 연결. (해당 소켓이 있고 DOCKER_HOST 가 안 잡혀 있을 때만; Linux·기타 런타임엔 영향 없음)
    if (System.getProperty("os.name").lowercase().contains("mac") && System.getenv("DOCKER_HOST") == null) {
        val rawSock = file("${System.getProperty("user.home")}/Library/Containers/com.docker.docker/Data/docker.raw.sock")
        if (rawSock.exists()) environment("DOCKER_HOST", "unix://${rawSock.absolutePath}")
    }
    // 4) 그 밖의 머신별 Docker 설정은 환경변수로 주면 테스트 JVM 에 그대로 전달.
    listOf("DOCKER_HOST", "DOCKER_API_VERSION", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH", "TESTCONTAINERS_RYUK_DISABLED")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
}
