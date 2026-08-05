# ShlyapOff Shop

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.7-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

**ShlyapOff Shop** — интернет-магазин с интеграцией **Telegram Mini App** и административной панелью для управления каталогом, заказами и клиентами.

Backend разработан на **Spring Boot** с использованием **PostgreSQL**, **Spring Security**, **Liquibase** и **Docker Compose**. Проект постепенно развивается и служит площадкой для изучения современных подходов к backend-разработке.

---

## Содержание

- [Возможности](#-возможности)
- [Технологический стек](#-технологический-стек)
- [Архитектура](#-архитектура)
- [Структура проекта](#-структура-проекта)
- [Быстрый запуск](#-быстрый-запуск)
- [Roadmap](#-roadmap)
- [Контакты](#-контакты)
- [Лицензия](#-лицензия)

---

# Возможности

## Клиентская часть (Telegram Mini App)

- Просмотр каталога товаров
- Поиск и фильтрация товаров
- Корзина покупок
- Оформление заказов
- История заказов
- Программа лояльности и персональные скидки
- Автоматическая авторизация через Telegram

---

## Административная панель

- Управление товарами
- Управление категориями
- Управление брендами
- Управление вариантами товаров
- Загрузка изображений
- Управление заказами
- Управление клиентами
- Telegram-уведомления о новых заказах

---

## Безопасность

- Авторизация администраторов через Spring Security
- Хранение паролей в виде BCrypt-хешей
- Серверная проверка Telegram `initData`
- Защита административной панели
- Валидация входящих данных
- Контроль изменения статусов заказов

---

# Технологический стек

| Категория | Технологии |
|-----------|------------|
| **Backend** | Java 21, Spring Boot 4 |
| **Database** | PostgreSQL 16 |
| **ORM** | Spring Data JPA (Hibernate) |
| **Security** | Spring Security |
| **Database migrations** | Liquibase |
| **Frontend** | Thymeleaf, Bootstrap 5 |
| **Integrations** | Telegram Bot API, Telegram Mini App |
| **Build Tool** | Maven |
| **Containerization** | Docker, Docker Compose |
| **Testing** | JUnit 5, Mockito |

---

# Архитектура

Проект построен по классической слоистой архитектуре.

```
Controller
    │
    ▼
 Service
    │
    ▼
Repository
    │
    ▼
PostgreSQL
```

### Используемые подходы

- Layered Architecture
- MVC
- Dependency Injection
- Repository Pattern
- DTO
- Bean Validation
- Exception Handling
- Database Migrations
- Environment-based Configuration

---

# Структура проекта

```
src
└── main
    ├── java
    │   └── ...
    │       ├── config
    │       ├── controller
    │       ├── dto
    │       ├── entity
    │       ├── exception
    │       ├── repository
    │       ├── security
    │       ├── service
    │       ├── util
    │       └── validation
    │
    └── resources
        ├── db
        ├── static
        ├── templates
        └── application.yml
```

---

# Быстрый запуск

## 1. Клонирование репозитория

```bash
git clone https://github.com/USERNAME/ShlyapOff_Shop.git

cd ShlyapOff_Shop
```

---

## 2. Создание `.env`

Создайте файл `.env` в корне проекта.

Пример переменных окружения:

```env
DB_USERNAME=postgres
DB_PASSWORD=password

ADMIN_USERNAME=admin
ADMIN_PASSWORD=password

TELEGRAM_BOT_TOKEN=xxxxxxxxxxxxxxxx
TELEGRAM_ADMIN_CHAT_ID=123456789

APP_BASE_URL=https://example.com
```

---

## 3. Запуск

```bash
docker compose up --build -d
```

---

## 4. Доступ к приложению

| Сервис | Адрес |
|---------|--------|
| Сайт | http://localhost:8080 |
| Административная панель | http://localhost:8080/admin |

---

# Используемые технологии

Проект позволяет попрактиковаться в работе с:

- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Liquibase
- Docker Compose
- Telegram Bot API
- Telegram Mini App
- Thymeleaf
- Bootstrap
- JUnit 5
- Mockito

---

# Roadmap

- ✅ Telegram Mini App
- ✅ Каталог товаров
- ✅ Корзина
- ✅ История заказов
- ✅ Административная панель
- ✅ Spring Security
- ✅ Программа лояльности
- ✅ Docker Compose
- ✅ Liquibase

### Планируется

- ⏳ REST API для внешних клиентов
- ⏳ Покрытие проекта большим количеством тестов
- ⏳ CI/CD
- ⏳ Кэширование
- ⏳ Улучшение интерфейса административной панели

---

# Контакты

Если вы нашли ошибку или хотите предложить улучшение проекта, создайте **Issue** в репозитории.

**Telegram:** https://t.me/h1llop

**Email:** h1llapple13@gmail.com

---

# 📄 Лицензия

Проект распространяется по лицензии **MIT**.

Подробности можно найти в файле **LICENSE**.