# ShlyapOff - Интернет-магазин вейп-продукции

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.7-brightgreen?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat-square&logo=docker)

**ShlyapOff** - это современный интернет-магазин, разработанный на базе **Spring Boot 4**. Проект включает в себя полноценную админ-панель, управление товарами с вариантами (вкусами), корзину для гостей и фильтрацию каталога.

## Особенности проекта
*   **Технологии:** Java 21, Spring Boot 4, Spring Security, Thymeleaf, JPA/Hibernate.
*   **База данных:** PostgreSQL с миграциями через Liquibase.
*   **Контейнеризация:** Полная поддержка Docker и Docker Compose.
*   **Функционал:**
    *   Публичный каталог с поиском, фильтрацией по брендам/категориям и пагинацией.
    *   Корзина, работающая без регистрации (на основе сессий).
    *   Админ-панель для управления товарами, категориями, брендами и наличием вкусов.
    *   Загрузка изображений товаров с проверкой MIME-типов.

## Технологический стек
| Компонент | Технология |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot 4.0.7 |
| **Database** | PostgreSQL 16, Liquibase |
| **Frontend** | Thymeleaf, Bootstrap 5 |
| **Security** | Spring Security (BCrypt, CSRF) |
| **DevOps** | Docker, Docker Compose, Localtunnel |

## Быстрый запуск
Для запуска проект вам понадобится только установленный **Docker**.

1. Создайте файл `.env` в корне проекта и укажите пароль для БД:
   ```env
   DB_PASSWORD=ваш_сложный_пароль
2. Запустите контейнеры одной командой:
   ```envа к
   docker-compose up --build
   ```
3. Откройте браузер и перейдите по адресу: http://localhost:8080
   **Данные для входа в админку:**
   ```text
   Логин: admin
   Пароль: admin123
   ```
   
