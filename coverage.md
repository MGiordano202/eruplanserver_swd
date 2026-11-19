# Code Coverage

Code coverage is measured with **JaCoCo** while the test suite described in [testing.md](testing.md) runs.

## Coverage Configuration

The `jacoco-maven-plugin` (version **0.8.12**) is declared in `pom.xml` with three executions:

| Execution | Phase | Purpose |
| :--- | :--- | :--- |
| `prepare-agent` | `initialize` | Attaches the JaCoCo agent to the test JVM through the `argLine` property |
| `report` | `test` | Writes the HTML, XML and CSV report to `target/site/jacoco/` |
| `check` | `verify` | Enforces the quality gates and fails the build if one is not met |

**Quality gates.** The rule is applied to every `PACKAGE`, so a well-tested package cannot hide an untested one:

| Counter | Minimum |
| :--- | ---: |
| Line coverage | 80% |
| Branch coverage | 75% |
| Class coverage | 100% |

**Related settings:**
- `lombok.config` sets `lombok.addLombokGeneratedAnnotation = true`. JaCoCo ignores code annotated as `@Generated`, so getters, setters and builders created by Lombok are not counted and the figures measure only hand-written code.
- Surefire uses `@{argLine} -javaagent:<mockito-core>`, so the JaCoCo and Mockito agents are both loaded.

```bash
mvn clean verify                      # tests + report + quality gates
# report: target/site/jacoco/index.html
```

**Continuous integration.** The CI pipeline ([CICD.md](CICD.md)) runs `mvn verify`, so the quality gates are checked on every push and pull request to `main`: a change that lowers coverage below a threshold fails the workflow.

## Coverage Analysis

Results of `mvn clean verify` on 412 tests (all quality gates met):

| Metric | Covered | Coverage |
| :--- | ---: | ---: |
| **Lines** | 721 / 741 | **97.3%** |
| **Branches** | 397 / 418 | **95.0%** |
| Methods | 120 / 122 | 98.4% |
| Classes | 33 / 33 | 100% |

**Before and after.** The first JaCoCo run showed 52% line and 64% branch coverage: controllers were almost untested (0-20%). After adding web and persistence tests the total reached 90%. Enabling the quality gates then made 6 packages fail (`Configuration` 0%, application class 25%, `Entity.GNF` 18%, `Exception.GNF` 50%, `Exception.GUM` 75% and one class never used). Instead of lowering the thresholds or excluding classes, tests were added for the missing behaviour: Firebase start-up with valid and invalid credentials, the `main` method, login when the HTTP response cannot be sent, request DTOs read from JSON and `Punto` equality.

**Classes with the lowest coverage:**

| Class | Lines | Branches | Reason |
| :--- | ---: | ---: | :--- |
| `Utility` | 75% | 50% | Hash padding branch: known defect, its test is `@Disabled` |
| `ZonaPericoloConverter` | 80% | 100% | `catch` of a JSON write error |
| `ZonaSicuraConverter` | 83% | 100% | Same as above |
| `URControl` | 100% | 85% | Logout and date-parsing edge branches |
| `GNFServiceImpl` | 96% | 89% | Validation branches of family unit creation |

**Methods and branches not covered:**
- `Validator` and `Utility` implicit constructors: the only 2 uncovered methods. The classes contain only static methods and are never instantiated.
- `Utility.encrypt`: padding of hashes shorter than 64 characters.
- `ZonaPericoloConverter` / `ZonaSicuraConverter.convertToDatabaseColumn`: the `JsonProcessingException` handler, which cannot be triggered with these simple objects.
- `GNFServiceImpl.creaNucleoFamiliare` (7 of 54 branches) and `aggiornaVeicolo` (4 of 12): null municipality or postcode, missing seats with a vehicle, user without a family unit.
- Single branches in `GNFControl`, `GSEControl` and `URControl.logout`: error paths of `abbandonaNucleo` and `rimuoviAppoggio`, an existing session at logout.

## Coverage Improvement Plan

1. **Family unit validation (highest priority).** `creaNucleoFamiliare` and `aggiornaVeicolo` hold most of the uncovered branches and are a critical path for evacuation planning. Add parameterized tests for each missing or invalid field and for users without a family unit.
2. **Controller error paths.** Add MockMvc tests where the service rejects the request in `abbandonaNucleo` and `rimuoviAppoggio`, and for logout with an active session.
3. **Hash padding defect.** Fix `Utility.encrypt` together with a migration of the stored hashes, then re-enable `UtilityTest.encrypt_lunghezzaFissa`: this removes the last uncovered branch of `Utility`.
4. **Code that cannot be reached.** Make the constructors of `Validator` and `Utility` private (utility classes); accept the converters' write-error handler as uncovered instead of writing artificial tests.
5. **Raise the bar gradually.** The CI already enforces the gates; as the plan above is completed, the branch threshold can be raised towards the current level (95%), so that new code keeps the same standard.
6. **Beyond coverage.** High coverage shows that code is executed, not that tests would detect a fault. Mutation testing will measure how many injected faults the tests actually catch.
