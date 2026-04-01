# Implementation Plan: [DSC] DSP Conformance Tests

## Overview

Integrate the Eclipse DSP Technology Compatibility Kit (TCK) into the fdsc-edc project so that DSP conformance tests run automatically on every push in CI, and can also be executed locally from the command line. The repository already has significant TCK groundwork in the `test-extension` module (webhook controller, guards, step recorders, test identity service); this plan focuses on creating the runtime configuration, Docker Compose orchestration, local run script, and CI workflow to wire everything together.

## Steps

### Step 1: Create the TCK configuration properties file

Create `config/tck/tck.properties` containing all DSP TCK configuration properties required by the `eclipsedataspacetck/dsp-tck-runtime` Docker image.

**Details:**
- Create directory `config/tck/`.
- Create `config/tck/tck.properties` with all required TCK properties:
  - `dataspacetck.dsp.connector.agent.id` — the connector's participant ID (e.g., `urn:connector:fdsc-edc`)
  - `dataspacetck.dsp.connector.http.url` — DSP protocol endpoint (e.g., `http://edc:8282/protocol`)
  - `dataspacetck.dsp.connector.http.base.url` — base URL for metadata endpoint (e.g., `http://edc:8282`)
  - `dataspacetck.callback.address` — TCK callback URL (e.g., `http://tck:8083`)
  - `dataspacetck.port` — TCK server port (e.g., `8083`)
  - `dataspacetck.dsp.default.wait` — wait time for responses (e.g., `15000`)
  - `dataspacetck.dsp.connector.negotiation.initiate.url` — consumer negotiation webhook (e.g., `http://edc:8687/tck/negotiations/requests`)
  - `dataspacetck.dsp.connector.transfer.initiate.url` — consumer transfer webhook (e.g., `http://edc:8687/tck/transfers/requests`)
  - Dataset IDs matching the asset IDs in `DataAssembly.java` (e.g., `CAT_01_01_DATASETID=CAT0101`, `CN_01_01_OFFERID=ACN0101`, etc.)
  - Agreement IDs matching `DataAssembly.java` (e.g., `TP_01_01_AGREEMENTID=ATP0101`, etc.)
- Include inline comments explaining each property group.

**Files affected:**
- `config/tck/tck.properties` (new)

**Acceptance criteria:**
- The properties file contains all required TCK configuration keys with correct values matching the existing `DataAssembly.java` asset/agreement IDs.
- Properties use Docker Compose service names for hostnames so they work in the compose network.

### Step 2: Create the EDC controlplane configuration for TCK mode

Create an EDC properties file that configures the controlplane to run in TCK-compatible mode, enabling the test extensions and setting up the required EDC ports and paths.

**Details:**
- Create `config/tck/edc.properties` with:
  - `edc.participant.id` — matching the agent ID in TCK config
  - `edc.hostname` — set to the Docker service name
  - DSP protocol port and path (`web.http.protocol.port=8282`, `web.http.protocol.path=/protocol`)
  - Management API port and path (`web.http.management.port=8181`, `web.http.management.path=/management`)
  - Default/control API port (`web.http.port=8080`, `web.http.path=/api`)
  - TCK webhook port and path (`web.http.tck.port=8687`, `web.http.tck.path=/tck`)
  - Enable test extensions: `testExtension.enabled=true`, `testExtension.controller.enabled=true`, `testExtension.identity.enabled=true`
  - DSP callback address pointing to the controlplane's protocol endpoint
  - Any other required EDC runtime settings (e.g., filesystem vault, no HashiCorp Vault)

**Files affected:**
- `config/tck/edc.properties` (new)

**Acceptance criteria:**
- The controlplane starts successfully with this configuration.
- Test identity, TCK controller, and TCK guard extensions are enabled.
- Ports match those referenced in `tck.properties`.

### Step 3: Create Docker Compose file for TCK orchestration

Create a `docker-compose.tck.yml` at the repository root that orchestrates the controlplane and TCK runner containers.

**Details:**
- Define a `edc` service:
  - Build from the `controlplane-oid4vc` module's shaded JAR using the existing `docker/Dockerfile`.
  - Alternatively, use a pre-built image pattern: `fdsc-edc-controlplane-oid4vc:${VERSION:-0.0.1-SNAPSHOT}`.
  - Mount `config/tck/edc.properties` as the EDC configuration file (via `EDC_FS_CONFIG` environment variable or JVM arg).
  - Expose ports: 8080 (default), 8181 (management), 8282 (protocol), 8687 (tck webhook).
  - Health check using `curl` on the default API port.
- Define a `tck` service:
  - Image: `eclipsedataspacetck/dsp-tck-runtime:latest`
  - Mount `config/tck/tck.properties` to `/etc/tck/config.properties`.
  - `depends_on` the `edc` service with a health check condition.
  - Add `host.docker.internal` if needed for callback resolution.
- Both services on a shared Docker network.
- Include a `--abort-on-container-exit` compatible setup so CI can detect pass/fail from the TCK exit code.

**Files affected:**
- `docker-compose.tck.yml` (new)

**Acceptance criteria:**
- `docker compose -f docker-compose.tck.yml up` starts both containers.
- The TCK container waits for the EDC controlplane to be healthy before starting tests.
- The TCK container's exit code reflects test pass (0) or failure (non-zero).

### Step 4: Create a local run script for TCK tests

Create a shell script `scripts/run-tck.sh` that allows developers to run TCK conformance tests from the command line with a single command.

**Details:**
- Create directory `scripts/`.
- Create `scripts/run-tck.sh` (executable) that:
  1. Builds the controlplane shaded JAR (`mvn clean package -pl controlplane-oid4vc -am -DskipTests`).
  2. Prepares the Docker build context (copies JAR and Dockerfile).
  3. Runs `docker compose -f docker-compose.tck.yml up --build --abort-on-container-exit --exit-code-from tck`.
  4. Captures the TCK exit code and prints a summary.
  5. Optionally tears down containers with `docker compose -f docker-compose.tck.yml down`.
- Accept optional flags:
  - `--skip-build` to skip the Maven build step if the JAR already exists.
  - `--keep-containers` to leave containers running for debugging.
- Add helpful usage output with `--help`.

**Files affected:**
- `scripts/run-tck.sh` (new)

**Acceptance criteria:**
- Running `./scripts/run-tck.sh` from the repo root builds, starts, runs TCK tests, and reports results.
- The script exits with a non-zero code if TCK tests fail.
- The `--skip-build` flag works correctly.

### Step 5: Add the TCK conformance test job to the CI workflow

Extend the existing `.github/workflows/test.yml` (or create a new workflow file) to run DSP TCK conformance tests on every push.

**Details:**
- Add a new job `tck-conformance` to `.github/workflows/test.yml` (or create `.github/workflows/tck.yml` if separation is preferred):
  - Runs on `ubuntu-latest`.
  - Sets up Java 21.
  - Builds the controlplane shaded JAR: `mvn clean package -pl controlplane-oid4vc -am -DskipTests`.
  - Prepares Docker build context for the controlplane image.
  - Runs `docker compose -f docker-compose.tck.yml up --build --abort-on-container-exit --exit-code-from tck`.
  - Captures TCK container logs as a build artifact on failure.
  - Tears down containers in a cleanup step (`if: always()`).
- The job should depend on the existing `test` job (or at least on spotless check passing) to avoid running TCK if basic tests fail.
- Ensure Docker Compose v2 is available in the runner (it is by default on `ubuntu-latest`).

**Files affected:**
- `.github/workflows/test.yml` (modified) or `.github/workflows/tck.yml` (new)

**Acceptance criteria:**
- TCK tests run automatically on every push.
- CI fails if any TCK conformance test fails.
- Container logs are uploaded as artifacts on failure for debugging.
- The workflow is efficient (builds only what is needed, uses caching where possible).

### Step 6: Add Maven caching and Docker layer caching to CI

Optimize CI performance by adding Maven dependency caching and Docker build caching to the workflow.

**Details:**
- Add `actions/cache` for Maven local repository (`~/.m2/repository`) keyed on `pom.xml` hash.
- Consider using `docker/build-push-action` with GitHub Actions cache for Docker layer caching, or leverage Docker Compose build cache.
- Update the existing `test` job to also use Maven caching if not already configured.

**Files affected:**
- `.github/workflows/test.yml` (modified)

**Acceptance criteria:**
- Subsequent CI runs with unchanged dependencies are significantly faster.
- Cache keys are correctly scoped to avoid stale caches.

### Step 7: Add documentation for running TCK tests

Update project documentation to explain how to run DSP conformance tests locally and in CI.

**Details:**
- Update `README.md` with a new section on DSP conformance testing:
  - Prerequisites (Docker, Docker Compose, Java 21, Maven).
  - How to run locally: `./scripts/run-tck.sh`.
  - How to run manually with Docker Compose.
  - How to interpret results.
  - Link to the DSP TCK repository for reference.
- Document the TCK configuration properties and how to customize them.
- Mention that TCK tests run automatically in CI on every push.

**Files affected:**
- `README.md` (modified)

**Acceptance criteria:**
- A developer can follow the README instructions to run TCK tests locally.
- The documentation is clear and complete.

### Step 8: Verify end-to-end TCK integration

Perform a final verification pass to ensure all components work together correctly.

**Details:**
- Verify that `docker-compose.tck.yml` correctly builds and starts the controlplane.
- Verify the TCK runner connects to the controlplane and executes all test suites (MET, CAT, CN, CN_C, TP, TP_C).
- Verify the CI workflow completes successfully.
- Verify the local run script works as documented.
- Check that all asset IDs, agreement IDs, and offer IDs in `tck.properties` align with the IDs defined in `DataAssembly.java`.
- Confirm TCK webhook endpoints (`/tck/negotiations/requests` and `/tck/transfers/requests`) are correctly reachable from the TCK container.
- Fix any configuration mismatches or runtime issues discovered during verification.

**Files affected:**
- Potentially any files from Steps 1-7 if fixes are needed.

**Acceptance criteria:**
- All TCK test suites pass against the controlplane.
- CI pipeline runs green with TCK tests included.
- Local execution via `scripts/run-tck.sh` works end-to-end.
