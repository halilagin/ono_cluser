
FROM openjdk:8
RUN mkdir -p /app
WORKDIR /app
COPY ono_cluster_master.jar  /app
EXPOSE 6001
CMD ["java", "-cp", "/app/ono_cluster_master.jar", "http.MainGateway"]
