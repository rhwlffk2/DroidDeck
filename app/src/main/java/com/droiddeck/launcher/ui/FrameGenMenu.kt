package com.droiddeck.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.droiddeck.launcher.R
import com.droiddeck.launcher.gpu.FrameGen

@Composable
internal fun FrameGenMenu(host: MenuHost, mode: FrameGen.Mode, onPick: (FrameGen.Mode) -> Unit) {
    val context = LocalContext.current
    AnchoredMenu(host.open == "fg", onDismiss = { if (host.open == "fg") host.open = null }, title = stringResource(R.string.frame_gen_title)) { firstItemFocus ->
        @Composable
        fun item(m: FrameGen.Mode, focus: FocusRequester? = null) =
            MenuItem(FrameGen.label(context, m), checked = mode == m, focusRequester = focus) {
                onPick(m); host.open = null
            }
        item(FrameGen.Mode.OFF, focus = firstItemFocus)
        for (m in 2..4) item(FrameGen.Mode(FrameGen.ENGINE_DDFG, m))
    }
}
