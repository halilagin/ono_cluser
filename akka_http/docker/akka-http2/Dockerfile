
FROM openjdk:8
RUN mkdir -p /app
WORKDIR /app
COPY akka_http.jar  /app
EXPOSE 8080
CMD ["java", "-cp", "/app/akka_http.jar", "part3_highlevelhttp.HigLevelHttp"]
