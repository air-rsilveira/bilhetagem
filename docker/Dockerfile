# Estágio 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
# Copia o Maven Wrapper e o POM primeiro para aproveitar o cache de camadas
COPY mvnw .
COPY .mvn ./.mvn
COPY pom.xml .
# Normaliza terminações de linha (mvnw pode vir com CRLF do Windows) e garante execução
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
# Baixa as dependências numa camada separada; só refaz quando o pom.xml muda.
# O cache mount reaproveita o repositório ~/.m2 entre builds (requer BuildKit).
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B
# Agora copia o código; mudanças em src não invalidam o download de dependências
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -B

# Estágio 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
