package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.databinding.ActivityTtsSettingsBinding
import com.viteacher.toolkit.util.ReminderScheduler
import com.viteacher.toolkit.util.setAccessibleSelection
import android.speech.tts.UtteranceProgressListener
import com.viteacher.toolkit.util.TtsVolumeHelper
import java.util.Locale

class TtsSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTtsSettingsBinding
    private val languages = listOf("English", "Malayalam", "Hindi")
    private val languageCodes = listOf("en", "ml", "hi")

    data class TtsEngineItem(val label: String, val packageName: String) {
        override fun toString(): String = label
    }

    private var engineList = listOf<TtsEngineItem>()
    private var previewTts: TextToSpeech? = null
    private var volumeHelper: TtsVolumeHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTtsSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        volumeHelper = TtsVolumeHelper(this)

        binding.btnTestTtsSettings.setOnClickListener {
            playTestAnnouncement()
        }

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)

        // 1. Setup Language Spinner
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = langAdapter
        binding.spinnerLanguage.setAccessibleSelection("Announcement Language")

        val savedLangCode = prefs.getString("reminder_language", "en") ?: "en"
        val langIndex = languageCodes.indexOf(savedLangCode)
        if (langIndex >= 0) {
            binding.spinnerLanguage.setSelection(langIndex)
        }

        // 2. Setup TTS Engine Spinner
        engineList = getTtsEngines()
        val engineAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, engineList)
        engineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerEngine.adapter = engineAdapter
        binding.spinnerEngine.setAccessibleSelection("TTS Engine")

        val savedEnginePackage = prefs.getString("tts_engine", "") ?: ""
        val engineIndex = engineList.indexOfFirst { it.packageName == savedEnginePackage }
        if (engineIndex >= 0) {
            binding.spinnerEngine.setSelection(engineIndex)
        } else {
            // Select default TTS engine if nothing matches
            val defaultTts = TextToSpeech(this, null)
            val defaultEngine = defaultTts.defaultEngine
            defaultTts.shutdown()
            val defIndex = engineList.indexOfFirst { it.packageName == defaultEngine }
            if (defIndex >= 0) {
                binding.spinnerEngine.setSelection(defIndex)
            }
        }

        // 3. Setup Speed Slider
        // range: 0..20 representing 0.5x..2.5x (default 0.9x which is progress 4)
        val savedSpeed = prefs.getFloat("tts_speed", 0.9f)
        val progressSpeed = ((savedSpeed - 0.5f) / 0.1f).toInt().coerceIn(0, 20)
        binding.sbSpeed.progress = progressSpeed
        updateSpeedLabel(progressSpeed)

        binding.sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSpeedLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 4. Setup Volume Slider
        // range: 0..100 representing 0..100% (default 100% progress 100)
        val savedVolume = prefs.getFloat("tts_volume", 1.0f)
        val progressVolume = (savedVolume * 100).toInt().coerceIn(0, 100)
        binding.sbVolume.progress = progressVolume
        updateVolumeLabel(progressVolume)

        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateVolumeLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 5. Save Button Click
        binding.btnSaveTtsSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun getTtsEngines(): List<TtsEngineItem> {
        val engines = mutableListOf<TtsEngineItem>()
        val intent = Intent("android.intent.action.TTS_SERVICE")
        val resolveInfos = packageManager.queryIntentServices(intent, 0)
        for (ri in resolveInfos) {
            val packageName = ri.serviceInfo.packageName
            val label = ri.loadLabel(packageManager).toString()
            engines.add(TtsEngineItem(label, packageName))
        }
        return engines
    }

    private fun updateSpeedLabel(progress: Int) {
        val speedFloat = 0.5f + (progress * 0.1f)
        val text = "Speech Speed: ${String.format(Locale.US, "%.1f", speedFloat)}x"
        binding.tvSpeedValue.text = text
        binding.sbSpeed.contentDescription = "Speech speed slider, currently ${String.format(Locale.US, "%.1f", speedFloat)} times speed"
    }

    private fun updateVolumeLabel(progress: Int) {
        val text = "Speech Volume: $progress%"
        binding.tvVolumeValue.text = text
        binding.sbVolume.contentDescription = "Speech volume slider, currently $progress percent"
    }

    private fun saveSettings() {
        val selectedLangIndex = binding.spinnerLanguage.selectedItemPosition
        val selectedLangCode = languageCodes[selectedLangIndex]

        val selectedEngineIndex = binding.spinnerEngine.selectedItemPosition
        val selectedEnginePackage = if (selectedEngineIndex >= 0 && selectedEngineIndex < engineList.size) {
            engineList[selectedEngineIndex].packageName
        } else {
            ""
        }

        val speedFloat = 0.5f + (binding.sbSpeed.progress * 0.1f)
        val volumeFloat = binding.sbVolume.progress / 100f

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("reminder_language", selectedLangCode)
            putString("tts_engine", selectedEnginePackage)
            putFloat("tts_speed", speedFloat)
            putFloat("tts_volume", volumeFloat)
        }.apply()

        // Reschedule reminders to use the new settings and language messages
        ReminderScheduler.rescheduleAll(this)

        val successMessage = "Language and TTS configured successfully"
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
        binding.root.announceForAccessibility(successMessage)
        finish()
    }

    private fun playTestAnnouncement() {
        stopPreviewTts()

        val selectedLangIndex = binding.spinnerLanguage.selectedItemPosition
        val selectedLangCode = if (selectedLangIndex in languageCodes.indices) languageCodes[selectedLangIndex] else "en"

        val selectedEngineIndex = binding.spinnerEngine.selectedItemPosition
        val selectedEnginePackage = if (selectedEngineIndex >= 0 && selectedEngineIndex < engineList.size) {
            engineList[selectedEngineIndex].packageName
        } else {
            ""
        }

        val speedFloat = 0.5f + (binding.sbSpeed.progress * 0.1f)
        val volumeFloat = binding.sbVolume.progress / 100f

        val previewMsg = when (selectedLangCode) {
            "ml" -> "ഇത് വി ഐ ടീച്ചേഴ്സ് ടൂൾകിറ്റിനായുള്ള ഒരു പരീക്ഷണ അറിയിപ്പാണ്."
            "hi" -> "यह वी आई टीचर्स टूलकिट के लिए एक परीक्षण घोषणा है।"
            else -> "This is a test announcement for V. I. Teachers Toolkit."
        }

        if (selectedEnginePackage.isNotEmpty()) {
            previewTts = TextToSpeech(this, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    speakPreview(selectedLangCode, speedFloat, volumeFloat, previewMsg)
                } else {
                    Toast.makeText(this, "Failed to initialize selected TTS engine", Toast.LENGTH_SHORT).show()
                }
            }, selectedEnginePackage)
        } else {
            previewTts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    speakPreview(selectedLangCode, speedFloat, volumeFloat, previewMsg)
                } else {
                    Toast.makeText(this, "Failed to initialize TTS engine", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun speakPreview(langCode: String, speed: Float, volume: Float, message: String) {
        val ttsEngine = previewTts ?: return
        val locale = when (langCode) {
            "ml" -> Locale("ml", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.ENGLISH
        }
        val result = ttsEngine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            ttsEngine.setLanguage(Locale.ENGLISH)
        }
        ttsEngine.setSpeechRate(speed)
        
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        ttsEngine.setAudioAttributes(audioAttributes)

        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                volumeHelper?.restoreVolume()
            }
            override fun onError(utteranceId: String?) {
                volumeHelper?.restoreVolume()
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                volumeHelper?.restoreVolume()
            }
            override fun onStart(utteranceId: String?) {}
        })

        volumeHelper?.setVolume(volume)

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        ttsEngine.speak(message, TextToSpeech.QUEUE_FLUSH, params, "preview_utterance")
    }

    private fun stopPreviewTts() {
        try {
            previewTts?.stop()
            previewTts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        previewTts = null
        volumeHelper?.restoreVolume()
    }

    override fun onDestroy() {
        stopPreviewTts()
        super.onDestroy()
    }
}
