
FROM openjdk:8
RUN mkdir -p /app
WORKDIR /app
COPY ono_cluster_node.jar  /app
COPY application.conf  /app
EXPOSE 6001
CMD ["java", "-cp", "/app/ono_cluster_node.jar", "-Dconfig.file=/app/application.conf",  "ono.OnoNode"]
