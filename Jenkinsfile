pipeline {
    agent any

    parameters {
        string(name: 'ROLLBACK_VERSION', defaultValue: '',
               description: 'Leave empty for a normal build. Set to an existing image tag to roll back to it.')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        REGISTRY  = 'localhost:5001'
        IMAGE     = "${REGISTRY}/finsecure-pay"
        NAMESPACE = 'finsecure'
        APP_URL   = 'http://finsecure-control-plane:30080'
        SONAR_PLUGIN = '5.8.0.7211'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.ROLLBACK = params.ROLLBACK_VERSION?.trim() ? 'true' : 'false'
                }
            }
        }

        stage('Build') {
            when { environment name: 'ROLLBACK', value: 'false' }
            steps {
                sh 'mvn -B -q clean compile'
            }
        }

        stage('Test') {
            when { environment name: 'ROLLBACK', value: 'false' }
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            when { environment name: 'ROLLBACK', value: 'false' }
            steps {
                sh 'mvn -B -q -DskipTests package'
                script {
                    def pomVersion = sh(script: 'mvn -q help:evaluate -Dexpression=project.version -DforceStdout', returnStdout: true).trim()
                    env.VERSION = "${pomVersion}-${env.BUILD_NUMBER}"
                }
                archiveArtifacts artifacts: 'target/finsecure-pay.jar', fingerprint: true
            }
        }

        stage('Security Checks') {
            when { environment name: 'ROLLBACK', value: 'false' }
            parallel {
                stage('Static Analysis') {
                    steps {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            sh '''
                                mvn -B -q org.sonarsource.scanner.maven:sonar-maven-plugin:${SONAR_PLUGIN}:sonar \
                                    -Dsonar.host.url=http://sonarqube:9000 -Dsonar.token=$SONAR_TOKEN \
                                    -Dsonar.projectKey=finsecure-pay -Dsonar.projectName=finsecure-pay \
                                    -Dsonar.qualitygate.wait=true
                            '''
                        }
                    }
                }
                stage('Secret Scan') {
                    steps {
                        sh 'trivy fs --scanners secret --skip-dirs target --exit-code 1 --quiet .'
                    }
                }
                stage('Dependency Scan') {
                    steps {
                        sh '''
                            mkdir -p reports
                            trivy rootfs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 \
                                --format table --output reports/dependency-scan.txt target/finsecure-pay.jar
                        '''
                    }
                    post {
                        always {
                            sh 'cat reports/dependency-scan.txt || true'
                            archiveArtifacts artifacts: 'reports/dependency-scan.txt', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Container Build') {
            when { environment name: 'ROLLBACK', value: 'false' }
            steps {
                sh 'docker build -t ${IMAGE}:${VERSION} --build-arg APP_VERSION=${VERSION} --label git-commit=${GIT_SHORT} .'
            }
        }

        stage('Image Scan') {
            when { environment name: 'ROLLBACK', value: 'false' }
            steps {
                sh '''
                    trivy image --severity HIGH,CRITICAL --exit-code 1 \
                        --format table --output reports/image-scan.txt ${IMAGE}:${VERSION}
                '''
            }
            post {
                always {
                    sh 'cat reports/image-scan.txt || true'
                    archiveArtifacts artifacts: 'reports/image-scan.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Push') {
            when { environment name: 'ROLLBACK', value: 'false' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry-creds',
                                                  usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    sh '''
                        echo "$REG_PASS" | docker login ${REGISTRY} -u "$REG_USER" --password-stdin
                        docker push ${IMAGE}:${VERSION}
                        docker logout ${REGISTRY}
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    if (env.ROLLBACK == 'true') {
                        env.VERSION = params.ROLLBACK_VERSION.trim()
                    }
                }
                withCredentials([file(credentialsId: 'kubeconfig-deployer', variable: 'KUBECONFIG'),
                                 usernamePassword(credentialsId: 'registry-creds',
                                                  usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    sh '''
                        kubectl create secret docker-registry regcred -n ${NAMESPACE} \
                            --docker-server=${REGISTRY} --docker-username="$REG_USER" --docker-password="$REG_PASS" \
                            --dry-run=client -o yaml | kubectl apply -f -

                        kubectl apply -f k8s/configmap.yaml -f k8s/service.yaml
                        sed -e "s#image: .*finsecure-pay:.*#image: ${IMAGE}:${VERSION}#" \
                            -e "s#value: \\"1.0.0\\"#value: \\"${VERSION}\\"#" k8s/deployment.yaml | kubectl apply -f -
                        kubectl annotate deployment/finsecure-pay -n ${NAMESPACE} --overwrite \
                            kubernetes.io/change-cause="build ${BUILD_NUMBER}: ${VERSION}"

                        if ! kubectl rollout status deployment/finsecure-pay -n ${NAMESPACE} --timeout=180s; then
                            echo "Rollout failed, rolling back"
                            kubectl rollout undo deployment/finsecure-pay -n ${NAMESPACE}
                            exit 1
                        fi
                        kubectl get pods -n ${NAMESPACE} -l app=finsecure-pay
                    '''
                }
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    for i in $(seq 1 10); do
                        curl -sf ${APP_URL}/health | tee health.json && break
                        sleep 3
                    done
                    grep -q "\\"version\\":\\"${VERSION}\\"" health.json
                '''
            }
        }
    }

    post {
        success {
            emailext subject: "SUCCESS: finsecure-pay #${env.BUILD_NUMBER} deployed ${env.VERSION}",
                     body: "<p>Build <b>#${env.BUILD_NUMBER}</b> deployed version <b>${env.VERSION}</b> (commit ${env.GIT_SHORT}).</p><p><a href='${env.BUILD_URL}'>Open build</a></p>",
                     to: '$DEFAULT_RECIPIENTS'
        }
        failure {
            emailext subject: "FAILED: finsecure-pay #${env.BUILD_NUMBER}",
                     body: "<p>Build <b>#${env.BUILD_NUMBER}</b> failed (commit ${env.GIT_SHORT}). Nothing was deployed.</p><p><a href='${env.BUILD_URL}console'>Console log</a></p>",
                     to: '$DEFAULT_RECIPIENTS',
                     attachLog: true
        }
    }
}
