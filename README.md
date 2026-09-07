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
