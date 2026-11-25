package app.mcleaner.ui.overview

import android.widget.Toast
import androidx.lifecycle.ViewModel
import app.mcleaner.core.i18n.tr
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.viewModelScope
import app.mcleaner.AppContainer
import app.mcleaner.core.history.BoostSource
import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostPolicy
import app.mcleaner.data.SystemStats
import app.mcleaner.domain.BoostReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OverviewUi(
    val topApps: List<AppInfo> = emptyList(),
    val topCache: List<AppInfo> = emptyList(),
    val loaded: Boolean = false,
    val candidates: Int = 0,
    val boosting: Boolean = false,
    val report: BoostReport? = null,
)

class OverviewViewModel(private val c: AppContainer) : ViewModel() {

    /** Polls every 2 s only while the screen is visible. */
    val stats: StateFlow<SystemStats?> =
        c.stats.stats().stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    val shizuku = c.shizuku.state

    private val _ui = MutableStateFlow(OverviewUi())
    val ui: StateFlow<OverviewUi> = _ui.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { shizuku.collect { refresh() } }
    }

    private var refreshJob: kotlinx.coroutines.Job? = null
    private var refreshAgain = false

    /** One load at a time: requests during a load (Shizuku state, screen resume) collapse into one more. */
    fun refresh() {
        if (refreshJob?.isActive == true) {
            refreshAgain = true
            return
        }
        refreshAgain = false
        refreshJob = viewModelScope.launch {
            val apps = c.apps.loadApps()
            val ctx = c.boostContext()
            val candidates = if (c.shizuku.isAvailable) {
                BoostPolicy.killCandidates(apps, ctx).size
            } else {
                BoostPolicy.blindKillCandidates(apps, ctx).size
            }
            val top = apps.filter { it.pssKb != null }.sortedByDescending { it.pssKb }.take(TOP)
            val topCache = apps.filter { (it.cacheBytes ?: 0) > 0 }.sortedByDescending { it.cacheBytes }.take(TOP)
            _ui.value = _ui.value.copy(topApps = top, topCache = topCache, candidates = candidates, loaded = true)
        }.also { job -> job.invokeOnCompletion { if (refreshAgain) viewModelScope.launch { refresh() } } }
    }

    fun boost() {
        if (_ui.value.boosting) return
        _ui.value = _ui.value.copy(boosting = true)
        viewModelScope.launch {
            try {
                val report = c.runBoost(BoostSource.MANUAL)
                _ui.value = _ui.value.copy(report = report)
                app.mcleaner.auto.BoostWidget.refresh(c.appContext, report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    c.appContext,
                    tr("Не удалось ускорить: ${e.message ?: e.javaClass.simpleName}", "Boost failed: ${e.message ?: e.javaClass.simpleName}"),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                _ui.value = _ui.value.copy(boosting = false)
            }
            refresh()
        }
    }

    fun dismissReport() {
        _ui.value = _ui.value.copy(report = null)
    }

    private companion object {
        const val TOP = 8
    }
}
