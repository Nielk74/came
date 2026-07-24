package com.nielk74.came.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lifecycle-aware integration point for the automatic check and manual update UI. */
class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UpdatePreferences(application)
    private val client = UpdateNetwork.createClient()
    private val checker = AppUpdateChecker(client = client)
    private val downloader = ApkDownloader(application, client)

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    init {
        checkJob = viewModelScope.launch {
            if (preferences.shouldCheckAutomatically()) performCheck()
        }
    }

    /** User-requested checks bypass the automatic 24-hour throttle. */
    fun checkForUpdates() {
        if (checkJob?.isActive == true && _updateStatus.value is UpdateStatus.Checking) return
        checkJob?.cancel()
        checkJob = viewModelScope.launch { performCheck() }
    }

    fun downloadAndInstall() {
        val release = (_updateStatus.value as? UpdateStatus.Available)?.release ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            downloader.downloadAndInstall(release).collect(_downloadState::emit)
        }
    }

    fun dismissUpdate() {
        if (_updateStatus.value is UpdateStatus.Available) _updateStatus.value = UpdateStatus.Idle
    }

    private suspend fun performCheck() {
        _updateStatus.value = UpdateStatus.Checking
        val result = checker.check()
        _updateStatus.value = result
        preferences.recordCheck()
        if (result is UpdateStatus.Available) _downloadState.value = DownloadState.Idle
    }
}
