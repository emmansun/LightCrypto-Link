## Purpose

Define the behavioral contract for the transparent-query capability to match current implementation behavior.

## Requirements
### Requirement: Custom QueryLookup for encrypted fields
The system SHALL provide a `CryptoMongoRepositoryFactory` that installs a custom `QueryLookupStrategy`. When a repository method name references a field annotated with `@Encrypted(blindIndex = true)`, the system SHALL transparently rewrite the query Criteria to target the blind index sub-field (`fieldName.b`) and hash the query value using the HMAC blind index.

#### Scenario: findByPhone with blind index
- **WHEN** `userRepository.findByPhone("13900001111")` is called and `phone` has `@Encrypted(blindIndex = true)`
- **THEN** the system SHALL execute `Criteria.where("phone.b").is(HMAC("phone:13900001111"))` against MongoDB

#### Scenario: findByPhoneAndName mixed query
- **WHEN** `userRepository.findByPhoneAndName("13900001111", "Zhang")` is called
- **THEN** the query SHALL be `Criteria.where("phone.b").is(hash).and("name").is("Zhang")` (only phone is rewritten)

#### Scenario: findByPhone without blind index
- **WHEN** `repository.findByPhone("13900001111")` is called but `phone` has `@Encrypted` with `blindIndex = false`
- **THEN** the system SHALL throw an `UnsupportedOperationException` indicating that blind index is not enabled for this field

### Requirement: Supported query types for encrypted fields
The system SHALL support the following Spring Data query types for `@Encrypted(blindIndex = true)` fields:
- `SIMPLE_PROPERTY` (eq): rewrite to `fieldName.b = hash`
- `IN`: rewrite to `fieldName.b in [hash1, hash2, ...]`
- `NEGATING_SIMPLE_PROPERTY` (ne): rewrite to `fieldName.b != hash`
- `NOT_IN`: rewrite to `fieldName.b nin [hash1, hash2, ...]`
- `IS_NULL`: pass through as `fieldName = null` (no hashing needed)
- `IS_NOT_NULL`: pass through as `fieldName != null`

#### Scenario: findByPhoneIn batch query
- **WHEN** `repository.findByPhoneIn(["139", "138", "137"])` is called
- **THEN** the system SHALL compute blind index for each value and execute `Criteria.where("phone.b").in([h1, h2, h3])`

#### Scenario: findByPhoneIsNull
- **WHEN** `repository.findByPhoneIsNull()` is called
- **THEN** the system SHALL execute `Criteria.where("phone").is(null)` without hashing

### Requirement: Unsupported query type rejection
The system SHALL reject query types that cannot be satisfied by a blind index (e.g., CONTAINING, STARTING_WITH, BETWEEN, GREATER_THAN) with a clear error message.

#### Scenario: findByPhoneStartingWith rejection
- **WHEN** `repository.findByPhoneStartingWith("139")` is called
- **THEN** the system SHALL throw `UnsupportedOperationException` with a message indicating that prefix queries require blind index extension (v2)


