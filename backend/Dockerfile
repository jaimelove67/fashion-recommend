FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -U -Dmaven.wagon.http.retryHandler.count=3 -DskipTests package

FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/fashion-recommendation-api-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
