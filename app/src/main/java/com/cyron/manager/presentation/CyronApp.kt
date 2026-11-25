package com.cyron.manager.presentation

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cyron.manager.BuildConfig
import com.cyron.manager.MainActivity
import com.cyron.manager.R
import com.cyron.manager.domain.model.DownloadSettings
import com.cyron.manager.domain.model.DownloadSource
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import com.cyron.manager.domain.model.visibleProgress
import com.cyron.manager.domain.model.visibleStatus
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class Tab(val label: String, val fa: String) {
    Dashboard("Overview", "نمای کلی"), Downloads("Downloads", "دانلودها"), Settings("Settings", "تنظیمات")
}
private enum class Filter(val label: String) { All("All files"), Active("Active"), Completed("Completed"), Paused("Paused"), Failed("Failed") }
private enum class DownloadSort(val label: String) { Newest("Newest first"), Oldest("Oldest first"), Name("Name"), Size("Size"), Priority("Priority") }


@Composable
internal fun UiText(text: String, modifier: Modifier = Modifier, color: Color = androidx.compose.ui.graphics.Color.Unspecified, style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default, fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight: FontWeight? = null, letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, fontFamily: FontFamily? = null, softWrap: Boolean = true) {
    val language = LocalAppLanguage.current
    val localized = localizeUiText(text, language)
    // Tracking separates Latin letters but visually breaks connected Persian script.
    val spacing = if (language == "fa" && localized.any { it in '؀'..'ۿ' }) 0.sp else letterSpacing
    androidx.compose.material3.Text(text = localized, modifier = modifier, color = color, style = style, fontSize = fontSize, fontWeight = fontWeight, letterSpacing = spacing, maxLines = maxLines, overflow = overflow, softWrap = softWrap, fontFamily = fontFamily ?: if (language == "fa") Vazirmatn else null)
}

/** Localizes a string that is built at runtime (counts, sizes) and cannot be a dictionary key. */
@Composable
private fun localized(english: String, persian: String): String = if (LocalAppLanguage.current == "fa") persian else english


@Composable
fun CyronApp(viewModel: DownloadViewModel = hiltViewModel()) {
    val state by viewModel.screen.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
    var selected by remember { mutableStateOf<DownloadTask?>(null) }
    var addDialogVisible by rememberSaveable { mutableStateOf(false) }
    var addMode by rememberSaveable { mutableStateOf("file") }
    var sharedText by remember { mutableStateOf<String?>(null) }
    var downloadFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDestructive by remember { mutableStateOf<Pair<DownloadTask, DownloadAction>?>(null) }
    var pendingFolderTask by remember { mutableStateOf<DownloadTask?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    var showFullLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val language = state.settings.language
    val rtl = language == "fa"
    val effectiveDark = if (state.settings.followSystemTheme) isSystemInDarkTheme() else state.settings.darkTheme
    val view = LocalView.current
    val uiPreferences = remember { context.getSharedPreferences("cyron_ui", Context.MODE_PRIVATE) }
    var showNotificationPrompt by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { showNotificationPrompt = false }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !uiPreferences.getBoolean("notification_prompt_shown", false)
        ) {
            uiPreferences.edit().putBoolean("notification_prompt_shown", true).apply()
            showNotificationPrompt = true
        }
    }

    SideEffect {
        uiPreferences.edit()
            .putBoolean("dark_theme", effectiveDark)
            .putBoolean("follow_system_theme", state.settings.followSystemTheme)
            .apply()
        if (!view.isInEditMode) {
            val window = context.findActivity()?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
        }
    }

    val activity = context.findActivity() as? MainActivity
    LaunchedEffect(activity) {
        activity?.observeSharedLinks()?.collect { text ->
            val links = extractDownloadLinks(text)
            addMode = when {
                links.isNotEmpty() && links.all { isYouTubeLink(it) } -> "youtube"
                links.isNotEmpty() && links.all { it.startsWith("magnet:", true) } -> "torrent"
                else -> "file"
            }
            sharedText = text
            viewModel.clearValidationError()
            viewModel.clearEnqueueResult()
            addDialogVisible = true
        }
    }

    val dismissAddDialog: () -> Unit = { addDialogVisible = false; sharedText = null; viewModel.clearValidationError() }
    val openAddDialog: () -> Unit = {
        // Clear old results before composing the dialog so a stale success flag
        // cannot instantly dismiss the new one.
        viewModel.clearEnqueueResult()
        viewModel.clearValidationError()
        addDialogVisible = true
    }
    BackHandler(enabled = addDialogVisible) { dismissAddDialog() }
    BackHandler(enabled = !addDialogVisible && selected != null) { selected = null }
    BackHandler(enabled = !addDialogVisible && selected == null && tab != Tab.Dashboard) { tab = Tab.Dashboard }
    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.substringBeforeLast('.') ?: "torrent"
            val folder = downloadFolder ?: state.settings.defaultFolderUri
            if (folder.isNullOrBlank()) viewModel.showError("Choose a destination folder first.")
            else viewModel.enqueueTorrent(uri.toString(), folder, name)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onSuccess {
                    if (addDialogVisible) downloadFolder = uri.toString()
                    else viewModel.updateSettings(SettingsAction.Destination(uri.toString()))
                }
                .onFailure { viewModel.showError("Unable to keep access to that folder. Please select a writable folder again.") }
        }
    }

    val taskFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val task = pendingFolderTask
        pendingFolderTask = null
        if (uri != null && task != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onSuccess { viewModel.changeFolder(task, uri.toString()) }
                .onFailure { viewModel.showError("Unable to keep access to that folder. Please select a writable folder again.") }
        }
    }
    LaunchedEffect(addDialogVisible, state.settings.defaultFolderUri) {
        if (addDialogVisible) {
            downloadFolder = state.settings.defaultFolderUri
            viewModel.clearValidationError()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(localizeUiText(it, language)); viewModel.clearMessage() }
    }
    // Dialog errors must stay visible inside the dialog; only snackbar them once it is closed.
    LaunchedEffect(state.validationError, addDialogVisible) {
        val error = state.validationError
        if (error != null && !addDialogVisible) {
            snackbar.showSnackbar(localizeUiText(error, language))
            viewModel.clearValidationError()
        }
    }

    val haptic = LocalHapticFeedback.current
    val performAction: (DownloadTask, DownloadAction) -> Unit = { task, action ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.action(task, action)
        if (action == DownloadAction.Delete) selected = null
    }
    val requestAction: (DownloadTask, DownloadAction) -> Unit = { task, action ->
        when (action) {
            DownloadAction.Cancel, DownloadAction.Delete -> pendingDestructive = task to action
            DownloadAction.ChangeFolder -> {
                pendingFolderTask = task
                taskFolderPicker.launch(task.outputFolderUri?.let(Uri::parse))
            }
            else -> performAction(task, action)
        }
    }
    val deleteTask: (DownloadTask, Boolean) -> Unit = { task, keepFile ->
        viewModel.delete(task, keepFile)
        selected = null
    }
    CompositionLocalProvider(LocalAppLanguage provides language, androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
    CyronTheme(darkTheme = effectiveDark, persian = rtl) {
        AmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (selected == null && tab != Tab.Settings) {
                    FloatingActionButton(
                        onClick = openAddDialog,
                        shape = RoundedCornerShape(19.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 3.dp)
                    ) { Icon(Icons.Filled.Add, localizeUiText("Add a download", language), Modifier.size(24.dp)) }
                }
            },
            bottomBar = {
                CyronNavBar(
                    items = Tab.entries.map { (if (rtl) it.fa else it.label) to navIcon(it) },
                    selectedIndex = Tab.entries.indexOf(tab),
                    onSelect = { index -> selected = null; tab = Tab.entries[index] }
                )
            }
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 11.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.cyron_logo),
                        contentDescription = null,
                        modifier = Modifier.size(35.dp).clip(RoundedCornerShape(11.dp)).border(.9.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f), RoundedCornerShape(11.dp))
                    )
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        UiText("CYRON", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.1.sp, color = MaterialTheme.colorScheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UiText("DOWNLOAD MANAGER", fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.05.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            UiText("  v${BuildConfig.VERSION_NAME}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (tab != Tab.Settings) {
                        IconButton(onClick = { tab = Tab.Downloads }) { Icon(Icons.Filled.Search, localizeUiText("Search downloads", language), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = { tab = Tab.Settings }) {
                        Icon(Icons.Filled.Tune, localizeUiText("Download settings", language), tint = if (tab == Tab.Settings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .48f), thickness = .6.dp)
                AnimatedContent(
                    targetState = if (selected != null) "detail" else tab.name,
                    transitionSpec = { slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 9 } togetherWith fadeOut(tween(140)) },
                    label = "cyron-screen-transition",
                    modifier = Modifier.weight(1f)
                ) { route ->
                    when {
                        route == "detail" -> {
                            val detailTask = state.downloads.firstOrNull { it.id == selected?.id } ?: selected ?: return@AnimatedContent
                            DownloadDetailScreen(
                                task = detailTask,
                                snapshot = state.snapshots[detailTask.id],
                                onBack = { selected = null },
                                onAction = requestAction,
                                onRetry = { task -> viewModel.action(task, DownloadAction.Retry) },
                                onOpenOutput = { task, uri -> viewModel.openOutput(task, uri) },
                                onShareOutput = { task, uri -> viewModel.shareOutput(task, uri) }
                            )
                        }
                        route == Tab.Dashboard.name -> DashboardScreen(state) { task -> selected = task }
                        route == Tab.Downloads.name -> DownloadsScreen(state, onSelect = { selected = it }, onAdd = openAddDialog, viewModel = viewModel, onAction = requestAction)
                        else -> SettingsScreen(state.settings, onAction = viewModel::updateSettings, onPickFolder = { directoryPicker.launch(state.settings.defaultFolderUri?.let(Uri::parse)) }, onOpenLicenses = { showFullLicenses = false; showLicenses = true }, onClearHistory = viewModel::clearHistory)
                    }
                }
            }
        }
        }
        if (addDialogVisible) {
            // Dialog content is composed in a separate window; re-provide the app
            // language and direction so the add sheet is Persian too.
            CompositionLocalProvider(
                LocalAppLanguage provides language,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                AddDownloadDialog(
                    state = state,
                    viewModel = viewModel,
                    mode = addMode,
                    onModeChange = { addMode = it },
                    folderUri = downloadFolder,
                    onDismiss = dismissAddDialog,
                    sharedText = sharedText,
                    onPickFolder = { directoryPicker.launch((downloadFolder ?: state.settings.defaultFolderUri)?.let(Uri::parse)) },
                    onPickTorrent = { torrentPicker.launch(arrayOf("application/x-bittorrent", "application/octet-stream", "*/*")) }
                )
            }
        }
        pendingDestructive?.let { (task, action) ->
            CompositionLocalProvider(
                LocalAppLanguage provides language,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                val completedDelete = action == DownloadAction.Delete && task.status == DownloadStatus.COMPLETED
                AlertDialog(
                    onDismissRequest = { pendingDestructive = null },
                    title = { UiText(when { completedDelete -> "Delete download?"; action == DownloadAction.Delete -> "Delete this download?"; else -> "Cancel download?" }) },
                    text = {
                        UiText(
                            when {
                                completedDelete -> "Delete the downloaded file from the selected folder, or keep the file and remove only the record."
                                action == DownloadAction.Delete -> "This removes the record and any partial data."
                                else -> "The transfer will stop and its partial data will be deleted."
                            }
                        )
                    },
                    confirmButton = {
                        if (completedDelete) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { pendingDestructive = null; deleteTask(task, true) }) { UiText("Keep file") }
                                Button(onClick = { pendingDestructive = null; deleteTask(task, false) }) { UiText("Delete file") }
                            }
                        } else {
                            Button(onClick = {
                                pendingDestructive = null
                                if (action == DownloadAction.Delete) deleteTask(task, false) else performAction(task, action)
                            }) { UiText(if (action == DownloadAction.Delete) "Delete" else "Cancel transfer") }
                        }
                    },
                    dismissButton = { TextButton(onClick = { pendingDestructive = null }) { UiText(if (completedDelete) "Cancel" else "Keep") } }
                )
            }
        }
        if (showLicenses) {
            CompositionLocalProvider(
                LocalAppLanguage provides language,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                val noticeAsset = if (showFullLicenses) "licenses/ALL_DEPENDENCY_LICENSES.txt" else "THIRD_PARTY_NOTICES.md"
                val notice = remember(showFullLicenses) {
                    runCatching { context.assets.open(noticeAsset).bufferedReader().use { it.readText() } }
                        .getOrDefault("License notice is unavailable.")
                }
                AlertDialog(
                    onDismissRequest = { showLicenses = false },
                    title = { UiText(if (showFullLicenses) "Full third-party licenses" else "Third-party licenses") },
                    text = {
                        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            if (!showFullLicenses) {
                                TextButton(onClick = { showFullLicenses = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                    UiText("Open full license texts")
                                }
                            }
                            UiText(notice, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    },
                    dismissButton = if (showFullLicenses) {
                        { TextButton(onClick = { showFullLicenses = false }) { UiText("Summary") } }
                    } else null,
                    confirmButton = { TextButton(onClick = { showLicenses = false }) { UiText("Close") } }
                )
            }
        }
        if (showNotificationPrompt) {
            CompositionLocalProvider(
                LocalAppLanguage provides language,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                AlertDialog(
                    onDismissRequest = { showNotificationPrompt = false },
                    title = { UiText("Allow download notifications?") },
                    text = { UiText("Notifications show live progress and tell you when a file finishes, even while Cyron is in the background. You can change this later in Android settings.") },
                    confirmButton = { Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { UiText("Allow") } },
                    dismissButton = { TextButton(onClick = { showNotificationPrompt = false }) { UiText("Not now") } }
                )
            }
        }
    }
    }
}

private fun isYouTubeLink(value: String): Boolean =
    Regex("(?i)^https?://(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be|youtube-nocookie\\.com)/").containsMatchIn(value.trim())

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 19.dp, end = 19.dp), content = content)
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    UiText(text.uppercase(Locale.ROOT), modifier, color = MaterialTheme.colorScheme.primary.copy(alpha = .86f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.65.sp)
}

@Composable
private fun SectionHeading(title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 23.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 10.dp).size(width = 3.dp, height = 16.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))))
        Column(Modifier.weight(1f)) { UiText(title, style = MaterialTheme.typography.titleMedium); if (subtitle != null) UiText(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        action?.invoke()
    }
}

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CyronCard(modifier, content = content)
}

@Composable
private fun DashboardScreen(state: ScreenState, onSelect: (DownloadTask) -> Unit) {
    val stats = state.stats
    val dateLocale = if (LocalAppLanguage.current == "fa") Locale("fa", "IR") else Locale.getDefault()
    PageColumn {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 17.dp, bottom = 88.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("PERSONAL DOWNLOAD SPACE")
                        UiText("Your files,\nin good hands.", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.headlineLarge)
                        UiText("Everything is where you left it.", Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(Modifier.padding(top = 8.dp, end = 1.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 9.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                            UiText("On device", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
            item { LiveTransferCard(state) }
            item { Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) { Eyebrow("AT A GLANCE", Modifier.weight(1f)); UiText(latinDigits(DateFormat.getDateInstance(DateFormat.MEDIUM, dateLocale).format(Date())), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    // IntrinsicSize.Min keeps each pair the same height while letting the cards
                    // grow for the taller Persian line heights instead of clipping the copy.
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatCard("Downloaded", formatBytes(stats.totalDownloadedBytes), "Files safely completed", Icons.Filled.ArrowDownward, MaterialTheme.colorScheme.primary, Modifier.weight(1f).fillMaxHeight())
                        StatCard("In progress", stats.activeCount.toString().padStart(2, '0'), "Active transfers", Icons.Filled.Bolt, MaterialTheme.colorScheme.secondary, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatCard("Completed (24h)", stats.completedToday.toString().padStart(2, '0'), "Successful downloads", Icons.Filled.CheckCircle, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f).fillMaxHeight())
                        StatCard("Total speed", if (stats.currentSpeedBytesPerSecond > 0) formatSpeed(stats.currentSpeedBytesPerSecond) else "—", "Across active transfers", Icons.Filled.Speed, MaterialTheme.colorScheme.primary, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            item {
                SectionHeading("Transfer activity", "Real downloaded data · last 7 days")
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                        UiText("  Download history", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        UiText("7 days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = .6.sp)
                    }
                    UiText(formatBytes(stats.dailyBytes.sum()), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    UiText("Completed on this device", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    TransferChart(stats.dailyBytes, Modifier.fillMaxWidth().height(105.dp).padding(top = 9.dp))
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("7d ago", "6d ago", "5d ago", "4d ago", "3d ago", "2d ago", "Today").forEach { UiText(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            item {
                SectionHeading("Recent downloads", if (state.downloads.isEmpty()) "Your file history appears here" else localized("${state.downloads.size} saved in your local library", "${state.downloads.size} فایل در کتابخانهٔ شما"))
            }
            if (state.downloads.isEmpty()) item { EmptyState("Your download list is ready", "Add a link to start your first real download.", Icons.Filled.CloudDownload) }
            else items(state.downloads.take(6), key = { it.id }) { task -> DownloadListItem(task, state.snapshots[task.id], onClick = { onSelect(task) }, compact = true) }
            item { Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary); UiText("  Private by default · Stored on your device", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

private fun navIcon(tab: Tab): androidx.compose.ui.graphics.vector.ImageVector = when (tab) {
    Tab.Dashboard -> Icons.Outlined.Home
    Tab.Downloads -> Icons.Outlined.Download
    Tab.Settings -> Icons.Outlined.Settings
}

/**
 * The dashboard's live strip: one animated speed reading plus the aggregate progress of
 * every running transfer. It replaces a static date row with the number a download
 * manager is actually about.
 */
@Composable
private fun LiveTransferCard(state: ScreenState) {
    val stats = state.stats
    val activeTasks = state.downloads.filter { it.visibleStatus(state.snapshots[it.id]) in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROBING, DownloadStatus.PUBLISHING) }
    val active = activeTasks.size
    val fraction = if (activeTasks.isEmpty()) 0f else activeTasks.map { it.visibleProgress(state.snapshots[it.id]) }.average().toFloat()
    val animatedSpeed by animateFloatAsState(stats.currentSpeedBytesPerSecond.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "liveSpeed")
    val tone = if (active > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    CyronCard(Modifier.fillMaxWidth(), glow = if (active > 0) MaterialTheme.colorScheme.primary else null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (active > 0) PulseDot(MaterialTheme.colorScheme.primary) else Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = .8f)))
            UiText(localized("LIVE TRANSFER", "دانلود زنده"), Modifier.padding(start = 8.dp).weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, color = tone)
            UiText(localized("$active active", "$active فعال"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        UiText(
            numeric(if (stats.currentSpeedBytesPerSecond > 0) formatSpeed(animatedSpeed.toLong()) else "—"),
            Modifier.padding(top = 8.dp),
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-1.2).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        UiText(
            if (active > 0) "Across active transfers" else "Ready for downloads",
            Modifier.padding(top = 2.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SegmentProgressBar(
            fraction = fraction,
            segments = 12,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            height = 5.dp
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            UiText(numeric(localized("${(fraction * 100).toInt()}% overall", "${(fraction * 100).toInt()}% کل")), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            UiText(localized("${stats.activeCount} of ${state.settings.maxConcurrentDownloads} slots busy", "${stats.activeCount} از ${state.settings.maxConcurrentDownloads} جایگاه مشغول"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    CyronCard(modifier.heightIn(min = 112.dp), glow = color) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UiText(label, Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(color.copy(alpha = .24f), color.copy(alpha = .06f))))
                    .border(.6.dp, color.copy(alpha = .28f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(14.dp), tint = color) }
        }
        UiText(numeric(value), Modifier.padding(top = 8.dp), fontSize = 23.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, letterSpacing = (-1).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        UiText(description, Modifier.padding(top = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TransferChart(values: List<Long>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = .46f)
    val accent = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val left = 3.dp.toPx(); val right = size.width - 3.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 6.dp.toPx()
        repeat(3) { i -> val y = top + (bottom - top) * i / 2; drawLine(grid, Offset(left, y), Offset(right, y), 1f) }
        if (values.isEmpty()) {
            val baseline = bottom - (bottom - top) * .24f
            drawLine(color.copy(alpha = .55f), Offset(left, baseline), Offset(right, baseline), 2.2.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val safe = values.takeLast(7).let { list -> if (list.size == 1) listOf(0L, list.first()) else list }
        val peak = safe.max().coerceAtLeast(1).toFloat()
        val points = safe.mapIndexed { i, v -> Offset(left + (right - left) * i / (safe.size - 1).coerceAtLeast(1), bottom - (bottom - top) * (.12f + .79f * v / peak)) }
        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                val midX = (previous.x + current.x) / 2f
                cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            }
        }
        val area = Path().apply { addPath(curve); lineTo(points.last().x, bottom); lineTo(points.first().x, bottom); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .30f), color.copy(alpha = .015f)), startY = top, endY = bottom))
        drawPath(curve, Brush.horizontalGradient(listOf(accent.copy(alpha = .85f), color)), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
        points.lastOrNull()?.let {
            drawCircle(color.copy(alpha = .12f), 14.dp.toPx(), it)
            drawCircle(color.copy(alpha = .22f), 8.dp.toPx(), it)
            drawCircle(color, 3.6.dp.toPx(), it)
        }
    }
}

@Composable
private fun DownloadsScreen(state: ScreenState, onSelect: (DownloadTask) -> Unit, onAdd: () -> Unit, viewModel: DownloadViewModel, onAction: (DownloadTask, DownloadAction) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var category by rememberSaveable { mutableStateOf("All types") }
    var sort by rememberSaveable { mutableStateOf(DownloadSort.Newest) }
    val language = LocalAppLanguage.current
    val normalized = normalizeSearchText(search)
    val visible = state.downloads.filter { task ->
        val searchable = normalizeSearchText("${task.fileName} ${task.url} ${task.status.name}")
        val matchesSearch = normalized.isBlank() || searchable.contains(normalized)
        val matchesStatus = when (filter) {
            Filter.All -> true
            Filter.Active -> task.visibleStatus(state.snapshots[task.id]) in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PROBING, DownloadStatus.PUBLISHING)
            Filter.Completed -> task.status == DownloadStatus.COMPLETED
            Filter.Paused -> task.status == DownloadStatus.PAUSED
            Filter.Failed -> task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELLED
        }
        val extension = task.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val matchesCategory = when (category) { "All types" -> true; "Archives" -> extension in setOf("zip", "rar", "7z", "tar", "gz"); "Video" -> extension in setOf("mp4", "mkv", "mov", "webm", "avi"); "Apps" -> extension in setOf("apk", "exe", "msi"); "Documents" -> extension in setOf("pdf", "doc", "docx", "txt", "epub"); "Disk images" -> extension in setOf("iso", "img", "dmg"); else -> true }
        matchesSearch && matchesStatus && matchesCategory
    }.let { list ->
        when (sort) {
            DownloadSort.Newest -> list.sortedByDescending(DownloadTask::createdAt)
            DownloadSort.Oldest -> list.sortedBy(DownloadTask::createdAt)
            DownloadSort.Name -> list.sortedBy { it.fileName.lowercase(Locale.ROOT) }
            DownloadSort.Size -> list.sortedByDescending { it.totalBytes }
            DownloadSort.Priority -> list.sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy(DownloadTask::createdAt))
        }
    }
    val queue = state.downloads.filter { it.status == DownloadStatus.QUEUED }
        .sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy(DownloadTask::createdAt))
    val canPause = state.downloads.any { it.visibleStatus(state.snapshots[it.id]) in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PROBING, DownloadStatus.PUBLISHING) }
    val canResume = state.downloads.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED }
    PageColumn {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 15.dp, bottom = 88.dp)) {
            item { Eyebrow("YOUR PERSONAL LIBRARY"); UiText("Downloads", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.headlineLarge, softWrap = false, overflow = TextOverflow.Visible); UiText(localized("${state.downloads.size} files · stored on this device", "${state.downloads.size} فایل · ذخیره‌شده روی این دستگاه"), Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            item {
                OutlinedTextField(value = search, onValueChange = { search = it }, Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = true, shape = RoundedCornerShape(12.dp), placeholder = { UiText("Search filenames, URL…", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, textStyle = LocalTextStyle.current.copy(fontFamily = if (LocalAppLanguage.current == "fa") Vazirmatn else LocalTextStyle.current.fontFamily))
            }
            item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Filter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { UiText(f.label, fontSize = 12.sp) }, shape = RoundedCornerShape(9.dp)) } } }
            item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All types", "Archives", "Video", "Apps", "Documents", "Disk images").forEach { type -> FilterChip(selected = category == type, onClick = { category = type }, label = { UiText(type, fontSize = 11.sp) }, shape = RoundedCornerShape(8.dp), leadingIcon = if (category == type) ({ Icon(Icons.Filled.Check, null, Modifier.size(13.dp)) }) else null) } } }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = viewModel::pauseAll, enabled = canPause, modifier = Modifier.weight(1f).height(37.dp), shape = RoundedCornerShape(9.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) { Icon(Icons.Filled.Pause, null, Modifier.size(14.dp)); UiText("  Pause all", fontSize = 11.sp) }
                    OutlinedButton(onClick = viewModel::resumeAll, enabled = canResume, modifier = Modifier.weight(1f).height(37.dp), shape = RoundedCornerShape(9.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(14.dp)); UiText("  Resume all", fontSize = 11.sp) }
                    Box {
                        var sortExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { sortExpanded = true }) { Icon(Icons.Filled.Sort, localizeUiText("Sort downloads", language), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            DownloadSort.entries.forEach { option -> DropdownMenuItem(text = { UiText(option.label, fontSize = 12.sp) }, leadingIcon = { if (sort == option) Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }, onClick = { sort = option; sortExpanded = false }) }
                        }
                    }
                }
            }
            item {
                SectionHeading(if (filter == Filter.All) "All your files" else filter.label, localized("${visible.size} ${if (visible.size == 1) "download" else "downloads"}", "${visible.size} دانلود"), action = {
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreHoriz, localizeUiText("More download actions", language)) }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { UiText("Add a download") }, onClick = { expanded = false; onAdd() }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                            DropdownMenuItem(text = { UiText("Clear search & filters") }, onClick = { search = ""; filter = Filter.All; category = "All types"; expanded = false }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
                        }
                    }
                })
            }
            if (visible.isEmpty()) item { EmptyState(if (state.downloads.isEmpty()) "Your library is empty" else "No matching downloads", if (state.downloads.isEmpty()) "Add a URL to download your first file." else "Try changing the search or selected filters.", Icons.Filled.CloudDownload, action = onAdd) }
            else items(visible, key = { it.id }) { task ->
                val position = queue.indexOfFirst { it.id == task.id }.takeIf { it >= 0 }?.plus(1)
                DownloadListItem(task, state.snapshots[task.id], onClick = { onSelect(task) }, compact = false, queuePosition = position, onAction = { action -> onAction(task, action) })
            }
            item { Spacer(Modifier.height(8.dp)); Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(47.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); UiText("  Add a download", style = MaterialTheme.typography.labelLarge) } }
        }
    }
}

@Composable
private fun DownloadListItem(task: DownloadTask, snapshot: com.cyron.manager.domain.model.TransferSnapshot?, onClick: () -> Unit, compact: Boolean, queuePosition: Int? = null, onAction: ((DownloadAction) -> Unit)? = null) {
    val status = task.visibleStatus(snapshot)
    val fraction = task.visibleProgress(snapshot)
    val animatedFraction by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(620, easing = FastOutSlowInEasing), label = "rowProgress")
    val active = status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROBING, DownloadStatus.PUBLISHING)
    // Media rows have no stored size until they finish; the live snapshot carries it.
    val knownTotalBytes = (snapshot?.totalBytes ?: task.totalBytes).takeIf { it > 0 } ?: -1L
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    CyronCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick),
        glow = if (active) MaterialTheme.colorScheme.primary else null,
        shape = RoundedCornerShape(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIcon(task.fileName, 39.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    UiText(task.fileName, style = MaterialTheme.typography.titleMedium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    UiText("${numeric(formatBytes(knownTotalBytes))}  ·  ${task.mimeType.substringAfter('/', "file").uppercase(Locale.ROOT)}", Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(status)
                if (onAction != null) {
                    Box { IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreHoriz, localizeUiText("Download actions", LocalAppLanguage.current), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }; DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        listOfNotNull(
                            (if (status == DownloadStatus.DOWNLOADING) "Pause" to DownloadAction.Pause else if (status in setOf(DownloadStatus.PAUSED, DownloadStatus.QUEUED)) "Resume" to DownloadAction.Resume else if (status in setOf(DownloadStatus.FAILED, DownloadStatus.CANCELLED)) "Retry" to DownloadAction.Retry else null),
                            (if (status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.QUEUED)) "Cancel" to DownloadAction.Cancel else null),
                            (if (status in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)) "Change folder" to DownloadAction.ChangeFolder else null),
                            (if (status in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED)) "Higher priority" to DownloadAction.PriorityUp else null),
                            (if (status in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED)) "Lower priority" to DownloadAction.PriorityDown else null),
                            (if (status == DownloadStatus.COMPLETED) "Open file" to DownloadAction.Open else null),
                            (if (status == DownloadStatus.COMPLETED) "Share file" to DownloadAction.Share else null),
                            (if (status == DownloadStatus.COMPLETED) "Delete file" to DownloadAction.Delete else "Delete" to DownloadAction.Delete)
                        ).forEach { (label, action) -> DropdownMenuItem(text = { UiText(label, fontSize = 12.sp) }, leadingIcon = { Icon(when(action) { DownloadAction.Pause -> Icons.Filled.Pause; DownloadAction.Resume, DownloadAction.Retry -> Icons.Filled.PlayArrow; DownloadAction.Cancel, DownloadAction.Delete -> Icons.Filled.Delete; DownloadAction.Open -> Icons.AutoMirrored.Filled.OpenInNew; DownloadAction.Share -> Icons.AutoMirrored.Filled.Send; DownloadAction.ChangeFolder -> Icons.Filled.FolderOpen; DownloadAction.PriorityUp -> Icons.Filled.ArrowUpward; DownloadAction.PriorityDown -> Icons.Filled.ArrowDownward }, null, Modifier.size(17.dp)) }, onClick = { menuExpanded = false; onAction(action) }) }
                    } }
                }
            }
            if (queuePosition != null || task.status in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED)) {
                UiText(
                    localized(
                        listOfNotNull(queuePosition?.let { "Queue position #$it" }, "Priority ${task.priority}").joinToString(" · "),
                        listOfNotNull(queuePosition?.let { "جایگاه صف $it" }, "اولویت ${task.priority}").joinToString(" · ")
                    ),
                    Modifier.padding(top = 7.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROBING, DownloadStatus.PUBLISHING, DownloadStatus.QUEUED, DownloadStatus.PAUSED)) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    SegmentProgressBar(fraction = animatedFraction, segments = if (task.rangeSupported) task.connectionCount else 1, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), height = 6.dp)
                    UiText("  ${(animatedFraction * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                }
                if (status == DownloadStatus.DOWNLOADING) {
                    val speed = snapshot?.bytesPerSecond ?: 0L
                    val knownTotal = (snapshot?.totalBytes ?: task.totalBytes).takeIf { it > 0 }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        UiText(if (knownTotal != null) localized(numeric(formatBytes(snapshot?.downloadedBytes ?: task.downloadedBytes)) + " of " + numeric(formatBytes(knownTotal)), numeric(formatBytes(snapshot?.downloadedBytes ?: task.downloadedBytes)) + " از " + numeric(formatBytes(knownTotal))) else "Streaming…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        UiText(if (speed > 0) numeric(formatSpeed(speed)) else "Connecting…", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                    }
                } else if (status == DownloadStatus.PUBLISHING) UiText("Saving the verified file to your selected folder…", Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                else if (!task.error.isNullOrBlank()) UiText(task.error, Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (status == DownloadStatus.FAILED && !task.error.isNullOrBlank()) {
                UiText(task.error, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onAction?.invoke(DownloadAction.Retry) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)) { Icon(Icons.Filled.Refresh, null, Modifier.size(15.dp)); UiText(" Retry", fontSize = 11.sp) }
            }
            if (status == DownloadStatus.COMPLETED && !compact) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { onAction?.invoke(DownloadAction.Open) }, modifier = Modifier.weight(1f).height(36.dp), shape = RoundedCornerShape(9.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(15.dp)); UiText("  Open", fontSize = 12.sp) }
                OutlinedButton(onClick = { onAction?.invoke(DownloadAction.Share) }, modifier = Modifier.weight(1f).height(36.dp), shape = RoundedCornerShape(9.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Icon(Icons.Filled.Share, null, Modifier.size(15.dp)); UiText("  Share", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun FileIcon(name: String, size: Dp) {
    val ext = name.substringAfterLast('.', "file").uppercase(Locale.ROOT)
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    val tone = when (ext) {
        "ZIP", "RAR", "7Z" -> if (light) Color(0xFF5B4BC4) else Color(0xFFAC9BFF)
        "MP4", "MOV", "MKV" -> if (light) Color(0xFFA93A86) else Color(0xFFF194CF)
        "APK", "EXE", "MSI" -> if (light) Color(0xFF166B85) else Color(0xFF78D4EF)
        "PDF" -> if (light) Color(0xFFB02734) else Color(0xFFFF8792)
        "ISO", "IMG" -> if (light) Color(0xFF1C6B4A) else Color(0xFF72D6A8)
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when (ext) { "ZIP", "RAR", "7Z" -> Icons.Filled.Archive; "MP4", "MOV", "MKV" -> Icons.Filled.Movie; "APK" -> Icons.Filled.Android; "EXE", "MSI" -> Icons.Filled.GifBox; "PDF", "DOC", "TXT" -> Icons.Filled.Description; "ISO", "IMG" -> Icons.Filled.Storage; else -> Icons.Filled.InsertDriveFile }
    Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(tone.copy(alpha = .105f)).border(.7.dp, tone.copy(alpha = .20f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size((size.value * .46).dp), tint = tone) }
}

@Composable
private fun StatusPill(status: DownloadStatus) {
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    val (label, tone) = when (status) {
        DownloadStatus.DOWNLOADING -> "ACTIVE" to MaterialTheme.colorScheme.primary
        DownloadStatus.PROBING -> "CHECKING" to MaterialTheme.colorScheme.secondary
        DownloadStatus.PUBLISHING -> "SAVING" to MaterialTheme.colorScheme.secondary
        DownloadStatus.QUEUED -> "QUEUED" to if (light) Color(0xFF5B3FA8) else Color(0xFFBD9AFF)
        DownloadStatus.PAUSED -> "PAUSED" to if (light) Color(0xFF8C5C12) else Color(0xFFF0C17A)
        DownloadStatus.COMPLETED -> "DONE" to MaterialTheme.colorScheme.tertiary
        DownloadStatus.FAILED -> "FAILED" to MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> "CANCELLED" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val animatedTone by animateColorAsState(tone, tween(320), label = "pillTone")
    val live = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PROBING || status == DownloadStatus.PUBLISHING
    Row(Modifier.clip(RoundedCornerShape(7.dp)).background(animatedTone.copy(alpha = .13f)).border(.6.dp, animatedTone.copy(alpha = .25f), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (live) PulseDot(animatedTone, 4.dp) else Box(Modifier.size(5.dp).clip(CircleShape).background(animatedTone))
        UiText(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, color = animatedTone)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDownloadDialog(state: ScreenState, viewModel: DownloadViewModel, mode: String, onModeChange: (String) -> Unit, folderUri: String?, sharedText: String?, onDismiss: () -> Unit, onPickFolder: () -> Unit, onPickTorrent: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(sharedText.orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    var connections by rememberSaveable { mutableFloatStateOf(state.settings.defaultConnections.coerceIn(1, 16).toFloat()) }
    var hash by rememberSaveable { mutableStateOf("") }
    var hasProbed by rememberSaveable { mutableStateOf(false) }
    var batch by rememberSaveable { mutableStateOf(false) }
    var formatsExpanded by remember { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            url = sharedText
            batch = extractDownloadLinks(sharedText).size > 1
            hasProbed = false
            viewModel.clearMediaInfo()
        }
    }
    val ltrInput = LocalTextStyle.current.copy(
        textDirection = TextDirection.Ltr,
        fontFamily = if (LocalAppLanguage.current == "fa") Vazirmatn else LocalTextStyle.current.fontFamily
    )
    LaunchedEffect(state.enqueueSucceeded) {
        if (state.enqueueSucceeded) {
            url = ""
            name = ""
            hash = ""
            hasProbed = false
            viewModel.clearEnqueueResult()
            onDismiss()
        }
    }
    LaunchedEffect(state.probedUrl, state.probedBytes, state.isProbing, url) { hasProbed = !state.isProbing && state.probedBytes > 0 && url == state.probedUrl }
    val destinationReady = !folderUri.isNullOrBlank()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { Box(Modifier.padding(top = 11.dp, bottom = 5.dp).size(width = 42.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f))) }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Eyebrow(when (mode) { "file" -> "SAFE · VERIFIED HTTP DOWNLOADS"; "youtube" -> "YOUTUBE · VIDEO & SHORTS"; "media" -> "MEDIA · YT-DLP + FFMPEG"; else -> "TORRENT · MAGNET / .TORRENT" })
            UiText("Add a download", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.headlineMedium)
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val maxBodyHeight = with(density) { windowInfo.containerSize.height.toDp() } * 0.58f
            Column(Modifier.fillMaxWidth().heightIn(max = maxBodyHeight).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("file" to "HTTP file", "youtube" to "YouTube", "media" to "Video / audio", "torrent" to "Torrent").forEach { (key, label) -> FilterChip(selected = mode == key, onClick = { onModeChange(key); hasProbed = false; batch = false }, label = { UiText(label, fontSize = 12.sp) }, shape = RoundedCornerShape(9.dp)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        UiText("Batch import", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                        UiText("Add several links at once", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = batch, onCheckedChange = { batch = it; hasProbed = false })
                }
                if (batch) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        if (text.isNotBlank()) { url = text; hasProbed = false }
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp)) { Icon(Icons.Filled.ContentPaste, null, Modifier.size(15.dp)); UiText("  Paste links", fontSize = 11.sp) }
                    TextButton(onClick = { url = ""; hasProbed = false }, modifier = Modifier.weight(1f)) { UiText("Clear links", fontSize = 11.sp) }
                }
                state.validationError?.takeIf { it.isNotBlank() }?.let { error ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = .12f)), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            UiText(error, Modifier.padding(start = 8.dp).weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                }
                when (mode) {
                    "file" -> {
                        UiText("Enter an HTTP(S) direct-download link. Cyron probes the server and verifies ranges before starting.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(url, { url = if (batch) it else it.trim(); hasProbed = false }, Modifier.fillMaxWidth(), singleLine = !batch, minLines = if (batch) 4 else 1, label = { UiText(if (batch) "Download links (one per line)" else "Direct download URL") }, placeholder = { UiText(if (batch) "https://example.com/file.zip\nhttps://example.com/video.mp4" else "https://example.com/file.zip") }, leadingIcon = { Icon(Icons.Filled.CloudDownload, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(11.dp), textStyle = ltrInput)
                        if (hasProbed && !batch) SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary); Column(Modifier.padding(start = 8.dp).weight(1f)) { UiText(state.probedName, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); UiText("${formatBytes(state.probedBytes)} · ${if (state.supportsRanges) "Verified multi-connection" else "Single connection"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        if (!batch) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { UiText("File name (optional)") }, placeholder = { UiText("Detected from server") }, shape = RoundedCornerShape(11.dp))
                        UiText(localized("HTTP connections · ${connections.toInt()} ${if (state.supportsRanges) "verified ranges" else "(requires server Range + validator)"}", "اتصال HTTP · ${connections.toInt()} ${if (state.supportsRanges) "بازه‌های تأییدشده" else "(نیازمند پشتیبانی Range و اعتبارسنج سرور)"}"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = connections, onValueChange = { connections = it }, valueRange = 1f..16f, steps = 14, enabled = !state.isProbing, modifier = Modifier.semantics { contentDescription = localizeUiText("HTTP connections per file", language); stateDescription = connections.toInt().toString() })
                        if (!batch) OutlinedTextField(hash, { hash = it.filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(64) }, Modifier.fillMaxWidth(), singleLine = true, label = { UiText("Expected SHA-256 (optional)") }, shape = RoundedCornerShape(11.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), textStyle = ltrInput)
                    }
                    "media", "youtube" -> {
                        val youtubeMode = mode == "youtube"
                        UiText(if (youtubeMode) "Paste a YouTube video, Shorts, or youtu.be link. Cyron downloads the video and audio streams and merges them on your device." else "Enter a media page to inspect available formats. Site support varies; access restrictions and DRM are not bypassed.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(url, { url = if (batch) it else it.trim(); viewModel.clearMediaInfo() }, Modifier.fillMaxWidth(), singleLine = !batch, minLines = if (batch) 4 else 1, label = { UiText(if (batch) "Media links (one per line)" else if (youtubeMode) "YouTube link" else "Media page URL") }, placeholder = { UiText(if (youtubeMode) "https://youtu.be/…" else "https://…") }, leadingIcon = { Icon(Icons.Filled.Movie, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(11.dp), textStyle = ltrInput)
                        if (!batch) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { UiText("File title (optional)") }, placeholder = { UiText(state.mediaInfo?.title ?: "Detected from page") }, shape = RoundedCornerShape(11.dp))
                        UiText(localized("Concurrent fragments · ${connections.toInt()}", "بخش‌های دانلود هم‌زمان · ${connections.toInt()}"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = connections, onValueChange = { connections = it }, valueRange = 1f..16f, steps = 14, modifier = Modifier.semantics { contentDescription = localizeUiText("Concurrent media fragments", language); stateDescription = connections.toInt().toString() })
                        if (state.mediaInfo != null && !batch) {
                            Box {
                                OutlinedButton(onClick = { formatsExpanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { UiText(state.mediaChoices.firstOrNull { it.selector == state.selectedMediaSelector }?.label ?: "Select format") }
                                DropdownMenu(expanded = formatsExpanded, onDismissRequest = { formatsExpanded = false }) {
                                    state.mediaChoices.forEach { choice -> DropdownMenuItem(text = { UiText(choice.label) }, onClick = { viewModel.selectMediaFormat(choice.selector); formatsExpanded = false }) }
                                }
                            }
                            UiText(if (youtubeMode) "Quality is merged on-device with FFmpeg. Some videos may be restricted by YouTube." else "yt-dlp formats and FFmpeg audio conversion. No browser cookies or DRM circumvention.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!batch) {
                            OutlinedButton(onClick = { viewModel.inspectMedia(url) }, enabled = url.startsWith("http", true) && !state.isInspectingMedia, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                                if (state.isInspectingMedia) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Search, null, Modifier.size(16.dp))
                                UiText(if (state.isInspectingMedia) "  Inspecting…" else if (youtubeMode) "  Inspect video" else "  Inspect formats")
                            }
                            TextButton(
                                onClick = { viewModel.enqueueMediaBest(url, name.ifBlank { state.mediaInfo?.title ?: "media" }, folderUri ?: state.settings.defaultFolderUri ?: "") },
                                enabled = url.startsWith("http", true) && destinationReady && !state.isEnqueuing,
                                modifier = Modifier.fillMaxWidth()
                            ) { UiText("Download best quality without inspecting", fontSize = 12.sp) }
                        }
                    }
                    else -> {
                        UiText("Use a magnet link or select a local .torrent file. Torrent data is private until complete, then exported to your selected folder.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(url, { url = if (batch) it else it.trim() }, Modifier.fillMaxWidth(), minLines = if (batch) 4 else 2, label = { UiText(if (batch) "Magnet links (one per line)" else "Magnet link (optional)") }, placeholder = { UiText("magnet:?xt=urn:btih:…") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(11.dp), textStyle = ltrInput)
                        if (!batch) OutlinedButton(onClick = onPickTorrent, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Filled.FolderOpen, null); UiText("  Choose a .torrent file") }
                        UiText("Torrent networking can use mobile data; respect the rights attached to the content.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    UiText(if (destinationReady) "Destination folder selected" else "Choose a destination folder", Modifier.padding(start = 8.dp).weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    TextButton(onClick = onPickFolder) { UiText("Browse", fontSize = 11.sp) }
                }
                UiText("No account or cloud transfer. Temporary data stays in app-private storage.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { UiText("Cancel") }
                Button(onClick = {
                    val folder = folderUri
                    val links = extractDownloadLinks(url)
                    when (mode) {
                        "file" -> when {
                            batch -> viewModel.enqueueBatch(url, folder ?: "", "file", connections.toInt())
                            !hasProbed -> viewModel.probeUrl(url, connections.toInt())
                            folder == null -> viewModel.showError("Choose a destination folder first.")
                            else -> viewModel.enqueue(url, name.ifBlank { state.probedName }, hash.takeIf(String::isNotBlank), folder, connections.toInt())
                        }
                        "media", "youtube" -> when {
                            batch -> viewModel.enqueueBatch(url, folder ?: "", mode, connections.toInt())
                            state.mediaInfo == null -> viewModel.inspectMedia(url)
                            folder == null -> viewModel.showError("Choose a destination folder first.")
                            else -> viewModel.enqueueMedia(url, name.ifBlank { state.mediaInfo.title }, folder)
                        }
                        else -> when {
                            batch -> viewModel.enqueueBatch(url, folder ?: "", "torrent", connections.toInt())
                            !url.startsWith("magnet:", true) -> onPickTorrent()
                            folder == null -> viewModel.showError("Choose a destination folder first.")
                            else -> viewModel.enqueueTorrent(url, folder, name.ifBlank { "torrent" })
                        }
                    }
                }, enabled = destinationReady && !state.isEnqueuing && if (batch) {
                    val links = extractDownloadLinks(url)
                    when (mode) {
                        "youtube" -> links.isNotEmpty() && links.all(::isYouTubeLink)
                        "torrent" -> links.isNotEmpty() && links.all { it.startsWith("magnet:", true) }
                        else -> links.isNotEmpty()
                    }
                } else when (mode) {
                    "file" -> url.startsWith("http", true) && !state.isProbing && (if (hasProbed) hash.isBlank() || hash.length == 64 else true)
                    "media" -> url.startsWith("http", true) && !state.isInspectingMedia
                    "youtube" -> isYouTubeLink(url) && !state.isInspectingMedia
                    else -> url.startsWith("magnet:", true)
                }, shape = RoundedCornerShape(10.dp)) {
                    val links = extractDownloadLinks(url)
                    val inspecting = !batch && (mode == "youtube" || mode == "media") && state.mediaInfo == null
                    Icon(if (state.isEnqueuing) Icons.Filled.CloudDownload else if (mode == "file" && !hasProbed && !batch || inspecting) Icons.Filled.Search else Icons.Filled.Download, null, Modifier.size(16.dp))
                    UiText(when {
                        batch -> "  Add ${links.size} links"
                        mode == "file" && !hasProbed -> "  Inspect link"
                        mode == "youtube" && inspecting -> "  Inspect video"
                        mode == "media" && inspecting -> "  Inspect formats"
                        else -> "  Start download"
                    }, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: DownloadSettings, onAction: (SettingsAction) -> Unit, onPickFolder: () -> Unit, onOpenLicenses: () -> Unit, onClearHistory: () -> Unit) {
    val language = LocalAppLanguage.current
    val settingsContext = LocalContext.current
    var confirmClearHistory by remember { mutableStateOf(false) }
    PageColumn {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 17.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            item { Eyebrow("MADE FOR YOUR DEVICE"); UiText("Settings", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.headlineLarge, softWrap = false, overflow = TextOverflow.Visible); UiText("Just how you want your downloads.", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { SectionHeading("Download behavior") }
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    var concurrentDownloads by remember(settings.maxConcurrentDownloads) { mutableIntStateOf(settings.maxConcurrentDownloads) }
                    var httpConnections by remember(settings.defaultConnections) { mutableIntStateOf(settings.defaultConnections) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(33.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f)), Alignment.Center) { Icon(Icons.Filled.Download, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary) }; Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText("Simultaneous downloads", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText("Maximum active files at once", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; UiText("$concurrentDownloads", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
                    Slider(value = concurrentDownloads.toFloat(), onValueChange = { concurrentDownloads = it.roundToInt().coerceIn(1, 8) }, onValueChangeFinished = { onAction(SettingsAction.Concurrent(concurrentDownloads)) }, valueRange = 1f..8f, steps = 6, modifier = Modifier.semantics { contentDescription = localizeUiText("Simultaneous downloads", language); stateDescription = concurrentDownloads.toString() })
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.RadioButtonChecked, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary); Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText("HTTP connections per file", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText("Independent verified byte ranges", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; UiText("$httpConnections", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary) }
                    Slider(value = httpConnections.toFloat(), onValueChange = { httpConnections = it.roundToInt().coerceIn(1, 16) }, onValueChangeFinished = { onAction(SettingsAction.Connections(httpConnections)) }, valueRange = 1f..16f, steps = 14, modifier = Modifier.semantics { contentDescription = localizeUiText("HTTP connections per file", language); stateDescription = httpConnections.toString() })
                }
            }
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            UiText("Unlimited transfer speed", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                            UiText("Downloads use all available network capacity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        UiText("∞", fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { SectionHeading("Download history") }
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    UiText("Keep completed and failed history", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                    UiText("Older records are removed automatically; downloaded files are kept.", Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7, 30, 90, 365).forEach { days -> FilterChip(selected = settings.historyRetentionDays == days, onClick = { onAction(SettingsAction.HistoryRetention(days)) }, label = { UiText(if (days == 365) "1 year" else "$days days", fontSize = 11.sp) }, shape = RoundedCornerShape(9.dp)) }
                    }
                    OutlinedButton(onClick = { confirmClearHistory = true }, modifier = Modifier.fillMaxWidth().padding(top = 11.dp), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Filled.DeleteSweep, null, Modifier.size(16.dp)); UiText("  Clear history now") }
                }
            }
            item { SectionHeading("Storage & notifications") }
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.FolderOpen, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary); Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText("Save location", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText(if (settings.defaultFolderUri == null) "Not selected · choose a folder" else "Selected folder · system-managed access", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }; TextButton(onClick = onPickFolder) { UiText("Change", fontSize = 12.sp) } }
                    Divider(Modifier.padding(vertical = 11.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.NotificationsActive, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.secondary); Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText("Download notifications", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText("A notification when a file finishes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = settings.notificationsEnabled, onCheckedChange = { onAction(SettingsAction.Notifications(it)) }) }
                    Divider(Modifier.padding(vertical = 11.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Wifi, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.secondary); Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText("Wi-Fi only", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText("Wait for Wi-Fi before starting downloads", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = settings.wifiOnly, onCheckedChange = { onAction(SettingsAction.WifiOnly(it)) }) }
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(settingsContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        TextButton(onClick = {
                            runCatching {
                                settingsContext.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, settingsContext.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { UiText("Open notification settings", fontSize = 12.sp) }
                    }
                }
            }
            item { SectionHeading("Appearance") }
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText("Follow system theme", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText("Switch automatically with Android", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(checked = settings.followSystemTheme, onCheckedChange = { onAction(SettingsAction.FollowSystemTheme(it)) })
                    }
                    Divider(Modifier.padding(vertical = 9.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (settings.darkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText(if (settings.darkTheme) "Dark mode" else "Light mode", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText("Appearance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(checked = settings.darkTheme, enabled = !settings.followSystemTheme, onCheckedChange = { onAction(SettingsAction.Theme(it)) })
                    }
                    Divider(Modifier.padding(vertical = 9.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { UiText("Language", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp); UiText(if (settings.language == "fa") "Persian" else "English", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        FilterChip(selected = settings.language == "en", onClick = { onAction(SettingsAction.Language("en")) }, label = { UiText("English", fontSize = 12.sp) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = settings.language == "fa", onClick = { onAction(SettingsAction.Language("fa")) }, label = { UiText("فارسی", fontSize = 12.sp) })
                    }
                }
            }
            item { SectionHeading("Network & speed") }
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    UiText("Downloads use your device's normal network; Cyron does not require a VPN. If a site only works through a VPN, the restriction may be from the network or the source.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { SectionHeading("About Cyron") }
            item {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Image(painter = painterResource(R.drawable.cyron_logo), contentDescription = null, modifier = Modifier.size(37.dp).clip(RoundedCornerShape(10.dp))); Column(Modifier.padding(start = 10.dp).weight(1f)) { UiText("CYRON MANAGER", style = MaterialTheme.typography.titleMedium, letterSpacing = .8.sp); UiText("Open source · Local-first · Private", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; UiText("${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary) }
                    UiText("Native Kotlin + Jetpack Compose · OkHttp · Room · DataStore · Android Storage Access Framework", Modifier.padding(top = 11.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenLicenses, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { UiText("Third-party licenses", fontSize = 12.sp) }
                }
            }
            item { Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Verified, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary); UiText("  Files and settings stay on your device. No accounts or cloud services.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { UiText("Clear download history?") },
            text = { UiText("This removes completed, failed, and cancelled records. Downloaded files are not deleted.") },
            confirmButton = { Button(onClick = { confirmClearHistory = false; onClearHistory() }) { UiText("Clear history") } },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { UiText("Cancel") } }
        )
    }
}

@Composable
private fun DownloadDetailScreen(task: DownloadTask, snapshot: com.cyron.manager.domain.model.TransferSnapshot?, onBack: () -> Unit, onAction: (DownloadTask, DownloadAction) -> Unit, onRetry: (DownloadTask) -> Unit, onOpenOutput: (DownloadTask, String) -> Unit, onShareOutput: (DownloadTask, String) -> Unit) {
    val status = task.visibleStatus(snapshot)
    val progress = task.visibleProgress(snapshot)
    val bytes = snapshot?.downloadedBytes ?: task.downloadedBytes
    val speed = snapshot?.bytesPerSecond ?: 0L
    // A media stream only reports its real size while it runs, so the live snapshot total is
    // the only size this screen can trust before the file is published.
    val totalBytes = snapshot?.totalBytes?.takeIf { it > 0 } ?: task.totalBytes
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 19.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 13.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, localizeUiText("Back", LocalAppLanguage.current)) }
            Eyebrow("FILE DETAILS")
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { FileIcon(task.fileName, 52.dp); Column(Modifier.padding(start = 13.dp).weight(1f)) { UiText(task.fileName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis); UiText("${task.mimeType}  ·  ${numeric(formatBytes(totalBytes))}", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        item {
            val tone = when (status) { DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary; DownloadStatus.FAILED -> MaterialTheme.colorScheme.error; DownloadStatus.PUBLISHING, DownloadStatus.PROBING -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.primary }
            CyronCard(Modifier.fillMaxWidth(), glow = tone) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GaugeRing(fraction = progress, color = tone, modifier = Modifier.size(90.dp), stroke = 8.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            UiText("${(progress.coerceIn(0f,1f)*100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = tone)
                            UiText("PROGRESS", fontSize = 8.sp, letterSpacing = .9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        UiText(when(status) { DownloadStatus.COMPLETED -> "COMPLETED"; DownloadStatus.DOWNLOADING -> "DOWNLOADING"; DownloadStatus.PAUSED -> "PAUSED"; DownloadStatus.QUEUED -> "IN QUEUE"; DownloadStatus.FAILED -> "DOWNLOAD FAILED"; DownloadStatus.CANCELLED -> "CANCELLED"; DownloadStatus.PUBLISHING -> "SAVING FILE"; DownloadStatus.PROBING -> "CHECKING SERVER" }, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, fontSize = 11.sp, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        UiText(localized("${numeric(formatBytes(bytes))} of ${numeric(formatBytes(totalBytes))}", "${numeric(formatBytes(bytes))} از ${numeric(formatBytes(totalBytes))}"), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        UiText(numeric(if (status == DownloadStatus.DOWNLOADING && speed > 0) formatSpeed(speed) else "—"), Modifier.padding(top = 6.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Divider(Modifier.padding(vertical = 13.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailMetric("TRANSFER SPEED", if (status == DownloadStatus.DOWNLOADING && speed > 0) numeric(formatSpeed(speed)) else "—", Icons.Filled.Speed)
                    val remaining = snapshot?.remainingMillis
                    DetailMetric("TIME REMAINING", if (remaining != null && status == DownloadStatus.DOWNLOADING && speed > 0) formatDuration(remaining) else "—", Icons.Filled.History)
                    DetailMetric("CONNECTIONS", "${task.connectionCount}", Icons.Filled.RadioButtonChecked)
                }
            }
        }
        if (!task.error.isNullOrBlank() || !snapshot?.error.isNullOrBlank()) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = .10f)), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error); UiText(task.error ?: snapshot?.error.orEmpty(), Modifier.padding(start = 9.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                when(status) {
                    DownloadStatus.DOWNLOADING -> Button(onClick = { onAction(task, DownloadAction.Pause) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.Pause, null, Modifier.size(18.dp)); UiText("  Pause") }
                    DownloadStatus.PAUSED, DownloadStatus.QUEUED -> Button(onClick = { onAction(task, DownloadAction.Resume) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp)); UiText("  Resume") }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Button(onClick = { onRetry(task) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); UiText("  Retry") }
                    DownloadStatus.COMPLETED -> Button(onClick = { onAction(task, DownloadAction.Open) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(17.dp)); UiText("  Open file") }
                    else -> Unit
                }
                if (status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.QUEUED)) OutlinedButton(onClick = { onAction(task, DownloadAction.Cancel) }, Modifier.weight(.8f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.Stop, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); UiText("  Cancel", color = MaterialTheme.colorScheme.error) }
                if (status == DownloadStatus.COMPLETED) OutlinedButton(onClick = { onAction(task, DownloadAction.Share) }, Modifier.weight(.8f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); UiText("  Share") }
            }
        }
        if (status in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)) {
            item {
                OutlinedButton(onClick = { onAction(task, DownloadAction.ChangeFolder) }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(17.dp)); UiText("  Change destination")
                }
            }
        }
        if (status in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAction(task, DownloadAction.PriorityUp) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Filled.ArrowUpward, null, Modifier.size(16.dp)); UiText("  Higher priority", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onAction(task, DownloadAction.PriorityDown) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Filled.ArrowDownward, null, Modifier.size(16.dp)); UiText("  Lower priority", fontSize = 11.sp) }
                }
            }
        }
        val outputs = task.publishedUris.ifEmpty { listOfNotNull(task.savedUri).filter { task.source != DownloadSource.TORRENT } }
        if (status == DownloadStatus.COMPLETED && outputs.isNotEmpty()) {
            item {
                SectionHeading("Published files", localized("${outputs.size} output files", "${outputs.size} فایل خروجی"))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    outputs.forEach { output ->
                        val name = outputDisplayName(output)
                        CyronCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FileIcon(name, 34.dp)
                                UiText(name, Modifier.padding(start = 9.dp).weight(1f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onOpenOutput(task, output) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, localized("Open ${name}", "بازکردن $name"), tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { onShareOutput(task, output) }) { Icon(Icons.Filled.Share, localized("Share ${name}", "اشتراک‌گذاری $name"), tint = MaterialTheme.colorScheme.secondary) }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeading("Transfer information")
            SurfaceCard(Modifier.fillMaxWidth()) {
                DetailRow("Source", Uri.parse(task.url).host ?: task.url)
                DetailRow("Full URL", task.url)
                DetailRow("Saved name", task.fileName)
                DetailRow("Content type", task.mimeType)
                DetailRow("HTTP resume", if (task.rangeSupported) "Verified byte-range requests" else "Single connection fallback")
                DetailRow("SHA-256", task.actualSha256 ?: task.expectedSha256 ?: "Calculated after completion")
                DetailRow("Destination", destinationLabel(task.outputFolderUri ?: task.savedUri))
                DetailRow("Priority", task.priority.toString())
                DetailRow("Added", latinDigits(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, if (LocalAppLanguage.current == "fa") Locale("fa", "IR") else Locale.getDefault()).format(Date(task.createdAt))))
                if (status == DownloadStatus.COMPLETED) DetailRow("Completion date", latinDigits(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, if (LocalAppLanguage.current == "fa") Locale("fa", "IR") else Locale.getDefault()).format(Date(task.updatedAt))))
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); UiText(label, Modifier.padding(top = 6.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = .4.sp); UiText(numeric(value), Modifier.padding(top = 3.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable
private fun DetailRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { UiText(label, Modifier.width(112.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); UiText(numeric(value), Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface) } }

private fun outputDisplayName(uri: String): String {
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return uri
    val documentId = runCatching { DocumentsContract.getDocumentId(parsed) }.getOrNull()
    return documentId?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: parsed.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: taskFallbackName(uri)
}

private fun taskFallbackName(uri: String): String = uri.substringAfterLast('/').ifBlank { "output" }

private fun destinationLabel(uri: String?): String {
    if (uri.isNullOrBlank()) return "Not selected"
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return uri
    return runCatching { DocumentsContract.getTreeDocumentId(parsed) }.getOrNull() ?: parsed.lastPathSegment ?: uri
}

@Composable
private fun EmptyState(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: (() -> Unit)? = null) {
    CyronCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .26f), Color.Transparent))), contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            UiText(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            UiText(description, Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (action != null) Button(onClick = action, Modifier.padding(top = 12.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.Add, null, Modifier.size(17.dp)); UiText("  Add a download") }
        }
    }
}

@Composable
private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val raw = if (bytes < 1024) "$bytes B" else {
        val units = listOf("KB", "MB", "GB", "TB")
        var size = bytes / 1024.0
        var i = 0
        while (size >= 1024 && i < units.lastIndex) { size /= 1024; i++ }
        "${DecimalFormat("#,##0.#").format(size)} ${units[i]}"
    }
    return raw
}

@Composable
private fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

/**
 * Values such as "12 MB" or "1.2 MB/s" are Latin runs; inside an RTL paragraph the bidi
 * algorithm would otherwise flip them to "MB 12". Persian sentences are left untouched.
 */
private fun numeric(text: String): String = if (text.any { it in '\u0600'..'\u06FF' }) text else isolateLtr(text)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
@Composable
private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (LocalAppLanguage.current == "fa") {
        if (hours > 0) "$hours ساعت $minutes دقیقه"
        else "$minutes دقیقه $remainingSeconds ثانیه"
    } else {
        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${remainingSeconds}s"
    }
}
