# Architecture

## Overview

This project follows a pragmatic **Clean Architecture + MVVM** pattern for a single-screen Android application that fetches and displays a list of users from a REST API.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────┐
│               Compose UI Layer              │
│  UserListRoute (stateful, collects state)   │
│  UserListScreen (stateless, renders state)  │
└─────────────────┬───────────────────────────┘
                  │  UiState<List<UserUI>>
                  ▼
┌─────────────────────────────────────────────┐
│              Presentation Layer             │
│  ListViewModel                              │
│   · MutableStateFlow<UiState<List<UserUI>>> │
│   · viewModelScope.launch                   │
│   · loadUsers() / retry                     │
└─────────────────┬───────────────────────────┘
                  │  Result<List<UserUI>>
                  ▼
┌─────────────────────────────────────────────┐
│               Domain Layer                  │
│  ListUseCause (operator invoke)             │
│  ListRepository (interface)                 │
│  UserUI (domain model)                      │
└─────────────────┬───────────────────────────┘
                  │  Result<List<UserUI>>
                  ▼
┌─────────────────────────────────────────────┐
│                Data Layer                   │
│  ListRepositoryImpl                         │
│  ApiService (Retrofit interface)            │
│  ListItem (DTO)                             │
│  UserMapper (DTO → domain)                  │
└─────────────────┬───────────────────────────┘
                  │  HTTP
                  ▼
        https://jsonplaceholder.typicode.com/users
```

---

## Layer Responsibilities

### Compose UI
- Renders the current `UiState` — nothing more.
- **Stateful** composable (`UserListRoute`) collects from `StateFlow` using `collectAsStateWithLifecycle()`.
- **Stateless** composable (`UserListScreen`) receives state + event lambdas — no ViewModel reference, fully previewable and testable.
- Events flow **up** (retry lambda), state flows **down** (uiState parameter).

### ViewModel (`ListViewModel`)
- Owns and exposes immutable `StateFlow<UiState<List<UserUI>>>`.
- Survives configuration changes.
- Translates `Result<T>` from the use case into `UiState`.
- Handles the `Empty` state (HTTP 200, empty list ≠ error).
- Exposes `loadUsers()` publicly for retry.
- **Never** catches `CancellationException` — structured concurrency is preserved.

### Use Case (`ListUseCause`)
- Thin orchestration layer: delegates directly to the repository.
- The right place to add business logic (sorting, filtering, pagination) without changing the ViewModel or Repository.
- `operator fun invoke()` — called as `useCase()`, idiomatic Kotlin.

### Repository (`ListRepository` / `ListRepositoryImpl`)
- Interface lives in the **domain** layer — depends on nothing.
- Implementation lives in the **data** layer — depends on Retrofit.
- Returns `Result<List<UserUI>>` — no `UiState`, no `Flow`, no Android framework types.
- Catches only `IOException` and `HttpException` — does **not** catch bare `Exception` to avoid swallowing `CancellationException`.
- No `withContext(Dispatchers.IO)` — Retrofit 3.x handles threading internally.

### DTO + Mapper
- `ListItem` (DTO): network contract, all fields nullable (API may omit them).
- `UserUI` (domain model): presentation contract, all fields non-nullable (null-safety resolved at the boundary).
- `toUserUI()` extension function: pure function, stateless, trivially unit-testable.

### Dependency Injection (`Hilt`)
- `NetworkModule` — `object`, all providers `@Singleton`. Interceptors created **inside** `@Provides` (lazy, not eager).
- `RepositoryModule` — `abstract class`, uses `@Binds` (zero-overhead compile-time binding, no reflection).

---

## Data Flow

```
REST API
  └─▶ ListItem (DTO, nullable fields)
        └─▶ toUserUI() mapper
              └─▶ UserUI (domain model, non-nullable fields)
                    └─▶ Result<List<UserUI>>
                          └─▶ ListViewModel
                                └─▶ UiState<List<UserUI>>
                                      └─▶ Compose UI
```

---

## State Machine

```
            ┌─────────┐
   init{}   │ Loading │◄────────────────┐
   ─────────►         │                 │
            └────┬────┘           loadUsers()
                 │                     │
       ┌─────────┼─────────┐           │
       ▼         ▼         ▼           │
  ┌─────────┐ ┌───────┐ ┌───────┐     │
  │ Success │ │ Empty │ │ Error │─────►┘
  └─────────┘ └───────┘ └───────┘
                            ▲
                         Retry button
                         calls loadUsers()
```

---

## Project Structure

```
app/
├── data/
│   ├── dto/
│   │   └── ListItem.kt              ← Network DTO
│   ├── mapper/
│   │   └── UserMapper.kt            ← DTO → Domain
│   ├── remote/
│   │   └── ApiService.kt            ← Retrofit interface
│   └── repository/
│       └── ListRepositoryImpl.kt    ← Repository implementation
│
├── di/
│   ├── NetworkModule.kt             ← Retrofit, OkHttp, Moshi
│   └── RepositoryModule.kt          ← @Binds interface → impl
│
├── domain/
│   ├── model/
│   │   └── UserUI.kt                ← Domain model
│   ├── repository/
│   │   └── ListRepository.kt        ← Repository contract
│   └── usecase/
│       └── ListUseCause.kt          ← Business operation
│
├── prasentation/
│   ├── view/
│   │   ├── ListViewScreen.kt        ← Compose UI (stateful + stateless)
│   │   └── UiState.kt               ← Screen state model
│   └── viewModel/
│       └── ListViewModel.kt         ← ViewModel
│
├── ui/theme/                        ← Material 3 theme
│
└── MainActivity.kt                  ← Single activity entry point
```

---

## Key Technology Decisions

| Concern | Choice | Reason |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Modern declarative UI, no XML |
| State | `StateFlow` | Hot, always has a value, lifecycle-safe with `collectAsStateWithLifecycle` |
| DI | Hilt | First-class Android DI, compile-time verification |
| Network | Retrofit 3 + OkHttp | Industry standard, suspend support built-in |
| JSON | Moshi + KotlinJsonAdapterFactory | Kotlin-friendly, null-safe |
| Error model | `Result<T>` | Kotlin stdlib, no custom wrapper needed |
| Testing | JUnit + coroutines-test + Turbine | Standard stack, no mocking framework |

---

## Production Enhancements (out of scope for interview)

- **Offline-first**: Add Room as local cache; repository reads from DB and refreshes from network.
- **Pagination**: Replace `List<UserUI>` with Paging 3 `PagingData`.
- **Certificate pinning**: Add `CertificatePinner` to `OkHttpClient`.
- **Logging**: Remove `HttpLoggingInterceptor.Level.BODY` in release builds.
- **Modularization**: Split into `:feature:users`, `:core:network`, `:core:data` modules.
- **Navigation**: Replace direct Composable call with Navigation Compose.
