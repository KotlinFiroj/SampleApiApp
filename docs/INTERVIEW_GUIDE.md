# Interview Guide — American Airlines Senior Android

## Timed Implementation Strategy

```
0–10 min   Understand requirements, design architecture, ask clarifying questions
10–20 min  DTO, domain model, mapper, repository interface + impl, ApiService
20–35 min  UseCase, ViewModel (StateFlow, loadUsers, error handling, retry)
35–55 min  Compose UI (all 4 states, LazyColumn with keys, collectAsStateWithLifecycle)
55–70 min  Error handling review, empty state, DI modules
70–85 min  Unit tests (ViewModel), FakeRepository
85–90 min  Clean up, explain trade-offs, answer questions
```

---

## Architecture Decisions — Q&A

### "Walk me through your architecture."

> "I'm using Clean Architecture with MVVM. Each layer depends only on the layer below it. The domain layer depends on nothing — no Android, no Retrofit, no UI.
>
> The Repository returns `Flow<Result<List<UserUI>>>`. The UseCase passes it through. The ViewModel collects the Flow, calls `fold` on each `Result` emission, and maps it to `UiState`. The Compose UI collects the `StateFlow` using `collectAsStateWithLifecycle()`."

---

### "Why `Flow<Result<T>>` in the repository instead of a plain `suspend fun`?"

> "`Flow<Result<T>>` is the production-correct pattern because it handles two things separately:
>
> `Flow` handles the *stream* — today it emits once (network only), tomorrow it can emit twice (cached DB data first, then fresh network data) without changing the contract at all.
>
> `Result` handles the *outcome* of each emission — if I used `Flow<T>` alone, any exception would terminate the entire stream permanently. Wrapping in `Result` means errors are values, not exceptions — the stream stays alive and the ViewModel maps each emission to the right `UiState`."

**Alternative**: `suspend fun` returning `Result<T>` — simpler, correct for one-shot calls, but can't support cache-then-network without changing the interface.

**Trade-off**: Slightly more complex than a suspend fun, but the contract is future-proof for offline-first.

**Follow-up**: *"How would you add offline caching?"*
> "I'd add Room. The repository emits the cached DB result immediately, then fires the network call and emits the fresh result. The ViewModel receives both emissions and updates the UI twice — users see stale data instantly, then fresh data arrives. The contract `Flow<Result<T>>` doesn't change at all."

---

### "Why `sealed interface` instead of `sealed class` for UiState?"

> "A `sealed interface` allows a class to implement multiple sealed types — useful in multi-module apps. `data object` and `data class` variants work naturally. There's no runtime overhead difference."

---

### "Why do you have an Empty state?"

> "HTTP 200 with an empty list is not an error — the server responded correctly, there's just no data. Treating it as an error shows 'Something went wrong' when the correct message is 'No users found.' The Empty state lets the UI show a meaningful message and a Refresh button."

---

### "Why `collectAsStateWithLifecycle()` instead of `collectAsState()`?"

> "`collectAsStateWithLifecycle()` stops collecting when the lifecycle drops below STARTED — when the app is backgrounded. This prevents unnecessary work. `collectAsState()` keeps collecting in the background, which wastes resources."

---

### "Why didn't you add `withContext(Dispatchers.IO)` in the repository?"

> "Retrofit 3.x handles threading internally for suspend functions. Adding `withContext(Dispatchers.IO)` would be redundant and misleading."

---

### "Why `@Binds` in RepositoryModule instead of `@Provides`?"

> "`@Binds` is a compile-time hint: 'when `ListRepository` is needed, inject `ListRepositoryImpl`.' Zero runtime overhead — no function call. `@Provides` requires Hilt to call a function at runtime. For binding interface to implementation, `@Binds` is the idiomatic choice."

---

### "Why didn't you catch `Exception` broadly in the ViewModel?"

> "`CancellationException` extends `Exception`. Catching it and emitting `UiState.Error` breaks structured concurrency — the coroutine appears failed when it was legitimately cancelled. I catch only `IOException` and `HttpException`. In the Flow pipeline, `.catch` also preserves this — `CancellationException` is never delivered to `.catch`."

---

### "Where do you use Flow in this project?"

> "In three places:
>
> 1. `ListRepository` returns `Flow<Result<List<UserUI>>>` — cold Flow built with `flow { }`, errors caught with `.catch { }`.
> 2. `ListUseCause` passes the Flow through unchanged — `operator fun invoke()` is not `suspend` because it returns a cold Flow.
> 3. `ListViewModel` exposes a `StateFlow<UiState<List<UserUI>>>` — the UI collects this hot stream with `collectAsStateWithLifecycle()`."

**Follow-up**: *"What is a cold vs hot Flow?"*
> "Cold Flow: the `flow { }` block doesn't execute until someone calls `collect`. Each collector gets its own independent execution — like a function. Hot Flow: `StateFlow` and `SharedFlow` are always active regardless of collectors. `StateFlow` always has a value and replays the latest to new collectors."

**Follow-up**: *"Why is UseCase invoke() not suspend?"*
> "Because it returns a `Flow`, not a value. `suspend` is for functions that suspend until a result is ready. `Flow` is lazy — it describes *how* to produce values, but does nothing until collected. Making it `suspend` would be incorrect — it would imply the function suspends, but it actually returns immediately with an unstarted stream."

---

### "How would you implement search-as-you-type?"

> "I'd add `val searchQuery = MutableStateFlow(\"\")` in the ViewModel, then build a pipeline:
>
> ```
> searchQuery
>     .debounce(300)           ← wait for user to stop typing
>     .distinctUntilChanged()  ← skip if query didn't change
>     .flatMapLatest { query → repository.search(query) }  ← cancel previous, start new
>     .catch { emit(Result.failure(it)) }
>     .launchIn(viewModelScope)
> ```
>
> `flatMapLatest` is critical — it cancels the previous in-flight API call the moment a new query arrives, so only the latest result reaches the UI."

---

### "How would you add offline support?"

> "The `Flow<Result<T>>` contract already supports it without any changes. In the repository I'd add Room and emit two values:
>
> ```kotlin
> override fun getUsers(): Flow<Result<List<UserUI>>> = flow {
>     emit(Result.success(dao.getUsers()))   // cached data immediately
>     val fresh = apiService.getUserList()   // then network refresh
>     dao.insertAll(fresh.body()...)
>     emit(Result.success(dao.getUsers()))   // updated cache
> }.catch { emit(Result.failure(it)) }
> ```
>
> The ViewModel and UI don't change at all — they already handle multiple emissions."

---

### "How would you add pagination?"

> "Replace `List<UserUI>` with Paging 3's `PagingData<UserUI>`. Repository returns a `Pager` with a `PagingSource`. ViewModel exposes `Flow<PagingData<UserUI>>` via `cachedIn(viewModelScope)`. UI uses `LazyPagingItems`."

---

### "What would you do differently in production?"

1. Remove `HttpLoggingInterceptor.Level.BODY` in release builds — logs auth tokens.
2. Add certificate pinning via `CertificatePinner` on `OkHttpClient`.
3. Add a `NetworkMonitor` to show offline banners proactively.
4. Modularize: `:feature:users`, `:core:network`, `:core:data`, `:core:ui`.
5. Add Compose Navigation for multi-screen flows.
6. Implement Paging 3 if the list can grow large.
7. Replace debug logs with a proper logging framework (Timber) that strips in release.

---

## Common Follow-Up Questions

| Question | Key points in your answer |
|---|---|
| What is structured concurrency? | Coroutines are scoped; child failures propagate; cancellation is cooperative |
| What is `viewModelScope`? | Tied to ViewModel lifecycle; cancelled when `onCleared()` is called |
| `StateFlow` vs `SharedFlow`? | StateFlow: always has value, replays 1, for screen state. SharedFlow: configurable replay, for one-time events |
| What is `stateIn`? | Converts a cold `Flow` to a hot `StateFlow` |
| Cold vs hot Flow? | Cold: runs per collector, lazy. Hot: always active (StateFlow, SharedFlow) |
| What is `flatMapLatest`? | Cancels previous inner Flow when a new value arrives — essential for search |
| What is `remember`? | Stores a value across recompositions; does NOT survive configuration changes |
| What is state hoisting? | Moving state up so composables are stateless and reusable |
| What is recomposition? | Compose re-executes composable functions when their inputs change |
| What is dependency inversion? | High-level modules depend on abstractions — `ListRepository` is an interface in the domain layer |

---

## Final Checklist Before Submitting

- [ ] All 4 UI states rendered: Loading, Success, Empty, Error
- [ ] Retry button on Error and Empty — calls `viewModel.loadUsers()` directly
- [ ] ViewModel exposes immutable `StateFlow`
- [ ] `CancellationException` is NOT caught anywhere
- [ ] Repository returns `Flow<Result<T>>` — not `UiState`, not bare `Flow<T>`
- [ ] UseCase `invoke()` is NOT suspend — returns cold Flow
- [ ] Domain model is non-nullable; null defaults in mapper
- [ ] `collectAsStateWithLifecycle()` used in the UI
- [ ] `LazyColumn` uses stable `key =` parameter
- [ ] `NetworkModule` providers are `@Singleton`
- [ ] `RepositoryModule` uses `@Binds`
- [ ] Unit tests cover all 8 scenarios
- [ ] No hardcoded secrets
- [ ] `innerPadding` passed to content in `Scaffold`
- [ ] Can explain every decision above without reading notes
