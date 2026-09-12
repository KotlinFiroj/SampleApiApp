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

> "I'm using Clean Architecture with MVVM. The key idea is that each layer only depends on the layer directly below it, and the domain layer depends on nothing — no Android, no Retrofit, no UI. This makes each piece independently testable and replaceable.
>
> The Compose UI collects state from the ViewModel. The ViewModel calls a UseCase, which calls the Repository. The Repository calls Retrofit, maps the DTO to a domain model, and returns a `Result`. The ViewModel translates that `Result` into `UiState` for the screen."

---

### "Why did you use `Result<T>` instead of `Flow<UiState>` in the repository?"

> "The repository's job is to fetch data and communicate success or failure. It should not know about UI state — that's the ViewModel's responsibility. `Result<T>` from the Kotlin stdlib wraps success and failure cleanly with no framework coupling. A single network fetch is a one-shot operation — a `suspend fun` returning `Result` is the correct abstraction. `Flow` belongs at the ViewModel layer when you need to react to ongoing streams like a Room database."

**Alternative**: Return `Flow<T>` from the repository and catch errors in the ViewModel's `catch` operator.

**Trade-off**: `Flow` is more powerful for reactive streams (like Room) but adds complexity for a simple one-shot network call.

**Follow-up**: *"How would you handle database caching?"* → Repository pattern with `Flow` from Room + network refresh on top.

---

### "Why `sealed interface` instead of `sealed class` for UiState?"

> "A `sealed interface` allows a class to implement multiple sealed types — useful in multi-module apps. `data object` and `data class` variants work naturally. There's no runtime overhead difference. For our single-module app, either works, but `sealed interface` is the more modern, forward-compatible choice."

---

### "Why do you have an Empty state?"

> "HTTP 200 with an empty list is not an error — the server responded correctly, there's just no data. If I treat it as an error, the user sees 'Something went wrong' when the correct message is 'No users found.' The Empty state lets the UI show a meaningful message and optionally a refresh button."

---

### "Why `collectAsStateWithLifecycle()` instead of `collectAsState()`?"

> "`collectAsStateWithLifecycle()` automatically stops collecting when the lifecycle drops below STARTED — for example, when the app is backgrounded. This prevents unnecessary work and state updates while the screen isn't visible. `collectAsState()` keeps collecting in the background, which wastes resources and can cause subtle bugs on return from background."

---

### "Why didn't you add `withContext(Dispatchers.IO)` in the repository?"

> "Retrofit 3.x (and 2.6+) handles threading internally when you call a `suspend` function. The library dispatches the network call to its internal executor and resumes your coroutine on the calling dispatcher. Adding `withContext(Dispatchers.IO)` would be redundant and misleading — it implies you're doing something thread-unsafe, which you're not."

---

### "Why `@Binds` in RepositoryModule instead of `@Provides`?"

> "`@Binds` is a compile-time hint to Hilt: 'when `ListRepository` is needed, inject `ListRepositoryImpl`.' Hilt resolves this with zero runtime overhead — no function call, no object allocation beyond the singleton itself. `@Provides` requires Hilt to call a function at runtime. For binding an interface to an implementation, `@Binds` is the idiomatic and more efficient choice."

---

### "Why didn't you catch `Exception` broadly in the ViewModel?"

> "`CancellationException` extends `Exception`. If I catch it and emit `UiState.Error`, the coroutine appears to have failed when it was legitimately cancelled — for example because the user navigated away and the ViewModel was cleared. This breaks structured concurrency and can cause state leaks. I catch only `IOException` and `HttpException`, the two meaningful failure modes for a network call."

---

### "Why do you use a Fake repository instead of Mockito/MockK?"

> "A Fake is a real implementation of the interface with simplified behavior controlled by the test. It's compiler-checked — if the interface changes, the Fake won't compile, and I know immediately what to fix. Mocks silently pass until runtime. Fakes are also faster (no reflection, no byte-code generation) and more readable: `fake.result = Result.failure(IOException())` clearly states the test's intent."

---

### "Why `LazyColumn` with `key = { user.id }`?"

> "LazyColumn with stable, unique keys allows Compose to track individual items across recompositions. If the list changes — items added, removed, or reordered — Compose knows exactly which items moved and can animate or skip them efficiently. Without keys, Compose assumes items at the same index are the same item, which causes incorrect animations and unnecessary recompositions."

---

### "You removed Flow from the Repository — but where IS Flow used?"

> "Flow is used in the ViewModel, which is exactly where it belongs for this use case. The Repository does a one-shot network call — wrapping that in `Flow` adds no value. The ViewModel needs to *react to events over time* — that's Flow's job.
>
> I use two Flow pipelines in the ViewModel:
>
> 1. A `retryTrigger` (`MutableStateFlow<Int>`) feeds into `flatMapLatest` to trigger and cancel in-flight loads.
> 2. A `searchQuery` (`MutableStateFlow<String>`) uses `debounce(300) → distinctUntilChanged() → flatMapLatest` to filter the list as the user types, without firing on every keystroke or redundant queries.
>
> Both pipelines terminate in `onEach { _uiState.value = it }` so the UI always collects from a single `StateFlow`."

**The canonical Flow pipeline interviewers expect you to know:**
```
searchQuery (MutableStateFlow<String>)
    │
    ├─ debounce(300)          ← wait for user to stop typing
    ├─ distinctUntilChanged() ← skip if query didn't change
    ├─ flatMapLatest { q →    ← cancel previous, start new
    │      callApiFlow(q)
    │  }
    ├─ catch { emit(Error) }  ← handle errors without killing the pipeline
    └─ launchIn(viewModelScope)
```

**Follow-up**: *"Why `flatMapLatest` instead of `flatMapMerge`?"*
> "`flatMapMerge` runs all inner Flows concurrently — if the user types 3 characters, 3 API calls run in parallel and the responses arrive out of order, so the UI could briefly show results for the *wrong* query. `flatMapLatest` cancels the previous inner Flow the moment a new one starts — only the latest query's result ever reaches the UI."

---



> "I'd extend the Repository to use Room as a local cache. The repository would emit data from the database immediately (via `Flow` from Room), then trigger a network refresh in the background. The UI would show cached data instantly and update when fresh data arrives. I'd differentiate between 'stale cache' and 'no cache + no network' states. For the 60-minute interview, I'd call this out as a production enhancement but not implement it unless asked."

```
Compose
   ↓
ViewModel
   ↓
Repository
   ├── Room (immediate local data via Flow)
   └── Retrofit (background refresh)
```

---

### "How would you add pagination?"

> "Replace `List<UserUI>` with Paging 3's `PagingData<UserUI>`. The repository returns a `Pager` with a `PagingSource` backed by the API (and optionally Room for cached paging). The ViewModel exposes `Flow<PagingData<UserUI>>` via `cachedIn(viewModelScope)`. The UI uses `LazyPagingItems` in the Compose collection. The main benefit: we don't load 1,000 items into memory upfront."

---

### "What would you do differently in a production app?"

1. Remove `HttpLoggingInterceptor.Level.BODY` in release builds — it logs request/response bodies including auth tokens.
2. Add certificate pinning via `CertificatePinner` on `OkHttpClient`.
3. Add a `NetworkMonitor` (ConnectivityManager) to show offline banners proactively.
4. Modularize: `:feature:users`, `:core:network`, `:core:data`, `:core:ui`.
5. Add Compose Navigation for multi-screen flows.
6. Implement Paging 3 if the list can grow large.
7. Add Room for offline-first caching.
8. Replace fake HTTP 500 construction in tests with a `MockWebServer`.

---

## Common Follow-Up Questions

| Question | Key points in your answer |
|---|---|
| What is structured concurrency? | Coroutines are scoped; child failures propagate; cancellation is cooperative |
| What is `viewModelScope`? | Tied to ViewModel lifecycle; cancelled when `onCleared()` is called |
| What is the difference between `StateFlow` and `SharedFlow`? | StateFlow: always has value, replays 1, for screen state. SharedFlow: configurable replay, for one-time events |
| What is `stateIn`? | Converts a cold `Flow` to a hot `StateFlow`; useful for converting repository Flows |
| What is `derivedStateOf`? | Memo-izes a derived Compose state to avoid unnecessary recompositions |
| What is `remember`? | Stores a value across recompositions; does NOT survive configuration changes |
| What is state hoisting? | Moving state up to the caller so composables are stateless and reusable |
| What is recomposition? | Compose re-executes composable functions when their inputs change |
| Explain SOLID in Android | Single responsibility per class; depend on interfaces not implementations; Repository/UseCase each have one job |
| What is dependency inversion? | High-level modules depend on abstractions (interfaces), not concrete classes — that's why `ListRepository` is an interface in the domain layer |

---

## Final Checklist Before Submitting

- [ ] All 4 UI states rendered: Loading, Success, Empty, Error
- [ ] Retry button present on Error and Empty screens
- [ ] ViewModel exposes immutable `StateFlow`
- [ ] `CancellationException` is NOT caught
- [ ] Repository returns `Result<T>`, not `UiState`
- [ ] Domain model is non-nullable
- [ ] `collectAsStateWithLifecycle()` used in the UI
- [ ] `LazyColumn` uses stable `key =` parameter
- [ ] `NetworkModule` providers are `@Singleton`
- [ ] `RepositoryModule` uses `@Binds`
- [ ] Unit tests cover: initial state, success, empty, IOException, HttpException, unexpected error, retry (error→success), retry (success→error)
- [ ] No hardcoded secrets
- [ ] `innerPadding` passed to content in `Scaffold`
- [ ] Can explain every decision above without reading notes
