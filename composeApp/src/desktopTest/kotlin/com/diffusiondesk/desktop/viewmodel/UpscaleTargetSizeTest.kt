package com.diffusiondesk.desktop.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class UpscaleTargetSizeTest {
    @Test
    fun preservesSixteenByTenAspectRatioForExactTargetWidth() {
        assertEquals(1600, proportionalDimension(2560, 1920, 1200))
    }

    @Test
    fun alignsDerivedDimensionToEightPixels() {
        assertEquals(1288, proportionalDimension(2060, 1920, 1200))
    }

    @Test
    fun derivesEffectiveImg2ImgStepsFromDenoisingStrength() {
        assertEquals(6, effectiveGenerativeUpscaleSteps(20, 0.25))
        assertEquals(17, effectiveGenerativeUpscaleSteps(32, 0.50))
    }

    @Test
    fun recognizesSamplingWhenLegacyWorkerStillReportsEncodingPhase() {
        assertEquals(
            GenerativeUpscaleProgressPhase.Sample,
            generativeUpscaleProgressPhase(
                rawPhase = "Encoding Source Image and Prompt...",
                step = 1,
                steps = 17,
                expectedSamplingSteps = 17,
            ),
        )
    }

    @Test
    fun progressDoesNotMoveBackwardsAcrossPhases() {
        val encoding = nextGenerativeUpscaleProgress(
            current = 0.2f,
            phase = GenerativeUpscaleProgressPhase.Encode,
            step = 12,
            steps = 38,
        )
        val sampling = nextGenerativeUpscaleProgress(
            current = encoding,
            phase = GenerativeUpscaleProgressPhase.Sample,
            step = 1,
            steps = 17,
        )
        val decode = nextGenerativeUpscaleProgress(
            current = sampling,
            phase = GenerativeUpscaleProgressPhase.Decode,
            step = 17,
            steps = 17,
        )

        assertEquals(true, sampling >= encoding)
        assertEquals(true, decode >= sampling)
    }
}
