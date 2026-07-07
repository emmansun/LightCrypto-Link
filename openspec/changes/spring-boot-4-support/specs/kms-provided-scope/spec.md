## ADDED Requirements

### Requirement: KMS modules use provided scope for starter dependency
Both `lightcrypto-link-azure-kms` and `lightcrypto-link-alibaba-kms` SHALL declare `lightcrypto-link-spring-boot-starter` with `<scope>provided</scope>`, so they do not transitively force the SB3 starter onto users who depend on `lightcrypto-link-spring-boot-starter-v4`.

#### Scenario: Azure KMS module does not transitively pull in SB3 starter
- **WHEN** a Spring Boot 4.x project depends on `lightcrypto-link-azure-kms` and `lightcrypto-link-spring-boot-starter-v4`
- **THEN** Maven SHALL NOT resolve `lightcrypto-link-spring-boot-starter` (SB3) as a transitive dependency

#### Scenario: Alibaba KMS module does not transitively pull in SB3 starter
- **WHEN** a Spring Boot 4.x project depends on `lightcrypto-link-alibaba-kms` and `lightcrypto-link-spring-boot-starter-v4`
- **THEN** Maven SHALL NOT resolve `lightcrypto-link-spring-boot-starter` (SB3) as a transitive dependency

#### Scenario: KMS modules still compile successfully
- **WHEN** `lightcrypto-link-azure-kms` or `lightcrypto-link-alibaba-kms` is compiled
- **THEN** the starter dependency SHALL be available on the compile classpath (provided scope) and compilation SHALL succeed

### Requirement: SB3 users explicitly declare starter dependency
Documentation and examples SHALL make clear that users must explicitly declare either `lightcrypto-link-spring-boot-starter` or `lightcrypto-link-spring-boot-starter-v4` in their project, even when using a KMS module.

#### Scenario: Example POMs show explicit starter dependency
- **WHEN** viewing example module `pom.xml` files
- **THEN** each example SHALL explicitly declare `lightcrypto-link-spring-boot-starter` as a dependency (not rely on transitive inclusion from KMS modules)
