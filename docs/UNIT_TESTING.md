# Unit Testing Guide

## Testing Philosophy

> Test behaviour, not implementation.

Each test answers one question: **given this input/state, does the system produce this output/state?** Tests never reach into private fields, never verify how many times a method was called (unless that *is* the behaviour being tested), and never depend on execution order.

---

## Test Pyramid

```
              ┌─────────────────┐
              │   UI Tests      │  ← fewest, slowest, highest confidence
              │ (Compose, E2E)  │
              └────────┬────────┘
           ┌───────────┴───────────┐
           │   Integration Tests   │  ← MockWebServer, Room in-memory DB
           └───────────┬───────────┘
     ┌─────────────────┴─────────────────┐
     │         Unit Tests                │  ← most, fastest, widest coverage
     │  (ViewModel, Mapper, UseCase)      │
     └───────────────────────────────────┘
```

The bulk of test coverage comes from **unit tests**. They are fast, isolated, and run on the JVM without a device or emulator.

---

## Tools

| Tool | Version | Purpose |
|---|---|---|
| JUnit 4 | 4.13.2 | Test runner and assertions |
| `kotlinx-coroutines-test` | 1.9.0 | `runTest`, `StandardTestDispatcher`, `advanceUntilIdle` |
| Turbine | 1.2.0 | Concise `StateFlow`/`Flow` assertion with `test {}` |
| `FakeListRepository` | — | Compiler-checked test double, no mocking framework |

---

## What Is Tested

### ViewModel (`ListViewModelTest`)

The ViewModel is the most important unit to test because it contains all the state-management logic. Every screen state transition is covered.

| # | Test | Scenario | Expected state |
|---|---|---|---|
| 1 | `initial state is Loading` | ViewModel created, coroutine not yet run | `UiState.Loading` |
| 2 | `loadUsers emits Loading then Success` | Repository returns non-empty list | `Loading → Success(users)` |
| 3 | `loadUsers emits Empty` | Repository returns empty list | `Loading → Empty` |
| 4 | `loadUsers emits Error on IOException` | Repository throws `IOException` | `Loading → Error("Network…")` |
| 5 | `loadUsers emits Error on HttpException` | Repository throws `HttpException(500)` | `Loading → Error("Server…")` |
| 6 | `loadUsers emits Error on unexpected failure` | Repository returns `Result.failure(RuntimeException)` | `Loading → Error` |
| 7 | `retry error → success` | First call fails; retry succeeds | `Loading → Error → Loading → Success` |
| 8 | `retry success → error` | First call succeeds; retry fails (network drops) | `Loading → Success → Loading → Error` |

### Why these 8 cases?

- **Initial state**: Guards against the ViewModel starting in a non-Loading state, which would cause a blank screen flash.
- **Success**: Proves the happy path works end-to-end.
- **Empty**: Proves we differentiate HTTP 200 + empty list from a real error.
- **IOException**: The most common real-world failure (no connectivity, timeout).
- **HttpException**: Server errors (401, 500) — different message, different handling.
- **Unexpected failure**: Defence against exceptions we didn't anticipate.
- **Retry (both directions)**: Retry is a first-class user action. Both directions are tested to prevent regressions where the state gets "stuck."

---

## Fake Repository Pattern

```kotlin
class FakeListRepository : ListRepository {
    var result: Result<List<UserUI>> = Result.success(defaultUsers())

    override suspend fun getUsers(): Result<List<UserUI>> = result
}
```

**Why Fake over Mock (Mockito/MockK)?**

| | Fake | Mock |
|---|---|---|
| Compiler-checked | ✅ Won't compile if interface changes | ❌ Silently passes until runtime |
| Framework dependency | ✅ None | ❌ Mockito/MockK annotation processing |
| Readability | ✅ `fake.result = ...` | ⚠️ `whenever(...).thenReturn(...)` |
| Speed | ✅ JVM, no reflection | ⚠️ Byte-code generation overhead |
| Flexibility | ✅ Can add delay, tracking, state | ⚠️ Requires additional setup |

**Interview answer**: "I prefer fakes for repository tests because they're compiler-checked, have no framework overhead, and communicate test intent clearly. If the `ListRepository` interface changes, the fake won't compile — I find out at build time, not test runtime."

---

## Coroutine Testing

### The problem with `viewModelScope` in unit tests

`viewModelScope` internally uses `Dispatchers.Main`. In a JVM unit test there is no Android `Looper`, so `Dispatchers.Main` throws.

### Solution: `StandardTestDispatcher` + `setMain`

```kotlin
@Before
fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
}

@After
fun tearDown() {
    Dispatchers.resetMain()
}
```

### `StandardTestDispatcher` vs `UnconfinedTestDispatcher`

| | `StandardTestDispatcher` | `UnconfinedTestDispatcher` |
|---|---|---|
| Coroutine execution | Queued; runs when you call `advanceUntilIdle()` | Runs eagerly on current thread |
| Can assert Loading state | ✅ Yes — coroutine hasn't run yet | ❌ No — Loading is skipped |
| Predictability | ✅ Explicit control | ⚠️ May miss intermediate states |

We use `StandardTestDispatcher` so we can assert `UiState.Loading` **before** the coroutine completes — critical for verifying the loading indicator appears.

### `runTest` vs `runBlocking`

Use `runTest` (from `kotlinx-coroutines-test`) for all coroutine tests:
- It installs a `TestCoroutineScheduler` that makes `delay()` virtual (no real wall-clock waiting).
- It automatically advances time at the end of the test.
- It fails the test if any coroutine leaks (uncompleted coroutines after test ends).

---

## Flow Testing with Turbine

Turbine replaces the boilerplate of manually collecting emissions into a list.

**Without Turbine:**
```kotlin
val emissions = mutableListOf<UiState<*>>()
val job = launch { viewModel.uiState.collect { emissions.add(it) } }
advanceUntilIdle()
job.cancel()
assertEquals(UiState.Loading, emissions[0])
assertTrue(emissions[1] is UiState.Success)
```

**With Turbine:**
```kotlin
viewModel.uiState.test {
    assertEquals(UiState.Loading, awaitItem())
    advanceUntilIdle()
    assertTrue(awaitItem() is UiState.Success)
    cancelAndIgnoreRemainingEvents()
}
```

Turbine is more readable, handles cancellation automatically, and gives better error messages when an unexpected item arrives.

---

## Compose UI Testing (What to add next)

Compose UI tests run on a device/emulator and verify user-visible behaviour using semantic assertions. They are in `src/androidTest/`.

### Test cases to implement

```kotlin
// Loading state
composeTestRule.setContent {
    UserListScreen(UiState.Loading, onRetry = {})
}
composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()

// Success state
composeTestRule.setContent {
    UserListScreen(UiState.Success(defaultUsers()), onRetry = {})
}
composeTestRule.onNodeWithText("Leanne Graham").assertIsDisplayed()

// Empty state
composeTestRule.setContent {
    UserListScreen(UiState.Empty, onRetry = {})
}
composeTestRule.onNodeWithText("No users found").assertIsDisplayed()

// Error state
composeTestRule.setContent {
    UserListScreen(UiState.Error("Network error."), onRetry = {})
}
composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
composeTestRule.onNodeWithText("Retry").assertIsDisplayed()

// Retry action
var retryClicked = false
composeTestRule.setContent {
    UserListScreen(UiState.Error("Network error."), onRetry = { retryClicked = true })
}
composeTestRule.onNodeWithText("Retry").performClick()
assertTrue(retryClicked)
```

**Key principle**: Assert on **semantics** (content descriptions, text, roles) rather than implementation details (view types, positions). This makes tests resilient to UI redesigns.

---

## Mapper Testing

`UserMapper` is a pure function — the simplest possible test:

```kotlin
@Test
fun `toUserUI maps all fields correctly`() {
    val dto = ListItem(id = 1, name = "Alice", username = "alice", email = "a@b.com", phone = "123", website = "alice.io")
    val result = dto.toUserUI()
    assertEquals(1, result.id)
    assertEquals("Alice", result.name)
    // ...
}

@Test
fun `toUserUI uses defaults for null fields`() {
    val dto = ListItem() // all nulls
    val result = dto.toUserUI()
    assertEquals(0, result.id)
    assertEquals("", result.name)
    // ...
}
```

---

## What Would Be Added in Production

1. **MockWebServer tests** for `ListRepositoryImpl` — verify the correct URL is called, headers are present, and HTTP error codes produce the right `Result.failure`.
2. **Room tests** — if offline caching is added, verify insert/query/update logic with an in-memory Room database.
3. **Navigation tests** — verify that clicking a user card navigates to a detail screen.
4. **Screenshot tests** — Paparazzi or Roborazzi to catch visual regressions without a device.
5. **Performance tests** — Macrobenchmark to verify list scrolling stays above 60fps with 100+ items.
6. **Accessibility tests** — verify content descriptions exist on all interactive elements.
