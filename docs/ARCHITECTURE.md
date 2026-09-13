# Architecture

## Overview

This project follows a pragmatic **Clean Architecture + MVVM** pattern for a single-screen Android application that fetches and displays a list of users from a REST API.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────┐
│               Compose UI Layer              │
│  UserListScreen (stateful — owns ViewModel) │
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
                  │  Flow<Result<List<UserUI>>>
                  ▼
┌─────────────────────────────────────────────┐
│               Domain Layer                  │
│  ListUseCause (operator invoke)             │
│  ListRepository (interface)                 │
│  UserUI (domain model)                      │
└─────────────────┬───────────────────────────┘
                  │  Flow<Result<List<UserUI>>>
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
- Single `UserListScreen` composable — owns the ViewModel via `hiltViewModel()`.
- Collects `StateFlow` using `collectAsStateWithLifecycle()` — stops collecting when lifecycle < STARTED.
- Calls `viewModel.loadUsers()` directly for retry — no lambda passed as parameter.
- `when (uiState)` is exhaustive — compiler enforces all 4 states are handled.

### ViewModel (`ListViewModel`)
- Owns and exposes immutable `StateFlow<UiState<List<UserUI>>>`.
- Survives configuration changes.
- Collects `Flow<Result<List<UserUI>>>` from the use case; maps each `Result` emission to `UiState`.
- Handles the `Empty` state (HTTP 200, empty list ≠ error).
- Exposes `loadUsers()` publicly for retry.
- **Never** catches `CancellationException` — structured concurrency is preserved.

### Use Case (`ListUseCause`)
- Thin pass-through: `operator fun invoke()` delegates to the repository.
- Not `suspend` — returns a cold `Flow`, nothing executes until collected.
- Right place to add business logic (sort, filter, combine sources) without changing ViewModel or Repository.

### Repository (`ListRepository` / `ListRepositoryImpl`)
- Interface lives in the **domain** layer — depends on nothing.
- Implementation lives in the **data** layer — depends on Retrofit.
- Returns `Flow<Result<List<UserUI>>>` — supports multiple emissions (e.g. cache + network).
- Uses `flow { }` builder + `.catch { }` operator.
- `.catch` receives only real errors — `CancellationException` is propagated transparently by Flow.
- No `withContext(Dispatchers.IO)` — Retrofit 3.x handles threading internally.

### DTO + Mapper
- `ListItem` (DTO): network contract, all fields nullable (API may omit them).
- `UserUI` (domain model): presentation contract, all fields non-nullable.
- `toUserUI()` extension function: pure function, null defaults resolved here at the boundary.

### Dependency Injection (`Hilt`)
- `NetworkModule` — `object`, all providers `@Singleton`. Interceptors created inside `@Provides`.
- `RepositoryModule` — `abstract class`, uses `@Binds` (zero-overhead compile-time binding).

### Logging
- `Log.d` in Repository on success — confirms data fetched and user count.
- `Log.e` in ViewModel on failure paths — records error message for debugging.
- No logs in UseCase or Mapper — pure pass-through and pure function respectively.

---

## Data Flow

```
REST API
  └─▶ ListItem (DTO, nullable fields)
        └─▶ toUserUI() mapper
              └─▶ UserUI (domain model, non-nullable fields)
                    └─▶ Flow<Result<List<UserUI>>>
                          └─▶ ListViewModel (.collect + .fold)
                                └─▶ UiState<List<UserUI>>  (StateFlow)
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
                         calls viewModel.loadUsers()
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
│       └── ListRepositoryImpl.kt    ← Flow<Result<T>> implementation
│
├── di/
│   ├── NetworkModule.kt             ← Retrofit, OkHttp, Moshi (@Singleton)
│   └── RepositoryModule.kt          ← @Binds interface → impl
│
├── domain/
│   ├── model/
│   │   └── UserUI.kt                ← Domain model (non-nullable)
│   ├── repository/
│   │   └── ListRepository.kt        ← Flow<Result<T>> contract
│   └── usecase/
│       └── ListUseCause.kt          ← operator fun invoke()
│
├── prasentation/
│   ├── view/
│   │   ├── ListViewScreen.kt        ← Single stateful Compose screen
│   │   └── UiState.kt               ← sealed interface, 4 states
│   └── viewModel/
│       └── ListViewModel.kt         ← StateFlow + collect + fold
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
| Stream | `Flow<Result<T>>` | Supports multiple emissions; errors as values, not exceptions |
| DI | Hilt | First-class Android DI, compile-time verification |
| Network | Retrofit 3 + OkHttp | Industry standard, suspend support built-in |
| JSON | Moshi + KotlinJsonAdapterFactory | Kotlin-friendly, null-safe |
| Testing | JUnit + coroutines-test + Turbine | Standard stack, no mocking framework |

---

## Production Enhancements (out of scope for interview)

- **Offline-first**: Repository emits cached Room data first, then refreshes from network — `Flow` already supports this without any contract change.
- **Pagination**: Replace `List<UserUI>` with Paging 3 `PagingData`.
- **Certificate pinning**: Add `CertificatePinner` to `OkHttpClient`.
- **Logging**: Remove `HttpLoggingInterceptor.Level.BODY` in release builds.
- **Modularization**: Split into `:feature:users`, `:core:network`, `:core:data` modules.
- **Navigation**: Replace direct Composable call with Navigation Compose.
