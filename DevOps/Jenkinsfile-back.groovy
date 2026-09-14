#! /usr/bin/env groovy

//  http://ip:port/jnlpJars/slave.jar

// 所需插件：Git/Git Parameter/Pipeline/Kubernetes/Pipeline: Stage View/Config File Provider

// 创建dokcer secret
// kubectl create secret docker-registry regcred \
//  --docker-server=<你的镜像仓库服务器> \
//  --docker-username=<你的用户名> \
//  --docker-password=<你的密码> \
//  --docker-email=<你的邮箱地址>

// vim /var/lib/jenkins/updates/default.json
// :1,$s/https:\/\/updates.jenkins.io\/download/https:\/\/mirrors.tuna.tsinghua.edu.cn\/jenkins/g
// :1,$s/https:\/\/www.google.com/https:\/\/www.baidu.com/g
// service jenkins restart

// jenkisn 安装必要插件
// Git/Git Parameter/Pipeline/Kubernetes/Kubernetes Continuous Deploy/Config File Provider
// Kubernetes Continuous Deploy： 用于将资源配置部署到Kubernetes
// Config File Provider：用于存储kubectl用于连接k8s集群的kubeconfig配置文件
// Kubernetes Continuous Deploy都集成在了Kubernetes插件

// 公共
def registry = "192.168.1.21:8088"

// 项目
def project_name = "mytest"
def registry_name = "test"
def image_name = "${registry}/${registry_name}/${project_name}:${BUILD_NUMBER}"
def gitlab_url = "http://gitlab-ce-svc.devops/root/mytest.git"
def sonarqube_url = "http://sonar-svc.devops.svc.cluster.local:9000"

// 认证
def gitlab_auth = "d4bda339-d9bd-439d-ae2a-2f8d95e104ad"
def harbor_auth = "03ed73cb-7ece-4b0e-b4e8-86e9f88d3a4c"
def sonarqube_token = "d51cb636-6eed-47ce-95f3-e3483746bd18"
def k8s_auth = "178479d2-fdcc-4e07-9537-5d409e3ff166"
def secret_name = "docker-registry"
 
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
  containers:
  - name: jnlp
    image: 192.168.1.21:8088/devops/jenkins-slave:jdk-17
    imagePullPolicy: Always
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
    image: 192.168.1.21:8088/devops/maven:3.9.5-jdk1.8
    imagePullPolicy: Always
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
    image: 192.168.1.21:8088/devops/sonar-scanner:4.6.2.2472
    imagePullPolicy: Always 
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
    image: 192.168.1.21:8088/devops/docker-cli:20.10.16
    imagePullPolicy: Always
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
    image: 192.168.1.21:8088/devops/kubectl:1.27.0-helm-3.12.3
    imagePullPolicy: Always
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
        choice(choices: ["test", "prod"], description: "命名空间", name: "Namespace")
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
              docker build -t ${image_name} .
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
              sed -i 's#IMAGE_NAME#${image_name}#' deploy.yaml
              sed -i 's#SECRET_NAME#${secret_name}#' deploy.yaml
              sed -i 's#APP_NAME#${project_name}#' deploy.yaml
              sed -i 's#REPLICAS#${ReplicaCount}#' deploy.yaml
              kubectl apply -f deploy.yaml -n ${Namespace} --kubeconfig=/home/jenkins/agent/workspace/${JOB_NAME}/admin.kubeconfig
              sleep 120
              kubectl get pod,svc,ingress -n ${Namespace} --kubeconfig=/home/jenkins/agent/workspace/${JOB_NAME}/admin.kubeconfig
              """
            }
          }
        }
      }
    }
}