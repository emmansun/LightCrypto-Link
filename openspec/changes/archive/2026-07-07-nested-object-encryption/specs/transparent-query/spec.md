## MODIFIED Requirements

### Requirement: Custom QueryLookup for encrypted fields
The system SHALL provide a `CryptoMongoRepositoryFactory` that installs a custom `QueryLookupStrategy`. When a repository method name references a field annotated with `@Encrypted(blindIndex = true)` — including nested fields discovered by recursive scanning — the system SHALL transparently rewrite the query Criteria to target the blind index sub-field using the full dot-notation path (e.g., `address.zipCode.b`) and hash the query value using HMAC with the full path as salt.

#### Scenario: findByPhone with blind index
- **WHEN** `userRepository.findByPhone("13900001111")` is called and `phone` has `@Encrypted(blindIndex = true)`
- **THEN** the system SHALL execute `Criteria.where("phone.b").is(HMAC("phone", "13900001111"))` against MongoDB

#### Scenario: findByPhoneAndName mixed query
- **WHEN** `userRepository.findByPhoneAndName("13900001111", "Zhang")` is called
- **THEN** the query SHALL be `Criteria.where("phone.b").is(hash).and("name").is("Zhang")` (only phone is rewritten)

#### Scenario: findByPhone without blind index
- **WHEN** `repository.findByPhone("13900001111")` is called but `phone` has `@Encrypted` with `blindIndex = false`
- **THEN** the system SHALL throw an `UnsupportedOperationException` indicating that blind index is not enabled for this field

#### Scenario: findByAddressZipCode with nested blind index
- **WHEN** `userRepository.findByAddressZipCode("200000")` is called and `Address.zipCode` has `@Encrypted(blindIndex = true)`
- **THEN** the system SHALL execute `Criteria.where("address.zipCode.b").is(HMAC("address.zipCode", "200000"))` against MongoDB

#### Scenario: findByAddressZipCodeIn with nested blind index
- **WHEN** `userRepository.findByAddressZipCodeIn(["200000", "100000"])` is called
- **THEN** the system SHALL compute blind index for each value using salt `"address.zipCode"` and execute `Criteria.where("address.zipCode.b").in([h1, h2])`
