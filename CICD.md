# CI/CD Pipeline

This document describes the pipeline defined in `.github/workflows/ci.yml` and the workflows that extend it.

## Triggers

The workflow runs automatically on:

- every **push** to the `main` branch;
- every **pull request** targeting the `main` branch.

## Stages

The workflow contains one job, `test` (shown as *Run Unit Tests*), which runs on a GitHub-hosted runner with the `ubuntu-latest` operating system. The job executes these steps in order:

1. **Checkout code** (`actions/checkout@v5`): downloads the code of the commit that triggered the workflow.
2. **Set up JDK 21** (`actions/setup-java@v4`): installs Java 21 (Temurin distribution), the version required by the project, and caches the Maven dependencies between runs.
3. **Run tests with coverage quality gates** (`mvn -B verify`): Maven compiles the project and runs its test suite (unit tests, web layer tests and persistence tests on an in-memory H2 database; no external service is needed). JaCoCo writes the coverage report and then checks the quality gates described in [coverage.md](coverage.md). If compilation fails, a test fails or coverage goes below a threshold, the workflow run is marked as failed. `-B` (batch mode) keeps the log readable on the runner.
4. **Upload test results** (`actions/upload-artifact@v4`): saves the Surefire reports (`target/surefire-reports/`) as the `test-results` artifact of the run.
5. **Upload coverage report** (`actions/upload-artifact@v4`): saves the JaCoCo HTML report (`target/site/jacoco/`) as the `coverage-report` artifact.
6. **Publish test report** (`dorny/test-reporter@v2`): shows the test results as a *Maven Test Results* check on the commit and in pull requests. It needs the `checks: write` permission.
7. **Upload coverage to Codecov** (`codecov/codecov-action@v5`): sends `jacoco.xml` to Codecov, which tracks coverage over time and comments it on pull requests.
8. **Upload test results to Codecov** (`codecov/codecov-action@v5`, `report_type: test_results`): sends the JUnit reports, so Codecov can show failing and flaky tests.

Steps 4-8 also run when tests or quality gates fail, which is when the reports are most useful. Artifacts can be downloaded from the run page in the **Actions** tab. A Codecov upload error does not fail the build (`fail_ci_if_error: false`).

## Build reproducibility

- **Clean environment.** Every run starts on a new virtual machine, without files or configuration left over from previous runs or from a developer's computer. The result depends only on the code in the repository, avoiding the "works on my machine" problem.
- **Explicit versions.** The Java version is fixed in the workflow (21), and the dependencies and their versions are declared in `pom.xml`. Every run therefore uses the same toolchain and libraries.

## Code quality

- **Automated testing.** The test suite runs automatically on every push and pull request to `main`, without depending on developers remembering to run it.
- **Early detection of faults.** Because the workflow also runs on pull requests, a change that breaks compilation or a test is detected before it is merged into `main`. The result is shown in the pull request, next to the merge button.
- **Regression prevention.** Since all tests are executed on every change, a modification that breaks previously working behaviour is reported immediately.
- **Coverage gates.** A change that lowers line, branch or class coverage below the JaCoCo thresholds fails the workflow, so untested code cannot be merged unnoticed.

## Codecov setup

1. Sign in to [Codecov](https://about.codecov.io) with GitHub. The repository owner activates the repository by installing the Codecov GitHub App.
2. Copy the repository upload token (*Configuration → General*).
3. In GitHub, add it as the repository secret `CODECOV_TOKEN` (Settings → Secrets and variables → Actions).

# Mutation Testing Workflow

The workflow `.github/workflows/mutation.yml` runs PITest (see [mutation.md](mutation.md)). It is separate from `CI` because mutation testing takes a few minutes and would slow down every run.

- **Triggers:** the same as `CI` (push and pull request to `main`), plus manual start (`workflow_dispatch`). A newer push cancels the run still in progress on the same branch.
- **Steps:** checkout, JDK 21 with Maven cache, `mvn -B test-compile org.pitest:pitest-maven:mutationCoverage`, upload of `target/pit-reports/` as the `mutation-report` artifact (also on failure).
- **Quality gate:** the run fails if the mutation score or the line coverage of the mutated classes is below 80%, the thresholds configured in `pom.xml`.

# Docker Build Workflow

The workflow `.github/workflows/docker-build.yml` extends the pipeline. When `CI` succeeds on `main`, it builds the Docker image (see [Docker.md](Docker.md)) for `linux/amd64` and `linux/arm64` and pushes it to Docker Hub as `<DOCKER_USERNAME>/eruplanserver`.

## Setup

1. Create a Docker Hub account and a repository named `eruplanserver`.
2. In Docker Hub, create a **personal access token** with *Read & Write* scope (Account settings → Personal access tokens).
3. In GitHub, add two repository secrets (Settings → Secrets and variables → Actions):
   - `DOCKER_USERNAME`: the Docker Hub username;
   - `DOCKER_PASSWORD`: the access token, not the account password.
4. Push to `main`. Then follow the `CI` run, and the `Docker Build` run that it triggers, in the **Actions** tab, and check the new tags on Docker Hub.
