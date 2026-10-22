// BadrLink — messaging-auth-service
//
// Test, then build and publish a versioned container image. This replaces the
// previous GitHub Actions workflow; the stages and the published tags are the
// same.
//
// What the Jenkins side must provide:
//   * A multibranch pipeline job for this repository. Branch and tag builds are
//     discovered automatically; pull request builds (CHANGE_ID set) run tests
//     and build the image without publishing it.
//   * An agent with git, a working Docker daemon and JDK 21 on PATH. Maven is
//     supplied by the committed wrapper (./mvnw), so no Maven tool is needed.
//   * A credential named `ghcr-credentials` of kind "Username with password":
//     the GitHub username plus a PAT with `write:packages`.
//
// Published images:
//   ghcr.io/ziyadsamhaoui/messaging-auth-service:sha-<7 chars>  every build
//   ghcr.io/ziyadsamhaoui/messaging-auth-service:latest         main only
//   ghcr.io/ziyadsamhaoui/messaging-auth-service:<version>      v*.*.* tag builds
//
// `sha-<7 chars>` is the immutable tag a deployment pins. Layer caching comes
// from the agent's local Docker cache; nothing is pulled before the build.

pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        IMAGE = 'ghcr.io/ziyadsamhaoui/messaging-auth-service'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                // The wrapper downloads Maven; a Windows checkout carries no
                // executable bit on mvnw. Testcontainers suites use the agent's
                // Docker daemon.
                sh 'chmod +x mvnw && ./mvnw -B -ntp verify'
            }
        }

        stage('Build image') {
            steps {
                script {
                    def shortSha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                    def tags = ["sha-${shortSha}"]

                    // Multibranch pipelines set BRANCH_NAME for branches, and
                    // TAG_NAME in addition for tag builds.
                    if (!env.TAG_NAME && env.BRANCH_NAME == 'main') {
                        tags << 'latest'
                    }
                    if (env.TAG_NAME?.startsWith('v')) {
                        tags << env.TAG_NAME.substring(1)
                    }

                    def tagArgs = tags.collect { "-t ${IMAGE}:${it}" }.join(' ')
                    sh "docker build ${tagArgs} ."

                    if (env.CHANGE_ID) {
                        // Pull request: prove the Dockerfile builds, publish nothing.
                        echo 'Pull request build — image built but not pushed.'
                    } else {
                        withCredentials([usernamePassword(
                                credentialsId: 'ghcr-credentials',
                                usernameVariable: 'GHCR_USER',
                                passwordVariable: 'GHCR_TOKEN')]) {
                            sh 'echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin'
                            tags.each { tag ->
                                sh "docker push ${IMAGE}:${tag}"
                            }
                        }
                    }
                }
            }
        }
    }
}
