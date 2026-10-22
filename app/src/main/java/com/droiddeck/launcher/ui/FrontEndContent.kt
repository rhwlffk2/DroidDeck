package com.droiddeck.launcher.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.CompositionLocalProvider
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.droiddeck.launcher.HomeApp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.frontend.Library
import java.io.File


@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
internal fun Pane(
    s: FrontEndState, selected: String, a: FrontEndActions, page: (@Composable () -> Unit)?, modifier: Modifier,
    onSelect: (String) -> Unit, onAndroidAppClick: (HomeApp.LaunchableApp) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
      CompositionLocalProvider(LocalNarrowPane provides (maxWidth < NarrowPaneWidth)) {
        val backdropArt: File? = when {
            s.pageKey != null && page != null -> null
            selected.startsWith("app:") -> s.steamGames.firstOrNull { "app:${it.appId}" == selected }?.art
            selected == "games" -> s.steamGames.maxByOrNull { it.lastPlayed }?.art
            else -> null
        }
        Backdrop(backdropArt)
        val pal = LocalPalette.current
        val pages = remember { HashMap<String, @Composable () -> Unit>() }
        val origins = remember { HashMap<String, Origin>() }
        val paneAt = remember { arrayOf(Offset.Zero) }
        val livePage by rememberUpdatedState(s.pageKey)
        val railContent = if (selected.startsWith("app:")) "games" else selected
        val target = if (page != null && s.pageKey != null) s.pageKey else railContent
        val gameShown = remember { arrayOf("games") }
        if (selected == "games" || selected.startsWith("app:")) gameShown[0] = selected
        val before = remember { arrayOf(target) }
        if (page != null && s.pageKey != null) {
            pages[s.pageKey] = page
            if (s.pageKey !in origins) PageOrigin.take()?.let { o ->
                origins[s.pageKey] = inPane(o.translate(-paneAt[0])).let { if (it.owner == null) it.ownedBy(railContent) else it }
            }
        } else if (target != before[0] && target !in origins) {
            PageOrigin.take()?.let { o -> origins[target] = inPane(o.translate(-paneAt[0])).let { if (it.owner == null) it.ownedBy(before[0]) else it } }
        }
        before[0] = target
        val targetNow by rememberUpdatedState(target)
        val leave = if (Hops.linking) PageLeave.Link else PageLeave.Ordinary
        LaunchedEffect(target) { Hops.linking = false }
        AnimatedContent(
            targetState = target,
            modifier = Modifier.onGloballyPositioned { paneAt[0] = it.positionInRoot() },
            transitionSpec = {
                when {
                    targetState in origins -> (fadeIn(Motion.tw(120)))
                        .togetherWith(fadeOut(Motion.tw(320, 80)) + scaleOut(Motion.tw(480), targetScale = 0.95f))
                        .apply { targetContentZIndex = 1f }
                    initialState in origins && pageExit(origins[initialState]?.owner, targetState, leave) == PageExit.Drain ->
                        (fadeIn(Motion.tw(300, 100)) + scaleIn(Motion.tw(460, 40), initialScale = 0.95f))
                            .togetherWith(ExitTransition.None)
                            .apply { targetContentZIndex = -1f }
                    initialState in origins -> (fadeIn(Motion.tw(300, 80)) + slideInVertically(Motion.tw(420, 80)) { it / 24 })
                        .togetherWith(fadeOut(Motion.tw(170)) + scaleOut(Motion.tw(170), targetScale = 0.95f))
                        .apply { targetContentZIndex = 1f }
                    else -> (fadeIn(Motion.tw(300, 80)) + slideInVertically(Motion.tw(420, 80)) { it / 24 })
                        .togetherWith(fadeOut(Motion.tw(170)) + slideOutVertically(Motion.tw(170)) { -it / 40 })
                        .apply { targetContentZIndex = 1f }
                }
            },
            label = "pane",
        ) { key ->
            val drawn = (if (page != null && key == s.pageKey) page else pages[key])
                ?: { Content(s, if (key == "games") gameShown[0] else key, a, Modifier.fillMaxSize(), onSelect, onAndroidAppClick, onOpenDeveloperOptions) }
            val hopSink by androidx.compose.animation.core.animateFloatAsState(
                if (Hops.sinking == key) 1f else 0f, Motion.tw(260), label = "hopSink",
            )
            val shown: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().graphicsLayer { val k = 1f - 0.05f * hopSink; scaleX = k; scaleY = k; alpha = 1f - 0.35f * hopSink }) { drawn() }
            }
            run {
                DisposableEffect(key) { onDispose { if (key != livePage && key != targetNow) { pages.remove(key); origins.remove(key) } } }
                val from = origins[key]
                if (from == null) shown()
                else {
                    val art = when (val m = from.art) {
                        null -> null
                        is Int -> androidx.compose.ui.res.painterResource(m)
                        else -> coil.compose.rememberAsyncImagePainter(m)
                    }
                    val leaving = transition.targetState == EnterExitState.PostExit
                    val drains = remember(leaving) { !leaving || pageExit(from.owner, target, leave) == PageExit.Drain }
                    transition.animateFloat(
                        transitionSpec = { if (targetState == EnterExitState.PostExit && drains) Motion.tw(PAGE_RETURN_MS) else snap() },
                        label = "pageReturn",
                    ) { if (it == EnterExitState.PostExit) 1f else 0f }
                    PageFlood(from, leaving = leaving, art = art, sinks = !drains) { shown() }
                }
            }
        }
      }
    }
}

internal fun inPane(o: Origin): Origin =
    if (o.bounds.right > 0f) o else o.translate(Offset(-o.bounds.right + o.bounds.width / 2f, 0f))

@Composable
private fun Backdrop(art: File?) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Crossfade(targetState = art, animationSpec = Motion.tw(700), label = "backdrop") { a ->
        if (a != null && Build.VERSION.SDK_INT >= 31) {
            Box(modifier = Modifier.fillMaxSize().alpha(0.26f).blur(70.dp)) {
                AsyncImage(model = a, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(pal.signal.copy(alpha = 0.07f), Color.Transparent), center = Offset.Zero, radius = 1400f)))
        }
    }
    Spacer(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.35f)))))
}

@Composable
private fun Content(
    s: FrontEndState, selected: String, a: FrontEndActions, modifier: Modifier,
    onSelect: (String) -> Unit, onAndroidAppClick: (HomeApp.LaunchableApp) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val detailPosterWidth = if (LocalConfiguration.current.screenHeightDp < 600) 72.dp else 120.dp
    val narrow = LocalNarrowPane.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val padH = if (narrow) 16.dp else 22.dp
    val padV = if (narrow) 12.dp else 18.dp
    if (selected == "setup") {
        Column(modifier = modifier.padding(horizontal = padH, vertical = padV)) {
            SetupPanel(s, a, onOpenDeveloperOptions)
        }
        return
    }
    if (selected == "steam") {
        SteamHome(s, a, modifier)
        return
    }
    if (selected == "games" || selected.startsWith("app:")) {
        GamesPage(s, a, selected, onSelect, modifier)
        return
    }
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = padH, vertical = padV)) {
        when {
            selected == "android-apps" && s.isHomeApp -> {
                Rise(0) { PageHeader(stringResource(R.string.content_android_apps)) { Chip(stringResource(R.string.content_apps_count, s.androidApps.size), ok = false) } }
                if (s.androidApps.isEmpty()) Rise(3) { Note(stringResource(R.string.content_no_android_apps)) }
                else Rise(3, Modifier.fillMaxWidth()) {
                    ArtGrid(s.androidApps.map { app ->
                        Tile(
                            app.label,
                            null,
                            null,
                            "android:${app.packageName}",
                            onClick = { onAndroidAppClick(app) },
                            iconBitmap = app.icon,
                        )
                    })
                }
            }
            else -> Note(stringResource(R.string.content_page_gone))
        }
    }
}

