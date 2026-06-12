package com.k3i.rayban_00

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.k3i.rayban_00.ui.theme.RayBan_00Theme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var appThemeMode by remember { mutableStateOf(loadAppThemeMode(context)) }
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.System -> systemDark
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            LaunchedEffect(appThemeMode) {
                saveAppThemeMode(context, appThemeMode)
            }
            RayBan_00Theme(darkTheme = useDarkTheme, dynamicColor = false) {
                ConcertExperienceApp(
                    appThemeMode = appThemeMode,
                    onThemeModeChange = { appThemeMode = it }
                )
            }
        }
    }
}

@Composable
fun ConcertExperienceApp(
    appThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedSession = remember { loadConcertSession(context) }
    val concertRepositoryResult = remember {
        LocalAssetConcertRepository(context).loadConcertPackages()
    }
    val concertPackageReport = concertRepositoryResult.packageReport
    val concertEvents = remember(concertPackageReport) {
        concertPackageReport.events
    }
    val glassesProfile = remember { defaultGlassesIntegrationProfile() }
    var boardPostsByEventId by remember(concertEvents) { mutableStateOf(defaultConcertBoardPostsByEventId(concertEvents)) }
    var selectedBoardEventId by remember { mutableStateOf<String?>(null) }
    var selectedBoardPostId by remember { mutableStateOf<String?>(null) }
    var appLanguage by remember { mutableStateOf(loadAppLanguage(context)) }
    var screen by remember { mutableStateOf(savedSession.screen) }
    var concertState by remember { mutableStateOf(savedSession.state.withEventCatalog(concertEvents)) }
    var micRequested by remember { mutableStateOf(false) }
    var micPermissionDenied by remember { mutableStateOf(false) }
    var appInForeground by remember { mutableStateOf(true) }
    var companionResumeNoticeVisible by remember { mutableStateOf(false) }
    var glassesDispatchRecords by remember { mutableStateOf(loadGlassesDispatchRecords(context)) }
    var settingsReturnScreen by remember { mutableStateOf(AppScreen.Home) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micRequested = granted
        micPermissionDenied = !granted
        if (!granted) concertState = concertState.stopAudioEnergy()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> appInForeground = true
                Lifecycle.Event.ON_STOP -> {
                    if (screen == AppScreen.Companion) {
                        companionResumeNoticeVisible = true
                    }
                    appInForeground = false
                    micRequested = false
                    concertState = concertState.pauseForBackground()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(screen, appInForeground) {
        while (screen == AppScreen.Companion && appInForeground) {
            delay(1000)
            concertState = concertState.tick()
        }
    }

    LaunchedEffect(screen, concertState) {
        saveConcertSession(context, screen, concertState)
    }

    LaunchedEffect(appLanguage) {
        saveAppLanguage(context, appLanguage)
    }

    if (screen == AppScreen.Companion && appInForeground && micRequested) {
        AudioEnergyEffect(
            onEnergy = { level -> concertState = concertState.applyAudioEnergy(level) }
        )
    }

    val canStartCompanion = concertState.event.ticket.checkedIn
    val backAction: (() -> Unit)? = when (screen) {
        AppScreen.BoardEventPosts -> ({ screen = AppScreen.Board })
        AppScreen.BoardPostDetail -> ({ screen = AppScreen.BoardEventPosts })
        AppScreen.Settings -> ({ screen = settingsReturnScreen })
        AppScreen.SettingsConcert,
        AppScreen.SettingsLanguage,
        AppScreen.SettingsAppearance,
        AppScreen.SettingsOperations,
        AppScreen.SettingsTechnical -> ({ screen = AppScreen.Settings })
        else -> null
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ConcertBottomNavigation(
                currentScreen = screen,
                language = appLanguage,
                canStartCompanion = canStartCompanion,
                onNavigate = { destination ->
                    when (destination) {
                        AppScreen.Home -> screen = AppScreen.Home
                        AppScreen.Readiness -> screen = AppScreen.Detail
                        AppScreen.Detail -> screen = AppScreen.Detail
                        AppScreen.Translation -> screen = AppScreen.Translation
                        AppScreen.Companion -> screen = if (canStartCompanion) AppScreen.Companion else AppScreen.Detail
                        AppScreen.Board -> {
                            if (concertState.event.boardAccessPolicy().canEnter) {
                                selectedBoardEventId = concertState.event.id
                                selectedBoardPostId = null
                                screen = AppScreen.BoardEventPosts
                            } else {
                                screen = AppScreen.Board
                            }
                        }
                        AppScreen.BoardEventPosts -> screen = AppScreen.Board
                        AppScreen.BoardPostDetail -> screen = AppScreen.Board
                        AppScreen.Settings -> screen = AppScreen.Settings
                        AppScreen.SettingsConcert -> screen = AppScreen.SettingsConcert
                        AppScreen.SettingsLanguage -> screen = AppScreen.SettingsLanguage
                        AppScreen.SettingsAppearance -> screen = AppScreen.SettingsAppearance
                        AppScreen.SettingsOperations -> screen = AppScreen.SettingsOperations
                        AppScreen.SettingsTechnical -> screen = AppScreen.SettingsTechnical
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(innerPadding)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when (screen) {
                    AppScreen.Home -> HomeScreen(
                        state = concertState,
                        glassesProfile = glassesProfile,
                        onReaction = { reaction -> concertState = concertState.applyReaction(reaction) },
                        onInteractionEvent = { event -> concertState = concertState.applyInteractionEvent(event) }
                    )

                    AppScreen.Readiness -> ReadinessScreen(
                        state = concertState,
                        glassesProfile = glassesProfile,
                        language = appLanguage,
                        micPermissionDenied = micPermissionDenied,
                        onOpenDetail = { screen = AppScreen.Detail }
                    )

                    AppScreen.Detail -> DetailScreen(
                        state = concertState,
                        glassesProfile = glassesProfile,
                        language = appLanguage,
                        onStart = {
                            if (concertState.event.ticket.checkedIn) {
                                screen = AppScreen.Companion
                            }
                        }
                    )

                    AppScreen.Translation -> TranslationScreen(
                        state = concertState
                    )

                    AppScreen.Companion -> CompanionScreen(
                        state = concertState,
                        glassesProfile = glassesProfile,
                        micPermissionDenied = micPermissionDenied,
                        appInForeground = appInForeground,
                        resumeNoticeVisible = companionResumeNoticeVisible,
                        glassesDispatchRecords = glassesDispatchRecords,
                        onReaction = { reaction -> concertState = concertState.applyReaction(reaction) },
                        onInteractionEvent = { event -> concertState = concertState.applyInteractionEvent(event) },
                        onDispatchHud = { instruction ->
                            val updatedRecords = dispatchHudToGlasses(
                                instruction = instruction,
                                profile = glassesProfile,
                                previousRecords = glassesDispatchRecords
                            ).records
                            glassesDispatchRecords = updatedRecords
                            saveGlassesDispatchRecords(context, updatedRecords)
                        },
                        onDismissResumeNotice = { companionResumeNoticeVisible = false },
                        onToggleMic = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (micRequested) {
                                micRequested = false
                                concertState = concertState.stopAudioEnergy()
                            } else if (hasPermission) {
                                micRequested = true
                                micPermissionDenied = false
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onFinish = {
                            micRequested = false
                            concertState = concertState.stopAudioEnergy()
                            screen = AppScreen.Home
                        }
                    )

                    AppScreen.Board -> BoardConcertSelectorScreen(
                        events = concertEvents,
                        selectedEventId = selectedBoardEventId,
                        language = appLanguage,
                        onOpenEventBoard = { event ->
                            if (event.boardAccessPolicy().canEnter) {
                                selectedBoardEventId = event.id
                                selectedBoardPostId = null
                                screen = AppScreen.BoardEventPosts
                            }
                        }
                    )

                    AppScreen.BoardEventPosts -> {
                        val boardEvent = concertEvents.firstOrNull { it.id == selectedBoardEventId }
                        if (boardEvent == null || !boardEvent.boardAccessPolicy().canEnter) {
                            screen = AppScreen.Board
                        } else {
                            ConcertBoardScreen(
                                eventTitle = boardEvent.title,
                                posts = boardPostsByEventId[boardEvent.id].orEmpty(),
                                language = appLanguage,
                                onAddPost = { post ->
                                    val currentPosts = boardPostsByEventId[boardEvent.id].orEmpty()
                                    boardPostsByEventId = boardPostsByEventId + (boardEvent.id to (listOf(post) + currentPosts))
                                    selectedBoardPostId = post.id
                                    screen = AppScreen.BoardPostDetail
                                },
                                onOpenPost = { post ->
                                    selectedBoardPostId = post.id
                                    screen = AppScreen.BoardPostDetail
                                }
                            )
                        }
                    }

                    AppScreen.BoardPostDetail -> {
                        val boardEvent = concertEvents.firstOrNull { it.id == selectedBoardEventId }
                        val currentEventPosts = boardPostsByEventId[boardEvent?.id].orEmpty()
                        val selectedPost = currentEventPosts.firstOrNull { it.id == selectedBoardPostId }
                        if (boardEvent == null || !boardEvent.boardAccessPolicy().canEnter || selectedPost == null) {
                            screen = AppScreen.Board
                        } else {
                            BoardPostDetailScreen(
                                post = selectedPost,
                                eventTitle = boardEvent.title,
                                language = appLanguage,
                                onBack = { screen = AppScreen.BoardEventPosts },
                                onAddComment = { postId, comment ->
                                    boardPostsByEventId = boardPostsByEventId + (
                                        boardEvent.id to boardPostsByEventId[boardEvent.id].orEmpty().map { post ->
                                            if (post.id == postId) post.copy(comments = post.comments + comment) else post
                                        }
                                    )
                                }
                            )
                        }
                    }

                    AppScreen.Settings -> SettingsScreen(
                        language = appLanguage,
                        themeMode = appThemeMode,
                        onOpenConcert = { screen = AppScreen.SettingsConcert },
                        onOpenLanguage = { screen = AppScreen.SettingsLanguage },
                        onOpenAppearance = { screen = AppScreen.SettingsAppearance }
                    )

                    AppScreen.SettingsConcert -> ConcertSettingsScreen(
                        state = concertState,
                        events = concertEvents,
                        language = appLanguage,
                        onSelectEvent = { event ->
                            concertState = concertState.selectEvent(event)
                            screen = AppScreen.Detail
                        },
                        onBack = { screen = AppScreen.Settings }
                    )

                    AppScreen.SettingsLanguage -> LanguageSettingsScreen(
                        language = appLanguage,
                        onLanguageChange = { appLanguage = it },
                        onBack = { screen = AppScreen.Settings }
                    )

                    AppScreen.SettingsAppearance -> AppearanceSettingsScreen(
                        language = appLanguage,
                        themeMode = appThemeMode,
                        onThemeModeChange = onThemeModeChange,
                        onBack = { screen = AppScreen.Settings }
                    )

                    AppScreen.SettingsOperations -> OperationsSettingsScreen(
                        state = concertState,
                        language = appLanguage,
                        onBack = { screen = AppScreen.Settings }
                    )

                    AppScreen.SettingsTechnical -> TechnicalSettingsScreen(
                        state = concertState,
                        glassesProfile = glassesProfile,
                        repositoryResult = concertRepositoryResult,
                        micPermissionDenied = micPermissionDenied,
                        appInForeground = appInForeground,
                        language = appLanguage,
                        onToggleMic = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (micRequested) {
                                micRequested = false
                                concertState = concertState.stopAudioEnergy()
                            } else if (hasPermission) {
                                micRequested = true
                                micPermissionDenied = false
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onBack = { screen = AppScreen.Settings }
                    )
                }
            }
            if (!screen.isSettingsScreen()) {
                GlobalSettingsButton(
                    onClick = {
                        settingsReturnScreen = screen
                        screen = AppScreen.Settings
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                )
            }
            if (backAction != null) {
                GlobalBackButton(
                    onClick = backAction,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 10.dp, start = 10.dp)
                )
            }
        }
    }
}

private fun AppScreen.isSettingsScreen(): Boolean =
    this == AppScreen.Settings ||
        this == AppScreen.SettingsConcert ||
        this == AppScreen.SettingsLanguage ||
        this == AppScreen.SettingsAppearance ||
        this == AppScreen.SettingsOperations ||
        this == AppScreen.SettingsTechnical

private data class BottomNavItem(
    val screen: AppScreen,
    val label: String,
    val icon: String,
    val activeIcon: String
)

private val bottomNavItems = listOf(
    BottomNavItem(AppScreen.Detail, "준비", "◇", "✦"),
    BottomNavItem(AppScreen.Translation, "번역", "T", "T"),
    BottomNavItem(AppScreen.Home, "홈", "⌂", "◆"),
    BottomNavItem(AppScreen.Companion, "AR Live", "♡", "♥"),
    BottomNavItem(AppScreen.Board, "게시판", "□", "■")
)

@Composable
private fun GlobalSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xCC050607),
            contentColor = Color.White
        )
    ) {
        Text(
            text = "⚙",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun GlobalBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xCC050607),
            contentColor = Color.White
        )
    ) {
        Text(
            text = "‹",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ConcertBottomNavigation(
    currentScreen: AppScreen,
    language: AppLanguage,
    canStartCompanion: Boolean,
    onNavigate: (AppScreen) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val selected = item.screen == currentScreen.bottomNavRoot()
                val available = item.screen != AppScreen.Companion || canStartCompanion
                val accent = when (item.screen) {
                    AppScreen.Home -> Color(0xFF62D6C4)
                    AppScreen.Readiness -> Color(0xFFFFD166)
                    AppScreen.Detail -> Color(0xFFE85D75)
                    AppScreen.Translation -> Color(0xFF8AB4F8)
                    AppScreen.Companion -> Color(0xFF8AB4F8)
                    AppScreen.Board -> Color(0xFFB794F4)
                    AppScreen.BoardEventPosts -> Color(0xFFB794F4)
                    AppScreen.BoardPostDetail -> Color(0xFFB794F4)
                    AppScreen.Settings -> Color(0xFF9CA3AF)
                    AppScreen.SettingsConcert -> Color(0xFF9CA3AF)
                    AppScreen.SettingsLanguage -> Color(0xFF9CA3AF)
                    AppScreen.SettingsAppearance -> Color(0xFF9CA3AF)
                    AppScreen.SettingsOperations -> Color(0xFF9CA3AF)
                    AppScreen.SettingsTechnical -> Color(0xFF9CA3AF)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                selected -> accent.copy(alpha = 0.22f)
                                !available -> Color(0xFF171A20)
                                else -> Color(0xFF1D2027)
                            }
                        )
                        .clickable { onNavigate(item.screen) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        if (selected) item.activeIcon else item.icon,
                        color = when {
                            selected -> accent
                            !available -> Color(0xFF7A828E)
                            else -> Color(0xFFE5E7EB)
                        },
                        fontSize = if (selected) 20.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        item.label(language),
                        color = when {
                            selected -> Color.White
                            !available -> Color(0xFF7A828E)
                            else -> Color(0xFFE5E7EB)
                        },
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun AppScreen.bottomNavRoot(): AppScreen =
    when (this) {
        AppScreen.Readiness -> AppScreen.Detail
        AppScreen.BoardEventPosts,
        AppScreen.BoardPostDetail -> AppScreen.Board
        else -> this
    }

private fun BottomNavItem.label(language: AppLanguage): String =
    when (language) {
        AppLanguage.Korean -> label
        AppLanguage.English -> when (screen) {
            AppScreen.Home -> "Home"
            AppScreen.Readiness -> "Check"
            AppScreen.Detail -> "Prep"
            AppScreen.Translation -> "Translate"
            AppScreen.Companion -> "AR Live"
            AppScreen.Board -> "Board"
            AppScreen.BoardEventPosts -> "Board"
            AppScreen.BoardPostDetail -> "Board"
            AppScreen.Settings -> "Settings"
            AppScreen.SettingsConcert -> "Settings"
            AppScreen.SettingsLanguage -> "Settings"
            AppScreen.SettingsAppearance -> "Settings"
            AppScreen.SettingsOperations -> "Settings"
            AppScreen.SettingsTechnical -> "Settings"
        }
    }

@Composable
private fun DetailScreen(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile,
    language: AppLanguage,
    onStart: () -> Unit
) {
    ScreenFrame {
        VisualEventHeader(
            title = if (language == AppLanguage.Korean) "공연 준비" else "Show Prep",
            subtitle = state.event.title,
            badge = if (state.event.ticket.checkedIn) {
                if (language == AppLanguage.Korean) "입장 완료" else "Checked in"
            } else {
                if (language == AppLanguage.Korean) "입장 대기" else "Check-in needed"
            }
        )
        PrepQuickStatusCard(state = state, glassesProfile = glassesProfile, language = language)
        AudiencePrepSection(
            state = state
        )
        if (!state.event.ticket.checkedIn) {
            WarningCard("티켓 입장 확인이 필요합니다", "예매처 또는 공연장 체크인이 완료되어야 AR Live를 사용할 수 있습니다.")
        }
        Button(
            onClick = onStart,
            enabled = state.event.ticket.checkedIn,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE85D75),
                disabledContainerColor = Color(0xFF4B3A1F),
                disabledContentColor = Color(0xFFFFD166)
            )
        ) {
            Text(
                if (state.event.ticket.checkedIn) {
                    if (language == AppLanguage.Korean) "AR Live 준비 완료" else "Ready for AR Live"
                } else {
                    if (language == AppLanguage.Korean) "입장 확인 후 사용" else "Check in to continue"
                }
            )
        }
    }
}

@Composable
private fun ReadinessScreen(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile,
    language: AppLanguage,
    micPermissionDenied: Boolean,
    onOpenDetail: () -> Unit
) {
    val checks = buildReadinessChecks(
        state = state,
        glassesProfile = glassesProfile,
        language = language,
        micPermissionDenied = micPermissionDenied
    )
    ScreenFrame {
        VisualEventHeader(
            title = if (language == AppLanguage.Korean) "준비 점검" else "Ready Check",
            subtitle = state.event.title,
            badge = "${checks.count { it.severity == ReadinessSeverity.Ready }}/${checks.size}"
        )
        ReadinessSummaryCard(checks)
        checks.forEach { check ->
            ReadinessCheckCard(check)
        }
        Button(
            onClick = onOpenDetail,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D75))
        ) {
            Text(if (language == AppLanguage.Korean) "공연 준비 화면으로 이동" else "Go to Show Prep")
        }
    }
}

private enum class ReadinessSeverity {
    Ready,
    Warning,
    Blocked
}

private data class ReadinessCheck(
    val title: String,
    val message: String,
    val severity: ReadinessSeverity
)

private fun buildReadinessChecks(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile,
    language: AppLanguage,
    micPermissionDenied: Boolean
): List<ReadinessCheck> {
    val ko = language == AppLanguage.Korean
    return listOf(
        ReadinessCheck(
            title = if (ko) "티켓 입장" else "Ticket",
            message = if (state.event.ticket.checkedIn) {
                if (ko) "입장 확인 완료" else "Check-in complete"
            } else {
                if (ko) "입장 확인이 필요합니다." else "Check-in is required."
            },
            severity = if (state.event.ticket.checkedIn) ReadinessSeverity.Ready else ReadinessSeverity.Blocked
        ),
        ReadinessCheck(
            title = if (ko) "마이크 권한" else "Microphone",
            message = if (micPermissionDenied) {
                if (ko) "마이크 권한이 거부되었습니다." else "Microphone permission was denied."
            } else {
                if (ko) "요청 시 실시간 볼륨 분석 사용 가능" else "Live volume analysis is available when started."
            },
            severity = if (micPermissionDenied) ReadinessSeverity.Warning else ReadinessSeverity.Ready
        ),
        ReadinessCheck(
            title = "Ray-Ban Display",
            message = "${glassesProfile.targetDevice.displayName} / ${glassesProfile.primaryRenderer.status.message}",
            severity = if (glassesProfile.targetDevice.hasDisplay) ReadinessSeverity.Warning else ReadinessSeverity.Blocked
        )
    )
}

@Composable
private fun ReadinessSummaryCard(checks: List<ReadinessCheck>) {
    val blocked = checks.count { it.severity == ReadinessSeverity.Blocked }
    val warnings = checks.count { it.severity == ReadinessSeverity.Warning }
    val ready = checks.count { it.severity == ReadinessSeverity.Ready }
    val statusColor = when {
        blocked > 0 -> Color(0xFFE85D75)
        warnings > 0 -> Color(0xFFFFD166)
        else -> Color(0xFF62D6C4)
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF1B222C), Color(0xFF211A21))))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = size.minDimension * 0.24f
                val center = Offset(size.width * 0.23f, size.height * 0.48f)
                drawCircle(statusColor.copy(alpha = 0.22f), radius * 1.35f, center)
                drawCircle(statusColor.copy(alpha = 0.44f), radius, center, style = Stroke(width = 10f))
                repeat(checks.size) { index ->
                    val angle = (index / checks.size.toFloat()) * 6.28318f
                    val point = Offset(
                        center.x + kotlin.math.cos(angle) * radius,
                        center.y + kotlin.math.sin(angle) * radius
                    )
                    drawCircle(readinessColor(checks[index].severity), 9f, point)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(18.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("$ready/${checks.size}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        blocked > 0 -> "필수 해결 $blocked"
                        warnings > 0 -> "주의 $warnings"
                        else -> "준비 완료"
                    },
                    color = statusColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StatusDot("완료", ready, Color(0xFF62D6C4))
                    StatusDot("주의", warnings, Color(0xFFFFD166))
                    StatusDot("필수", blocked, Color(0xFFE85D75))
                }
            }
        }
    }
}

@Composable
private fun ReadinessCheckCard(check: ReadinessCheck) {
    val color = readinessColor(check.severity)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191B1F)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (check.severity) {
                        ReadinessSeverity.Ready -> "✓"
                        ReadinessSeverity.Warning -> "!"
                        ReadinessSeverity.Blocked -> "×"
                    },
                    color = color,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(check.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(check.message, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when (check.severity) {
                    ReadinessSeverity.Ready -> "준비"
                    ReadinessSeverity.Warning -> "주의"
                    ReadinessSeverity.Blocked -> "필수"
                },
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PrepQuickStatusCard(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile,
    language: AppLanguage
) {
    val ko = language == AppLanguage.Korean
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (ko) "입장 전 확인" else "Before the show", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (ko) "공연장에 들어가기 전 필요한 정보만 확인합니다." else "Only the essentials before entering the venue.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip(
                    if (state.event.ticket.checkedIn) {
                        if (ko) "준비됨" else "Ready"
                    } else {
                        if (ko) "확인 필요" else "Check needed"
                    },
                    if (state.event.ticket.checkedIn) Color(0xFF62D6C4) else Color(0xFFFFD166)
                )
            }
            VisualStatusRow(
                icon = "TKT",
                title = if (ko) "입장 확인" else "Ticket check-in",
                detail = if (state.event.ticket.checkedIn) {
                    if (ko) "티켓 확인이 완료되었습니다." else "Your ticket is checked in."
                } else {
                    if (ko) "입장 게이트 또는 예매처 확인이 필요합니다." else "Check in at the gate or ticket provider."
                },
                status = if (state.event.ticket.checkedIn) "OK" else "WAIT",
                color = if (state.event.ticket.checkedIn) Color(0xFF62D6C4) else Color(0xFFFFD166)
            )
            VisualStatusRow(
                icon = "SEAT",
                title = state.event.venueInfo.seat,
                detail = "${state.event.venueInfo.gate} · ${state.event.venueInfo.nearestExit}",
                status = if (ko) "좌석" else "Seat",
                color = Color(0xFF8AB4F8)
            )
            VisualStatusRow(
                icon = "GLS",
                title = glassesProfile.targetDevice.displayName,
                detail = if (ko) "표시 가능 여부는 AR Live에서 확인합니다." else "Display availability is checked in AR Live.",
                status = if (ko) "선택" else "Optional",
                color = Color(0xFFB794F4)
            )
        }
    }
}

@Composable
private fun CompanionScreen(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile,
    micPermissionDenied: Boolean,
    appInForeground: Boolean,
    resumeNoticeVisible: Boolean,
    glassesDispatchRecords: List<GlassesDispatchRecord>,
    onReaction: (ReactionSignal) -> Unit,
    onInteractionEvent: (ConcertInteractionEvent) -> Unit,
    onDispatchHud: (HudRenderInstruction) -> Unit,
    onDismissResumeNotice: () -> Unit,
    onToggleMic: () -> Unit,
    onFinish: () -> Unit
) {
    ScreenFrame {
        LiveCompanionTopBar(state = state)
        if (resumeNoticeVisible) {
            CompanionResumeNoticeCard(onDismiss = onDismissResumeNotice)
        }
        CompanionInteractionEventsCard(state = state, onInteractionEvent = onInteractionEvent)
        StageView(state)
        GlassHudPreview(
            hudState = state.hudState,
            glassesProfile = glassesProfile,
            dispatchRecords = glassesDispatchRecords,
            showDispatchControls = false,
            onDispatchHud = onDispatchHud
        )
        OutlinedButton(onClick = onFinish, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("홈으로 돌아가기")
        }
    }
}

@Composable
private fun TranslationScreen(
    state: ConcertState
) {
    ScreenFrame {
        TranslationCommandCard(state = state)
        AudienceTranslationProblemsCard(state = state)
        AudienceTranslationLensCard(state = state)
        TranslationFallbackTipsCard(state = state)
    }
}

@Composable
private fun TranslationCommandCard(state: ConcertState) {
    val translation = state.liveTranslation
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF12242A), Color(0xFF241A2E))))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width * 0.82f, size.height * 0.24f)
                drawCircle(Color(0xFF62D6C4).copy(alpha = 0.20f), size.minDimension * 0.35f, center)
                drawCircle(Color(0xFF8AB4F8).copy(alpha = 0.18f), size.minDimension * 0.24f, Offset(size.width * 0.18f, size.height * 0.82f))
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(size.width * 0.54f, size.height * 0.58f),
                    size = Size(size.width * 0.34f, 9f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = Color(0xFF62D6C4).copy(alpha = 0.55f),
                    topLeft = Offset(size.width * 0.54f, size.height * 0.70f),
                    size = Size(size.width * 0.24f, 9f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TranslationGlyph(color = Color(0xFF62D6C4))
                    StatusChip("공연 중", Color(0xFF62D6C4))
                }
                Text("지금 멘트", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    translation.hudSummary,
                    color = Color(0xFFE5E7EB),
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill("렌즈", "짧게 표시", Color(0xFF62D6C4), Modifier.weight(1f))
                    MetricPill("긴 멘트", "폰에서 확인", Color(0xFFFFD166), Modifier.weight(1f))
                    MetricPill("방해", "최소화", Color(0xFF8AB4F8), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TranslationGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text("T", color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC101113))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 10.sp, maxLines = 1)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudienceTranslationProblemsCard(state: ConcertState) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("지금 놓치기 쉬운 부분", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("공연 중 번역은 짧게, 늦으면 의미만 먼저 보여줍니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip("관객 모드", Color(0xFFFFD166))
            }
            AudienceProblemRow(
                icon = "1",
                title = "멘트가 길어질 때",
                detail = "렌즈에는 핵심 의미만 1-2줄로 줄이고, 긴 문장은 스마트폰에서 확인합니다.",
                color = Color(0xFF62D6C4)
            )
            AudienceProblemRow(
                icon = "2",
                title = "환호성 때문에 안 들릴 때",
                detail = "확실하지 않은 문장은 단정적으로 보이지 않게 조심스럽게 표시합니다.",
                color = Color(0xFFFFD166)
            )
            AudienceProblemRow(
                icon = "3",
                title = "번역이 늦어질 때",
                detail = "실시간 번역 대신 준비된 자막이나 다음 큐 안내로 시야를 방해하지 않게 전환합니다.",
                color = Color(0xFF8AB4F8)
            )
        }
    }
}

@Composable
private fun AudienceProblemRow(
    icon: String,
    title: String,
    detail: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF171A20))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = color, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AudienceTranslationLensCard(state: ConcertState) {
    val translation = state.liveTranslation
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("렌즈에 보일 문장", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("무대를 가리지 않도록 짧은 문장만 표시합니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip("1-2줄", Color(0xFF62D6C4))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF080A0D)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC12161D))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        translation.hudSummary,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationFallbackTipsCard(state: ConcertState) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("번역이 불안정할 때", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("관객에게 필요한 행동만 바로 보여줍니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip("안내", Color(0xFF8AB4F8))
            }
            TranslationTipRow("무대를 먼저 보기", "문장이 늦으면 렌즈 표시를 줄이고 다음 안내만 유지합니다.")
            TranslationTipRow("스마트폰에서 원문 확인", "긴 멘트나 애매한 번역은 스마트폰 화면에서 더 넓게 확인합니다.")
            TranslationTipRow("녹음 정책 우선", "공연장 정책상 마이크 사용이 제한되면 준비된 자막만 사용합니다.")
        }
    }
}

@Composable
private fun TranslationTipRow(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF171A20))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(detail, color = Color(0xFFB8BDC7), fontSize = 12.sp)
    }
}

@Composable
private fun TranslationPipelineCard(state: ConcertState) {
    val translation = state.liveTranslation
    val steps = listOf(
        Triple("IN", translation.source.label, Color(0xFFFFD166)),
        Triple("STT", "음성 인식", Color(0xFF8AB4F8)),
        Triple("KO", "요약 번역", Color(0xFF62D6C4)),
        Triple("HUD", "렌즈 표시", Color(0xFFB794F4))
    )
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("처리 흐름", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("스마트폰 앱이 클라우드와 통신하고 DAT HUD로 넘깁니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip("PHONE", Color(0xFF8AB4F8))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, step ->
                    PipelineStep(
                        icon = step.first,
                        label = step.second,
                        color = step.third,
                        active = index <= 3,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineStep(
    icon: String,
    label: String,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) color.copy(alpha = 0.14f) else Color(0xFF1D2027))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (active) 0.24f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = if (active) color else Color(0xFF9CA3AF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AudiencePrepSection(state: ConcertState) {
    TicketCard(state.event.ticket)
    VenueCard(state.event.venueInfo)
}

@Composable
private fun OperationsPrepSection(state: ConcertState) {
    PartnerBriefCard(state.partnerBrief)
    PartnerAssetsCard(state.event.partnerAssets)
    OperationsChecklistCard(state.event.operationsChecklist)
    ConsentNoticeCard(defaultConcertConsentNotice)
    CapturePolicyCard(state.event)
    SessionPolicyCard()
}

@Composable
private fun TechnicalPrepSection(
    glassesProfile: GlassesIntegrationProfile,
    repositoryResult: ConcertRepositoryResult
) {
    GlassesIntegrationCard(glassesProfile)
    DatMockDeviceGuideCard(glassesProfile)
    PlatformIntegrationChannelsCard(defaultPlatformIntegrationChannels())
    BackendArchitectureCard(defaultBackendProductArchitecture())
    ConcertRepositoryStatusCard(repositoryResult)
    TimelineCard(repositoryResult.packageReport.events.firstOrNull()?.tracks ?: sampleConcertEvents.first().tracks)
    GlassesInteractionPolicyCard(defaultGlassesInteractionPolicy)
}

@Composable
private fun DatMockDeviceGuideCard(profile: GlassesIntegrationProfile) {
    val mockDeviceStatus = profile.mockDeviceRenderer?.status
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DAT MockDevice", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Android Studio AVD가 아니라 앱 내부 SDK 테스트 디바이스 경로입니다.", color = Color(0xFFB8BDC7), fontSize = 12.sp)
                }
                StatusChip(
                    label = if (mockDeviceStatus?.availability == GlassesRendererAvailability.Ready) "READY" else "WAIT",
                    color = if (mockDeviceStatus?.availability == GlassesRendererAvailability.Ready) Color(0xFF62D6C4) else Color(0xFFFFD166)
                )
            }
            Text(
                mockDeviceStatus?.message ?: "MockDevice 렌더러가 없습니다.",
                color = Color(0xFFE5E7EB),
                fontSize = 13.sp
            )
            VisualStatusRow(
                icon = "1",
                title = "GitHub Packages",
                detail = "mwdat-mockdevice artifact 다운로드가 가능해진 상태입니다. AR Live 전송 테스트는 MockDevice 렌더러를 우선 사용합니다.",
                status = "연결됨",
                color = Color(0xFF62D6C4)
            )
            VisualStatusRow(
                icon = "2",
                title = "에뮬레이터 확인 방식",
                detail = "하단 AR Live 탭에서 HUD 전송 테스트를 누르면 DAT MockDevice 경로로 payload accepted 상태를 확인합니다.",
                status = "AR Live",
                color = Color(0xFF8AB4F8)
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    language: AppLanguage,
    themeMode: AppThemeMode,
    onOpenConcert: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenAppearance: () -> Unit
) {
    ScreenFrame {
        VisualEventHeader(
            title = if (language == AppLanguage.Korean) "설정" else "Settings",
            subtitle = if (language == AppLanguage.Korean) "공연 관람에 필요한 기본 설정만 조정합니다." else "Adjust only the essentials for the show.",
            badge = if (language == AppLanguage.Korean) "일반" else "General"
        )
        SettingsCategoryCard(
            icon = "TIX",
            title = if (language == AppLanguage.Korean) "공연/티켓" else "Concert & Ticket",
            description = if (language == AppLanguage.Korean) "오늘 볼 공연과 입장 상태를 확인합니다." else "Choose your show and check entry status.",
            status = if (language == AppLanguage.Korean) "공연 연결" else "Concert link",
            color = Color(0xFFE85D75),
            onClick = onOpenConcert
        )
        SettingsCategoryCard(
            icon = "A",
            title = if (language == AppLanguage.Korean) "언어" else "Language",
            description = if (language == AppLanguage.Korean) "한국어/영어 표시 언어를 변경합니다." else "Change Korean/English display language.",
            status = if (language == AppLanguage.Korean) language.nativeLabel else language.englishLabel,
            color = Color(0xFF62D6C4),
            onClick = onOpenLanguage
        )
        SettingsCategoryCard(
            icon = "◐",
            title = if (language == AppLanguage.Korean) "화면 모드" else "Appearance",
            description = if (language == AppLanguage.Korean) "시스템, 라이트, 다크 모드를 선택합니다." else "Choose system, light, or dark mode.",
            status = if (language == AppLanguage.Korean) themeMode.koreanLabel else themeMode.englishLabel,
            color = Color(0xFF8AB4F8),
            onClick = onOpenAppearance
        )
    }
}

@Composable
private fun ConcertSettingsScreen(
    state: ConcertState,
    events: List<ConcertEvent>,
    language: AppLanguage,
    onSelectEvent: (ConcertEvent) -> Unit,
    onBack: () -> Unit
) {
    ScreenFrame {
        SettingsDetailHeader(
            title = if (language == AppLanguage.Korean) "공연/티켓" else "Concert & Ticket",
            subtitle = if (language == AppLanguage.Korean) "오늘 볼 공연과 입장 상태를 확인합니다." else "Choose your show and check entry status."
        )
        if (events.isEmpty()) {
            EmptyConcertStateCard()
        } else {
            events.forEach { event ->
                SettingsConcertEventCard(
                    event = event,
                    selected = event.id == state.event.id,
                    onClick = { onSelectEvent(event) }
                )
            }
        }
    }
}

@Composable
private fun LanguageSettingsScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit
) {
    ScreenFrame {
        SettingsDetailHeader(
            title = if (language == AppLanguage.Korean) "언어" else "Language",
            subtitle = if (language == AppLanguage.Korean) "앱 표시 언어" else "App display language"
        )
        LanguageSettingsCard(
            selectedLanguage = language,
            onLanguageChange = onLanguageChange
        )
    }
}

@Composable
private fun AppearanceSettingsScreen(
    language: AppLanguage,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onBack: () -> Unit
) {
    ScreenFrame {
        SettingsDetailHeader(
            title = if (language == AppLanguage.Korean) "화면 모드" else "Appearance",
            subtitle = if (language == AppLanguage.Korean) "공연장 밝기에 맞춰 앱 화면을 조정합니다." else "Adjust the app display for the venue."
        )
        AppearanceSettingsCard(
            language = language,
            selectedThemeMode = themeMode,
            onThemeModeChange = onThemeModeChange
        )
    }
}

@Composable
private fun OperationsSettingsScreen(
    state: ConcertState,
    language: AppLanguage,
    onBack: () -> Unit
) {
    ScreenFrame {
        SettingsDetailHeader(
            title = if (language == AppLanguage.Korean) "운영 설정" else "Operations",
            subtitle = if (language == AppLanguage.Korean) "공연 주최사 제공 데이터" else "Concert partner data"
        )
        OperationsPrepSection(state)
    }
}

@Composable
private fun TechnicalSettingsScreen(
    state: ConcertState,
    glassesProfile: GlassesIntegrationProfile,
    repositoryResult: ConcertRepositoryResult,
    micPermissionDenied: Boolean,
    appInForeground: Boolean,
    language: AppLanguage,
    onToggleMic: () -> Unit,
    onBack: () -> Unit
) {
    ScreenFrame {
        SettingsDetailHeader(
            title = if (language == AppLanguage.Korean) "기술 설정" else "Technical",
            subtitle = if (language == AppLanguage.Korean) "글래스, 백엔드, 플랫폼 연동" else "Glasses, backend, platform integration"
        )
        TranslationPipelineCard(state = state)
        TranslationProviderPlanCard(defaultTranslationProviderOptions)
        AudioPanel(
            state = state,
            micPermissionDenied = micPermissionDenied,
            appInForeground = appInForeground,
            onToggleMic = onToggleMic
        )
        TechnicalPrepSection(
            glassesProfile = glassesProfile,
            repositoryResult = repositoryResult
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    icon: String,
    title: String,
    description: String,
    status: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(description, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color(0xFF6B7280), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsConcertEventCard(event: ConcertEvent, selected: Boolean, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(event.subtitle, color = Color(0xFFB8BDC7), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusChip(if (selected) "현재" else "연결 가능", if (selected) Color(0xFF62D6C4) else Color(0xFFFFD166))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsMiniMetric("티켓", if (event.ticket.checkedIn) "입장 확인" else "인증 필요", Modifier.weight(1f))
                SettingsMiniMetric("좌석", event.venueInfo.seat, Modifier.weight(1f))
                SettingsMiniMetric("게이트", event.venueInfo.gate, Modifier.weight(1f))
            }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Color(0xFFE85D75) else Color(0xFF62D6C4),
                    contentColor = Color(0xFF050607)
                )
            ) {
                Text(if (selected) "공연 준비로 이동" else "이 공연 연결")
            }
        }
    }
}

@Composable
private fun SettingsMiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF202329))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyConcertStateCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("연결 가능한 공연이 없습니다", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "티켓 확인이 완료되면 이용 가능한 공연이 여기에 표시됩니다.",
                color = Color(0xFFB8BDC7),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SettingsDetailHeader(
    title: String,
    subtitle: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151820)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(subtitle, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LanguageSettingsCard(
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (selectedLanguage == AppLanguage.Korean) "언어" else "Language",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (selectedLanguage == AppLanguage.Korean) {
                    "현재 한국어와 영어를 지원합니다. 이후 언어는 같은 구조로 추가합니다."
                } else {
                    "Korean and English are currently supported. More languages can be added with the same structure."
                },
                color = Color(0xFFB8BDC7),
                fontSize = 12.sp
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { language ->
                    val selected = language == selectedLanguage
                    Button(
                        onClick = { onLanguageChange(language) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Color(0xFFE85D75) else Color(0xFF20242D),
                            contentColor = Color.White
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selected) "●" else "○", fontSize = 16.sp)
                            Text(language.nativeLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsCard(
    language: AppLanguage,
    selectedThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val ko = language == AppLanguage.Korean
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (ko) "화면 모드" else "Appearance",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (ko) {
                    "기본값은 시스템 설정을 따릅니다. 공연장에서는 눈부심을 줄이려면 다크 모드가 적합합니다."
                } else {
                    "The default follows your system setting. Dark mode is better for reducing glare in venues."
                },
                color = Color(0xFFB8BDC7),
                fontSize = 12.sp
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    val selected = mode == selectedThemeMode
                    Button(
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Color(0xFF8AB4F8) else Color(0xFF20242D),
                            contentColor = Color.White
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(themeModeIcon(mode, selected), fontSize = 16.sp)
                            Text(
                                if (ko) mode.koreanLabel else mode.englishLabel,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun themeModeIcon(mode: AppThemeMode, selected: Boolean): String =
    when (mode) {
        AppThemeMode.System -> if (selected) "●" else "○"
        AppThemeMode.Light -> "☀"
        AppThemeMode.Dark -> "◐"
    }

private data class ConcertBoardPost(
    val id: String,
    val category: String,
    val author: String,
    val message: String,
    val timeLabel: String,
    val likes: Int,
    val comments: List<ConcertBoardComment> = emptyList()
)

private data class ConcertBoardComment(
    val author: String,
    val message: String,
    val timeLabel: String
)

private fun defaultConcertBoardPostsByEventId(events: List<ConcertEvent>): Map<String, List<ConcertBoardPost>> =
    events.associate { event ->
        event.id to defaultConcertBoardPosts(event)
    }

private fun defaultConcertBoardPosts(event: ConcertEvent): List<ConcertBoardPost> =
    if (event.id.contains("nova", ignoreCase = true)) {
        defaultNovaBoardPosts()
    } else {
        defaultLumenBoardPosts()
    }

private fun defaultLumenBoardPosts(): List<ConcertBoardPost> =
    listOf(
        ConcertBoardPost(
            id = "post-alert-entry",
            category = "주의",
            author = "Floor B 12열",
            message = "스탠딩 쪽 통로가 좁아요. 입장 후 오른쪽 이동이 더 빠릅니다.",
            timeLabel = "방금",
            likes = 18,
            comments = listOf(
                ConcertBoardComment("A구역", "맞아요. 왼쪽은 촬영 장비 때문에 더 막혀요.", "방금"),
                ConcertBoardComment("첫 방문", "정보 감사합니다. 입장 전에 오른쪽으로 갈게요.", "방금")
            )
        ),
        ConcertBoardPost(
            id = "post-tip-merch",
            category = "팁",
            author = "MD 대기중",
            message = "1층 동쪽 MD 부스는 포토카드 줄이 따로 있어요.",
            timeLabel = "5분 전",
            likes = 11,
            comments = listOf(
                ConcertBoardComment("포카교환", "앨범 줄이랑 헷갈리기 쉬워요. 직원분께 먼저 물어보세요.", "3분 전")
            )
        ),
        ConcertBoardPost(
            id = "post-fan-chant",
            category = "응원",
            author = "LUMEN 팬",
            message = "두 번째 곡 후렴에서 하트 응원 큐 같이 맞춰요.",
            timeLabel = "12분 전",
            likes = 27,
            comments = listOf(
                ConcertBoardComment("2층 중앙", "2층도 같이 맞출게요.", "10분 전"),
                ConcertBoardComment("응원봉ON", "후렴 첫 박자에 들어가면 되죠?", "8분 전")
            )
        ),
        ConcertBoardPost(
            id = "post-question-camera",
            category = "질문",
            author = "첫 방문",
            message = "앵콜 때 촬영 가능한 구간인지 아시는 분 있나요?",
            timeLabel = "18분 전",
            likes = 4,
            comments = listOf(
                ConcertBoardComment("운영 공지 봄", "앵콜 첫 곡만 가능하다고 입구 화면에 나왔어요.", "15분 전")
            )
        )
    )

private fun defaultNovaBoardPosts(): List<ConcertBoardPost> =
    listOf(
        ConcertBoardPost(
            id = "post-nova-entry",
            category = "주의",
            author = "NOVA 입장 대기",
            message = "B 게이트 보안 검색 줄이 길어요. A 게이트 쪽 우회가 더 빠릅니다.",
            timeLabel = "방금",
            likes = 9,
            comments = listOf(
                ConcertBoardComment("2층 좌석", "A 게이트는 지금 5분 정도 걸렸어요.", "방금")
            )
        ),
        ConcertBoardPost(
            id = "post-nova-light",
            category = "응원",
            author = "Blue Pulse",
            message = "세 번째 곡 시작 전에 파란색 응원봉으로 맞추는 이벤트 있습니다.",
            timeLabel = "7분 전",
            likes = 21,
            comments = listOf(
                ConcertBoardComment("응원봉ON", "앱 AR Live에서도 큐 뜨는지 확인해볼게요.", "5분 전")
            )
        ),
        ConcertBoardPost(
            id = "post-nova-merch",
            category = "팁",
            author = "MD 체크",
            message = "NOVA 포스터는 2층 팝업 부스에 재고가 더 많다고 합니다.",
            timeLabel = "14분 전",
            likes = 13,
            comments = emptyList()
        )
    )

@Composable
private fun BoardConcertSelectorScreen(
    events: List<ConcertEvent>,
    selectedEventId: String?,
    language: AppLanguage,
    onOpenEventBoard: (ConcertEvent) -> Unit
) {
    val ko = language == AppLanguage.Korean
    ScreenFrame {
        VisualEventHeader(
            title = if (ko) "게시판" else "Fan Board",
            subtitle = if (ko) "공연 참가자 인증 후 입장" else "Choose a verified concert",
            badge = if (ko) "공연별 분리" else "Per concert"
        )
        Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (ko) "공연별 게시판" else "Concert-specific boards", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    if (ko) {
                        "잘못된 공연 참가자의 정보 유입을 줄이기 위해 티켓/입장 인증이 확인된 공연 게시판만 열 수 있습니다."
                    } else {
                        "Only verified ticket holders can enter a concert board to reduce cross-show misinformation."
                    },
                    color = Color(0xFFB8BDC7),
                    fontSize = 13.sp
                )
            }
        }
        events.forEach { event ->
            BoardConcertAccessCard(
                event = event,
                selected = event.id == selectedEventId,
                language = language,
                onOpen = { onOpenEventBoard(event) }
            )
        }
    }
}

@Composable
private fun BoardConcertAccessCard(
    event: ConcertEvent,
    selected: Boolean,
    language: AppLanguage,
    onOpen: () -> Unit
) {
    val ko = language == AppLanguage.Korean
    val accessPolicy = event.boardAccessPolicy()
    val verified = accessPolicy.canEnter
    val color = if (verified) Color(0xFF62D6C4) else Color(0xFFFFD166)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${event.partnerBrief.showDate} · ${event.venueInfo.seat}", color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(accessPolicy.reason, color = Color(0xFF9CA3AF), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                StatusChip(
                    if (verified) {
                        if (selected) "선택됨" else "인증됨"
                    } else {
                        if (ko) "인증 필요" else "Locked"
                    },
                    color
                )
            }
            Button(
                onClick = onOpen,
                enabled = verified,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB794F4),
                    disabledContainerColor = Color(0xFF2B3038),
                    disabledContentColor = Color(0xFF9CA3AF)
                )
            ) {
                Text(
                    if (verified) {
                        if (ko) "이 공연 게시판 입장" else "Open Board"
                    } else {
                        if (ko) "티켓/입장 인증 후 이용 가능" else "Verify ticket to enter"
                    }
                )
            }
        }
    }
}

@Composable
private fun ConcertBoardScreen(
    eventTitle: String,
    posts: List<ConcertBoardPost>,
    language: AppLanguage,
    onAddPost: (ConcertBoardPost) -> Unit,
    onOpenPost: (ConcertBoardPost) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(if (language == AppLanguage.Korean) "팁" else "Tips") }
    val ko = language == AppLanguage.Korean
    val categories = if (ko) {
        listOf("팁", "주의", "응원", "질문")
    } else {
        listOf("Tips", "Alerts", "Fan", "Question")
    }
    ScreenFrame {
        VisualEventHeader(
            title = if (ko) "게시판" else "Fan Board",
            subtitle = eventTitle,
            badge = if (ko) "같은 공연 참가자" else "Same show"
        )
        Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ko) "팁이나 주의사항 공유" else "Share tips or alerts", color = Color.White, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        Button(
                            onClick = { selectedCategory = category },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = if (selectedCategory == category) {
                                ButtonDefaults.buttonColors(containerColor = boardCategoryColor(category))
                            } else {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFF252A33), contentColor = Color(0xFFE5E7EB))
                            }
                        ) {
                            Text(category, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(if (ko) "예: 입장 줄, MD 부스, 촬영 정책, 응원 타이밍" else "e.g. entry line, merch booth, camera policy, fan chant timing")
                    },
                    maxLines = 3
                )
                Button(
                    onClick = {
                        val message = draft.trim()
                        if (message.isNotEmpty()) {
                            onAddPost(
                                ConcertBoardPost(
                                    id = "post-${System.currentTimeMillis()}",
                                    category = selectedCategory,
                                    author = if (ko) "나" else "Me",
                                    message = message,
                                    timeLabel = if (ko) "방금" else "Now",
                                    likes = 0,
                                    comments = emptyList()
                                )
                            )
                            draft = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D75))
                ) {
                    Text(if (ko) "게시하기" else "Post")
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                VisualMiniTile(category, posts.count { boardCategoryMatches(category, it.category) }.toString(), boardCategoryColor(category), Modifier.weight(1f))
            }
        }
        posts.forEach { post ->
            BoardPostCard(post = post, onClick = { onOpenPost(post) })
        }
    }
}

@Composable
private fun BoardPostCard(
    post: ConcertBoardPost,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusChip(post.category, boardCategoryColor(post.category))
                Text(post.timeLabel, color = Color(0xFF9CA3AF), fontSize = 11.sp)
            }
            Text(post.message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(post.author, color = Color(0xFFB8BDC7), fontSize = 12.sp)
                Text("♡ ${post.likes} · 댓글 ${post.comments.size}", color = Color(0xFFE85D75), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoardPostDetailScreen(
    post: ConcertBoardPost,
    eventTitle: String,
    language: AppLanguage,
    onBack: () -> Unit,
    onAddComment: (String, ConcertBoardComment) -> Unit
) {
    var draft by remember(post.id) { mutableStateOf("") }
    val ko = language == AppLanguage.Korean
    ScreenFrame {
        SettingsDetailHeader(
            title = if (ko) "게시글" else "Post",
            subtitle = eventTitle
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(post.category, boardCategoryColor(post.category))
                    Text(post.timeLabel, color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                Text(post.message, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(post.author, color = Color(0xFFB8BDC7), fontSize = 13.sp)
                    Text("♡ ${post.likes}", color = Color(0xFFE85D75), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ko) "댓글 ${post.comments.size}" else "${post.comments.size} comments", color = Color.White, fontWeight = FontWeight.Bold)
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (ko) "댓글을 입력하세요" else "Write a comment") },
                    maxLines = 3
                )
                Button(
                    onClick = {
                        val message = draft.trim()
                        if (message.isNotEmpty()) {
                            onAddComment(
                                post.id,
                                ConcertBoardComment(
                                    author = if (ko) "나" else "Me",
                                    message = message,
                                    timeLabel = if (ko) "방금" else "Now"
                                )
                            )
                            draft = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB794F4))
                ) {
                    Text(if (ko) "댓글 남기기" else "Add Comment")
                }
            }
        }
        post.comments.forEach { comment ->
            BoardCommentCard(comment)
        }
    }
}

@Composable
private fun BoardCommentCard(comment: ConcertBoardComment) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(comment.author, color = Color(0xFF62D6C4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(comment.timeLabel, color = Color(0xFF6B7280), fontSize = 11.sp)
            }
            Text(comment.message, color = Color.White, fontSize = 14.sp)
        }
    }
}

private fun boardCategoryColor(category: String): Color =
    when (category) {
        "주의", "Alerts" -> Color(0xFFFFD166)
        "팁", "Tips" -> Color(0xFF62D6C4)
        "응원", "Fan" -> Color(0xFFE85D75)
        "질문", "Question" -> Color(0xFF8AB4F8)
        else -> Color(0xFFB794F4)
    }

private fun boardCategoryMatches(displayCategory: String, postCategory: String): Boolean =
    when (displayCategory) {
        "팁", "Tips" -> postCategory == "팁" || postCategory == "Tips"
        "주의", "Alerts" -> postCategory == "주의" || postCategory == "Alerts"
        "응원", "Fan" -> postCategory == "응원" || postCategory == "Fan"
        "질문", "Question" -> postCategory == "질문" || postCategory == "Question"
        else -> displayCategory == postCategory
    }

@Composable
private fun VisualEventHeader(
    title: String,
    subtitle: String,
    badge: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF050607))
    ) {
        Image(
            painter = painterResource(R.drawable.concert_hero_youth),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color(0x77050607), Color(0xEE050607))))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(badge, Color(0xFFFFD166))
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFE5E7EB), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC050607))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StatusDot(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x99050607))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text("$label $count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VisualMiniTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF20242D))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, color = Color(0xFF9CA3AF), fontSize = 10.sp, maxLines = 1)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VisualStatusRow(icon: String, title: String, detail: String, status: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF20242D))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Color(0xFFB8BDC7), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(status, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun readinessColor(severity: ReadinessSeverity): Color =
    when (severity) {
        ReadinessSeverity.Ready -> Color(0xFF62D6C4)
        ReadinessSeverity.Warning -> Color(0xFFFFD166)
        ReadinessSeverity.Blocked -> Color(0xFFE85D75)
    }

@Composable
private fun TicketCard(ticket: ConcertTicket) {
    val color = if (ticket.checkedIn) Color(0xFF62D6C4) else Color(0xFFFFD166)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(color.copy(alpha = 0.35f), Color(0xFF252A33)))),
                contentAlignment = Alignment.Center
            ) {
                Text(if (ticket.checkedIn) "✓" else "ID", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("티켓 패스", color = Color.White, fontWeight = FontWeight.Bold)
                Text(ticket.holderName, color = Color(0xFFE5E7EB), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(ticket.ticketId, color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (ticket.checkedIn) "입장" else "대기", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SessionPolicyCard() {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Companion 동작 정책", color = Color.White, fontWeight = FontWeight.Bold)
            Text("앱을 다시 열면 마지막 선택 공연과 세션 요약을 복원합니다.", color = Color(0xFFB8BDC7), fontSize = 13.sp)
            Text("진행 중이던 Companion 화면은 안전을 위해 공연 준비 화면에서 재개합니다.", color = Color(0xFFFFD166), fontSize = 13.sp)
            Text("마이크 분석은 사용자가 다시 시작해야 합니다.", color = Color(0xFFB8BDC7), fontSize = 13.sp)
        }
    }
}

@Composable
private fun WarningCard(title: String, message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2113)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color(0xFFFFD166), fontWeight = FontWeight.Bold)
            Text(message, color = Color(0xFFE7D8B8), fontSize = 13.sp)
        }
    }
}

@Composable
private fun CompanionResumeNoticeCard(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2A2E)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AR Live 재개 확인", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "앱이 백그라운드로 전환되어 공연 진행이 일시 중지되었습니다. 현재 곡 흐름을 확인한 뒤 다시 이어가세요.",
                color = Color(0xFFB8BDC7),
                fontSize = 13.sp
            )
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("확인")
            }
        }
    }
}

@Composable
private fun PartnerBriefCard(brief: PartnerBrief) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("주최사 사전 연동 정보", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${brief.promoter} · ${brief.venue} · ${brief.showDate}", color = Color(0xFFB8BDC7), fontSize = 13.sp)
            Text(brief.dataStatus, color = Color(0xFF9AE6B4), fontSize = 13.sp)
        }
    }
}

@Composable
private fun VenueCard(venue: VenueInfo) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("공연장 맵", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(venue.name, color = Color(0xFFB8BDC7), fontSize = 12.sp)
                }
                Text(venue.gate, color = Color(0xFFFFD166), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF20242D))
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val lineColor = Color(0xFF62D6C4).copy(alpha = 0.55f)
                    drawRoundRect(Color(0xFF2C3240), size = size)
                    drawCircle(Color(0xFFE85D75).copy(alpha = 0.25f), size.width * 0.2f, Offset(size.width * 0.5f, size.height * 0.15f))
                    drawLine(lineColor, Offset(size.width * 0.14f, size.height * 0.76f), Offset(size.width * 0.82f, size.height * 0.34f), strokeWidth = 9f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFFFFD166), 12f, Offset(size.width * 0.14f, size.height * 0.76f))
                    drawCircle(Color(0xFF62D6C4), 12f, Offset(size.width * 0.82f, size.height * 0.34f))
                }
                Text("내 좌석", modifier = Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(venue.seat, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), color = Color(0xFFFFD166), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualMiniTile("출구", venue.nearestExit, Color(0xFF62D6C4), Modifier.weight(1f))
                VisualMiniTile("MD", venue.merchBooth, Color(0xFFE85D75), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PartnerAssetsCard(assets: List<PartnerAsset>) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("공연사 제공 데이터", color = Color.White, fontWeight = FontWeight.Bold)
            assets.forEachIndexed { index, asset ->
                VisualStatusRow(
                    icon = listOf("♪", "AR", "ⓘ", "✓")[index % 4],
                    title = asset.name,
                    detail = asset.detail,
                    status = asset.status.label,
                    color = assetStatusColor(asset.status)
                )
            }
        }
    }
}

@Composable
private fun OperationsChecklistCard(items: List<OperationsChecklistItem>) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("공연 전 운영 체크리스트", color = Color.White, fontWeight = FontWeight.Bold)
            items.forEachIndexed { index, item ->
                VisualStatusRow(
                    icon = listOf("01", "02", "03", "04")[index % 4],
                    title = item.title,
                    detail = item.owner,
                    status = item.status.label,
                    color = assetStatusColor(item.status)
                )
            }
        }
    }
}

@Composable
private fun ConsentNoticeCard(clauses: List<ConsentNoticeClause>) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("공연장 고지문/동의 문안 초안", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "주최사와 법무 검토를 위한 초안입니다. 실제 배포 전 공연장 정책과 지역 법규에 맞춰 조정해야 합니다.",
                color = Color(0xFFFFD166),
                fontSize = 12.sp
            )
            clauses.forEach { clause ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(clause.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${clause.audience.label} · ${if (clause.required) "필수" else "선택"}",
                            color = Color(0xFF9AE6B4),
                            fontSize = 11.sp
                        )
                    }
                    Text(clause.body, color = Color(0xFFB8BDC7), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CapturePolicyCard(event: ConcertEvent) {
    val capturePolicy = event.partnerAssets.firstOrNull { it.name.contains("촬영") }?.detail
        ?: "촬영 가능/불가 구간은 공연 중 AR 큐로 안내됩니다."
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("촬영/녹음 정책", color = Color.White, fontWeight = FontWeight.Bold)
            Text(capturePolicy, color = Color(0xFFB8BDC7), fontSize = 13.sp)
            Text("글래스 카메라 직접 제어는 공식 SDK와 주최사 동의 체계가 확인되기 전까지 보류합니다.", color = Color(0xFF6B7280), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlatformIntegrationChannelsCard(channels: List<PlatformIntegrationChannel>) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("플랫폼 연동 경로", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Meta Wearables DAT, Meta AI 앱, Android 앱은 서로 다른 경로입니다. AI 에이전트 기능 없이도 플랫폼 연동 리스크는 별도로 추적합니다.",
                color = Color(0xFFB8BDC7),
                fontSize = 12.sp
            )
            channels.forEach { channel ->
                VisualStatusRow(
                    icon = if (channel.affectsGlassesUi) "AR" else "APP",
                    title = channel.label,
                    detail = "${channel.role.label} · ${channel.description}",
                    status = channel.statusLabel,
                    color = integrationChannelColor(channel)
                )
            }
        }
    }
}

@Composable
private fun BackendArchitectureCard(architecture: BackendProductArchitecture) {
    val importReport = samplePartnerImportValidationReport()
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("백엔드/DB 제품화 상태", color = Color.White, fontWeight = FontWeight.Bold)
            Text(architecture.currentAppMode, color = Color(0xFFB8BDC7), fontSize = 12.sp)
            Text(architecture.targetProductMode, color = Color(0xFFFFD166), fontSize = 12.sp)
            Text(
                "MVP 필수 기능 ${architecture.requiredCapabilityCount}개 · 필수 원본 계약 ${architecture.requiredPartnerContractCount}개 · 개인정보 테이블 후보 ${architecture.personalDataTableCount}개",
                color = Color(0xFF9AE6B4),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("원본 데이터 소스", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            architecture.partnerDataSources.forEach { source ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(source.sourceName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (source.requiredBeforeLaunch) "출시 전 필수" else "정책 확인",
                            color = if (source.requiredBeforeLaunch) Color(0xFFFFD166) else Color(0xFF8AB4F8),
                            fontSize = 11.sp
                        )
                    }
                    Text("원본: ${source.sourceOfTruth}", color = Color(0xFFB8BDC7), fontSize = 12.sp)
                    Text(source.reconciliationRule, color = Color(0xFF6B7280), fontSize = 11.sp)
                }
            }
            Text("Import 매핑 검증", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${importReport.sourceName} · source ${importReport.sourceVersion} · approved ${importReport.approvedVersion ?: "없음"}",
                color = Color(0xFFB8BDC7),
                fontSize = 12.sp
            )
            Text(
                if (importReport.canPublish) {
                    "배포 가능 · 필수 매핑 ${importReport.requiredFieldCount}개 확인"
                } else {
                    "배포 차단 · 차단 ${importReport.blockingIssueCount}개 · 경고 ${importReport.warningIssueCount}개"
                },
                color = if (importReport.canPublish) Color(0xFF9AE6B4) else Color(0xFFFF6B6B),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            importReport.issues.take(3).forEach { issue ->
                Text(
                    "${issue.severity.label} · ${issue.sourceField}: ${issue.message}",
                    color = importIssueColor(issue.severity),
                    fontSize = 11.sp
                )
            }
            Text("제품 기능", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            architecture.capabilities.forEach { capability ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(capability.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            capability.state.label,
                            color = backendStateColor(capability.state),
                            fontSize = 12.sp
                        )
                    }
                    Text(capability.currentImplementation, color = Color(0xFFB8BDC7), fontSize = 12.sp)
                    Text(capability.productRequirement, color = Color(0xFF6B7280), fontSize = 11.sp)
                }
            }
            Text("DB 후보", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            architecture.databaseTables.take(4).forEach { table ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(table.tableName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(table.purpose, color = Color(0xFFB8BDC7), fontSize = 11.sp)
                    }
                    Text(
                        if (table.containsPersonalData) "개인정보" else "운영 데이터",
                        color = if (table.containsPersonalData) Color(0xFFFFD166) else Color(0xFF9AE6B4),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConcertRepositoryStatusCard(result: ConcertRepositoryResult) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("데이터 저장소", color = Color.White, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(result.source.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (result.isFallback) "fallback" else "active",
                    color = if (result.isFallback) Color(0xFFFFD166) else Color(0xFF9AE6B4),
                    fontSize = 12.sp
                )
            }
            Text(result.message, color = Color(0xFFB8BDC7), fontSize = 12.sp)
            Text(
                "공연 ${result.packageReport.loadedCount}개 · 실패 ${result.packageReport.failedCount}개 · 경고 ${result.packageReport.warningCount}개",
                color = Color(0xFF6B7280),
                fontSize = 11.sp
            )
            result.importValidationReport?.let { report ->
                Text(
                    if (report.canPublish) {
                        "Import 검증 통과 · ${report.requiredFieldCount}개 필수 매핑"
                    } else {
                        "Import 검증 실패 · 차단 ${report.blockingIssueCount}개"
                    },
                    color = if (report.canPublish) Color(0xFF9AE6B4) else Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(tracks: List<ConcertTrack>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AR 타임라인", color = Color.White, fontWeight = FontWeight.Bold)
            tracks.forEachIndexed { index, track ->
                val color = listOf(Color(0xFF62D6C4), Color(0xFFE85D75), Color(0xFFFFD166), Color(0xFF8AB4F8))[index % 4]
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${track.cues.size} AR", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2F39))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(((track.cues.size.coerceAtLeast(1)) / 5f).coerceIn(0.22f, 1f))
                                .height(10.dp)
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCompanionTopBar(
    state: ConcertState
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15171C)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.event.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${state.currentTrack.artist} · ${state.currentTrack.title}", color = Color(0xFFB8BDC7), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("공연 중", color = Color(0xFF9AE6B4), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${formatTime(state.elapsedSeconds)} / ${formatTime(state.currentTrack.durationSeconds)}", color = Color(0xFF6B7280), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StageView(state: ConcertState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111318)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF141923), Color(0xFF1E1A22), Color(0xFF0C0E12))))
        ) {
            ArStageCanvas(state)
            NextCueChip(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
            TranslationBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("현재 안내", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(state.activeCue.titleKo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = Color(0xFF62D6C4),
                    trackColor = Color(0xFF373A40)
                )
            }
        }
    }
}

@Composable
private fun ArStageCanvas(state: ConcertState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.43f)
        val energy = state.fanEnergy / 100f
        drawCircle(Color(0x22FFD166), radius = 90f + (energy * 92f), center = center)
        drawCircle(Color(0x2262D6C4), radius = 150f + (energy * 34f), center = center)
        drawCircle(Color(0xFF62D6C4), radius = 48f, center = center)
        drawCircle(Color(0xFFE8F1F2), radius = 15f, center = center)
        repeat(22) { index ->
            val x = 22f + (index * size.width / 21f)
            val height = 24f + ((index * 13 + state.fanEnergy) % 62)
            drawLine(
                color = when (index % 4) {
                    0 -> Color(0xFFFF6B6B)
                    1 -> Color(0xFF9AE6B4)
                    2 -> Color(0xFFFFD166)
                    else -> Color(0xFF8AB4F8)
                }.copy(alpha = 0.7f),
                start = Offset(x, size.height - 26f),
                end = Offset(x, size.height - 26f - height),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
        drawArc(
            color = Color(0x99FFD166),
            startAngle = 205f,
            sweepAngle = 130f * energy,
            useCenter = false,
            topLeft = Offset(center.x - 126f, center.y - 126f),
            size = androidx.compose.ui.geometry.Size(252f, 252f),
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun NextCueChip(state: ConcertState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xDD111418))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("다음 안내", color = Color(0xFF9AE6B4), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(
            state.nextCue?.titleKo ?: "마지막 큐",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TranslationBadge(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xDD111418))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text("번역", color = Color(0xFFB8BDC7), fontSize = 12.sp)
        Text("LIVE", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveTranslationHudCard(state: ConcertState) {
    val translation = state.liveTranslation
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    Text("지금 멘트 번역", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("무대를 가리지 않도록 짧게 보여줍니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip("LIVE", Color(0xFF8AB4F8))
            }
            VisualStatusRow(
                icon = "T",
                title = "렌즈 표시 문구",
                detail = translation.hudSummary,
                status = "1-2줄",
                color = Color(0xFF62D6C4)
            )
            VisualStatusRow(
                icon = "P",
                title = "긴 멘트",
                detail = "긴 문장과 원문은 스마트폰에서 확인합니다.",
                status = "폰 확인",
                color = Color(0xFFFFD166)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    translation.originalText,
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    translation.policyNote,
                    color = Color(0xFFB8BDC7),
                    fontSize = 12.sp
                )
                translation.fallbackReason?.let { reason ->
                    Text(
                        reason,
                        color = Color(0xFFFFD166),
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                "환호성이나 지연 때문에 문장이 불확실하면 의미만 짧게 표시합니다.",
                color = Color(0xFFB8BDC7),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TranslationProviderPlanCard(options: List<TranslationProviderOption>) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("번역 엔진 구성", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("클라우드 기본, 사전 자막 fallback, Meta AI 직접 호출 제외", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip("MVP", Color(0xFF62D6C4))
            }
            options.forEach { option ->
                VisualStatusRow(
                    icon = if (option.selectedForMvp) "ON" else "--",
                    title = "${option.role.label} · ${option.name}",
                    detail = option.executionPath,
                    status = option.connectivityOwner,
                    color = translationProviderColor(option.role)
                )
            }
        }
    }
}

@Composable
private fun ReactionControls(state: ConcertState, onReaction: (ReactionSignal) -> Unit) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("응원 스티커", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("탭하면 오늘의 순간 기록에 남아요.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                Text("${state.totalReactions}회", color = Color(0xFFFFD166), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReactionSignal.entries.forEach { signal ->
                Button(
                    onClick = { onReaction(signal) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = reactionColor(signal))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(reactionIcon(signal), fontSize = 26.sp)
                        Text(signal.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
                    }
                }
            }
        }
            AnimatedVisibility(visible = state.lastReaction != null) {
                Text("최근 반응: ${state.lastReaction?.label ?: ""}", color = Color(0xFFFFD166), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CompanionInteractionEventsCard(
    state: ConcertState,
    onInteractionEvent: (ConcertInteractionEvent) -> Unit
) {
    val featuredEvent = state.currentFeaturedInteractionEvent
    val upcoming = state.upcomingInteractionEvents.take(2)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171A20)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("지금 할 일", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("공연 흐름에 맞춰 바로 참여할 행동만 보여줍니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip(featuredEvent?.type?.label ?: "대기", featuredEvent?.type?.eventColor() ?: Color(0xFF6B7280))
            }
            if (featuredEvent != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(featuredEvent.type.eventColor().copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(featuredEvent.type.eventIcon(), color = featuredEvent.type.eventColor(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(featuredEvent.titleKo, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(featuredEvent.messageKo, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "내 참여 ${state.interactionEventParticipationCounts[featuredEvent.id] ?: 0}회",
                            color = Color(0xFFFFD166),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { onInteractionEvent(featuredEvent) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = featuredEvent.type.eventColor())
                    ) {
                        Text(featuredEvent.ctaLabel, fontSize = 12.sp)
                    }
                }
            } else {
                Text("지금은 무대를 편하게 보면 됩니다.", color = Color(0xFFB8BDC7), fontSize = 12.sp)
            }
            upcoming.forEach { event ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(event.titleKo, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (event.trackIndex == state.trackIndex) "${formatTime((event.startSecond - state.elapsedSeconds).coerceAtLeast(0))} 후" else "다음 곡",
                        color = Color(0xFFFFD166),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassHudPreview(
    hudState: HudState,
    glassesProfile: GlassesIntegrationProfile,
    dispatchRecords: List<GlassesDispatchRecord>,
    showDispatchControls: Boolean,
    onDispatchHud: (HudRenderInstruction) -> Unit
) {
    val renderInstruction = hudState.toRenderInstruction()
    val toolkitResult = glassesProfile.primaryRenderer.render(renderInstruction)
    val toolkitDocument = renderInstruction.toToolkitDisplayDocument(glassesProfile.targetDevice)
    val previewResult = glassesProfile.fallbackRenderer.render(renderInstruction)
    val fallbackPlan = renderInstruction.toFallbackDeliveryPlan(toolkitResult)
    val visualScene = renderInstruction.visualScene
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202329)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("렌즈 미리보기", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("공연 중 시야를 가리지 않는지 확인합니다.", color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${visualScene.durationMillis / 1000}s", color = Color(0xFFFFD166), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (showDispatchControls) {
                Text(
                    "Toolkit: ${toolkitResult.status.message}",
                    color = Color(0xFFFFD166),
                    fontSize = 12.sp
                )
                Text(
                    "Payload: ${toolkitDocument.elements.size} elements / ${toolkitDocument.durationMillis / 1000}s / ${toolkitDocument.priority.label}",
                    color = Color(0xFFB8BDC7),
                    fontSize = 12.sp
                )
                Text(
                    "Scene: ${visualScene.arObjectKey.label} · ${visualScene.layout.label} · ${visualScene.animation.label} · ${visualScene.colorToken} · ${visualScene.safeAreaHint}",
                    color = Color(0xFFB8BDC7),
                    fontSize = 12.sp
                )
                Text(
                    "Preview: ${if (previewResult.accepted) "스마트폰 미리보기 렌더링 중" else "미리보기 비활성"}",
                    color = Color(0xFF9AE6B4),
                    fontSize = 12.sp
                )
                FallbackDeliveryPlanCard(fallbackPlan)
                MockDeviceVerificationCard(
                    result = toolkitResult,
                    document = toolkitDocument,
                    visualScene = visualScene
                )
            }
            if (showDispatchControls) {
                OutlinedButton(
                    onClick = { onDispatchHud(renderInstruction) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("현재 HUD 전송 테스트")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (showDispatchControls) 168.dp else 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF050607))
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF090B10), Color(0xFF020304))
                            )
                        )
                )
                LensSafeAreaOverlay(visualScene = visualScene, visible = showDispatchControls)
                HudEdgeEffect(hudState)
                HudPrimitiveOverlay(
                    visualScene = visualScene,
                    modifier = when (visualScene.arObjectKey) {
                        HudArObjectKey.CaptionLine -> Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.82f)
                            .height(82.dp)
                            .padding(bottom = 18.dp)

                        HudArObjectKey.CountdownRing -> Modifier
                            .align(Alignment.Center)
                            .size(116.dp)

                        HudArObjectKey.ParticipationWave -> Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.74f)
                            .height(96.dp)

                        HudArObjectKey.EnergyGauge -> Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.72f)
                            .height(82.dp)
                            .padding(bottom = 18.dp)
                    }
                )
                HudCaptionBlock(
                    instruction = renderInstruction,
                    secondary = hudState.secondary,
                    modifier = when (renderInstruction.placement) {
                        HudPlacement.LowerEdge -> Modifier.align(Alignment.BottomCenter)
                        HudPlacement.RightCorner -> Modifier.align(Alignment.CenterEnd)
                        HudPlacement.PeripheralPulse -> Modifier.align(Alignment.BottomCenter)
                    }
                )
                if (showDispatchControls) {
                    Text("마이크 ${hudState.micLevel}", modifier = Modifier.align(Alignment.TopEnd).padding(14.dp), color = Color(0xFFB8BDC7), fontSize = 11.sp)
                    Text(
                        "${renderInstruction.effect.label} · ${renderInstruction.placement.label} · ${renderInstruction.priority.label} · ${renderInstruction.durationMillis / 1000}s",
                        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                        color = Color(0xFFB8BDC7),
                        fontSize = 11.sp
                    )
                    Text(
                        "${visualScene.icon} · ${visualScene.arObjectKey.label}",
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                        color = visualScene.colorToken.toHudColor(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (showDispatchControls && dispatchRecords.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("최근 전송 기록", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("앱 재실행 후에도 최근 기록을 로컬 감사 로그로 복원합니다.", color = Color(0xFF6B7280), fontSize = 11.sp)
                    dispatchRecords.takeLast(3).reversed().forEach { record ->
                        DispatchRecordRow(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun FallbackDeliveryPlanCard(plan: FallbackDeliveryPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (plan.active) "대체 경로 활성" else "대체 경로 대기",
            color = if (plan.active) Color(0xFFFFD166) else Color(0xFF9AE6B4),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(plan.reason, color = Color(0xFF6B7280), fontSize = 11.sp)
        plan.steps.forEach { step ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(step.channel.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(step.message, color = Color(0xFFB8BDC7), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (step.required) "필수" else "선택", color = Color(0xFFB8BDC7), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DispatchRecordRow(record: GlassesDispatchRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "#${record.sequence} · ${record.route.label}",
                color = if (record.accepted) Color(0xFF9AE6B4) else Color(0xFFFFD166),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(record.priority.label, color = Color(0xFFB8BDC7), fontSize = 12.sp)
        }
        Text(record.textKo, color = Color(0xFFB8BDC7), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${record.rendererName} · ${record.availability.name} · ${record.documentId}",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassesCueTimelineCard(state: ConcertState, onSeekCue: (Int) -> Unit) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("글래스 출력 큐 타임라인", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "${state.currentTrack.title}에서 Ray-Ban Display에 표시될 HUD 큐를 검증합니다.",
                color = Color(0xFFB8BDC7),
                fontSize = 12.sp
            )
            state.currentTrack.cues.forEachIndexed { index, cue ->
                val isActive = cue == state.activeCue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "${formatTime(cue.atSecond)} · ${cue.titleKo}",
                            color = if (isActive) Color(0xFFFFD166) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${cue.effect.label} · ${cue.placement.label} · ${cue.durationMillis / 1000}s",
                            color = Color(0xFFB8BDC7),
                            fontSize = 12.sp
                        )
                        Text(cue.hudMessageKo, color = Color(0xFF6B7280), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { onSeekCue(index) }, shape = RoundedCornerShape(8.dp)) {
                        Text(if (isActive) "현재" else "보기", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassesInputSimulatorCard(
    policy: GlassesInteractionPolicy,
    onInput: (GlassesInputAction) -> Unit
) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("글래스 간단 조작 시뮬레이터", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "실제 Neural Band/DAT 입력이 연결되기 전, 스마트폰에서 동일한 입력 의도를 검증합니다.",
                color = Color(0xFFB8BDC7),
                fontSize = 12.sp
            )
            policy.allowedIntents.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { intent ->
                        OutlinedButton(
                            onClick = {
                                onInput(
                                    GlassesInputAction(
                                        intent = intent,
                                        source = GlassesInputSource.SmartphoneSimulator
                                    )
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(intent.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                Text(intent.gestureHint, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = Color(0xFFB8BDC7))
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassesInteractionPolicyCard(policy: GlassesInteractionPolicy) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("글래스 입력 정책", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "공연 중 글래스 직접 조작은 ${policy.maxPrimaryActions}개 이하의 짧은 입력으로 제한합니다.",
                color = Color(0xFFB8BDC7),
                fontSize = 13.sp
            )
            policy.allowedIntents.forEach { intent ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(intent.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(intent.description, color = Color(0xFFB8BDC7), fontSize = 12.sp)
                    }
                    Text(intent.gestureHint, color = Color(0xFFFFD166), fontSize = 12.sp)
                }
            }
            Text("글래스에서 차단", color = Color(0xFFFFD166), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(policy.blockedOnGlasses.joinToString(" · "), color = Color(0xFFB8BDC7), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MockDeviceVerificationCard(
    result: GlassesRenderResult,
    document: ToolkitDisplayDocument,
    visualScene: HudVisualScene
) {
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MockDevice 검증", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("렌즈 뷰어가 아니라 DAT 연결/payload 흐름 검증입니다.", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                StatusChip(
                    label = if (result.accepted) "ACCEPTED" else "WAIT",
                    color = if (result.accepted) Color(0xFF62D6C4) else Color(0xFFFFD166)
                )
            }
            Text(
                "${document.documentId} · ${visualScene.arObjectKey.name} · ${document.elements.size} elements",
                color = Color(0xFFE5E7EB),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "시각 위치 확인은 아래 Lens Simulator의 safe area overlay를 기준으로 봅니다.",
                color = Color(0xFFB8BDC7),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LensSafeAreaOverlay(
    visualScene: HudVisualScene,
    visible: Boolean
) {
    if (!visible) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val guideColor = Color(0xFF62D6C4)
        val warningColor = Color(0xFFFFD166)
        val edgePadding = 14f
        val lowerTop = size.height * 0.66f
        val rightStart = size.width * 0.58f
        drawRoundRect(
            color = guideColor.copy(alpha = 0.28f),
            topLeft = Offset(edgePadding, edgePadding),
            size = Size(size.width - edgePadding * 2, size.height - edgePadding * 2),
            style = Stroke(width = 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )
        drawRect(
            color = warningColor.copy(alpha = if (visualScene.safeAreaHint == "lower-third") 0.16f else 0.06f),
            topLeft = Offset(edgePadding, lowerTop),
            size = Size(size.width - edgePadding * 2, size.height - lowerTop - edgePadding)
        )
        drawRect(
            color = guideColor.copy(alpha = if (visualScene.safeAreaHint == "right-corner") 0.16f else 0.05f),
            topLeft = Offset(rightStart, edgePadding),
            size = Size(size.width - rightStart - edgePadding, size.height * 0.46f)
        )
        if (visualScene.safeAreaHint == "peripheral-edge") {
            drawRoundRect(
                color = Color(0xFFE85D75).copy(alpha = 0.24f),
                topLeft = Offset(edgePadding * 0.5f, edgePadding * 0.5f),
                size = Size(size.width - edgePadding, size.height - edgePadding),
                style = Stroke(width = 5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
            )
        }
    }
}

@Composable
private fun HudPrimitiveOverlay(
    visualScene: HudVisualScene,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val color = visualScene.colorToken.toHudColor()
        Canvas(modifier = Modifier.matchParentSize()) {
            when (visualScene.arObjectKey) {
                HudArObjectKey.CaptionLine -> {
                    drawRoundRect(
                        color = color.copy(alpha = 0.22f),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                    )
                    drawLine(
                        color = color.copy(alpha = 0.95f),
                        start = Offset(size.width * 0.08f, size.height * 0.34f),
                        end = Offset(size.width * 0.92f, size.height * 0.34f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color.copy(alpha = 0.56f),
                        start = Offset(size.width * 0.18f, size.height * 0.58f),
                        end = Offset(size.width * 0.82f, size.height * 0.58f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }

                HudArObjectKey.CountdownRing -> {
                    drawCircle(color = color.copy(alpha = 0.18f), radius = size.minDimension * 0.46f)
                    drawCircle(
                        color = color.copy(alpha = 0.92f),
                        radius = size.minDimension * 0.42f,
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                    drawCircle(color = Color.White.copy(alpha = 0.12f), radius = size.minDimension * 0.24f)
                }

                HudArObjectKey.ParticipationWave -> {
                    val centerY = size.height * 0.5f
                    repeat(4) { index ->
                        val width = size.width * (0.28f + index * 0.16f)
                        drawRoundRect(
                            color = color.copy(alpha = 0.36f - index * 0.06f),
                            topLeft = Offset((size.width - width) / 2f, centerY - 12f - index * 5f),
                            size = Size(width, 24f + index * 10f),
                            style = Stroke(width = 3f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(32f, 32f)
                        )
                    }
                }

                HudArObjectKey.EnergyGauge -> {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.45f),
                        size = Size(size.width * 0.84f, size.height * 0.18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                    )
                    drawRoundRect(
                        color = color.copy(alpha = 0.78f),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.45f),
                        size = Size(size.width * 0.58f, size.height * 0.18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                    )
                }
            }
        }
        Text(
            text = visualScene.arObjectKey.symbol,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HudCaptionBlock(
    instruction: HudRenderInstruction,
    secondary: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(if (instruction.placement == HudPlacement.RightCorner) 0.58f else 0.92f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC111418))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(instruction.textKo, color = Color(0xFFE8F1F2), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(instruction.textEn, color = Color(0xFF9AE6B4), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(effectColor(instruction.effect)))
            Spacer(modifier = Modifier.width(8.dp))
            Text(secondary, color = Color(0xFFB8BDC7), fontSize = 12.sp)
        }
    }
}

@Composable
private fun HudEdgeEffect(hudState: HudState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val effectColor = effectColor(hudState.effect)
        val energy = hudState.energyPercent / 100f
        if (hudState.effect == HudEffect.EdgePulse || hudState.placement == HudPlacement.PeripheralPulse) {
            drawRoundRect(
                color = effectColor.copy(alpha = 0.36f),
                style = Stroke(width = 5f + (energy * 5f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
            )
        }
        if (hudState.effect == HudEffect.EnergyMeter) {
            drawLine(
                color = effectColor.copy(alpha = 0.85f),
                start = Offset(0f, size.height - 8f),
                end = Offset(size.width * energy, size.height - 8f),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun AudioPanel(
    state: ConcertState,
    micPermissionDenied: Boolean,
    appInForeground: Boolean,
    onToggleMic: () -> Unit
) {
    val audioUiState = state.toAudioPanelUiState(
        micPermissionDenied = micPermissionDenied,
        appInForeground = appInForeground
    )
    Card(colors = darkCard(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("마이크 번역 실험", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(audioUiState.statusMessage, color = Color(0xFFB8BDC7), fontSize = 12.sp)
                }
                OutlinedButton(onClick = onToggleMic, shape = RoundedCornerShape(8.dp)) {
                    Text(audioUiState.actionLabel)
                }
            }
            LinearProgressIndicator(
                progress = { state.audioEnergy / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Color(0xFFFF6B6B),
                trackColor = Color(0xFF373A40)
            )
            if (audioUiState.warningMessage != null) {
                Text(
                    audioUiState.warningMessage,
                    color = Color(0xFFFFD166),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun assetStatusColor(status: PartnerAssetStatus): Color =
    when (status) {
        PartnerAssetStatus.Confirmed -> Color(0xFF9AE6B4)
        PartnerAssetStatus.NeedsReview -> Color(0xFFFFD166)
        PartnerAssetStatus.Scheduled -> Color(0xFF8AB4F8)
    }

private fun effectColor(effect: HudEffect): Color =
    when (effect) {
        HudEffect.Caption -> Color(0xFF9AE6B4)
        HudEffect.Countdown -> Color(0xFFFFD166)
        HudEffect.EdgePulse -> Color(0xFF62D6C4)
        HudEffect.EnergyMeter -> Color(0xFFFF6B6B)
    }

private fun String.toHudColor(): Color =
    when (this) {
        "cyan" -> Color(0xFF62D6C4)
        "amber" -> Color(0xFFFFD166)
        "pink" -> Color(0xFFE85D75)
        "lime" -> Color(0xFF9AE6B4)
        else -> Color(0xFFE5E7EB)
    }

private fun integrationChannelColor(channel: PlatformIntegrationChannel): Color =
    when (channel.type) {
        PlatformIntegrationChannelType.AndroidApp -> Color(0xFF9AE6B4)
        PlatformIntegrationChannelType.WearablesDat -> Color(0xFFFFD166)
        PlatformIntegrationChannelType.MetaAiApp -> Color(0xFFB794F4)
        PlatformIntegrationChannelType.ExternalFallback -> Color(0xFF8AB4F8)
    }

private fun translationProviderColor(role: TranslationProviderRole): Color =
    when (role) {
        TranslationProviderRole.Primary -> Color(0xFF62D6C4)
        TranslationProviderRole.Fallback -> Color(0xFFFFD166)
        TranslationProviderRole.Experimental -> Color(0xFF8AB4F8)
        TranslationProviderRole.NotSupported -> Color(0xFF9CA3AF)
    }

private fun backendStateColor(state: BackendRuntimeState): Color =
    when (state) {
        BackendRuntimeState.LocalOnly -> Color(0xFF9AE6B4)
        BackendRuntimeState.Planned -> Color(0xFFFFD166)
        BackendRuntimeState.Required -> Color(0xFFFF6B6B)
        BackendRuntimeState.Deferred -> Color(0xFF8AB4F8)
    }

private fun importIssueColor(severity: ImportIssueSeverity): Color =
    when (severity) {
        ImportIssueSeverity.Info -> Color(0xFF8AB4F8)
        ImportIssueSeverity.Warning -> Color(0xFFFFD166)
        ImportIssueSeverity.Blocking -> Color(0xFFFF6B6B)
    }

private fun RepositoryReadinessSeverity.toReadinessSeverity(): ReadinessSeverity =
    when (this) {
        RepositoryReadinessSeverity.Ready -> ReadinessSeverity.Ready
        RepositoryReadinessSeverity.Warning -> ReadinessSeverity.Warning
        RepositoryReadinessSeverity.Blocked -> ReadinessSeverity.Blocked
    }

private fun reactionColor(signal: ReactionSignal): Color =
    when (signal) {
        ReactionSignal.Heart -> Color(0xFFE24A68)
        ReactionSignal.Clap -> Color(0xFF2F80ED)
        ReactionSignal.Cheer -> Color(0xFF00A878)
        ReactionSignal.SingAlong -> Color(0xFFB7791F)
    }

private fun reactionIcon(signal: ReactionSignal): String =
    when (signal) {
        ReactionSignal.Heart -> "♥"
        ReactionSignal.Clap -> "CLAP"
        ReactionSignal.Cheer -> "!"
        ReactionSignal.SingAlong -> "♪"
    }

private fun ConcertInteractionEventType.eventColor(): Color =
    when (this) {
        ConcertInteractionEventType.CallAndResponse -> Color(0xFFE85D75)
        ConcertInteractionEventType.FanChant -> Color(0xFFB7791F)
        ConcertInteractionEventType.LightstickWave -> Color(0xFF2F80ED)
        ConcertInteractionEventType.Surprise -> Color(0xFFB794F4)
        ConcertInteractionEventType.EncoreGauge -> Color(0xFFFFD166)
        ConcertInteractionEventType.CameraPolicy -> Color(0xFFFF8A65)
        ConcertInteractionEventType.PhotoCountdown -> Color(0xFF62D6C4)
        ConcertInteractionEventType.MerchBooth -> Color(0xFF8AB4F8)
        ConcertInteractionEventType.ExitFlow -> Color(0xFF9AE6B4)
        ConcertInteractionEventType.FanMission -> Color(0xFFE24A68)
        ConcertInteractionEventType.SetlistHint -> Color(0xFF62D6C4)
        ConcertInteractionEventType.Translation -> Color(0xFF8AB4F8)
    }

private fun ConcertInteractionEventType.eventIcon(): String =
    when (this) {
        ConcertInteractionEventType.CallAndResponse -> "CALL"
        ConcertInteractionEventType.FanChant -> "♪"
        ConcertInteractionEventType.LightstickWave -> "SYNC"
        ConcertInteractionEventType.Surprise -> "!"
        ConcertInteractionEventType.EncoreGauge -> "%"
        ConcertInteractionEventType.CameraPolicy -> "CAM"
        ConcertInteractionEventType.PhotoCountdown -> "10"
        ConcertInteractionEventType.MerchBooth -> "MD"
        ConcertInteractionEventType.ExitFlow -> "EXIT"
        ConcertInteractionEventType.FanMission -> "♥"
        ConcertInteractionEventType.SetlistHint -> "NEXT"
        ConcertInteractionEventType.Translation -> "TXT"
    }

@Preview(showBackground = true)
@Composable
fun ConcertExperiencePreview() {
    RayBan_00Theme(dynamicColor = false) {
        CompanionScreen(
            state = ConcertState(),
            glassesProfile = defaultGlassesIntegrationProfile(),
            micPermissionDenied = false,
            appInForeground = true,
            resumeNoticeVisible = false,
            glassesDispatchRecords = emptyList(),
            onReaction = {},
            onInteractionEvent = {},
            onDispatchHud = {},
            onDismissResumeNotice = {},
            onToggleMic = {},
            onFinish = {}
        )
    }
}
