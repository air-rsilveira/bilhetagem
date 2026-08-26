# Estágio 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
# Copia o Maven Wrapper primeiro para aproveitar o cache de camadas
COPY mvnw .
COPY .mvn ./.mvn
COPY pom.xml .
# Normaliza terminações de linha (mvnw pode vir com CRLF do Windows) e garante execução
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Estágio 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
