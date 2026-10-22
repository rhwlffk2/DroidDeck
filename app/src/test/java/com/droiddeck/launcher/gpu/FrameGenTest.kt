package com.droiddeck.launcher.gpu

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FrameGenTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("frame_gen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun offUntilPicked() {
        assertEquals(FrameGen.Mode.OFF, FrameGen.mode(context))
    }

    @Test fun aSavedWinFgChoiceBecomesDdFg() {
        context.getSharedPreferences("frame_gen", Context.MODE_PRIVATE).edit()
            .putString("engine", "winfg").putInt("multiplier", 2).commit()
        assertEquals(FrameGen.Mode(FrameGen.ENGINE_DDFG, 2), FrameGen.mode(context))
    }
}
