#!/usr/bin/env node
import {$}  from 'zx';
import Crypto from 'crypto';
import {tmpdir} from 'os';
//import * as path from 'path';
import * as fs from 'fs';

const path = require('path');

let KUBE_POD_NAME = "ono-master";
let KUBE_DEP_NAME = `${KUBE_POD_NAME}-dep`;
let KUBE_POD_PORT = 2551;
let KUBE_SRV_PORT = 2551;
let KUBE_SRV_IP = "10.96.100.1";
let KUBE_SRV_NAME = `${KUBE_POD_NAME}-srv`;
let KUBE_ING_NAME = `${KUBE_SRV_NAME}-i`;
let KUBE_NAMESPACE = "ono-cluster-demo";
var DOCKERTAG = `ono_cluster_demo/${KUBE_POD_NAME}:0.0.1`;
let JARFILE = "kube_akka_wordcount_cluster.jar";
let SCALA_VERSION = "2.13";
let MAINCLASS = "udemy.cluster.onokube3.OnoClusterCreateMaster";
let script_path = path.dirname(__filename);
let rootProjectPath = script_path.split("/").slice(0,-2).join("/");
let projectPath = script_path.split("/").slice(0,-1).join("/");
let projectName = projectPath.split("/").slice(-1)[0];
let rootDockerDirPath = `${rootProjectPath}/docker`;
let dockerDirPath = `${rootDockerDirPath}/${projectName}/${KUBE_POD_NAME}`;
let dockerPath = `${dockerDirPath}/Dockerfile`;
let startupScriptFilename="java_startup.sh";
let startupScriptPath = `${dockerDirPath}/${startupScriptFilename}`;
let jarPath = `${projectPath}/target/scala-${SCALA_VERSION}/${JARFILE}`;
let rootDockerEnvPath = `${rootDockerDirPath}/docker.env`;
let inputFilename="lipsum.txt";
let inputFilePath=`${projectPath}/src/main/resources/${inputFilename}`;

function test(){
    console.log("a/b/c/d".split("/").slice(-1)[0]);
}


    
function tmpFilePath() {
    let path_ = tmpdir()+ "/" + `archive.${Crypto.randomBytes(6).readUIntLE(0,6).toString(36)}`;
    return path_
}

async function build(args:string[]) {
    if (args.length<1){
        console.log("usage: build seed_node_ip");
        return ;
    }
    let seedNodeIp = args[0];

    await $`mkdir -p ${dockerDirPath}`;
    await $`cp ${jarPath} ${dockerDirPath}`;
    await $`cp ${inputFilePath} ${dockerDirPath}`;
    //java -cp ${jarPath} -Dono.cluster.seednodes.node1.hostname='${ipAddress}' -Dono.cluster.seednodes.node2.hostname='${ipAddress}'  udemy.cluster.onokube3.OnoClusterSeedNodes1

let startupScriptContent = `
#ip_addr=\`ip addr |grep inet|grep -v 127.0.0.1 |awk  '{print $2}'|awk -F\/ '{print $1}'\`
#MY_POD_IP is defined in kube deployment
ip_addr=\${MY_POD_IP}
java -cp /app/${JARFILE} -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=2 -Dono.cluster.seednodes.node1.hostname="${seedNodeIp}" -Dono.cluster.seednodes.node2.hostname="${seedNodeIp}" -Dono.cluster.master.filePath="/app/${inputFilename}"  ${MAINCLASS}
`;

    fs.writeFile(startupScriptPath, startupScriptContent, function (err:any) {
      if (err) 
          return console.log(err);
    });

let dockerfileContent=`
FROM openjdk:8
RUN mkdir -p /app
WORKDIR /app
COPY ${startupScriptFilename}  /app
COPY ${JARFILE}  /app
COPY ${inputFilename}  /app
EXPOSE ${KUBE_POD_PORT}
#CMD ["java", "-cp", "/app/${JARFILE}", "${MAINCLASS}"]
CMD ["sh", "/app/${startupScriptFilename}"]
`;


    console.log("docker file path", dockerPath);
    fs.writeFile(dockerPath, dockerfileContent, function (err:any) {
      if (err) 
          return console.log(err);
      else 
          $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && docker build -t ${DOCKERTAG} .`;
    });

    return true;
}


function run_docker(args:any){
    let container_name=args[0];
    if (container_name==undefined || container_name==null || container_name.trim()==""){
        console.log(`usage: zx ${process.argv[2]} run_docker <docker_container_name> `);
        return;
    }
    //ip addr |grep inet|grep -v 127.0.0.1 |awk  '{print $2}'|awk -F\/ '{print $1}'
    $`eval $(minikube -p minikube docker-env) && docker run --rm -p ${KUBE_POD_PORT}:${KUBE_POD_PORT} --shm-size=1024m --name ${container_name} ${DOCKERTAG}`;
    return true;
}


function kube_deploy(args:any){

let template = `
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${KUBE_DEP_NAME}
  namespace: ${KUBE_NAMESPACE}
  labels:
    app: ${KUBE_POD_NAME}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ${KUBE_POD_NAME}
  template:
    metadata:
      labels:
        app: ${KUBE_POD_NAME}
    spec:
      containers:
      - name: ${KUBE_POD_NAME}
        image: ${DOCKERTAG}
        imagePullPolicy: Never
        env:
        - name: APP_LIB_DIR
          value: "./lib"
        - name: MY_POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        ports:
        - containerPort: ${KUBE_POD_PORT}
          name: ${KUBE_POD_NAME}
        resources:
          limits:
            memory: 1024Mi
          requests:
            cpu: 0.5
            memory: 1024Mi
        volumeMounts:
          - mountPath: /dev/shm
            name: dshm
      volumes:
        - name: dshm
          emptyDir:
            medium: Memory
`;

    let tempFilePath = tmpFilePath();
    fs.writeFile(tempFilePath, template, function (err:any) {
      if (err) 
          return console.log(err);
      else
        $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && kubectl apply -f ${tempFilePath} -n ${KUBE_NAMESPACE}`;

    });

    return true;
}

function kube_describe(args:any) {
    $`kubectl describe pod -n ${KUBE_NAMESPACE} ${KUBE_POD_NAME}`
}

function kube_getpod(args:any) {
    $`kubectl get pods -n ${KUBE_NAMESPACE} |grep ${KUBE_POD_NAME}`
    return true;
}


async function kube_deploy_service(args:any) {
    var ip = require("ip");
    let ipAddress = ip.address();
    let onoMasterIpAddr =  await $`cat ${rootDockerEnvPath} | grep ono_master_cluster_ip|cut -d= -f2`;
    console.log("onoMasterIpAddr", onoMasterIpAddr["stdout"]);

let template = `
apiVersion: v1
kind: Service
metadata:
  name: ${KUBE_SRV_NAME}
  namespace: ${KUBE_NAMESPACE}
  labels:
    app: ${KUBE_SRV_NAME} 
spec:
  ports:
  - port: ${KUBE_SRV_PORT}
    targetPort: ${KUBE_POD_PORT}
  #type: NodePort 
  type: LoadBalancer 
  clusterIP: ${onoMasterIpAddr}
  externalIPs:
  - ${ipAddress}
  #type: ClusterIP 
  #clusterIP: None
  selector:
    app: ${KUBE_POD_NAME} 
`
    let tempFilePath = tmpFilePath();
    fs.writeFile(tempFilePath, template, function (err:any) {
        if (err) 
            return console.log(err);
        else
        $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && kubectl create -f ${tempFilePath} -n ${KUBE_NAMESPACE}`;
    });

    return true;
}


function kube_deploy_ingress(args:any) {
    let template=`
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${KUBE_ING_NAME}
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$1
spec:
  rules:
    - host: akka-http-test.kube
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: ${KUBE_SRV_NAME}
                port:
                  number: ${KUBE_SRV_PORT}
`;
    let tempFilePath = tmpFilePath();
    fs.writeFile(tempFilePath, template, function (err:any) {
      if (err) return console.log(err);
    });

    $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && kubectl create -f ${tempFilePath} -n ${KUBE_NAMESPACE}`;
    return true;
}

function kube_del_deploy(args:any){
    $` kubectl delete deploy -n ${KUBE_NAMESPACE} ${KUBE_DEP_NAME}`;
    return true;
}
function kube_del_service(args:any){
    $` kubectl delete service -n ${KUBE_NAMESPACE} ${KUBE_SRV_NAME}`;
    return true;
}
function kube_del_ingress(args:any){
    $` kubectl delete ingress -n ${KUBE_NAMESPACE} ${KUBE_ING_NAME}`;
    return true;
}

function kube_pod_shell(args:any) {
//kubectl exec --stdin --tty shell-demo -- /bin/bash
    let namespace=args[0];
    let podName=args[1];
    if (podName==undefined || podName==null || podName.trim()==""){
        console.log(`usage: zx ${process.argv[2]} kube_pod_shell <namespace> <pod_name> `);
        return;
    }
    $`kubectl exec --stdin --tty ${podName} -n ${namespace} -- /bin/bash`;
}


function run_jar(args:any) {
    var ip = require("ip");
    let ipAddress =  ip.address();
    if (args.length<1){
        console.log("usage ts-node run_jar  <main_class>")
        console.log("usage ts-node run_jar  seednodes")
        console.log("usage ts-node run_jar  master")
        console.log("usage ts-node run_jar  worker")
        console.log("usage ts-node run_jar  client")
        console.log("usage ts-node run_jar  additional_worker")
        return ;
    }
    let cmd=args[0];
    if (cmd=="seednodes")
        $`set -x; java -cp ${jarPath} -Dono.cluster.seednodes.node1.hostname='${ipAddress}' -Dono.cluster.seednodes.node2.hostname='${ipAddress}'  udemy.cluster.onokube3.OnoClusterSeedNodes1`;
    else if (cmd=="master")
        $`set -x; java -cp ${jarPath} -Dono.cluster.seednodes.node1.hostname='${ipAddress}' -Dono.cluster.seednodes.node2.hostname='${ipAddress}'  -Dono.cluster.master.filePath=../src/main/resources/lipsum.txt  udemy.cluster.onokube3.OnoClusterCreateMaster`;
    else if (cmd=="worker")
        $`set -x; java -cp ${jarPath} -Dono.cluster.seednodes.node1.hostname='${ipAddress}' -Dono.cluster.seednodes.node2.hostname='${ipAddress}'  udemy.cluster.onokube3.OnoClusterCreateWorkers`;
    else if (cmd=="additional_worker")
        $`set -x; java -cp ${jarPath} -Dono.cluster.seednodes.node1.hostname='${ipAddress}' -Dono.cluster.seednodes.node2.hostname='${ipAddress}'  udemy.cluster.onokube3.AdditionalWorker1`;
    else if (cmd=="client")
        $`set -x; java -cp ${jarPath} -Dono.cluster.seednodes.node1.hostname='${ipAddress}' -Dono.cluster.seednodes.node2.hostname='${ipAddress}'  udemy.cluster.onokube3.RunOnoClusterClient1`;
    else
        $`set -x; java -cp ${jarPath} -Dono.cluster.seednodes.node1.hostname='${ipAddress}' -Dono.cluster.seednodes.node2.hostname='${ipAddress}'  ${args[0]}`;

}

function run(){
    if (process.argv.length<3){
        console.log(`usage: ts-node ${process.argv[1]} [build|run_jar|run_docker|kube_deploy] `);
        return;
    } 

    let command=process.argv[2];
    let args=process.argv.slice(3);
    if (command=="test")
        test();
    if (command=="run_jar")
        run_jar(args);
    if (command=="build")
        build(args);
    if (command=="run_docker")
        run_docker(args);
    if (command=="kube_deploy")
        kube_deploy(args);
    if (command=="kube_desc")
        kube_describe(args);
    if (command=="kube_getpod")
        kube_getpod(args);
    if (command=="kube_deploy_service")
        kube_deploy_service(args);
    if (command=="kube_deploy_ingress")
        kube_deploy_ingress(args);
    if (command=="kube_del_service")
        kube_del_service(args);
    if (command=="kube_del_ingress")
        kube_del_ingress(args);
    if (command=="kube_pod_shell")
        kube_pod_shell(args);

    console.log("none of the command line options are executed")

}


run();
