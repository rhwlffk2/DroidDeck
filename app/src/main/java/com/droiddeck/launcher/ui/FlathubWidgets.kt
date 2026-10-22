package com.droiddeck.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.view.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.droiddeck.launcher.R
import com.droiddeck.launcher.frontend.Library
import com.droiddeck.launcher.session.SessionPrefs
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** 데스크톱 모드의 Flathub 화면이 쓰는 헤더와 테마 (스토어 페이지에서 분리). */
@Composable
internal fun ModeHeader(
    title: String, query: String, onQuery: (String) -> Unit, placeholder: String,
    lead: (@Composable () -> Unit)? = null, trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    var searching by remember { mutableStateOf(query.isNotEmpty()) }
    var focusOnOpen by remember { mutableStateOf(false) }
    val open by animateFloatAsState(if (searching) 1f else 0f, Motion.sp(0.75f, 420f), label = "searchOpen")
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp)) {
            if (lead != null) lead()
            else Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, maxLines = 1,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            )
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searching || open > 0.01f) {
                    val w = androidx.compose.ui.unit.lerp(44.dp, maxWidth.coerceAtMost(520.dp), open)
                    Box(Modifier.width(w).graphicsLayer { alpha = (open * 1.6f).coerceAtMost(1f) }) {
                        AdbTextField(
                            value = query, onValueChange = onQuery, label = "", keyboardType = KeyboardType.Text, imeAction = ImeAction.Search,
                            placeholder = placeholder, compact = true, focusOnShow = focusOnOpen,
                            onFocusChange = { focused -> if (!focused && query.isEmpty()) { searching = false; focusOnOpen = false } },
                        )
                    }
                } else SearchIcon(stringResource(R.string.stores_search_open)) { searching = true; focusOnOpen = true }
            }
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
    }
}

internal fun flathubPalette(base: Palette): Palette = Palette(
    base.id, base.label, base.detail, base.background, base.surface, base.surfaceVariant, base.line, base.line2,
    base.onBackground, base.onSurfaceVariant, primary = FlathubTeal, primary2 = Color(0xFF2E958D), onPrimary = FlathubInk, signal = FlathubTeal,
    good = base.good, error = base.error,
)

@Composable
internal fun FlathubTheme(content: @Composable () -> Unit) {
    val base = LocalPalette.current
    val p = remember(base) { flathubPalette(base) }
    val scheme = MaterialTheme.colorScheme.copy(primary = p.primary, onPrimary = p.onPrimary)
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content) }
}

@Composable
internal fun SearchIcon(description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.paneItem("storesearch").size(40.dp).clip(CircleShape).background(if (hot) pal.signal.copy(alpha = 0.14f) else Color.Transparent)
            .glideBorder(hot, CircleShape, pal.signal)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .controllerConfirm(onClick = onClick),
    ) { Icon(Icons.Filled.Search, description, tint = if (hot) pal.signal else colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
}
