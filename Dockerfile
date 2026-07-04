# ЭТАП 1: Сборка (здесь есть JDK и Maven)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Копируем файлы Maven для кэширования зависимостей
COPY mvnw pom.xml ./
COPY .mvn ./.mvn
RUN ./mvnw dependency:go-offline

# Копируем исходный код и собираем jar
COPY src ./src
RUN ./mvnw clean package -DskipTests

# ЭТАП 2: Запуск (здесь только JRE, образ будет весить ~150МБ)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копируем собранный jar из первого этапа
COPY --from=build /app/target/*.jar app.jar

# Создаем папку для картинок внутри контейнера
RUN mkdir -p /app/uploads

# Открываем порт 8080
EXPOSE 8080

# Команда запуска приложения
ENTRYPOINT ["java", "-jar", "app.jar"]