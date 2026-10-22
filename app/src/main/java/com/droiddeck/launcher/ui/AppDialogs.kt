package com.droiddeck.launcher.ui

import android.app.Activity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.FileUtils
import com.droiddeck.launcher.files.InAppFilePicker
import com.droiddeck.launcher.runtime.LinuxFex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun rememberShown(onDismiss: () -> Unit): MutableTransitionState<Boolean> {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(shown.currentState, shown.isIdle) { if (shown.isIdle && !shown.currentState && !shown.targetState) onDismiss() }
    return shown
}

@Composable
internal fun AppDialog(
    shown: MutableTransitionState<Boolean>, close: () -> Unit, label: String, wide: Boolean,
    modifier: Modifier = Modifier, maxWidth: Dp? = null,
    width: Dp? = null, fitContent: Boolean = false,
    back: (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pal = LocalPalette.current
    VeilRing()
    Dialog(onDismissRequest = back ?: close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      CompositionLocalProvider(LocalFrontFocus provides null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().imePadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        ) {
            AnimatedVisibility(
                visibleState = shown,
                enter = fadeIn(Motion.tw(180)) + scaleIn(Motion.sp(0.7f), initialScale = 0.94f),
                exit = fadeOut(Motion.tw(140)) + scaleOut(Motion.tw(140), targetScale = 0.96f),
                label = label,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .padding(12.dp)
                        .widthIn(max = maxWidth ?: if (wide) 860.dp else 560.dp)
                        .then(
                            when {
                                width != null -> Modifier.width(width)
                                fitContent -> Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                                else -> Modifier.fillMaxWidth()
                            },
                        )
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp - 24).dp)
                        .shadow(24.dp, Shape16, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(Shape16)
                        .background(pal.surfaceVariant.copy(alpha = 0.97f))
                        .border(1.dp, pal.signal.copy(alpha = 0.22f), Shape16)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .controllerBack(back ?: close)
                        .then(modifier)
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    content = content,
                )
            }
        }
      }
    }
}

@Composable
internal fun DialogHeader(eyebrow: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    val heading = @Composable {
        Column {
            Eyebrow(eyebrow)
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Rise(0) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            when {
                trailing == null -> heading()
                maxWidth >= 600.dp -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { heading() }
                    trailing()
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { heading(); trailing() }
            }
        }
    }
}

@Composable
internal fun DialogFooter(note: String?, confirm: String, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val lineColor = LocalPalette.current.line
    Rise(2) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.drawBehind { drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 14.dp),
        ) {
            Box(Modifier.weight(1f)) { if (note != null) Small(note) }
            SecondaryButton(stringResource(R.string.add_app_cancel), onClick = onCancel)
            PrimaryButton(confirm, enabled = enabled, onClick = onConfirm)
        }
    }
}

@Composable
internal fun Small(text: String, error: Boolean = false) =
    Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
