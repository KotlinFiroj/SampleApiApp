# Unit Testing Guide

## Testing Philosophy

> Test behaviour, not implementation.

Each test answers one question: **given this input/state, does the system produce this output/state?**

---

## Test Pyramid

```
              ┌─────────────────┐
              │   UI Tests      │  ← fewest, slowest, highest confidence
              └────────┬────────┘
           ┌───────────┴───────────┐
           │   Integration Tests   │  ← MockWebServer, Room in-memory DB
           └───────────┬───────────┘
     ┌─────────────────┴─────────────────┐
     │         Unit Tests                │  ← most, fastest, widest coverage
     │  (ViewModel, Mapper, UseCase)      │
     └───────────────────────────────────┘
```

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

| # | Test | Scenario | Expected state |
|---|---|---|---|
| 1 | `initial state is Loading` | ViewModel created, coroutine not yet run | `UiState.Loading` |
| 2 | `loadUsers emits Loading then Success` | Repository emits success with users | `Loading → Success(users)` |
| 3 | `loadUsers emits Empty` | Repository emits success with empty list | `Loading → Empty` |
| 4 | `loadUsers emits Error on IOException` | Repository emits `Result.failure(IOException)` | `Loading → Error("Network…")` |
| 5 | `loadUsers emits Error on HttpException` | Repository emits `Result.failure(HttpException(500))` | `Loading → Error("Server…")` |
| 6 | `loadUsers emits Error on unexpected failure` | Repository emits `Result.failure(RuntimeException)` | `Loading → Error` |
| 7 | `retry error → success` | First emission fails; retry emits success | `Loading → Error → Loading → Success` |
| 8 | `retry success → error` | First emission succeeds; retry emits failure | `Loading → Success → Loading → Error` |

---

## Fake Repository Pattern

The repository now returns `Flow<Result<List<UserUI>>>`. The fake wraps the result in `flowOf()`:

```kotlin
class FakeListRepository : ListRepository {
    var result: Result<List<UserUI>> = Result.success(defaultUsers())
    // flowOf() emits one value then completes — mirrors real single-fetch behaviour
    override fun getUsers(): Flow<Result<List<UserUI>>> = flowOf(result)
}
```

**Why Fake over Mock?**

| | Fake | Mock |
|---|---|---|
| Compiler-checked | ✅ Won't compile if interface changes | ❌ Silently passes until runtime |
| Framework dependency | ✅ None | ❌ Mockito/MockK annotation processing |
| Readability | ✅ `fake.result = ...` | ⚠️ `whenever(...).thenReturn(...)` |
| Speed | ✅ JVM, no reflection | ⚠️ Byte-code generation overhead |

---

## Coroutine Testing

### Why `StandardTestDispatcher`?

`viewModelScope` uses `Dispatchers.Main` — not available in JVM tests. We replace it:

```kotlin
@Before fun setUp()    { Dispatchers.setMain(StandardTestDispatcher()) }
@After  fun tearDown() { Dispatchers.resetMain() }
```

`StandardTestDispatcher` queues coroutines until `advanceUntilIdle()` is called — lets us assert `UiState.Loading` **before** the fetch completes. `UnconfinedTestDispatcher` runs eagerly and skips the Loading state.

---

## Flow Testing with Turbine

```kotlin
viewModel.uiState.test {
    assertEquals(UiState.Loading, awaitItem())   // assert before fetch
    advanceUntilIdle()                            // run the coroutine
    assertTrue(awaitItem() is UiState.Success)   // assert after fetch
    cancelAndIgnoreRemainingEvents()
}
```

---

## Compose UI Testing (What to add next)

`UserListScreen` now owns the ViewModel internally via `hiltViewModel()`. To test it in isolation, call the private composables directly or test with a real Hilt test rule.

```kotlin
// Test private composables directly — no ViewModel needed
composeTestRule.setContent { LoadingContent() }
composeTestRule.onNodeWithTag("loading").assertIsDisplayed()

// Or test the full screen with Hilt
@HiltAndroidTest
class UserListScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test fun shows_users_on_success() {
        composeTestRule.onNodeWithText("Leanne Graham").assertIsDisplayed()
    }

    @Test fun shows_retry_on_error() {
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }
}
```

---

## Mapper Testing

```kotlin
@Test fun `toUserUI maps all fields correctly`() {
    val dto = ListItem(1, "Alice", "alice", "a@b.com", "123", "alice.io")
    val result = dto.toUserUI()
    assertEquals(1, result.id)
    assertEquals("Alice", result.name)
}

@Test fun `toUserUI uses defaults for null fields`() {
    val result = ListItem().toUserUI()  // all nulls
    assertEquals(0, result.id)
    assertEquals("", result.name)
}
```

---

## What Would Be Added in Production

1. **MockWebServer tests** for `ListRepositoryImpl` — verify URL, headers, HTTP error codes produce `Result.failure`.
2. **Room tests** — when offline caching added, test with in-memory Room database.
3. **Multiple Flow emissions** — extend `FakeListRepository` to emit multiple values for cache-then-network testing.
4. **Screenshot tests** — Paparazzi or Roborazzi for visual regression.
5. **Accessibility tests** — verify content descriptions on all interactive elements.
