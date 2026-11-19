# Testing Strategy

The test suite uses **JUnit 6.1.3** and **Mockito 5.24.0** and runs with `mvn test`, locally and in CI ([CICD.md](CICD.md)). It needs no database, network or credentials.

## Test layers

| Layer | Location | Tests | Tools |
| :--- | :--- | ---: | :--- |
| Unit: services and business logic | `IS/Service/**` | 176 | JUnit, Mockito |
| Unit: validation and data conversion | `IS/Utility/**` | 129 | JUnit (parameterized) |
| Web: HTTP → controller → service | `IS/Control/**` | 74 | `@WebMvcTest`, MockMvc |
| Persistence: entities and queries | `IS/Repository/**` | 12 | `@DataJpaTest`, H2 in memory |

- **Unit tests** cover every service (GE, GNF, GPE, GSE, GUM, GUW, Firebase), the `Validator` rules, password hashing and the JSON converters of the evacuation zones.
- **Web tests** start only the Spring MVC layer and check routing, JSON parsing, session/login checks, HTTP status codes and error messages. Services are mocked with `@MockBean`.
- **Persistence tests** run Hibernate on an in-memory H2 database: derived queries (e.g. `findByPianoId`, eruptions sorted by date), database constraints (unique tax code, column lengths) and the zones saved as JSON and read back.

## FIRST principles

- **Fast**: unit tests use only mocks and run in about 4 seconds; Spring slices are loaded only where the integration itself is tested.
- **Independent**: `@ExtendWith(MockitoExtension.class)` creates fresh mocks for each test, static mocks are closed in `@AfterEach`, persistence tests are rolled back after each test. No test depends on another or on execution order.
- **Repeatable**: no external resources, dates relative to today, no shared state. The only clock-sensitive test is skipped with `assumeTrue` if it runs across midnight, instead of failing randomly.
- **Self-validating**: every test asserts the exact result: the exception type and message, the saved entity (`ArgumentCaptor`), missing calls (`verify(..., never())`) and call order (`InOrder`). No log has to be read.
- **Thorough**: tests cover valid and invalid inputs, boundary values (e.g. 2 and 30 characters for names), missing JSON fields, unauthorized users, database errors and Firebase failures, not only the happy path.

## Mocking external dependencies

All external dependencies are mocked with Mockito 5: the JPA repositories (database), `FirebaseService` and `FirebaseMessaging` (`mockStatic`, no real notification is sent), and `HttpServletRequest`/`HttpSession`. Pure logic (`Validator`, `Utility`, Jackson in the converters) is not mocked, because it is what the tests verify. The Mockito agent is loaded with `-javaagent` in Surefire, as recommended by Mockito for recent JDKs.

## Use of AI agents

Tests were drafted with an AI coding agent and then reviewed, following the lab guide:
- generated tests were checked one by one, and missing edge cases were added;
- the existing tests were reviewed against FIRST and fixed: some passed for the wrong reason (e.g. the TC-M-10.x tests all failed on "user not found" instead of their own scenario) or asserted only `assertThrows(Exception.class)`;
- test case IDs from the Test Case Specification were kept; new scenarios have descriptive `@DisplayName`s.

## Defects found by the tests

Writing tests that check real behaviour, not only mocked calls, revealed defects that the previous suite did not detect. All were fixed, each with a regression test:

| Area | Defect |
| :--- | :--- |
| GPE | Danger zones could not be saved (`Punto` not serializable) and safe zones lost their coordinates |
| GPE | Plans with zones sent to the API failed with HTTP 500 (wrong cast in `GPEControl`) |
| GPE | A plan name with surrounding spaces bypassed the duplicate check |
| GUM/GUW | Login without a password crashed (`NullPointerException`) instead of returning "wrong password" |
| GNF | Impossible birth dates (e.g. 31/02) were accepted |
| GNF | Creating a family unit without a vehicle always failed |
| GNF | Updating the residence was always rejected |
| GNF | The response of `/gestoreNucleo/crea` failed with infinite JSON recursion |
| Security | Password hashes were included in JSON responses |
| Security | Any user could accept an invitation meant for someone else |
| Security | `Utility.encrypt` padded hashes to 32 instead of 64 characters, so hashes starting with `0` were shorter than 64 characters |

## Running the tests

```bash
mvn test                              # whole suite
mvn test -Dtest='*ServiceImplTest'    # a subset
```

Surefire reports are written to `target/surefire-reports/` and uploaded by CI as the `test-results` artifact.
