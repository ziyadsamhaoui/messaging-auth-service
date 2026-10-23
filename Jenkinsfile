pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh 'chmod +x mvnw && ./mvnw -B -ntp verify'
            }
        }

        stage('Build image') {
            steps {
                script {
                    def shortSha = sh(
                        script: 'git rev-parse --short=7 HEAD',
                        returnStdout: true
                    ).trim()

                    def image = "messaging-auth-service:sha-${shortSha}"

                    sh "docker build -t ${image} ."

                    echo "Built Docker image: ${image}"
                }
            }
        }
    }
}
