package com.velnox.velseller.presentation.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.SellerGoal
import com.velnox.core.data.model.SellerIncome
import com.velnox.core.data.repository.SellerRepository
import com.velnox.core.ui.component.VelnoxScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The seller's income and goals.
 *
 * ## Nothing is computed here
 *
 * `grossSales`, `commission`, `netEarnings` and `pendingPayout` all come from
 * `GET /api/seller/income`, which aggregates over real orders in Neon. Summing orders on
 * the device would be cheaper to write and wrong: it would disagree with the seller's
 * payout and with the web dashboard, and a financial figure that differs between two
 * screens of the same product destroys trust in both.
 *
 * ## Goals are optional, income is not
 *
 * A failed income call fails the screen — it is the reason the screen exists. A failed
 * goals call does not: the income card is still useful, so goals degrade to a labelled
 * "unavailable" state instead of hiding real money behind an error.
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val sellerRepository: SellerRepository,
) : ViewModel() {

    data class OverviewData(
        val income: SellerIncome,
        val goals: List<SellerGoal>,
        /** `true` when the goals call failed — rendered as unavailable, not as empty. */
        val goalsUnavailable: Boolean,
    )

    data class UiState(
        val result: VelnoxScreenState<OverviewData> = VelnoxScreenState.Loading,
        val mutatingGoal: Boolean = false,
        val notice: OverviewNotice? = null,
    )

    sealed interface OverviewNotice {
        data object GoalCreated : OverviewNotice
        data object GoalDeleted : OverviewNotice
        data class Failure(val error: AppError) : OverviewNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    fun createGoal(title: String, metric: String, targetText: String, period: String) {
        val target = targetText.trim().toDoubleOrNull()
        if (target == null || target <= 0.0) {
            _state.update {
                it.copy(
                    notice = OverviewNotice.Failure(
                        AppError.Validation(serverMessage = "The target must be a number greater than zero."),
                    ),
                )
            }
            return
        }

        if (_state.value.mutatingGoal) return

        viewModelScope.launch {
            _state.update { it.copy(mutatingGoal = true, notice = null) }

            val result = sellerRepository.createGoal(
                title = title.trim(),
                metric = metric.trim().ifBlank { DEFAULT_METRIC },
                target = target,
                period = period.trim().takeIf { it.isNotEmpty() },
            )

            _state.update { current ->
                when (result) {
                    is VelnoxResult.Success -> current.copy(
                        mutatingGoal = false,
                        notice = OverviewNotice.GoalCreated,
                    )

                    is VelnoxResult.Failure -> current.copy(
                        mutatingGoal = false,
                        notice = OverviewNotice.Failure(result.error),
                    )
                }
            }

            if (result is VelnoxResult.Success) refresh()
        }
    }

    fun deleteGoal(goalId: String) {
        if (_state.value.mutatingGoal) return

        viewModelScope.launch {
            _state.update { it.copy(mutatingGoal = true, notice = null) }

            when (val result = sellerRepository.deleteGoal(goalId)) {
                is VelnoxResult.Success -> _state.update {
                    it.copy(mutatingGoal = false, notice = OverviewNotice.GoalDeleted)
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(mutatingGoal = false, notice = OverviewNotice.Failure(result.error))
                }
            }

            refresh()
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(result = VelnoxScreenState.Loading) }

            val (incomeResult, goalsResult) = coroutineScope {
                val income = async { sellerRepository.income() }
                val goals = async { sellerRepository.goals() }
                income.await() to goals.await()
            }

            when (incomeResult) {
                is VelnoxResult.Failure ->
                    _state.update { it.copy(result = VelnoxScreenState.Failure(incomeResult.error)) }

                is VelnoxResult.Success -> {
                    val goals = when (goalsResult) {
                        is VelnoxResult.Success -> goalsResult.data
                        is VelnoxResult.Failure -> emptyList()
                    }
                    _state.update {
                        it.copy(
                            result = VelnoxScreenState.Content(
                                OverviewData(
                                    income = incomeResult.data,
                                    goals = goals,
                                    goalsUnavailable = goalsResult is VelnoxResult.Failure,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** The metric the web goals form defaults to (`revenue`). */
        const val DEFAULT_METRIC = "revenue"
    }
}
