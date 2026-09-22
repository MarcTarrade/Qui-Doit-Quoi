FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn ./.mvn
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -DskipTests

COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/target/api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]