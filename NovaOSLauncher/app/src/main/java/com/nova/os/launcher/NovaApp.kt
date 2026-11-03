package com.nova.os.launcher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nova.os.launcher.data.NovaSettings
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.system.findActivity
import com.nova.os.launcher.ui.apps.AppManagerScreen
import com.nova.os.launcher.ui.components.WallpaperLayer
import com.nova.os.launcher.ui.control.ControlCenter
import com.nova.os.launcher.ui.drawer.AppDrawer
import com.nova.os.launcher.ui.home.HomeScreen
import com.nova.os.launcher.ui.settings.AboutScreen
import com.nova.os.launcher.ui.settings.SettingsScreen
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.LocalNovaMotion
import com.nova.os.launcher.ui.theme.NovaMotion
import com.nova.os.launcher.ui.theme.NovaTheme
import com.nova.os.launcher.ui.tools.CalculatorScreen
import com.nova.os.launcher.ui.tools.ClockScreen
import com.nova.os.launcher.ui.tools.NotesScreen

@Composable
fun NovaApp(vm: NovaViewModel) {
    val settings by vm.settings.collectAsState()
    val base = LocalDensity.current
    // The in-app font scale setting multiplies the system font scale.
    val density = remember(base.density, base.fontScale, settings.fontScale) {
        Density(base.density, base.fontScale * settings.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides density) {
        NovaTheme(settings) { NovaRoot(vm, settings) }
    }
}

@Composable
private fun NovaRoot(vm: NovaViewModel, settings: NovaSettings) {
    val colors = LocalNovaColors.current
    val motion = LocalNovaMotion.current
    val overlay = vm.overlay
    val active = overlay != Overlay.None
    val customWallpaper by vm.customWallpaper.collectAsState()

    SystemBarsEffect(dark = colors.isDark)

    // The launcher must never "exit" on Back. Layers registered later take priority.
    BackHandler(enabled = true) { }
    BackHandler(enabled = active) { vm.back() }

    val blur by animateDpAsState(
        targetValue = if (active) 24.dp else 0.dp,
        animationSpec = motion.spec(420),
        label = "wallpaperBlur",
    )
    val homeScale by animateFloatAsState(
        targetValue = if (active) 0.94f else 1f,
        animationSpec = motion.spec(420),
        label = "homeScale",
    )
    val homeAlpha by animateFloatAsState(
        targetValue = if (active) 0f else 1f,
        animationSpec = motion.spec(320),
        label = "homeAlpha",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        WallpaperLayer(settings, customWallpaper, blur)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                    alpha = homeAlpha
                }
        ) {
            HomeScreen(vm)
        }

        AnimatedVisibility(
            visible = overlay == Overlay.Drawer,
            enter = slideInVertically(motion.spec(460)) { it } + fadeIn(motion.spec(300)),
            exit = slideOutVertically(motion.spec(360)) { it } + fadeOut(motion.spec(260)),
        ) { AppDrawer(vm) }

        AnimatedVisibility(
            visible = overlay == Overlay.Control,
            enter = slideInVertically(motion.spec(460)) { -it } + fadeIn(motion.spec(300)),
            exit = slideOutVertically(motion.spec(340)) { -it } + fadeOut(motion.spec(240)),
        ) { ControlCenter(vm) }

        ToolLayer(overlay == Overlay.Settings, motion) { SettingsScreen(vm) }
        ToolLayer(overlay == Overlay.Apps, motion) { AppManagerScreen(vm) }
        ToolLayer(overlay == Overlay.About, motion) { AboutScreen(vm) }
        ToolLayer(overlay == Overlay.Calculator, motion) { CalculatorScreen(vm) }
        ToolLayer(overlay == Overlay.Notes, motion) { NotesScreen(vm) }
        ToolLayer(overlay == Overlay.Clock, motion) { ClockScreen(vm) }
    }
}

@Composable
private fun ToolLayer(visible: Boolean, motion: NovaMotion, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.spec(300)) + scaleIn(motion.spec(420), initialScale = 0.92f),
        exit = fadeOut(motion.spec(220)) + scaleOut(motion.spec(260), targetScale = 0.96f),
    ) { content() }
}

@Composable
private fun SystemBarsEffect(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context.findActivity() as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}
