package com.example.yyplayer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yyplayer.data.repository.EqualizerRepository
import com.example.yyplayer.player.EqualizerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = EqualizerRepository(context)

    private val _currentPreset = MutableStateFlow("normal")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Short>>(emptyList())
    val bandLevels: StateFlow<List<Short>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<String>>(emptyList())
    val bandFrequencies: StateFlow<List<String>> = _bandFrequencies.asStateFlow()

    init {
        viewModelScope.launch {
            repository.currentPreset.collect { preset ->
                _currentPreset.value = preset
            }
        }
    }

    fun loadFromController(controller: EqualizerController?) {
        controller ?: return
        val bands = controller.getNumberOfBands()
        if (bands <= 0) return

        val levels = mutableListOf<Short>()
        val freqs = mutableListOf<String>()
        for (i in 0 until bands) {
            levels.add(controller.getBandLevel(i))
            val freqHz = controller.getCenterFreq(i)
            freqs.add(formatFrequency(freqHz))
        }
        _bandLevels.value = levels
        _bandFrequencies.value = freqs
    }

    fun setBandLevel(controller: EqualizerController?, band: Int, level: Short) {
        controller?.setBandLevel(band, level)
        val levels = _bandLevels.value.toMutableList()
        if (band < levels.size) {
            levels[band] = level
            _bandLevels.value = levels
        }
        // Switch to custom preset when user manually adjusts
        viewModelScope.launch {
            repository.setPreset("custom")
        }
    }

    fun applyPreset(controller: EqualizerController?, preset: String) {
        controller?.applyPreset(preset)
        viewModelScope.launch {
            repository.setPreset(preset)
            _currentPreset.value = preset
        }
        loadFromController(controller)
    }

    private fun formatFrequency(freqHz: Int): String {
        return when {
            freqHz >= 1000 -> "${freqHz / 1000}kHz"
            else -> "${freqHz}Hz"
        }
    }
}
