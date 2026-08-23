# Отчёт по задаче 3. GraphQL Federation

## Цель задания

Реализовать федеративный GraphQL API с использованием Apollo Federation, состоящий из трёх модулей:

- **booking-subgraph** – получение бронирований по `userId` из gRPC-сервиса, с ACL (пользователь видит только свои бронирования).
- **hotel-subgraph** – получение данных об отелях из REST API монолита, с поддержкой `__resolveReference` по `id`.
- **apollo-gateway** – агрегация схем и проксирование запросов к субграфам.

---

## Архитектура решения

```
┌─────────────────────────────────────────────────────────────────┐
│                     Клиент (GraphQL-запрос)                     │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Apollo Gateway (порт 4000)                  │
│  - Объединяет схемы booking и hotel                            │
│  - Пробрасывает заголовок userid в субграфы                    │
│  - Использует RemoteGraphQLDataSource для передачи заголовков  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
┌─────────────────────────┐   ┌─────────────────────────────┐
│  Booking Subgraph       │   │  Hotel Subgraph             │
│  (порт 4001)            │   │  (порт 4002)                │
│  - GraphQL схема        │   │  - GraphQL схема            │
│  - gRPC клиент к        │   │  - REST клиент к монолиту   │
│    booking-service      │   │  - __resolveReference       │
│  - ACL (userid header)  │   │    для Hotel                │
│  - __resolveReference   │   │                             │
│    для Booking          │   │                             │
└───────────┬─────────────┘   └─────────────────────────────┘
            │
            ▼
┌─────────────────────────┐
│  gRPC booking-service   │
│  (Java, порт 9090)      │
└─────────────────────────┘
```

---

## Реализация субграфов

### 1. Booking Subgraph

**Назначение:** предоставляет список бронирований пользователя, вызывая gRPC-сервис.

**Ключевые файлы:**

- `index.js` – точка входа, схема GraphQL, резолверы.
- `grpcClient.js` – gRPC-клиент к `booking-service`, использующий `@grpc/grpc-js` и `@grpc/proto-loader`.
- `booking.proto` – Protobuf-контракт, общий с Java-сервисом.
- `Dockerfile` – сборка образа на Node.js 18.
- `package.json` – зависимости: `@apollo/server`, `@apollo/subgraph`, `graphql-tag`, `@grpc/grpc-js`, `@grpc/proto-loader`.

**Особенности реализации:**

- **Загрузка proto-файла:** используется `protoLoader.loadSync` с `keepCase: false` для получения методов в camelCase.
- **gRPC-клиент:** создаётся клиент `BookingService` с адресом из переменной окружения `BOOKING_SERVICE_GRPC_SOCKET`. Метод `listBookings` промисфицирован.
- **ACL:** в резолвере `Query.bookingsByUser` проверяется заголовок `userid`, переданный в контексте. Если он отсутствует или не совпадает с запрошенным `userId`, выбрасывается ошибка `Forbidden`.
- **Связь с отельным субграфом:** в схеме объявлен тип `Hotel @key(fields: "id")` с полем `id`. Резолвер `Booking.hotel` возвращает `{ __typename: 'Hotel', id: parent.hotelId }`, что позволяет Gateway подгрузить данные об отеле из `hotel-subgraph`.
- **`__resolveReference` для Booking:** реализован как заглушка `{ id: reference.id }`, так как в текущей задаче не требуется расширение `Booking` другими субграфами.

**Исправление ошибок ESM-модулей:**

При переходе на ES-модули (в `package.json` установлен `"type": "module"`) возникли проблемы с `__dirname` и `require`. В `grpcClient.js` добавлена эмуляция:

```javascript
import { fileURLToPath } from 'url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
```

Все `require` заменены на `import`, а `module.exports` – на `export { listBookings }`.

---

### 2. Hotel Subgraph

**Назначение:** предоставляет данные об отелях, обращаясь к REST API монолита.

**Ключевые файлы:**

- `index.js` – точка входа, схема GraphQL, резолверы.
- `package.json` – зависимости: `@apollo/server`, `@apollo/subgraph`, `graphql-tag`, `axios`.
- `Dockerfile` – сборка образа на Node.js 18.

**Особенности реализации:**

- **REST-клиент:** используется `axios` для запросов к монолиту. Адрес монолита задаётся переменной окружения `MONOLITH_API_URL`.
- **Маппинг данных:** функция `mapHotel` преобразует сущность отеля из монолита (поля `id`, `city`, `rating`, `description`) в формат GraphQL (`id`, `name`, `city`, `stars`). Поле `name` генерируется из `description` или `id`, `stars` – из `rating` через округление.
- **`__resolveReference` для Hotel:** при получении `id` отеля из Gateway, субграф делает REST-запрос к монолиту и возвращает преобразованный объект.
- **Пакетный запрос `hotelsByIds`:** позволяет клиенту получить несколько отелей по списку ID (используется `Promise.all` с параллельными запросами).

**Обработка ошибок:** при отсутствии отеля или ошибке запроса возвращается `null`, чтобы не прерывать весь GraphQL-запрос.

---

### 3. Apollo Gateway

**Назначение:** агрегирует схемы субграфов и проксирует запросы.

**Ключевые файлы:**

- `index.js` – точка входа, настройка Gateway.
- `package.json` – зависимости: `@apollo/gateway`, `@apollo/server`, `graphql`.

**Особенности реализации:**

- Используется `IntrospectAndCompose` (заменяет устаревший `serviceList`).
- Создан кастомный `RemoteGraphQLDataSource` – в методе `willSendRequest` добавляется заголовок `userid` из контекста в запросы к субграфам.
- В контексте Gateway извлекается заголовок `userid` из входящего запроса и передаётся в субграфы.

**Код Gateway:**

```javascript
class AuthenticatedDataSource extends RemoteGraphQLDataSource {
  willSendRequest({ request, context }) {
    if (context.userid) {
      request.http.headers.set('userid', context.userid);
    }
  }
}

const gateway = new ApolloGateway({
  supergraphSdl: new IntrospectAndCompose({
    subgraphs: [
      { name: 'booking', url: 'http://booking-subgraph:4001' },
      { name: 'hotel', url: 'http://hotel-subgraph:4002' },
    ],
  }),
  buildService({ url }) {
    return new AuthenticatedDataSource({ url });
  },
});
```

---

## Интеграция с внешними сервисами

- **booking-service (gRPC):** субграф обращается к Java-сервису по адресу `booking-service:9090` (внутри Docker-сети). Используется insecure-соединение.
- **монолит (REST):** субграф обращается к монолиту по адресу `http://hotelio-monolith:8080` для получения данных об отелях.

---

## Проверка работы (ACL и склейка данных)

Пример запроса через GraphQL Playground (Gateway на порту 4000):

```graphql
query {
  bookingsByUser(userId: "user1") {
    id
    hotel {
      name
      city
    }
    discountPercent
  }
}
```

Заголовок: `userid: user1`.

**Результат:** список бронирований пользователя `user1`, внутри каждого – объект отеля с полями `name` и `city`. При попытке запросить другого пользователя (заголовок `userid: user2`, а `userId: "user1"`) возвращается ошибка `Forbidden`.

---

## Запуск и развёртывание

Все сервисы упакованы в Docker и запускаются через `docker-compose.yml` (в основной сети `hotelio-net`).

**Команды:**

```bash
docker-compose up -d --build
```

Сервисы доступны:

- Gateway – `http://localhost:4000`
- Booking subgraph – `http://localhost:4001`
- Hotel subgraph – `http://localhost:4002`

Переменные окружения задаются в compose-файле.

---

## Список созданных и изменённых файлов

| Файл | Описание |
|------|----------|
| `booking-subgraph/index.js` | Основной код subgraph’а, схема, резолверы |
| `booking-subgraph/grpcClient.js` | gRPC-клиент к booking-service |
| `booking-subgraph/booking.proto` | Protobuf-контракт |
| `booking-subgraph/package.json` | Зависимости и настройки модулей |
| `booking-subgraph/Dockerfile` | Dockerfile для сборки образа |
| `hotel-subgraph/index.js` | Основной код subgraph’а, REST-клиент к монолиту |
| `hotel-subgraph/package.json` | Зависимости |
| `hotel-subgraph/Dockerfile` | Dockerfile |
| `apollo-gateway/index.js` | Gateway с кастомным DataSource |
| `apollo-gateway/package.json` | Зависимости |
| `apollo-gateway/Dockerfile` | Dockerfile |

---

## Выводы

В ходе выполнения задания успешно реализована федеративная архитектура GraphQL:

- Два субграфа (booking и hotel) независимо развёрнуты и предоставляют свои части данных.
- Gateway объединяет схемы и обеспечивает сквозную передачу заголовка `userid` для авторизации.
- Реализована связь между сущностями через `@key` и `__resolveReference`.
- Внешние сервисы (gRPC и REST) интегрированы без изменения их кода.
- ACL работает на уровне запроса, ограничивая доступ пользователя к его собственным бронированиям.

Решение готово к использованию и может быть расширено для добавления новых субграфов.