# CI/CD Pipeline

This document describes the pipeline defined in `.github/workflows/ci.yml`.

## Triggers

The workflow runs automatically on:

- every **push** to the `main` branch;
- every **pull request** targeting the `main` branch.

## Stages

The workflow contains one job, `test` (shown as *Run Unit Tests*), which runs on a GitHub-hosted runner with the `ubuntu-latest` operating system. The job executes four steps in order:

1. **Checkout code** (`actions/checkout@v5`): downloads the code of the commit that triggered the workflow.
2. **Set up JDK 21** (`actions/setup-java@v4`): installs Java 21 (Temurin distribution), the version required by the project.
3. **Run tests** (`mvn -B test`): Maven downloads the dependencies declared in `pom.xml`, compiles the project and runs its test suite (unit tests, web layer tests and persistence tests on an in-memory H2 database; no external service is needed). If compilation fails or any test fails, the workflow run is marked as failed. `-B` (batch mode) keeps the log readable on the runner.
4. **Upload test results** (`actions/upload-artifact@v4`): saves the Surefire reports (`target/surefire-reports/`) as the `test-results` artifact of the run. It uses `if: always()`, so the reports are kept also when tests fail, which is when they are most useful. They can be downloaded from the run page in the **Actions** tab.

## Build reproducibility

- **Clean environment.** Every run starts on a new virtual machine, without files or configuration left over from previous runs or from a developer's computer. The result depends only on the code in the repository, avoiding the "works on my machine" problem.
- **Explicit versions.** The Java version is fixed in the workflow (21), and the dependencies and their versions are declared in `pom.xml`. Every run therefore uses the same toolchain and libraries.

## Code quality

- **Automated testing.** The test suite runs automatically on every push and pull request to `main`, without depending on developers remembering to run it.
- **Early detection of faults.** Because the workflow also runs on pull requests, a change that breaks compilation or a test is detected before it is merged into `main`. The result is shown in the pull request, next to the merge button.
- **Regression prevention.** Since all tests are executed on every change, a modification that breaks previously working behaviour is reported immediately.

# Docker Build Workflow

The workflow `.github/workflows/docker-build.yml` extends the pipeline. When `CI` succeeds on `main`, it builds the Docker image (see [Docker.md](Docker.md)) for `linux/amd64` and `linux/arm64` and pushes it to Docker Hub as `<DOCKER_USERNAME>/eruplanserver`.

## Setup

1. Create a Docker Hub account and a repository named `eruplanserver`.
2. In Docker Hub, create a **personal access token** with *Read & Write* scope (Account settings → Personal access tokens).
3. In GitHub, add two repository secrets (Settings → Secrets and variables → Actions):
   - `DOCKER_USERNAME`: the Docker Hub username;
   - `DOCKER_PASSWORD`: the access token, not the account password.
4. Push to `main`. Then follow the `CI` run, and the `Docker Build` run that it triggers, in the **Actions** tab, and check the new tags on Docker Hub.
