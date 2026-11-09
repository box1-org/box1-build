# box1-build

Build images for Box1 CI. Tracking issue: box1-org/box1-docs#113.

## box1-builder

`builder/Containerfile` builds the image that Box1's Tekton build and security
Tasks run in. Its purpose is to stop every CI run from downloading its tools and
Maven dependencies from the internet.

| Path in the image | What it is |
| --- | --- |
| `/opt/maven` (`mvn` on `PATH`) | Apache Maven 3.9.16, sha256-verified |
| `/usr/local/bin/syft` | syft 1.55.0 (SBOM), sha256-verified |
| `/usr/local/bin/roxctl` | roxctl 4.11.6 (RHACS), sha256-verified |
| `/usr/local/bin/python3` | Python 3.11, for the Task scripts |
| `/opt/box1/settings.xml` | Maven settings: every repository mirrored to the in-cluster Reposilite (`http://reposilite.box1-maven.svc:8080/maven-central`) |
| `/opt/box1/m2` | Read-only Maven repository, baked at image build time |
| `/opt/box1/release` | The versions above, plus the Java and Quarkus platform versions |

- **Base image:** `registry.access.redhat.com/ubi9/openjdk-21`, pinned by
  digest. This is the same base the `box1-quarkus-maven` Task used before. The
  base also contains Maven 3.9.9 and Python 3.9 in `/usr/bin`; the pinned
  versions in `/usr/local/bin` take precedence.
- **User:** the image runs as UID 185. Task pods run as the namespace's
  arbitrary UID, and everything under `/opt/box1` is world-readable.

### The baked Maven repository

`builder/warmup` is a throwaway Quarkus project. It declares the union of
extensions and build plugins used by the ten Box1 Quarkus services, at their
versions: Quarkus platform 3.40.1.1, compiler, surefire/failsafe 3.5.6,
checkstyle 3.6.0, and spotbugs 4.10.5.0 with findsecbugs.

The image build runs the full `verify` lifecycle twice:

- **`core` profile:** the Quarkus BOM only.
- **`camel` profile:** the Camel BOM imported first, as in
  fulfillment-adapter.

The import order changes some resolved versions, so both passes are needed.

Builds use the baked repository as a read-only *tail* (Maven 3.9+):

```sh
mvn -s /opt/box1/settings.xml \
    -Dmaven.repo.local=/workspace/m2 \
    -Dmaven.repo.local.tail=/opt/box1/m2 verify
```

Maven reads cached artifacts from the tail and writes anything new to
`/workspace/m2`. Anything new comes from Reposilite, which in turn fetches from
Maven Central only once.

**Coverage, verified 2026-11-08 on a workstation with Maven 3.9.16:** each
service was built in **offline** mode, with an empty `maven.repo.local` and only
this cache as the tail.

- All ten services resolved every Maven artifact from the cache.
- Nine built successfully.
- operations-console resolved all its Maven artifacts too, but then failed: its
  build downloads Node.js from nodejs.org and runs `npm ci` against the npm
  registry, and neither is a Maven artifact. That service still needs the
  internet, or a later Node/npm mirror.

**Keeping it current:** when the services move to a new Quarkus platform or
plugin version, bump the matching property in `builder/warmup/pom.xml`. Then
release a new image and bump its digest in box1-gitops. Until then, builds
still work; they just fetch the new versions through Reposilite.

## CI

| Event | PipelineRun | Service account | Result |
| --- | --- | --- | --- |
| Pull request to `main` | `.tekton/pull-request.yaml` | `ci-builder-check` (buildah SCC only) | Builds the image; the warm-up `verify` and each tool's version command are the smoke test. Nothing is pushed. |
| Push to `main` | `.tekton/release.yaml` | `ci-builder-release` | Builds and pushes `box1-build/box1-builder:<sha12>` and prints the digest. |

**Consuming a release:** the Tasks in box1-gitops
`platform/pipelines/tasks` reference the image by digest, as
`image-registry.openshift-image-registry.svc:5000/box1-build/box1-builder@sha256:…`.
Bumping that digest is a reviewed box1-gitops PR.

**Ownership:** the Pipelines as Code Repository, service accounts, ImageStream
and RBAC live in box1-gitops, in `platform/pipelines/ci/box1-build.yaml`.

## Building locally

From a workstation, Reposilite is not reachable. Point the mirror at Maven
Central instead:

```sh
podman build --platform linux/amd64 \
  --build-arg BOX1_MAVEN_MIRROR=https://repo.maven.apache.org/maven2 \
  -t localhost/box1-builder:dev -f builder/Containerfile builder
```

The resulting image's `/opt/box1/settings.xml` then points at Central. Use
local builds for testing only.
