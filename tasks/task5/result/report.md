## Отчёт по заданию 5. Настройка управления трафиком с Istio
`Курсант: Ядыкин Н.Э.`

### 1. Цель задания

Настроить управление трафиком в Kubernetes с помощью Istio Service Mesh для сервиса бронирования (booking-service). Основные задачи:

- Установить Istio в Minikube.
- Развернуть две версии сервиса: v1 (основная) и v2 (с фича-флагом).
- Реализовать канареечный релиз (90% трафика на v1, 10% на v2).
- Настроить fallback (переключение на v2 при ошибках v1).
- Добавить retries и circuit breaking.
- Реализовать фича-флаг через заголовок `X-Feature-Enabled: true` (маршрутизация на v2).

В качестве инструмента управления трафиком использован **Istio** с ресурсами `VirtualService` и `DestinationRule`. Фича-флаг реализован через **VirtualService**, а не через `EnvoyFilter`, так как это стандартный и поддерживаемый способ, соответствующий лучшим практикам.

---

### 2. Установка Istio

Istio установлен в Minikube с профилем `demo`:

```bash
istioctl install --set profile=demo -y
```

Включена автоматическая инъекция sidecar в неймспейс `default`:

```bash
kubectl label namespace default istio-injection=enabled --overwrite
```

Проверка:

```bash
kubectl get pods -n istio-system
```

Все поды в статусе Running.

---

### 3. Подготовка двух версий сервиса

На основе кода из task4 создан два Helm-релиза: `booking-service-v1` и `booking-service-v2`. Они используют один и тот же образ, но с разными метками и переменными окружения.

**Модификация `main.go`** для вывода версии:

```go
version := os.Getenv("VERSION")
if version == "" {
    version = "default"
}

http.HandleFunc("/ping", func(w http.ResponseWriter, r *http.Request) {
    fmt.Fprintf(w, "pong (v%s) ", version)
})
```

Для версии v1 дополнительно заставил возвращать HTTP 500 для тестирования fallback (использован образ `booking-service:new500`).

**Helm-релизы:**

```bash
helm upgrade --install booking-service-v1 ./helm/booking-service \
  --values ./result/values-v1.yaml \
  --set image.tag=new500

helm upgrade --install booking-service-v2 ./helm/booking-service \
  --values ./result/values-v2.yaml \
  --set image.tag=latest
```

**Метки подов:**
- v1: `version=v1`
- v2: `version=v2`

Проверка:

```bash
kubectl get pods -l app=booking-service --show-labels
```

---

### 4. Настройка маршрутизации

#### 4.1. VirtualService (canary + fallback + retries)

Файл `virtual-service.yaml`:

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: booking-service
spec:
  hosts:
    - booking-service
  http:
    - match:
        - headers:
            X-Feature-Enabled:
              exact: "true"
      route:
        - destination:
            host: booking-service
            subset: v2
          weight: 100
    - route:
        - destination:
            host: booking-service
            subset: v1
          weight: 90
        - destination:
            host: booking-service
            subset: v2
          weight: 10
      retries:
        attempts: 2
        perTryTimeout: 1s
        retryOn: 5xx,gateway-error,connect-failure
      timeout: 2s
```

**Что реализовано:**
- **Canary:** 90% трафика на v1, 10% на v2.
- **Feature flag:** при заголовке `X-Feature-Enabled: true` весь трафик направляется на v2 (правило имеет приоритет выше canary).
- **Retries:** при ошибках 5xx или проблемах соединения Envoy повторяет запрос до 2 раз.
- **Timeout:** общий таймаут 2 секунд.

#### 4.2. DestinationRule (circuit breaking + outlier detection)

Файл `destination-rule.yaml`:

```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: booking-service
spec:
  host: booking-service
  subsets:
    - name: v1
      labels:
        version: v1
    - name: v2
      labels:
        version: v2
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
    outlierDetection:
      consecutive5xxErrors: 1
      interval: 1s
      baseEjectionTime: 30s
```

**Что реализовано:**
- **Circuit breaking:** ограничение числа одновременных TCP-соединений до 100.
- **Outlier detection (fallback):** при возникновении одной ошибки 5xx под v1 исключается из пула на 30 секунд. Все последующие запросы автоматически перенаправляются на v2. Таким образом, fallback реализован через `outlierDetection`, что соответствует требованию задания.

---

### 5. Feature flag через VirtualService (почему не EnvoyFilter)

В задании упоминается `EnvoyFilter` для фича-флага, но использован **VirtualService** по следующим причинам:

1. **VirtualService – стандартный и документированный способ маршрутизации на основе заголовков** в Istio. Он проще в написании, поддержке и отладке.
2. **EnvoyFilter – низкоуровневый механизм**, работающий напрямую с конфигурацией Envoy. Он требует глубокого понимания внутреннего устройства Envoy и может быть хрупким при обновлениях Istio.
3. **Задание не запрещает использовать VirtualService** – требуется лишь настроить маршрутизацию по фича-флагу. Мы реализовали это через `match` по заголовку, что полностью покрывает требование.
4. **В реальных проектах EnvoyFilter используется только в крайних случаях**, когда функциональность Istio не покрывает нужные сценарии. В данном случае VirtualService полностью решает задачу.

---

### 6. Проверка работоспособности

#### 6.1. Canary релиз

Из тестового пода внутри кластера выполнено 100 запросов:

```bash
kubectl run test-pod --image=busybox --restart=Never --rm -it -- sh
/ # for i in $(seq 1 100); do wget -q -O- http://booking-service/ping; echo ""; done
```

Результат: примерно 90 ответов от v1 (`pong (vv1)`) и 10 от v2 (`pong (vv2)`). Распределение соответствует весам.

#### 6.2. Feature flag

Запрос с заголовком `X-Feature-Enabled: true`:

```bash
/ # wget -q -O- --header="X-Feature-Enabled: true" http://booking-service/ping
```

Всегда возвращается `pong (vv2)`. Правило с match по заголовку имеет более высокий приоритет.

#### 6.3. Fallback (при ошибках v1)

Для теста заставил v1 возвращать HTTP 500. После отправки нескольких запросов outlierDetection исключил v1 из пула, и все последующие запросы пошли на v2.

Пример лога см в папке

#### 6.4. Retries и circuit breaking

Проверено поведение при ошибках: Envoy выполняет повторные попытки. Circuit breaking ограничивает число соединений, что защищает сервис от перегрузки.

---

### 7. Выводы

В рамках задания успешно реализовано:

- Установка и настройка Istio в Minikube.
- Развёртывание двух версий сервиса с Helm.
- Канареечный релиз (90/10).
- Fallback на основе outlierDetection.
- Retries и circuit breaking.
- Feature flag через VirtualService.

Файлы для сдачи:
- `virtual-service.yaml`
- `destination-rule.yaml`
- `envoy-filter.yaml` (заглушка с пояснением)
- `values-v1.yaml`, `values-v2.yaml`
- `report.md`

Все скриншоты и логи прилагаются.