## MODIFIED Requirements

### Requirement: Custom QueryLookup for encrypted fields
The system SHALL rewrite queries on `@Encrypted(blindIndex = true)` collection fields to target the blind index sub-field within array elements. For `@Encrypted List<String> tags`, a query like `findByTagsContaining("java")` SHALL be rewritten to `Criteria.where("tags.b").is(hash)`, leveraging MongoDB's array query semantics that automatically match against any element's `b` field.

#### Scenario: findByTagsContaining with collection blind index
- **WHEN** `repository.findByTagsContaining("java")` is called and `tags` has `@Encrypted(blindIndex = true)` of type `List<String>`
- **THEN** the system SHALL execute `Criteria.where("tags.b").is(HMAC("tags", "java"))` against MongoDB

#### Scenario: findByTagsContainingIn with collection blind index
- **WHEN** `repository.findByTagsContainingIn(["java", "spring"])` is called
- **THEN** the system SHALL compute blind index for each value and execute `Criteria.where("tags.b").in([h1, h2])`
