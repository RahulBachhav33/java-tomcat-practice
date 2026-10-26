pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        AWS_REGION = 'ap-south-1'
        ECR_REPOSITORY = 'java-tomcat-practice'
        EKS_CLUSTER = 'k8s-practice'
        // Configure AWS_ACCOUNT_ID as a Jenkins job/global environment variable.
        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        IMAGE_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/java-tomcat-practice"
        IMAGE_TAG = "${GIT_COMMIT_SHORT}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim()
                    env.IMAGE_TAG = env.GIT_COMMIT_SHORT
                }
            }
        }

        stage('Test and package WAR') {
            steps {
                sh 'mvn --batch-mode --no-transfer-progress clean verify'
                archiveArtifacts artifacts: 'target/ROOT.war', fingerprint: true
            }
        }

        stage('Build container image') {
            steps {
                sh 'docker build --pull -t "$IMAGE_URI:$IMAGE_TAG" .'
            }
        }

        stage('Push image to ECR') {
            steps {
                sh '''
                    aws sts get-caller-identity
                    aws ecr get-login-password --region "$AWS_REGION" \
                      | docker login --username AWS --password-stdin "$ECR_REGISTRY"
                    docker push "$IMAGE_URI:$IMAGE_TAG"
                '''
            }
        }

        stage('Deploy DEV') {
            steps {
                sh '''
                    aws eks update-kubeconfig --name "$EKS_CLUSTER" --region "$AWS_REGION"
                    kubectl get namespace dev
                    sed -e "s|IMAGE_URI_PLACEHOLDER|$IMAGE_URI:$IMAGE_TAG|g" \
                        -e 's|ENVIRONMENT_PLACEHOLDER|dev|g' \
                        k8s/deployment.yml | kubectl apply -n dev -f -
                    kubectl rollout status deployment/java-tomcat-practice -n dev --timeout=180s
                    kubectl get pods,svc -n dev
                '''
            }
        }

        stage('Approve PROD promotion') {
            steps {
                input message: "Promote image ${IMAGE_URI}:${IMAGE_TAG} to PROD?", ok: 'Deploy to PROD'
            }
        }

        stage('Deploy PROD') {
            steps {
                sh '''
                    kubectl get namespace prod
                    sed -e "s|IMAGE_URI_PLACEHOLDER|$IMAGE_URI:$IMAGE_TAG|g" \
                        -e 's|ENVIRONMENT_PLACEHOLDER|prod|g' \
                        k8s/deployment.yml | kubectl apply -n prod -f -
                    kubectl rollout status deployment/java-tomcat-practice -n prod --timeout=180s
                    kubectl get pods,svc -n prod
                '''
            }
        }
    }

    post {
        always {
            sh 'docker logout "$ECR_REGISTRY" || true'
            sh 'docker image rm "$IMAGE_URI:$IMAGE_TAG" || true'
        }
    }
}
