# CI/CD Pipeline

This document describes the pipeline defined in `.github/workflows/ci.yml`.

## Triggers

The workflow runs automatically on:

- every **push** to the `main` branch;
- every **pull request** targeting the `main` branch.

## Stages

The workflow contains one job, `test`, which runs on a GitHub-hosted runner with the `ubuntu-latest` operating system. The job executes three steps in order:

1. **Checkout code** (`actions/checkout@v4`): downloads the code of the commit that triggered the workflow.
2. **Set up JDK 21** (`actions/setup-java@v4`): installs Java 21 (Temurin distribution), the version required by the project.
3. **Run tests** (`mvn test`): Maven downloads the dependencies declared in `pom.xml`, compiles the project and runs its test suite. If compilation fails or any test fails, the workflow run is marked as failed.

## Build reproducibility

- **Clean environment.** Every run starts on a new virtual machine, without files or configuration left over from previous runs or from a developer's computer. The result depends only on the code in the repository, avoiding the "works on my machine" problem.
- **Explicit versions.** The Java version is fixed in the workflow (21), and the dependencies and their versions are declared in `pom.xml`. Every run therefore uses the same toolchain and libraries.
- **Fixed action versions.** The actions used are referenced with an explicit version tag (`@v4`).

## Code quality

- **Automated testing.** The test suite runs automatically on every push and pull request to `main`, without depending on developers remembering to run it.
- **Early detection of faults.** Because the workflow also runs on pull requests, a change that breaks compilation or a test is detected before it is merged into `main`. The result is shown in the pull request, next to the merge button.
- **Regression prevention.** Since all tests are executed on every change, a modification that breaks previously working behaviour is reported immediately.