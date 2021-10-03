
FROM openjdk:8
RUN mkdir -p /app
WORKDIR /app
COPY java_startup.sh  /app
COPY kube_akka_wordcount_cluster.jar  /app
EXPOSE 2551
#CMD ["java", "-cp", "/app/kube_akka_wordcount_cluster.jar", "udemy.cluster.onokube3.RunOnoClusterClient1"]
CMD ["sh", "/app/java_startup.sh"]
