@echo off
echo ==========================================
echo   Запуск ShlyapOff (Docker + PostgreSQL)
echo ==========================================

:: Проверяем, есть ли файл .env
if not exist ".env" (
    echo [ОШИБКА] Файл .env не найден! Создай его перед запуском.
    pause
    exit /b
)

:: Запускаем контейнеры в фоновом режиме (-d)
docker-compose up -d

echo.
echo [УСПЕХ] Всё запущено!
echo Открывай браузер: http://localhost:8080
echo.
echo Чтобы остановить, запусти файл stop.bat
pause