package com.example.sampleapp.prasentation.viewModel

import app.cash.turbine.test
import com.example.sampleapp.domain.usecase.ListUseCause
import com.example.sampleapp.fake.FakeListRepository
import com.example.sampleapp.fake.defaultUsers
import com.example.sampleapp.prasentation.view.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * ViewModel unit tests — covers every UiState transition.
 *
 * Tools:
 *   StandardTestDispatcher — coroutines queue until advanceUntilIdle(), so we can
 *   assert Loading BEFORE the fetch completes. UnconfinedTestDispatcher would skip it.
 *   Turbine — concise ordered Flow/StateFlow assertions.
 *   FakeListRepository — compiler-checked test double, no mocking framework.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp()    { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun buildViewModel(fake: FakeListRepository) =
        ListViewModel(ListUseCause(fake))

    // ── 1. Initial state ──────────────────────────────────────────────────────

    @Test fun `initial state is Loading`() = runTest {
        assertEquals(UiState.Loading, buildViewModel(FakeListRepository()).uiState.value)
    }

    // ── 2. Success ────────────────────────────────────────────────────────────

    @Test fun `loadUsers emits Loading then Success`() = runTest {
        val fake = FakeListRepository().apply { result = Result.success(defaultUsers()) }
        buildViewModel(fake).uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val state = awaitItem() as UiState.Success
            assertEquals(defaultUsers(), state.data)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 3. Empty ──────────────────────────────────────────────────────────────

    @Test fun `loadUsers emits Empty when list is empty`() = runTest {
        val fake = FakeListRepository().apply { result = Result.success(emptyList()) }
        buildViewModel(fake).uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(UiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 4. Network error ──────────────────────────────────────────────────────

    @Test fun `loadUsers emits Error on IOException`() = runTest {
        val fake = FakeListRepository().apply { result = Result.failure(IOException("No network")) }
        buildViewModel(fake).uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val state = awaitItem() as UiState.Error
            assertTrue(state.message.contains("Network", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 5. HTTP error ─────────────────────────────────────────────────────────

    @Test fun `loadUsers emits Error on HttpException`() = runTest {
        val fakeResponse = retrofit2.Response.error<List<Any>>(
            500, okhttp3.ResponseBody.create(null, "")
        )
        val fake = FakeListRepository().apply {
            result = Result.failure(retrofit2.HttpException(fakeResponse))
        }
        buildViewModel(fake).uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val state = awaitItem() as UiState.Error
            assertTrue(state.message.contains("Server", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 6. Unexpected error ───────────────────────────────────────────────────

    @Test fun `loadUsers emits Error on unexpected failure`() = runTest {
        val fake = FakeListRepository().apply {
            result = Result.failure(RuntimeException("Parse error"))
        }
        buildViewModel(fake).uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is UiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 7. Retry: error → success ─────────────────────────────────────────────

    @Test fun `retry transitions Error to Success`() = runTest {
        val fake = FakeListRepository().apply { result = Result.failure(IOException()) }
        val vm = buildViewModel(fake)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is UiState.Error)

            fake.result = Result.success(defaultUsers())
            vm.loadUsers()

            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is UiState.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 8. Retry: success → error (regression guard) ──────────────────────────

    @Test fun `retry after success transitions to Error`() = runTest {
        val fake = FakeListRepository().apply { result = Result.success(defaultUsers()) }
        val vm = buildViewModel(fake)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is UiState.Success)

            fake.result = Result.failure(IOException("Lost"))
            vm.loadUsers()

            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is UiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
