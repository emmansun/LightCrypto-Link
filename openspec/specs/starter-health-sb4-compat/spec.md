## ADDED Requirements

### Requirement: Health indicator compiles on both SB3 and SB4
The starter's health indicator support SHALL compile and function correctly on both Spring Boot 3.x and 4.x, despite the package rename from `org.springframework.boot.actuate.health` to `org.springframework.boot.health.contributor`.

#### Scenario: LclHealthIndicator works on SB3
- **WHEN** `lcl-spring-boot-starter` is used with Spring Boot 3.x and `spring-boot-actuator` on the classpath
- **THEN** `LclHealthIndicator` SHALL implement `HealthIndicator` from `org.springframework.boot.actuate.health`

#### Scenario: LclHealthIndicator works on SB4
- **WHEN** `lcl-spring-boot-starter` is used with Spring Boot 4.x and `spring-boot-health` on the classpath
- **THEN** `LclHealthIndicator` SHALL implement `HealthIndicator` from `org.springframework.boot.health.contributor`

### Requirement: Health abstraction does not affect SB3 users
The SB4 compatibility changes for health classes SHALL NOT change the behavior or API for existing SB3 users.

#### Scenario: SB3 health behavior preserved
- **WHEN** a Spring Boot 3.x application uses `lcl-spring-boot-starter` with actuator
- **THEN** the health endpoint SHALL report LCL status identically to the pre-change behavior

### Requirement: ObservabilityAutoConfiguration health reference works cross-version
The `ObservabilityAutoConfiguration` class references `HealthIndicator` for conditional bean registration. This reference SHALL resolve correctly on both SB3 and SB4.

#### Scenario: ObservabilityAutoConfiguration loads on SB4
- **WHEN** a Spring Boot 4.x application includes `lcl-spring-boot-starter` with health support
- **THEN** `ObservabilityAutoConfiguration` SHALL correctly reference the SB4 `HealthIndicator` type
