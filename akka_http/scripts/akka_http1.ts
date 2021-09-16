#!/usr/bin/env node
import {$}  from 'zx';
import Crypto from 'crypto';
import {tmpdir} from 'os';
//import * as path from 'path';
import * as fs from 'fs';

const path = require('path');


let KUBE_POD_NAME="akka-http-test"
let KUBE_SRV_PORT=8080
let KUBE_SRV_NAME=`${KUBE_POD_NAME}-srv`
let KUBE_ING_NAME=`${KUBE_SRV_NAME}-i`
let KUBE_NAMESPACE="test"
var DOCKERTAG="onomoly/akka_http_training:0.0.1";
let JARFILE="akka_http.jar";
let SCALA_VERSION="2.13";
let MAINCLASS="part3_highlevelhttp.HigLevelHttp";
let script_path = path.dirname(__filename);
let projectPath=script_path.split("/").slice(0,-1).join("/");
let dockerDirPath=`${projectPath}/docker`;
let dockerPath=`${dockerDirPath}/Dockerfile`;
let jarPath=`${projectPath}/target/scala-${SCALA_VERSION}/${JARFILE}`;



    
function tmpFilePath() {
    let path_ = tmpdir()+ "/" + `archive.${Crypto.randomBytes(6).readUIntLE(0,6).toString(36)}`;
    return path_
}

function build(args:string[]) {
    console.log("build", args);

    $`mkdir -p ${dockerDirPath}`;
    $`cp ${jarPath} ${dockerDirPath}`;

let dockerfileContent=`
FROM openjdk:8
RUN mkdir -p /app
WORKDIR /app
COPY ${JARFILE}  /app
EXPOSE 8080
CMD ["java", "-cp", "/app/${JARFILE}", "${MAINCLASS}"]
`;

    fs.writeFile(dockerPath, dockerfileContent, function (err:any) {
      if (err) return console.log(err);
    });

    $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && docker build -t ${DOCKERTAG} .`;
}


function run_docker(args:any){
    let container_name=args[0];
    if (container_name==undefined || container_name==null || container_name.trim()==""){
        console.log(`usage: zx ${process.argv[2]} run_docker <docker_container_name> `);
        return;
    }
    $`docker run --rm -p 8080:8080 --name ${container_name} ${DOCKERTAG}`;
}


function kube_deploy(args:any){

let template = `
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${KUBE_POD_NAME}
  namespace: test
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
        image: onomoly/akka_http_training:0.0.1
        imagePullPolicy: Never
        env:
        - name: APP_LIB_DIR
          value: "./lib"
        ports:
        - containerPort: 8080
          name: ${KUBE_POD_NAME}
`;

    let tempFilePath = tmpFilePath();
    fs.writeFile(tempFilePath, template, function (err:any) {
      if (err) return console.log(err);
    });

    $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && kubectl apply -f ${tempFilePath} -n test`;
}

function kube_describe(args:any) {
    $`kubectl describe pod -n ${KUBE_NAMESPACE} ${KUBE_POD_NAME}`
}

function kube_getpod(args:any) {
    $`kubectl get pods -n ${KUBE_NAMESPACE} |grep ${KUBE_POD_NAME}`
}


function kube_deploy_service(args:any) {
    var ip = require("ip");
    let ipAddress = ip.address();

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
    name: ${KUBE_POD_NAME}
  #type: NodePort 
  type: LoadBalancer 
  externalIPs:
  - ${ipAddress}
  #type: ClusterIP 
  #clusterIP: None
  selector:
    app: ${KUBE_POD_NAME} 
`
    let tempFilePath = tmpFilePath();
    fs.writeFile(tempFilePath, template, function (err:any) {
      if (err) return console.log(err);
    });

    $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && kubectl create -f ${tempFilePath} -n test`;
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
          - path: /service1
            pathType: Prefix
            backend:
              service:
                name: ${KUBE_SRV_NAME}
                port:
                  number: 8080
`;
    let tempFilePath = tmpFilePath();
    fs.writeFile(tempFilePath, template, function (err:any) {
      if (err) return console.log(err);
    });

    $`eval $(minikube -p minikube docker-env) && cd ${dockerDirPath} && kubectl create -f ${tempFilePath} -n test`;
}

function kube_del_service(args:any){
    $` kubectl delete service -n ${KUBE_NAMESPACE} ${KUBE_SRV_NAME}`;
}
function kube_del_ingress(args:any){
    $` kubectl delete ingress -n ${KUBE_NAMESPACE} ${KUBE_ING_NAME}`;
}

function run(){
    if (process.argv.length<3){
        console.log(`usage: ts-node ${process.argv[1]} [build|run_docker|kube_deploy] `);
        return;
    } 

    let command=process.argv[2];
    let args=process.argv.slice(3);
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

}


run();
