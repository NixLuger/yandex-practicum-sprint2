# Отчёт по задаче task4
`Курсант: Ядыкин Н.Э.`

## Выполненные работы

1. **Дописан Helm-чарт**:
    - В `values.yaml` добавлены секции `env`, `resources`, `livenessProbe`, `readinessProbe`.
    - В `deployment.yaml` добавлены соответствующие блоки с использованием `toYaml`.
    - Созданы отдельные `values-staging.yaml` и `values-prod.yaml` для разных окружений.

2. **Дописан `.gitlab-ci.yml`**:
    - Реализованы стадии `build`, `test`, `deploy`, `tag`.
    - В `test` выполняется запуск контейнера и проверка `/ping`.
    - В `deploy` выполняется загрузка образа в Minikube и установка Helm-релиза с использованием `values-staging.yaml`.
    - Создан git-тег с меткой времени: `deploy-20260822231733`.

3. **Проверена работа сервиса**:
    - `/ping` возвращает `pong`.
    - При включённом `ENABLE_FEATURE_X=true` доступен `/feature`.

4. **Проверено DNS-обнаружение**: из пода внутри кластера доступен `http://booking-service/ping`.

## Процесс сборки и деплоя в Minikube

1. **Сборка Docker-образа**:
    - В корне проекта выполнен `docker build -t booking-service:latest ./booking-service`.
    - Образ успешно собран на основе Go-приложения и содержит необходимые зависимости (curl, скомпилированный бинарник).

2. **Загрузка образа в Minikube**:
    - Так как Minikube имеет собственное registry, образ был загружен командой `minikube image load booking-service:latest`.
    - Это сделало образ доступным для использования в подах кластера.

3. **Установка Helm-релиза**:
    - Выполнена команда `helm upgrade --install booking-service ./helm/booking-service --values ./values-staging.yaml --set image.tag=latest`.
    - Релиз успешно установлен, поды запущены, пробы (liveness/readiness) настроены.

4. **Проверка через port‑forward**:
    - Для тестирования из локальной сети был запущен проброс порта: `kubectl port-forward svc/booking-service 8080:80`.
    - После этого эндпоинт `/ping` стал доступен по адресу `http://localhost:8080/ping`.

## Использованные команды

- `minikube start --driver=docker --memory=2048`
- `docker build -t booking-service:latest ./booking-service`
- `minikube image load booking-service:latest`
- `helm upgrade --install booking-service ./helm/booking-service --values ./values-staging.yaml --set image.tag=latest`
- `kubectl port-forward svc/booking-service 8080:80`
- `curl http://localhost:8080/ping`
- `gitlab-ci-local build test deploy`

## Файлы конфигураций

- `values-staging.yaml` – включён фича-флаг, 1 реплика, малые ресурсы.
- `values-prod.yaml` – фича-флаг выключен, 2 реплики, увеличенные ресурсы.

## Скриншоты

Все скриншоты/выводы команд приложены в этой папке.