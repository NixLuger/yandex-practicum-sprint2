# Используем официальный минимальный образ Go
FROM golang:1.21-alpine

# TODO: Добавить необходимые пакеты (например, curl)
# Ожидаемый код:
RUN apk add --no-cache curl

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем Go-файл и скрипт проверки DNS
COPY main.go .

# TODO: Собрать бинарник из main.go
# Ожидаемый код:
RUN go build -o booking-service main.go

# TODO: Задать команду запуска
# Ожидаемый код:
CMD ["./booking-service"]
