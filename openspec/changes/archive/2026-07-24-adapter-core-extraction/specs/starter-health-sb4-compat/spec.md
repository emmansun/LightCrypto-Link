## MODIFIED Requirements

### Requirement: Health indicator fully provided by starter for both SB3 and SB4
The `lcl-spring-boot-starter` module SHALL provide health indicator support for both Spring Boot 3.x and 4.x without requiring any adapter module involvement. The starter SHALL contain:
- `LclHealthCollector` — pure logic class that composes `ComponentHealthCheck` results into overall status and details map (no Spring Health imports)
- `LclHealthIndicator` — SB3 thin shell implementing `org.springframework.boot.actuate.health.HealthIndicator`, delegating to `LclHealthCollector`
- `LclHealthIndicatorV4` — SB4 thin shell implementing `org.springframework.boot.health.contributor.HealthIndicator`, delegating to `LclHealthCollector`

#### Scenario: LclHealthIndicator works on SB3
- **WHEN** `lcl-spring-boot-starter` is used with Spring Boot 3.x and `spring-boot-actuator` on the classpath
- **THEN** `LclHealthIndicator` SHALL implement `HealthIndicator` from `org.springframework.boot.actuate.health`
- **THEN** it SHALL delegate health composition logic to `LclHealthCollector`

#### Scenario: LclHealthIndicatorV4 works on SB4
- **WHEN** `lcl-spring-boot-starter` is used with Spring Boot 4.x and `spring-boot-health` on the classpath
- **THEN** `LclHealthIndicatorV4` SHALL implement `HealthIndicator` from `org.springframework.boot.health.contributor`
- **THEN** it SHALL delegate health composition logic to `LclHealthCollector`

#### Scenario: No adapter module needed for health
- **WHEN** a Spring Boot 4.x application uses `lcl-spring-boot-starter` with a hypothetical non-MongoDB adapter
- **THEN** health indicator support SHALL be available without depending on `lcl-adapter-mongodb-v4`

### Requirement: Conditional activation via @ConditionalOnClass
The starter SHALL use `@ConditionalOnClass` to activate the correct health indicator based on classpath:
- SB3: `@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")`
- SB4: `@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")`

Both conditions SHALL be mutually exclusive (only one Spring Boot major version is on the classpath at runtime).

#### Scenario: Only SB3 indicator active on SB3 classpath
- **WHEN** the application runs on Spring Boot 3.x
- **THEN** `LclHealthIndicator` bean SHALL be registered
- **THEN** `LclHealthIndicatorV4` bean SHALL NOT be registered

#### Scenario: Only SB4 indicator active on SB4 classpath
- **WHEN** the application runs on Spring Boot 4.x
- **THEN** `LclHealthIndicatorV4` bean SHALL be registered
- **THEN** `LclHealthIndicator` bean SHALL NOT be registered

### Requirement: Health abstraction does not affect SB3 users
The SB4 compatibility changes for health classes SHALL NOT change the behavior or API for existing SB3 users.

#### Scenario: SB3 health behavior preserved
- **WHEN** a Spring Boot 3.x application uses `lcl-spring-boot-starter` with actuator
- **THEN** the health endpoint SHALL report LCL status identically to the pre-change behavior

### Requirement: Starter POM includes spring-boot-health as optional dependency
The `lcl-spring-boot-starter` POM SHALL declare `spring-boot-health` (SB4 health module) as an `<optional>true</optional>` dependency to enable compilation of `LclHealthIndicatorV4` without forcing it onto SB3 users' classpaths.

#### Scenario: SB3 users do not get spring-boot-health transitively
- **WHEN** a Spring Boot 3.x application depends on `lcl-spring-boot-starter`
- **THEN** `spring-boot-health` SHALL NOT appear on the application's compile/runtime classpath

### Requirement: ObservabilityAutoConfiguration health reference works cross-version
The `ObservabilityAutoConfiguration` class SHALL contain two inner `@Configuration` classes for health registration, each guarded by the appropriate `@ConditionalOnClass`. No direct import of SB4 health classes SHALL exist outside the SB4-specific inner class.

#### Scenario: ObservabilityAutoConfiguration loads on SB4
- **WHEN** a Spring Boot 4.x application includes `lcl-spring-boot-starter` with health support
- **THEN** `ObservabilityAutoConfiguration` SHALL correctly register the SB4 health indicator

#### Scenario: ObservabilityAutoConfiguration loads on SB3
- **WHEN** a Spring Boot 3.x application includes `lcl-spring-boot-starter` with actuator
- **THEN** `ObservabilityAutoConfiguration` SHALL correctly register the SB3 health indicator
