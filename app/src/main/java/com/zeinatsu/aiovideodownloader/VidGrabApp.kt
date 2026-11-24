package com.zeinatsu.aiovideodownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

// Theme Colors
val PrimaryBlue = Color(0xFF2563EB)
val SuccessGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)

val LightBg = Color(0xFFF1F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceSubtle = Color(0xFFF8FAFC)
val LightBorder = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)

val DarkBg = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceSubtle = Color(0xFF334155)
val DarkBorder = Color(0xFF334155)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)

data class AppThemeColors(
    val bg: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean
)

val LocalAppColors = compositionLocalOf {
    AppThemeColors(LightBg, LightSurface, LightSurfaceSubtle, LightBorder, LightTextPrimary, LightTextSecondary, false)
}

enum class NavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    FILES("Files", Icons.Outlined.Folder),
    ALERTS("Alerts", Icons.Outlined.Notifications),
    ACCOUNT("Account", Icons.Outlined.Person)
}

@Composable
fun VidGrabApp(viewModel: DownloaderViewModel) {
    var isDarkMode by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(NavTab.HOME) }

    val storagePath by viewModel.storagePath.collectAsState()
    val advancedOptions by viewModel.advancedOptions.collectAsState()

    var showStorageModal by remember { mutableStateOf(false) }
    var showAdvancedOptionsModal by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }

    val themeColors = if (isDarkMode) {
        AppThemeColors(DarkBg, DarkSurface, DarkSurfaceSubtle, DarkBorder, DarkTextPrimary, DarkTextSecondary, true)
    } else {
        AppThemeColors(LightBg, LightSurface, LightSurfaceSubtle, LightBorder, LightTextPrimary, LightTextSecondary, false)
    }

    CompositionLocalProvider(LocalAppColors provides themeColors) {
        Scaffold(
            containerColor = themeColors.bg,
            topBar = {
                VidGrabHeader(
                    isDark = isDarkMode,
                    onToggleDark = { isDarkMode = !isDarkMode }
                )
            },
            bottomBar = {
                VidGrabBottomNav(
                    currentTab = currentTab,
                    onTabSelected = {
                        currentTab = it
                        if (it == NavTab.FILES) viewModel.refreshFilesList()
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    NavTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        storagePath = storagePath,
                        onOpenStorageModal = { showStorageModal = true },
                        onOpenAdvancedOptions = { showAdvancedOptionsModal = true }
                    )
                    NavTab.FILES -> FilesHistoryScreen(viewModel = viewModel)
                    NavTab.ALERTS -> AlertsScreen(viewModel = viewModel)
                    NavTab.ACCOUNT -> AccountSettingsScreen(
                        viewModel = viewModel,
                        storagePath = storagePath,
                        advancedOptions = advancedOptions,
                        onOpenStorageModal = { showStorageModal = true },
                        onOpenDonate = { showDonateDialog = true }
                    )
                }

                if (showStorageModal) {
                    StoragePickerModal(
                        currentPath = storagePath,
                        onDismiss = { showStorageModal = false },
                        onSavePath = {
                            viewModel.updateStoragePath(it)
                            showStorageModal = false
                        }
                    )
                }

                if (showAdvancedOptionsModal) {
                    AdvancedFormatOptionsModal(
                        currentOptions = advancedOptions,
                        onApply = {
                            viewModel.updateAdvancedOptions(it)
                            showAdvancedOptionsModal = false
                        },
                        onDismiss = { showAdvancedOptionsModal = false }
                    )
                }

                if (showDonateDialog) {
                    DonateDialog(onDismiss = { showDonateDialog = false })
                }
            }
        }
    }
}

@Composable
fun VidGrabHeader(isDark: Boolean, onToggleDark: () -> Unit) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("VidGrab", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("Fast Link Downloader", fontSize = 12.sp, color = colors.textSecondary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(SuccessGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Online 4K", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                }
            }

            IconButton(
                onClick = onToggleDark,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CircleShape)
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "Toggle Dark Mode",
                    tint = if (isDark) WarningAmber else colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: DownloaderViewModel,
    storagePath: String,
    onOpenStorageModal: () -> Unit,
    onOpenAdvancedOptions: () -> Unit
) {
    val colors = LocalAppColors.current
    val clipboardManager = LocalClipboardManager.current
    val urlText by viewModel.urlInput.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var selectedQualityIndex by remember { mutableIntStateOf(0) }

    val qualityMeta = listOf(
        Pair("Video", "FHD"),
        Pair("Cepat", "HD"),
        Pair("Hemat kuota", "SD"),
        Pair("Suara jernih", "Audio")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFFDBEAFE), Color(0xFFEFF6FF)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(26.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tempel tautan video di sini", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mendukung YouTube, TikTok, Instagram & Facebook hingga resolusi 1080p / 4K.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceSubtle,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (urlText.isEmpty()) {
                                    Text("https://...", fontSize = 13.sp, color = colors.textSecondary)
                                }
                                BasicTextField(
                                    value = urlText,
                                    onValueChange = { viewModel.onUrlChanged(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = colors.textPrimary),
                                    cursorBrush = SolidColor(PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (urlText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.onUrlChanged("")
                                        viewModel.resetState()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Button(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { pasted ->
                                        viewModel.handleSharedText(pasted)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surface),
                                border = BorderStroke(1.dp, colors.border),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                elevation = ButtonDefaults.buttonElevation(0.dp)
                            ) {
                                Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.analyzeUrl() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = uiState !is DownloadUiState.FetchingInfo && uiState !is DownloadUiState.Downloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cek Video", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        when (val state = uiState) {
            is DownloadUiState.Idle -> {}

            is DownloadUiState.FetchingInfo -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp), color = PrimaryBlue, strokeWidth = 3.dp)
                            Column {
                                Text("Menganalisis tautan...", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                                Text("Mengambil thumbnail & daftar resolusi", fontSize = 12.sp, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            is DownloadUiState.InfoReady -> {
                val details = state.details
                val platformInfo = detectPlatform(details.url)

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 92.dp, height = 74.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1E1B4B)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!details.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = details.thumbnailUrl,
                                        contentDescription = "Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(details.durationText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Surface(shape = RoundedCornerShape(6.dp), color = platformInfo.second.copy(alpha = 0.14f)) {
                                    Text(
                                        text = platformInfo.first,
                                        color = platformInfo.second,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = details.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@${details.uploader}",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PILIH KUALITAS FILE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        TextButton(onClick = onOpenAdvancedOptions, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Opsi Lanjutan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                        }
                    }
                }

                item {
                    val formats = details.formats
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (rowIndex in 0 until formats.size step 2) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (colIndex in 0..1) {
                                    val idx = rowIndex + colIndex
                                    if (idx < formats.size) {
                                        val item = formats[idx]
                                        val meta = qualityMeta.getOrElse(idx) { Pair("MP4", "Auto") }
                                        ResolutionCard(
                                            label = item.label,
                                            formatExt = if (item.isAudioOnly) "M4A" else "MP4",
                                            note = meta.first,
                                            badge = meta.second,
                                            isSelected = selectedQualityIndex == idx,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedQualityIndex = idx }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.resetState() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        }

                        Button(
                            onClick = {
                                val chosenFormat = details.formats.getOrElse(selectedQualityIndex) { details.formats.first() }
                                viewModel.startDownload(details, chosenFormat)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Text("Download", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            is DownloadUiState.Downloading -> {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (state.isPaused) WarningAmber.copy(alpha = 0.15f) else Color(0xFFDBEAFE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (state.isPaused) Icons.Filled.Pause else Icons.Filled.Videocam,
                                        contentDescription = null,
                                        tint = if (state.isPaused) WarningAmber else PrimaryBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.details.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = colors.textPrimary
                                    )
                                    Row {
                                        Text(
                                            text = if (state.isPaused) "Dijeda • ${state.progress.toInt()}%" else "${state.progress.toInt()}% • ",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (state.isPaused) WarningAmber else PrimaryBlue
                                        )
                                        if (!state.isPaused && state.etaSeconds > 0) {
                                            Text("${state.etaSeconds} sec left", fontSize = 12.sp, color = colors.textSecondary)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (state.isPaused) WarningAmber else PrimaryBlue,
                                trackColor = colors.surfaceSubtle
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.statusLine,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelCurrentDownload() },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Batal", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                                }

                                Button(
                                    onClick = {
                                        if (state.isPaused) viewModel.resumeCurrentDownload()
                                        else viewModel.pauseCurrentDownload()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isPaused) SuccessGreen else WarningAmber
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (state.isPaused) "Lanjut" else "Jeda",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            is DownloadUiState.Success -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Unduhan Selesai!", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.textPrimary)
                                Text(state.filePath, fontSize = 11.sp, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            is DownloadUiState.Error -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DangerRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Terjadi Kesalahan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DangerRed)
                                Text(state.message, fontSize = 11.sp, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Penyimpanan: ", fontSize = 12.sp, color = colors.textSecondary)
                    Text(
                        text = storagePath,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.textPrimary
                    )
                }
                TextButton(onClick = onOpenStorageModal, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("Ubah", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

private fun detectPlatform(url: String): Pair<String, Color> {
    val lower = url.lowercase()
    return when {
        lower.contains("youtube.com") || lower.contains("youtu.be") -> Pair("YouTube", Color(0xFFE11D48))
        lower.contains("tiktok.com") -> Pair("TikTok", Color(0xFF06B6D4))
        lower.contains("instagram.com") -> Pair("Instagram", Color(0xFFD946EF))
        lower.contains("facebook.com") || lower.contains("fb.watch") -> Pair("Facebook", Color(0xFF2563EB))
        else -> Pair("Web Video", PrimaryBlue)
    }
}

@Composable
fun ResolutionCard(
    label: String,
    formatExt: String,
    note: String,
    badge: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else colors.border
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isSelected) PrimaryBlue else colors.textPrimary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) PrimaryBlue else colors.surfaceSubtle
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("$formatExt • $note", fontSize = 11.sp, color = colors.textSecondary)
        }
    }
}

@Composable
fun FilesHistoryScreen(viewModel: DownloaderViewModel) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val files by viewModel.downloadedFiles.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Riwayat Unduhan (${files.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (files.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Belum ada file terunduh di folder ini", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        items(files) { item ->
            var showMenu by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .clickable { openMediaFile(context, item.file) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (item.format == "M4A") Color(0xFFFEF3C7) else Color(0xFFDBEAFE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.format == "M4A") Icons.Filled.Audiotrack else Icons.Filled.Movie,
                            contentDescription = null,
                            tint = if (item.format == "M4A") WarningAmber else PrimaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${item.format} • ${item.sizeText} • ${item.dateText}", fontSize = 11.sp, color = colors.textSecondary)
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Putar") },
                                onClick = {
                                    showMenu = false
                                    openMediaFile(context, item.file)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bagikan") },
                                onClick = {
                                    showMenu = false
                                    shareMediaFile(context, item.file)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus", color = DangerRed) },
                                onClick = {
                                    showMenu = false
                                    viewModel.deleteFile(item.file)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openMediaFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mime = if (file.extension.equals("m4a", true)) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Putar dengan"))
    } catch (e: Exception) {
        Toast.makeText(context, "Tidak ada aplikasi pemutar media", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMediaFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mime = if (file.extension.equals("m4a", true)) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membagikan file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AccountSettingsScreen(
    viewModel: DownloaderViewModel,
    storagePath: String,
    advancedOptions: AdvancedDownloadOptions,
    onOpenStorageModal: () -> Unit,
    onOpenDonate: () -> Unit
) {
    val colors = LocalAppColors.current
    val updateStatus by viewModel.engineUpdateStatus.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("UNDUHAN & JARINGAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unduh hanya lewat Wi-Fi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.textPrimary)
                            Text("Mencegah kuota data seluler terpakai", fontSize = 11.sp, color = colors.textSecondary)
                        }
                        Switch(
                            checked = advancedOptions.wifiOnly,
                            onCheckedChange = { viewModel.updateAdvancedOptions(advancedOptions.copy(wifiOnly = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.border)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenStorageModal() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Folder Penyimpanan Bawaan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.textPrimary)
                            Text(storagePath, fontSize = 11.sp, color = PrimaryBlue)
                        }
                        Text("Ubah", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Simpan Subtitle Otomatis", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.textPrimary)
                            Text("Unduh file .srt jika tersedia", fontSize = 11.sp, color = colors.textSecondary)
                        }
                        Switch(
                            checked = advancedOptions.includeSubtitles,
                            onCheckedChange = { viewModel.updateAdvancedOptions(advancedOptions.copy(includeSubtitles = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }
                }
            }
        }

        item {
            Text("DUKUNGAN & AUTHOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFE4E6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Donate untuk Author", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.textPrimary)
                        Text("Dukung pengembangan aplikasi agar tetap gratis & tanpa iklan.", fontSize = 11.sp, color = colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenDonate,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Donasi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("TENTANG APLIKASI & ENGINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Core Engine (yt-dlp)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text(
                                text = updateStatus ?: "Versi: ${viewModel.getEngineVersion()}",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                        Button(
                            onClick = { viewModel.updateYtDlpEngine() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Versi Aplikasi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceSubtle) {
                            Text(
                                text = "v1.0.0 (yt-dlp Native)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsScreen(viewModel: DownloaderViewModel) {
    val colors = LocalAppColors.current
    val alerts by viewModel.alerts.collectAsState()

    if (alerts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tidak ada notifikasi baru", fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(alerts) { alert ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (alert.isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (alert.isError) DangerRed else SuccessGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(alert.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.textPrimary)
                            Text(alert.message, fontSize = 11.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoragePickerModal(
    currentPath: String,
    onDismiss: () -> Unit,
    onSavePath: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState()
    var selectedPath by remember { mutableStateOf(currentPath) }
    var customPathInput by remember { mutableStateOf("") }

    val presetPaths = listOf(
        "/Download/VidGrab/",
        "/Movies/VidGrab/",
        "/Documents/VidGrab/"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Ubah Folder Penyimpanan", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(14.dp))

            presetPaths.forEach { path ->
                val isChosen = selectedPath == path
                Surface(
                    onClick = {
                        selectedPath = path
                        customPathInput = ""
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isChosen) PrimaryBlue.copy(alpha = 0.08f) else colors.surfaceSubtle,
                    border = BorderStroke(1.dp, if (isChosen) PrimaryBlue else colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = if (isChosen) PrimaryBlue else colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(path, fontSize = 13.sp, fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (isChosen) Icon(Icons.Filled.Check, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customPathInput,
                onValueChange = {
                    customPathInput = it
                    if (it.isNotBlank()) selectedPath = it
                },
                placeholder = { Text("/Download/FolderCustom/") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSavePath(selectedPath) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Simpan Lokasi", color = Color.White)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFormatOptionsModal(
    currentOptions: AdvancedDownloadOptions,
    onApply: (AdvancedDownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    var selectedCodec by remember { mutableStateOf(currentOptions.codec) }
    var selectedAudioBitrate by remember { mutableStateOf(currentOptions.audioBitrate) }
    var includeSubtitles by remember { mutableStateOf(currentOptions.includeSubtitles) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Opsi Format Lanjutan", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(14.dp))

            Text("Video Codec", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("H.264", "VP9", "AV1").forEach { codec ->
                    FilterChip(selected = selectedCodec == codec, onClick = { selectedCodec = codec }, label = { Text(codec) })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Bitrate Audio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("320 kbps", "256 kbps", "128 kbps").forEach { bitrate ->
                    FilterChip(selected = selectedAudioBitrate == bitrate, onClick = { selectedAudioBitrate = bitrate }, label = { Text(bitrate) })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sertakan Subtitle (.srt)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Switch(checked = includeSubtitles, onCheckedChange = { includeSubtitles = it })
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    onApply(currentOptions.copy(codec = selectedCodec, audioBitrate = selectedAudioBitrate, includeSubtitles = includeSubtitles))
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Terapkan Pengaturan", color = Color.White)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Ganti dengan link Saweria / Trakteer kamu
    val donateUrl = "https://saweria.co/zeinatsu"
    // Ganti dengan nomor DANA kamu
    val danaNumber = "082258498837"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE11D48)) },
        title = { Text("Dukung Developer VidGrab", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Terima kasih telah menggunakan VidGrab! Dukungan Anda membantu pengembangan aplikasi agar tetap gratis & tanpa iklan.",
                    fontSize = 13.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF008CEB).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF008CEB).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Transfer Langsung DANA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF008CEB))
                            Text(danaNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(danaNumber))
                                Toast.makeText(context, "Nomor DANA disalin!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Salin", fontWeight = FontWeight.Bold, color = Color(0xFF008CEB))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Tidak dapat membuka browser", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
            ) {
                Text("Via Saweria / QRIS", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
fun VidGrabBottomNav(currentTab: NavTab, onTabSelected: (NavTab) -> Unit) {
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Filled.Home, "Home", currentTab == NavTab.HOME) { onTabSelected(NavTab.HOME) }
            BottomNavItem(Icons.Filled.Folder, "Files", currentTab == NavTab.FILES) { onTabSelected(NavTab.FILES) }

            FloatingActionButton(
                onClick = { onTabSelected(NavTab.HOME) },
                shape = CircleShape,
                containerColor = colors.surface,
                contentColor = PrimaryBlue,
                elevation = FloatingActionButtonDefaults.elevation(3.dp),
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, colors.border, CircleShape)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Download")
            }

            BottomNavItem(Icons.Filled.Notifications, "Alerts", currentTab == NavTab.ALERTS) { onTabSelected(NavTab.ALERTS) }
            BottomNavItem(Icons.Filled.Person, "Account", currentTab == NavTab.ACCOUNT) { onTabSelected(NavTab.ACCOUNT) }
        }
    }
}

@Composable
fun BottomNavItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val itemColor = if (isSelected) PrimaryBlue else colors.textSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = itemColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = itemColor)
    }
}
