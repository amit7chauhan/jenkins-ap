// Function to delete existing helm chart

def deleteExistingHelmChart() {              
    container('jenkins-agent') {
        withFolderProperties {
            sh '''
            helm repo add stable https://artifactory.test.cicd.com/artifactory/anuragok-helm-virtual
            if [ $(helm list -n ${TASK_NAMESPACE} | grep seed-${ENVIRONMENT_NAME} >/dev/null; echo $?) -eq 0 ];
            then
                helm delete seed-${ENVIRONMENT_NAME} -n ${TASK_NAMESPACE};
            fi
            '''
        }
    }
}

def setServiceVersion(String upstreamEnv) {
    withFolderProperties {
        container('jenkins-agent') {
            script {
                if("${ENVIRONMENT_NAME}" == "sit") {
                    def app = sh(returnStdout: true, script: "curl -k 'https://artifactory.test.cicd.com/artifactory/anurag-generic/services-build-versions/service/version.txt'")
                    env.SERVICE_VERSION = app.split('=')[1]
                }
                else {
                    def upstream_build_number = UPSTREAM_BUILD.split('#')[1]
                    def app = sh(returnStdout: true, script: "curl -k https://artifactory.test.cicd.com/artifactory/anurag-generic/${upstreamEnv}-verified/seed/version-${upstream_build_number}.txt")
                    env.SERVICE_VERSION = app.split('=')[1]
                }
            }
        }
    }
}

def installHelmChart() {
    withFolderProperties {
        container('jenkins-agent') {
            sh '''
            echo "### Installing seed helm chart with version: ${SEED_VERSION}"
            helm repo add stable https://artifactory.test.cicd.com/artifactory/anurag-helm-virtual
            helm upgrade service-${ENVIRONMENT_NAME} stable/service  --install --version ${service_VERSION} --set environment.name=${ENVIRONMENT_NAME} --namespace ${TASK_NAMESPACE}
            '''
        }
    }
}

def publishVersion() {
    withFolderProperties {
        container('jenkins-agent') {
            sh '''
            echo "### Publishing service helm chart version: ${service_VERSION} in file version-${BUILD_NUMBER}.txt"
            printf "service_version=%s\n" ${service_VERSION} >> version-${BUILD_NUMBER}.txt
            cp version-${BUILD_NUMBER}.txt version-latest.txt
            curl -k -u ${ARTIFACTORY_CREDENTIALS_USR}:${ARTIFACTORY_CREDENTIALS_PSW} -T version-latest.txt https://artifactory.test.cicd.com/artifactory/anurag-generic/${ENVIRONMENT_NAME}-verified/service/version-latest.txt
            curl -k -u ${ARTIFACTORY_CREDENTIALS_USR}:${ARTIFACTORY_CREDENTIALS_PSW} -T version-${BUILD_NUMBER}.txt https://artifactory.test.cicd.com/artifactory/anurag-generic/${ENVIRONMENT_NAME}-verified/service/version-${BUILD_NUMBER}.txt
            '''
        }
    }
}

def triggerDownstreamSeed(String downstreamEnv) {
    container('jenkins-agent') {
        build job: "${downstreamEnv}/seed-deploy-${downstreamEnv}", propagate: false, parameters: [string(name: 'UPSTREAM_BUILD', value: "${BUILD_NUMBER}")]
    }
}
