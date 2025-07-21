// 公共
def registry = "192.168.31.154:8443"

// 项目
def project_name = "local-standard-address-backend"
def registry_name = "standard-address-backend"
def image_name = "${registry}/${registry_name}/${project_name}:${BUILD_NUMBER}"
def gitlab_url = "http://gitlab-ce-svc.devops.svc.cluster.local/bzdz/standard-address-backend.git"
def sonarqube_url = "http://sonar-svc.devops.svc.cluster.local:9000"

// 认证
def gitlab_auth = "e3331756-8746-4592-89c3-c81f0c2a2dad"
def harbor_auth = "ee470ba7-e601-40de-a3ff-c4edbe645edc"
def sonarqube_token = "eaf58907-611e-47c2-81e7-43c2fafd41ca"
def k8s_auth = "544797b2-0f12-4f6e-8c1c-0f2faaf9bbf1"
def secret_name = "registry-pull-secret"
pipeline {
    agent {
        kubernetes {
            cloud "kubernetes-default"
            slaveConnectTimeout 1200
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  name: jenkins-slave
spec:
  volumes:
    - name: "maven-cache"
      persistentVolumeClaim:
        claimName: "mavencache"
    - name: "volume-localtime"
      hostPath:
        path: "/usr/share/zoneinfo/Asia/Shanghai"
    - name: "volume-docker"
      hostPath: 
        path: "/var/run/docker.sock"
    - name: "volume-hosts"
      hostPath: 
        path: "/etc/hosts"
  imagePullSecrets:
  - name: registry-pull-secret
  nodeSelector:
    devops: "true"
  containers:
  - name: jnlp
    image: 192.168.31.154:8443/devops/jenkins-slave:jdk-17
    imagePullPolicy: IfNotPresent
    env:
      - name: "LANGUAGE"
        value: "en_US:en"
      - name: "LC_ALL"
        value: "en_US.UTF-8"
      - name: "LANG"
        value: "en_US.UTF-8"
    volumeMounts:
      - name: "volume-localtime"
        mountPath: "/etc/localtime"  
  - name: build
    image: 192.168.31.154:8443/devops/maven:3.9.5-jdk1.8
    imagePullPolicy: IfNotPresent
    tty: true
    command:
      - "cat"
    env:
      - name: "LANGUAGE"
        value: "en_US:en"
      - name: "LC_ALL"
        value: "en_US.UTF-8"
      - name: "LANG"
        value: "en_US.UTF-8"
    volumeMounts:
      - name: "volume-localtime"
        mountPath: "/etc/localtime"
        readOnly: false
      - name: "maven-cache"
        mountPath: "/root/.m2/repository"
  - name: sonarqube
    image: 192.168.31.154:8443/devops/sonar-scanner:4.6.2.2472
    imagePullPolicy: IfNotPresent 
    tty: true
    command:
      - "cat"
    env:
      - name: "LANGUAGE"
        value: "en_US:en"
      - name: "LC_ALL"
        value: "en_US.UTF-8"
      - name: "LANG"
        value: "en_US.UTF-8"
    volumeMounts:
      - name: "volume-localtime"
        mountPath: "/etc/localtime"
        readOnly: false
  - name: docker-cli
    image: 192.168.31.154:8443/devops/docker-cli:26.1.3
    imagePullPolicy: IfNotPresent
    tty: true
    command:
      - "cat"
    volumeMounts:
      - name: "volume-docker"
        mountPath: "/var/run/docker.sock" 
        readOnly: false
      - name: "volume-localtime"
        mountPath: "/etc/localtime"
        readOnly: false
      - name: "volume-hosts"
        mountPath: "/etc/hosts"
        readOnly: false
  - name: kubectl
    image: 192.168.31.154:8443/devops/kubectl:1.28.0-helm-3.12.3
    imagePullPolicy: IfNotPresent
    tty: true
    command:
      - "cat"
    env:
      - name: "LANGUAGE"
        value: "en_US:en"
      - name: "LC_ALL"
        value: "en_US.UTF-8"
      - name: "LANG"
        value: "en_US.UTF-8"
    volumeMounts:
      - name: "volume-localtime"
        mountPath: "/etc/localtime" 
        readOnly: false
      - name: "volume-hosts"
        mountPath: "/etc/hosts"
        readOnly: false
'''
        }
    }

    parameters {
        gitParameter(
            branch: '', 
            branchFilter: '.*',     // 表示允许显示所有分支
            defaultValue: '',       // 空字符串，需要手动选择分支，没有默认值
            description: '选择要发布的分支',    // 参数解释
            name: 'Branch',     // 选择参数分支的名称
            quickFilterEnabled: false,      // 禁止分支筛选功能
            selectedValue: 'NONE',          // 未选择分支时显示的值
            sortMode: 'NONE',       // 分支的排序方式，NONE不排序
            tagFilter: '*',         // 过滤标签表达式，'*'允许选择任何标签
            type: 'PT_BRANCH'      // 该参数用于选择分支
        )   
        choice(choices: ["1", "3", "5", "7"], description: "副本数", name: "ReplicaCount")
        choice(choices: ["standard-address"], description: "命名空间", name: "Namespace")
    }

    stages {
      stage("拉取代码") {
        steps {
            checkout([$class: 'GitSCM',
                branches: [[name: "${params.Branch}"]],     // 获取选择的分支名称
                doGenerateSubmoduleConfigurations: false,   // 是否生成子模块配置
                extensions: [],     // 配置Git插件的扩展功能
                submoduleCfg: [],   // 配置子模块的信息
                userRemoteConfigs: [[credentialsId: "${gitlab_auth}", url: "${gitlab_url}"]]])  // 配置凭据
        }
      }
      stage("并行构建和扫描") {
          parallel {
              stage("代码编译") {
                  steps {
                      container(name: "build") {
                          sh """
                          mvn clean package -Dmaven.test.skip=true
                          ls -l target/
                          pwd
                          """
                      }
                  }
              }
              stage("代码扫描") {
                  steps {
                      container(name: "sonarqube") {
                        withCredentials([string(credentialsId: "${sonarqube_token}", variable: "SONAR_TOKEN")]) {
                          sh """
                          sonar-scanner \
                          -Dsonar.projectKey="${project_name}" \
                          -Dsonar.sources=src \
                          -Dsonar.java.binaries=target/classes \
                          -Dsonar.host.url="${sonarqube_url}" \
                          -Dsonar.login="${SONAR_TOKEN}"
                          """
                        }
                      }
                  }
              }               
          }
      }
      stage("构建镜像并推送仓库") {
        steps {
          container(name: "docker-cli") {
            withCredentials([usernamePassword(credentialsId: "${harbor_auth}", passwordVariable: 'password', usernameVariable: 'username')]) {
              sh """
              docker build -f Dockerfile/Dockerfile-local -t ${image_name} .
              docker login -u ${username} -p "${password}" ${registry}
              docker push ${image_name}
              """
            }
          }
        }
      }
      stage("部署到k8s平台") {
        steps {
          container(name: "kubectl") {
            configFileProvider([configFile(fileId: "${k8s_auth}", targetLocation: "admin.kubeconfig")]) {
              sh """
              sed -i 's#IMAGE_NAME#${image_name}#' Deployment/deployment-local.yaml
              sed -i 's#SECRET_NAME#${secret_name}#' Deployment/deployment-local.yaml
              sed -i 's#APP_NAME#${project_name}#' Deployment/deployment-local.yaml
              sed -i 's#REPLICAS#${ReplicaCount}#' Deployment/deployment-local.yaml
              kubectl apply -f Deployment/deployment-local.yaml -n ${Namespace} --kubeconfig=/home/jenkins/agent/workspace/${JOB_NAME}/admin.kubeconfig
              sleep 120
              kubectl get pod,svc,ingress -n ${Namespace} --kubeconfig=/home/jenkins/agent/workspace/${JOB_NAME}/admin.kubeconfig
              """
            }
          }
        }
      }
    }
}