# ShlyapOff Shop

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.7-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)


**ShlyapOff Shop** - полнофункциональный e-commerce сервис с клиентским интерфейсом, интеграцией **Telegram Mini App** и отдельной административной панелью. 

Проект построен на **Java 21 + Spring Boot**, использует PostgreSQL, Spring Security, Liquibase, Docker Compose и Telegram Bot API.

Это не демонстрационный CRUD: приложение содержит полноценную работу с каталогом, корзиной, заказами, остатками товаров, протоколами, акциями, программой лояльности и Telegram-интеграцией. 

---

## Возможности

### Магазин

- каталог товаров;
- категории и бренды;
- варианты товаров;
- характеристики товаров;
- поиск и фильтрация;
- корзина;
- сохранение корзины;
- контроль остатков;
- оформление заказа;
- история заказов;
- профиль покупателя.

### Скидки и лояльность

- уровни программы лояльности;
- персональные скидки;
- промокод;
- акции;
- автоматический расчет итоговой стоимости заказа.

### Telegram Mini App

- запуск магазина внутри Telegram;
- автоматическая идентификация пользователя;
- серверная проверка Telegram initData;
- связь Telegram-пользователя с профилем магазина;
- уведомления администратора о новых заказах.

### Административная панель

Администратор может управлять:

- товарами;
- категориями;
- брендами;
- вариантами товаров;
- остатками;
- заказами;
- клиентами;
- программой лояльности;
- протоколами;
- акциями.

Также доступна dashboard-страница со статистикой продаж.

---

## Backend

Приложение построено по классической многослойной архитектуре:

HTTP / Telegram
       │
       ▼
 Controllers
       │
       ▼
   Services
       │
       ▼
 Repositories
       │
       ▼
 PostgreSQL


 Основная бизнес-логика находится в service-слое, доступ к данным реализован через Spring Data JPA.

 Дата изменения структуры базы данных используются версионированные Liquibase migrations.

 ---

 ## Технологический стек

 | Область | Технологии |
 |---|---|
 | Language | Java 21 |
 | Framework | Spring Boot 4.0.7 |
 | Web | Spring MVC |
 | ORM | Spring Data JPA | Hibernate |
 | Database | PostgreSQL 16 |
 | Migrations | Liquibase |
 | Security | Spring Security, BCrypt |
 | Templates | Thymeleaf |
 | Frontend | HTML, CSS, JavaScript |
 | Telegram | Telegram Bot API, Telegram Mini App |
 | Cache | Spring Cache, Ceffeine |
 | Monitoring | Spring Boot Actuator |
 | Build | Maven |
 | Containers | Docker, Docker Compose |
 | Testing | JUnit 5, Mockito, AssertJ |
 | Reserve Proxy | nginx |

 ---

 ## Структура проекта

 src/
├── main/
│   ├── java/com/shlyapoff/shop/
│   │   ├── bot/          # Telegram Bot
│   │   ├── config/       # Spring, Security и Telegram configuration
│   │   ├── controller/   # MVC и API controllers
│   │   ├── dto/          # Data Transfer Objects
│   │   ├── model/        # JPA entities и enums
│   │   ├── repository/   # Spring Data repositories
│   │   └── service/      # Business logic
│   │
│   └── resources/
│       ├── db/changelog/ # Liquibase migrations
│       ├── static/       # CSS, JavaScript, images
│       ├── templates/    # Thymeleaf templates
│       └── application.yml
│
└── test/
    └── java/com/shlyapoff/shop/
        ├── controller/
        ├── integration/
        └── service/


---

## База данных

Схема базы данных управляется через **Liquibase**.

Миграции покрывают создание и развитие:

- товаров;
- категорий;
- брендов;
- вариантов товаров;
- корзин;
- заказов;
- программы лояльности;
- остатков;
- уведомлений;
- акций;
- промокодов;
- индексов производительности.

При запуске приложения Liquibase автоматически приводит структуру базы к актуальной версии.

---

## Безопасность

В проекте реализованы:

- Spring Security;
- BCrypt hashing для паролей администратора;
- защищенная административная панель;
- серверная проверка Telegram WebApp initData;
- Bean Validation;
- контроль доступа к пользовательским данным;
- проверка допустимых переходов статусов заказа;
- конфигурация секретов через environment variables.

Секретные значения не должны храниться в репозитория.

---

## Надежность уведомлений

Telegram-уведомления реализованы через Notification Outbox.

Вместо прямой отправки Telegram-сообщения внутри основной транзакции событие сохраняется в базе данных и обрабатывается отдельным worker'ом.

Это уменьшает зависимость оформления заказа от доступности Telegram API.

---

## Тестирование

В проекте присутствуют unit и integration tests.

Проверяются, в частности:

- CartService;
- OrderService;
- ProductService;
- UserService;
- Telegram authentication;
- Telegram notifications;
- checkout;
- MVC controllers;
- полный сценарий оформления заказа.

Запуск:

``` bash
./mvnw test
```

Windows:

``` bash
mvnw.cmd test
```

---

## Запуск проекта

Самый простой способ запустить приложение - через Docker Compose.

## Требования

Необходимо установить:

- Git;
- Docker;
- Docker Compose.

Java и PostgreSQL отдельно устанавливать не требуется при Docker-запуске.

---

### 1. Клонирование

``` bash
git clone https://github.com/h1llop13/ShlyappOff_Shop.git
cd ShlyappOff_Shop
```

---

### 2. Создание .env

Скопируйте пример конфигурации:

#### Linux / macOS

``` bash
cp .env.example .env
```

#### Windows

``` bash
copy .env.example .env
```

Заполните значения:

``` txt
DB_PASSWORD=your_secure_password

TELEGRAM_BOT_TOKEN=
TELEGRAM_ADMIN_CHAT_ID=

TELEGRAM_FEEDBACK_URL=https://t.me/h1llop

APP_BASE_URL=https://localhost:8080

ADMIN_USERNAME=admin
ADMIN_PASSWORD=your_admin_password
```

Telegram-переменные необходимы для полноценной работы Telegram-интеграции.

---

## 3. Запуск

``` bash
docker compose up --build -d
```

Docker Compose запустит:

``` txt
Spring Boot application
        │
        └──── PostgreSQL 16
```

PostgreSQL автоматически проходит healthcheck перед запуском приложения.

---

## 4. Открытие приложения

После запуска:

#### Магазин

``` bash
http://localhost:8080
```

#### Административная панель

``` bash
http://localhost:8080/admin
```

#### Actuator health endpoint

``` bash
http://localhost:8080/actuator/health
```

---

### Остановка

``` bash
docker compose down
```

Для удаления также PostgreSQL volume:

``` bash
docker compose down -v
```

>> Команда с -v удалит локальные данные базы.

---

## Запуск без Docker

Для локальной разработки можно использовать Maven Wrapper.

Понадобятся:

- Java 21;
- PostgreSQL;
- Maven Wrapper уже находится репозитория.

Linux / macOS:

``` bash
./mvnw spring-boot:run
```

Windows:

``` bash
mvnw.cmd spring-boot:run
```

Параметры подключения к PostgreSQL и остальные необходимые значения задаются через environment variables.

---

## Docker

Проект содержит собственный Dockerfile b docker-compose.yml.

Docker Compose отвечает за:

- Spring Boot application;
- PostgreSQL 16;
- persistent PostgreSQL volume;
- persistent uploads;
- health checks;
- resource limits;
- автоматический restart контейнеров.

---

## Deployment

В репозитория находится пример конфигурации production-развертывания:

``` txt
deploy/
├── VPS-DEPLOYMENT.md
└── nginx/
    └── shlyapoff.conf.example
```

Типичная production-схема:

``` txt
Telegram / Browser
        │
      HTTPS
        │
        ▼
      nginx
        │
        ▼
 Spring Boot
        │
        ▼
 PostgreSQL
```

---

## Perfomance

В проект добавлены инструменты для анализа производительности:

``` txt
scripts/
├── backfill-thumbnails.sh
├── load-test.js
└── verify-performance.sql
```

Также в PostgreSQL используются отдельные индексы для оптимизации часто выполняемых запросов.

---

## Roadmap

Планируемые улучшения:

- расширение REST API;
- увеличение test coverage;
- полноценный CI pipeline;
- улучшение observability и monitoring;
- дальнейшая оптимизация SQL-запросов;
- улучшение интерфейса административной панели;
- расширение Telegram Mini App.

---

## Статус проекта

Проект находится в активной разработке.

Основной функционал интернет-магазина уже реализован, но архитектура, тестирование, производительность и пользовательский интерфейс продолжают улучшаться.

---

## Author

GitHub: [@h1llop13](https://github.com/h1llop13)

Telegram: [@h1llop](https://t.me/h1llop)

---

## License

Проект распространяется под лицензией MIT.

Подробнее по вкладке MIT license
