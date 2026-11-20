# Mutation Testing

Mutation testing is done with **PITest**. It checks whether the test suite in [testing.md](testing.md) detects small faults injected into the code. Coverage is in [coverage.md](coverage.md).

## Mutation Testing Configuration

The `pitest-maven` plugin (**1.30.2**) is declared in `pom.xml` with `pitest-junit5-plugin` **1.2.3**, which runs the JUnit 6 tests.

| Setting | Value |
| :--- | :--- |
| Target classes | `IS.Service.*`, `IS.Utility.*`, `IS.Control.*`, `IS.Entity.GPE.Punto` |
| Target tests | `it.unisa.eruplanserver.*` |
| Excluded classes | `Configuration.*`, `EruplanServerApplication`, `IS.Exception.*`, `IS.Repository.*` |
| Mutators | `CONDITIONALS_BOUNDARY`, `INVERT_NEGS`, `NEGATE_CONDITIONALS`, `REMOVE_CONDITIONALS`, `EMPTY_RETURNS`, `FALSE_RETURNS`, `TRUE_RETURNS`, `NULL_RETURNS`, `PRIMITIVE_RETURNS` |
| Quality gates | `mutationThreshold` 80, `coverageThreshold` 80 |
| Timeouts / threads | `timeoutConstant` 5000, `timeoutFactor` 1.5, 4 threads |
| Output formats | HTML, XML |

Excluded classes have no logic to mutate: repository interfaces, exceptions, Spring and Firebase start-up. Entities are left out except `Punto`, the only one with hand-written code (`equals`, `hashCode`); PIT already skips Lombok code. The mutators target the conditions and return values that hold most of the logic.

```bash
mvn test-compile org.pitest:pitest-maven:mutationCoverage
# report: target/pit-reports/index.html
```

## Mutation Analysis

**Mutation score 95%**: 702 mutants, **666 killed** (2 by timeout), **34 survived**, **2 with no coverage**; test strength 95%, line coverage 97%. Both gates are met.

| Class | Mutants | Killed | Survived | Score |
| :--- | ---: | ---: | ---: | ---: |
| `GNFServiceImpl` | 212 | 194 | 18 | 91% |
| `Validator` | 187 | 178 | 9 | 95% |
| `GNFControl` | 90 | 87 | 1 (+2 no cov.) | 96% |
| `GSEControl` | 55 | 54 | 1 | 98% |
| `URControl` | 27 | 25 | 2 | 92% |
| `GPEControl` / `OPCControl` | 10 | 8 | 2 | 80% |
| `Punto` | 15 | 14 | 1 | 93% |
| Other services, converters, `Utility` | 106 | 106 | 0 | 100% |

`NEGATE_CONDITIONALS` and the return-value mutators are almost all killed: tests assert exact results. `REMOVE_CONDITIONALS` (29 survivors) and `CONDITIONALS_BOUNDARY` (4 of 15) survive the most.

**Common patterns in survived mutations:**
1. **Redundant checks (equivalent mutants).** The `Validator.is...Valid` methods check `compareTo("") == 0` before a regex that already rejects the empty string; `creaNucleoFamiliare` checks `civico.trim().isEmpty()` and then `civico.length() < 1`. Removing one check changes nothing, so these mutants cannot be killed.
2. **Untested guard clauses.** `null` or missing inputs that no test sends: `accettaInvito` with no invited user, `aggiornaVeicolo` and `rimuoviAppoggio` without a family unit, empty municipality or postcode in `creaNucleoFamiliare`, a `null` invited tax code in `invitaUtente`.
3. **Missing boundary values.** `numeroPostiVeicolo > 8` is tested with 4 seats, not 8 or 9; `creaZonaPericolo` (`size() <= 3`) is not tested with exactly 3 and 4 points; `comune.length() < 2` not with 1 and 2 characters.
4. **Controller edge branches.** Logout with an active session (`URControl`, `OPCControl`), a `null` list in `GSEControl`, registration without a birth date, and the error paths of `abbandonaNucleo` and `rimuoviAppoggio` (no coverage).

**Areas with low mutation coverage:** `GNFServiceImpl` (family unit creation and vehicle update) and the session handling of the controllers.

## Mutation Score Improvement Plan

1. **Family unit service (highest priority).** Add parameterized tests for `creaNucleoFamiliare` and `aggiornaVeicolo` with `null`/empty fields and boundary values (8 and 9 seats, 1 and 2 characters), each asserting the exact message. This also covers the gaps in [coverage.md](coverage.md).
2. **Guard clauses and boundaries.** Test `accettaInvito` without an invited user, `rimuoviAppoggio` without a family unit, and danger zones with 3 and 4 points.
3. **Controllers.** Add MockMvc tests for logout with an active session, a `null` result in `GSEControl`, registration without a date and the error paths of `abbandonaNucleo` and `rimuoviAppoggio`.
4. **Equivalent mutants.** Remove the redundant checks (`compareTo("")`, `civico.length() < 1`) instead of writing tests that cannot kill them; then check that `Punto.hashCode` differs for different points.
5. **Raise the bar.** Once these are done, raise `mutationThreshold` towards 90% and run PITest in a separate CI workflow, so the main pipeline stays fast.
