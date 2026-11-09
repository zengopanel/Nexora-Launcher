package com.nexora.launcher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val prefs = app.getSharedPreferences("nexora", Context.MODE_PRIVATE)
    val icons = IconProvider(app)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    /** Bumped whenever icons must be redrawn (icon pack loaded or changed). */
    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion

    private val _selectedPack = MutableStateFlow<String?>(prefs.getString(KEY_PACK, null))
    val selectedPack: StateFlow<String?> = _selectedPack

    /** Bumped when Home is pressed again, so the drawer closes. */
    val homeSignal = MutableStateFlow(0)

    private var reloadJob: Job? = null
    private val callback = repo.register { scheduleReload(300) }

    init {
        scheduleReload(0)
        val saved = _selectedPack.value
        if (saved != null) {
            viewModelScope.launch(Dispatchers.Default) {
                icons.setPack(saved)
                _iconVersion.value++
            }
        }
    }

    private fun scheduleReload(delayMs: Long) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            _apps.value = withContext(Dispatchers.Default) { repo.load() }
        }
    }

    fun launch(app: AppInfo) = repo.launch(app)

    fun selectPack(pkg: String?) {
        prefs.edit().putString(KEY_PACK, pkg).apply()
        _selectedPack.value = pkg
        viewModelScope.launch(Dispatchers.Default) {
            icons.setPack(pkg)
            _iconVersion.value++
        }
    }

    override fun onCleared() {
        repo.unregister(callback)
    }

    private companion object {
        const val KEY_PACK = "icon_pack"
    }
}
