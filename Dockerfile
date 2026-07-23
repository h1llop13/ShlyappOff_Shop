# ЭТАП 1: Сборка (используем образ с Maven)
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Копируем pom.xml и скачиваем зависимости (кэшируем)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Копируем исходный код и собираем jar
COPY src ./src
RUN mvn clean verify

# ЭТАП 2: Запуск (используем только JRE, образ будет легким)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копируем собранный jar из этапа сборки
COPY --from=build /app/target/*.jar app.jar

# Открываем порт
EXPOSE 8080

# Запускаем приложение
CMD ["java", "-jar", "app.jar"]
