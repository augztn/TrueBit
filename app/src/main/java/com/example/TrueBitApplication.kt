package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.local.TrueBitDatabase
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.repository.AudioScannerRepository
import com.example.domain.usecase.DuplicateRankerUseCase
import com.example.dsp.AcousticFingerprinter
import com.example.dsp.BitDepthAuditor
import com.example.dsp.FftSpectralAnalyzer
import com.example.dsp.LosslessContainerDecoder
import com.example.dsp.LosslessSignalGenerator

class AppContainer(application: Application) {

    val database: TrueBitDatabase by lazy {
        Room.databaseBuilder(
            application.applicationContext,
            TrueBitDatabase::class.java,
            "truebit_dsp_cache.db"
        ).fallbackToDestructiveMigration().build()
    }

    val fftSpectralAnalyzer: FftSpectralAnalyzer by lazy {
        FftSpectralAnalyzer()
    }

    val bitDepthAuditor: BitDepthAuditor by lazy {
        BitDepthAuditor()
    }

    val acousticFingerprinter: AcousticFingerprinter by lazy {
        AcousticFingerprinter(fftSpectralAnalyzer)
    }

    val losslessDecoder: LosslessContainerDecoder by lazy {
        LosslessContainerDecoder(
            context = application.applicationContext,
            fftAnalyzer = fftSpectralAnalyzer,
            bitDepthAuditor = bitDepthAuditor,
            fingerprinter = acousticFingerprinter
        )
    }

    val signalGenerator: LosslessSignalGenerator by lazy {
        LosslessSignalGenerator(application.applicationContext)
    }

    val duplicateRankerUseCase: DuplicateRankerUseCase by lazy {
        DuplicateRankerUseCase()
    }

    val themePreferencesRepository: ThemePreferencesRepository by lazy {
        ThemePreferencesRepository(application.applicationContext)
    }

    val audioScannerRepository: AudioScannerRepository by lazy {
        AudioScannerRepository(
            context = application.applicationContext,
            trackCacheDao = database.trackCacheDao(),
            trashAuditDao = database.trashAuditDao(),
            decoder = losslessDecoder,
            signalGenerator = signalGenerator
        )
    }
}

class TrueBitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
