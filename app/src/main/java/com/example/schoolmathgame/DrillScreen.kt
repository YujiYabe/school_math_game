package com.example.schoolmathgame

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

private const val YoutubeUrl = "https://m.youtube.com/"
private const val WifiLogTag = "SchoolMathWifi"
private const val YoutubeUserAgent =
    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

@Composable
fun DrillScreen() {
    val context = LocalContext.current
    val viewModel: DrillViewModel = viewModel(
        factory = remember(context) {
            DrillViewModel.Factory(context)
        },
    )

    DrillScreenContent(viewModel = viewModel)
}

@Composable
private fun DrillScreenContent(viewModel: DrillViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (uiState.screen) {
                DrillScreenState.Settings -> SettingsScreen(
                    uiState = uiState,
                    onTimeLimitChanged = viewModel::setTimeLimit,
                    onQuestionCountChanged = viewModel::setQuestionCount,
                    onOperationChanged = viewModel::setOperation,
                    onStart = viewModel::startDrill,
                    onHistory = viewModel::showHistory,
                    onAdmin = viewModel::showAdmin,
                    onYoutubeReward = viewModel::startYoutubeRewardSession,
                )

                DrillScreenState.Drill -> ActiveDrillScreen(
                    uiState = uiState,
                    onChoiceSelected = viewModel::selectChoice,
                )

                DrillScreenState.RetryResult -> RetryResultScreen(
                    uiState = uiState,
                    onRetry = viewModel::startRetryDrill,
                )

                DrillScreenState.Result -> ResultScreen(
                    uiState = uiState,
                    onBackToSettings = viewModel::returnToSettings,
                )

                DrillScreenState.History -> HistoryScreen(
                    uiState = uiState,
                    onBack = viewModel::returnToSettings,
                    onDetail = viewModel::showHistoryDetail,
                    onDelete = viewModel::deleteHistory,
                )

                DrillScreenState.HistoryDetail -> HistoryDetailScreen(
                    history = uiState.selectedHistory,
                    onBack = viewModel::returnToHistory,
                )

                DrillScreenState.Admin -> AdminScreen(
                    uiState = uiState,
                    onBack = viewModel::returnToSettings,
                    onUnlock = viewModel::unlockAdmin,
                    onOperationEnabledChanged = viewModel::setOperationEnabled,
                    onInAppYoutubeEnabledChanged = viewModel::setInAppYoutubeEnabled,
                    onYoutubeMinutesPer100CorrectChanged = viewModel::setYoutubeMinutesPer100Correct,
                    onYoutubeRewardAvailableSecondsChanged = viewModel::setYoutubeRewardAvailableSeconds,
                    onSaveYoutubeWifiSettings = viewModel::saveYoutubeWifiSettings,
                    onSelectYoutubeWifiSettings = viewModel::selectYoutubeWifiSettings,
                    onDeleteYoutubeWifiSettings = viewModel::deleteYoutubeWifiSettings,
                    onSavePassword = viewModel::saveParentPassword,
                )

                DrillScreenState.YoutubeReward -> YoutubeRewardScreen(
                    uiState = uiState,
                    onBack = viewModel::hideYoutubeReward,
                    onTimerRunningChanged = viewModel::setYoutubeRewardTimerRunning,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: DrillUiState,
    onTimeLimitChanged: (Int) -> Unit,
    onQuestionCountChanged: (Int) -> Unit,
    onOperationChanged: (Operation) -> Unit,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onAdmin: () -> Unit,
    onYoutubeReward: () -> Unit,
) {
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFB9E9FF),
                            Color(0xFF3F9DFF),
                            Color(0xFF1F73E8),
                        ),
                    ),
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(18.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xAAE9F7FF),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        text = "メニュー",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF16408F),
                    )

                    OperationSelector(
                        selectedOperation = uiState.selectedOperation,
                        enabledOperations = uiState.enabledOperations,
                        onSelected = onOperationChanged,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TimerSlider(
                        seconds = uiState.timeLimitSeconds,
                        onSecondsChanged = onTimeLimitChanged,
                    )

                    QuestionCountSlider(
                        count = uiState.questionCount,
                        onCountChanged = onQuestionCountChanged,
                    )

                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        shape = RoundedCornerShape(31.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF86DC23),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(text = "スタート", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = onHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16408F),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(text = "履歴", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onYoutubeReward,
                        enabled = uiState.isInAppYoutubeEnabled &&
                            (uiState.isYoutubeRewardUnlimited || uiState.youtubeRewardAvailableSeconds > 0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0033),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            text = "YouTube ${uiState.youtubeRewardTimeText()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Button(
                        onClick = onAdmin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(text = "管理画面", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationSelector(
    selectedOperation: Operation,
    enabledOperations: Set<Operation>,
    onSelected: (Operation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Operation.values().forEach { operation ->
            val selected = operation == selectedOperation
            val enabled = operation in enabledOperations
            Button(
                onClick = { onSelected(operation) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Color(0xFF86DC23) else Color(0xFF277AE8),
                    contentColor = if (selected) Color(0xFF16408F) else Color.White,
                    disabledContainerColor = Color(0xFFE5E7EB),
                    disabledContentColor = Color(0xFF6B7280),
                ),
            ) {
                Text(
                    text = operation.symbol,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TimerSlider(
    seconds: Int,
    onSecondsChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "タイマー",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
            Text(
                text = "${seconds}秒",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onSecondsChanged((it + 0.5f).toInt().coerceIn(1, 30)) },
            valueRange = 1f..30f,
            steps = 28,
        )
    }
}

@Composable
private fun QuestionCountSlider(
    count: Int,
    onCountChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "問題数",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
            Text(
                text = "${count}問",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
        }
        Slider(
            value = count.toFloat(),
            onValueChange = {
                val rounded = ((it + 2.5f).toInt() / 5) * 5
                onCountChanged(rounded.coerceIn(5, 100))
            },
            valueRange = 5f..100f,
            steps = 18,
        )
    }
}

@Composable
private fun YoutubeRewardScreen(
    uiState: DrillUiState,
    onBack: () -> Unit,
    onTimerRunningChanged: (Boolean) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenActive by remember { mutableStateOf(true) }
    var youtubeWebView by remember { mutableStateOf<WebView?>(null) }
    var isYoutubeLoaded by remember { mutableStateOf(false) }
    var youtubeLoadError by remember { mutableStateOf<String?>(null) }
    var showYoutubeWifiConnectionAlert by remember { mutableStateOf(false) }
    var youtubeWifiConnectionCheckStartedAt by remember(uiState.youtubeWifiSsid) {
        mutableStateOf(System.currentTimeMillis())
    }
    var hasWifiPermission by remember(uiState.youtubeWifiSsid) {
        mutableStateOf(context.hasFineLocationPermission())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasWifiPermission = granted
    }

    LaunchedEffect(uiState.youtubeWifiSsid, hasWifiPermission) {
        if (
            uiState.youtubeWifiSsid.isNotBlank() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasWifiPermission
        ) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isScreenActive = true
                Lifecycle.Event.ON_STOP -> {
                    isScreenActive = false
                    isYoutubeLoaded = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val wifiStatus = rememberYoutubeWifiStatus(
        uiState = uiState,
        hasWifiPermission = hasWifiPermission,
        isScreenActive = isScreenActive,
    )

    LaunchedEffect(uiState.youtubeWifiSsid, isScreenActive) {
        if (uiState.youtubeWifiSsid.isNotBlank() && isScreenActive) {
            youtubeWifiConnectionCheckStartedAt = System.currentTimeMillis()
            showYoutubeWifiConnectionAlert = false
        }
    }

    LaunchedEffect(wifiStatus, uiState.youtubeWifiSsid, isScreenActive) {
        if (uiState.youtubeWifiSsid.isBlank() || !isScreenActive) {
            showYoutubeWifiConnectionAlert = false
            return@LaunchedEffect
        }
        if (wifiStatus.startsWith("WiFi接続完了")) {
            showYoutubeWifiConnectionAlert = false
            return@LaunchedEffect
        }

        val elapsedMillis = System.currentTimeMillis() - youtubeWifiConnectionCheckStartedAt
        val remainingMillis = 10_000L - elapsedMillis
        if (remainingMillis > 0L) {
            delay(remainingMillis)
        }
        if (!wifiStatus.startsWith("WiFi接続完了")) {
            showYoutubeWifiConnectionAlert = true
        }
    }

    LaunchedEffect(wifiStatus, uiState.youtubeWifiSsid, isScreenActive) {
        val canLoadYoutube = uiState.youtubeWifiSsid.isBlank() ||
            wifiStatus.startsWith("WiFi接続完了")
        if (isScreenActive && canLoadYoutube) {
            isYoutubeLoaded = false
            youtubeLoadError = null
            youtubeWebView?.loadUrl(YoutubeUrl)
        }
    }

    LaunchedEffect(isScreenActive, isYoutubeLoaded, youtubeLoadError) {
        onTimerRunningChanged(isScreenActive && isYoutubeLoaded && youtubeLoadError == null)
    }

    DisposableEffect(Unit) {
        onDispose {
            onTimerRunningChanged(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(text = "戻る", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "残り ${uiState.youtubeRewardTimeText()}",
                color = Color(0xFFFF0033),
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        youtubeLoadError?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFE4E6))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color(0xFFBE123C),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != "about:blank") {
                                youtubeLoadError = null
                                isYoutubeLoaded = true
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                isYoutubeLoaded = false
                                youtubeLoadError = error?.description?.toString()
                                    ?: "YouTubeを読み込めませんでした。"
                            }
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = YoutubeUserAgent
                    youtubeWebView = this
                    if (uiState.youtubeWifiSsid.isBlank()) {
                        isYoutubeLoaded = false
                        loadUrl(YoutubeUrl)
                    } else {
                        loadUrl("about:blank")
                    }
                }
            },
            onRelease = { webView ->
                if (youtubeWebView === webView) {
                    youtubeWebView = null
                }
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }

    if (showYoutubeWifiConnectionAlert) {
        AlertDialog(
            onDismissRequest = { showYoutubeWifiConnectionAlert = false },
            title = { Text(text = "接続確認") },
            text = { Text(text = "このアプリ内にある管理画面でWiFiの接続をしてください") },
            confirmButton = {
                TextButton(onClick = { showYoutubeWifiConnectionAlert = false }) {
                    Text(text = "閉じる")
                }
            },
        )
    }
}

@Composable
private fun AdminScreen(
    uiState: DrillUiState,
    onBack: () -> Unit,
    onUnlock: (String) -> Unit,
    onOperationEnabledChanged: (Operation) -> Unit,
    onInAppYoutubeEnabledChanged: (Boolean) -> Unit,
    onYoutubeMinutesPer100CorrectChanged: (Int) -> Unit,
    onYoutubeRewardAvailableSecondsChanged: (Int) -> Unit,
    onSaveYoutubeWifiSettings: (String, String) -> Unit,
    onSelectYoutubeWifiSettings: (String) -> Unit,
    onDeleteYoutubeWifiSettings: (String) -> Unit,
    onSavePassword: (String, String) -> Boolean,
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswordChangeFields by remember { mutableStateOf(false) }
    var localPasswordError by remember { mutableStateOf<String?>(null) }
    var youtubeWifiSsid by remember(uiState.youtubeWifiSsid) { mutableStateOf(uiState.youtubeWifiSsid) }
    var youtubeWifiPassword by remember(uiState.youtubeWifiPassword) {
        mutableStateOf(uiState.youtubeWifiPassword)
    }
    var showYoutubeWifiPassword by remember { mutableStateOf(false) }
    var wifiSsids by remember { mutableStateOf<List<String>>(emptyList()) }
    var showWifiSsidList by remember { mutableStateOf(false) }
    var wifiScanMessage by remember { mutableStateOf<String?>(null) }
    var isWifiScanning by remember { mutableStateOf(false) }
    var pendingWifiSsidListDisplay by remember { mutableStateOf(false) }
    var wifiConnectionMessage by remember { mutableStateOf<String?>(null) }
    var isWifiConnectionTesting by remember { mutableStateOf(false) }
    var isWifiConnectionConfirmed by remember { mutableStateOf(false) }
    var pendingWifiSsidAutoFill by remember { mutableStateOf(false) }
    var wifiSsidAutoFillPermissionRequested by remember { mutableStateOf(false) }
    var pendingWifiConnectionTest by remember { mutableStateOf(false) }
    var wifiConnectionCallback by remember {
        mutableStateOf<ConnectivityManager.NetworkCallback?>(null)
    }

    fun updateWifiSsidList(emptyMessage: String, showList: Boolean) {
        wifiSsids = context.availableWifiSsids()
        Log.d(WifiLogTag, "SSID list updated count=${wifiSsids.size} showList=$showList selected=$youtubeWifiSsid")
        showWifiSsidList = showList && wifiSsids.isNotEmpty()
        wifiScanMessage = if (wifiSsids.isEmpty()) emptyMessage else null
    }

    fun applyWifiNetwork(network: SavedWifiNetwork) {
        youtubeWifiSsid = network.ssid
        youtubeWifiPassword = network.password
        isWifiConnectionConfirmed = false
        wifiScanMessage = null
        wifiConnectionMessage = null
    }

    fun fillCurrentWifiSsid() {
        if (uiState.youtubeWifiSsid.isNotBlank() || youtubeWifiSsid.isNotBlank()) return
        context.currentConnectedWifiSsid()?.let { ssid ->
            youtubeWifiSsid = ssid
            uiState.savedYoutubeWifiNetworks.firstOrNull { network -> network.ssid == ssid }?.let { network ->
                youtubeWifiPassword = network.password
            }
            isWifiConnectionConfirmed = false
            wifiScanMessage = null
        }
    }

    fun stopWifiConnectionTest() {
        val callback = wifiConnectionCallback ?: return
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE,
        ) as ConnectivityManager
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        wifiConnectionCallback = null
        isWifiConnectionTesting = false
    }

    fun startWifiConnectionTest() {
        stopWifiConnectionTest()
        val ssid = youtubeWifiSsid.trim()
        val wifiPassword = youtubeWifiPassword
        Log.d(WifiLogTag, "Admin connection test start ssid=$ssid passwordLength=${wifiPassword.length}")
        if (ssid.isBlank()) {
            isWifiConnectionConfirmed = false
            Log.d(WifiLogTag, "Admin connection test blocked: blank ssid")
            wifiConnectionMessage = "SSIDを入力してください。"
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            isWifiConnectionConfirmed = false
            Log.d(WifiLogTag, "Admin connection test blocked: unsupported sdk=${Build.VERSION.SDK_INT}")
            wifiConnectionMessage = "この端末ではアプリからのWiFi接続確認に対応していません。"
            return
        }

        val currentWifi = context.currentWifiConnection()
        Log.d(
            WifiLogTag,
            "Current wifi=${currentWifi?.ssid} hasInternet=${currentWifi?.hasInternet} validated=${currentWifi?.isValidated}",
        )
        if (currentWifi?.ssid == ssid) {
            if (currentWifi.isValidated) {
                wifiConnectionMessage = "接続できました: $ssid"
                isWifiConnectionConfirmed = true
                Log.d(WifiLogTag, "Admin connection test success: already connected and validated")
            } else {
                wifiConnectionMessage = "WiFiに接続済みですが、インターネットに接続できません。"
                isWifiConnectionConfirmed = false
                Log.d(WifiLogTag, "Admin connection test failed: already connected but not validated")
            }
            return
        }

        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE,
        ) as ConnectivityManager
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(WifiLogTag, "Admin request onAvailable network=$network")
                mainHandler.post {
                    wifiConnectionMessage = "WiFi接続中です。インターネット接続を確認しています。"
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(
                    WifiLogTag,
                    "Admin request onCapabilitiesChanged network=$network hasInternet=$hasInternet validated=$validated",
                )
                mainHandler.post {
                    if (validated) {
                        wifiConnectionMessage = "接続できました: $ssid"
                        isWifiConnectionConfirmed = true
                        stopWifiConnectionTest()
                    } else if (hasInternet) {
                        wifiConnectionMessage = "WiFi接続中です。インターネット接続を確認しています。"
                    } else {
                        wifiConnectionMessage = "WiFiに接続しましたが、インターネットに接続できません。"
                        isWifiConnectionConfirmed = false
                    }
                }
            }

            override fun onUnavailable() {
                Log.d(WifiLogTag, "Admin request onUnavailable ssid=$ssid")
                mainHandler.post {
                    wifiConnectionMessage = "接続できませんでした。SSIDまたはパスワードを確認してください。"
                    isWifiConnectionConfirmed = false
                    stopWifiConnectionTest()
                }
            }

            override fun onLost(network: Network) {
                Log.d(WifiLogTag, "Admin request onLost network=$network")
                mainHandler.post {
                    wifiConnectionMessage = "接続が切れました。"
                    isWifiConnectionConfirmed = false
                    stopWifiConnectionTest()
                }
            }
        }

        runCatching {
            val request = context.wifiNetworkRequest(ssid = ssid, password = wifiPassword)
            Log.d(WifiLogTag, "Admin requestNetwork start ssid=$ssid")
            connectivityManager.requestNetwork(request, callback, 15_000)
            wifiConnectionCallback = callback
            isWifiConnectionTesting = true
            isWifiConnectionConfirmed = false
            wifiConnectionMessage = "接続確認中です。"
        }.onFailure { throwable ->
            Log.e(WifiLogTag, "Admin requestNetwork failed to start ssid=$ssid", throwable)
            wifiConnectionCallback = null
            isWifiConnectionTesting = false
            isWifiConnectionConfirmed = false
            wifiConnectionMessage = throwable.message ?: "SSIDまたはWiFiパスワードを確認してください。"
        }
    }

    val wifiScanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        Log.d(WifiLogTag, "Permission result=$grants")
        if (grants.values.all { granted -> granted }) {
            if (pendingWifiSsidAutoFill) {
                pendingWifiSsidAutoFill = false
                fillCurrentWifiSsid()
            } else if (pendingWifiConnectionTest) {
                pendingWifiConnectionTest = false
                startWifiConnectionTest()
            } else {
                isWifiScanning = context.requestWifiScan()
                pendingWifiSsidListDisplay = isWifiScanning
                val emptyMessage = if (isWifiScanning) {
                        "SSIDを取得中です。しばらく待ってから一覧を確認してください。"
                    } else {
                        "SSID取得を開始できませんでした。端末の位置情報とWiFiを有効にしてください。"
                    }
                updateWifiSsidList(
                    emptyMessage = emptyMessage,
                    showList = !isWifiScanning,
                )
            }
        } else {
            pendingWifiSsidAutoFill = false
            pendingWifiConnectionTest = false
            pendingWifiSsidListDisplay = false
            isWifiScanning = false
            wifiScanMessage = "SSID取得にはWiFiと位置情報の権限が必要です。"
            wifiConnectionMessage = "接続確認にはWiFiと位置情報の権限が必要です。"
            isWifiConnectionConfirmed = false
        }
    }

    LaunchedEffect(uiState.isAdminAuthenticated, uiState.youtubeWifiSsid) {
        if (!uiState.isAdminAuthenticated || uiState.youtubeWifiSsid.isNotBlank() || youtubeWifiSsid.isNotBlank()) {
            return@LaunchedEffect
        }
        val missingPermissions = context.missingWifiScanPermissions()
        if (missingPermissions.isEmpty()) {
            fillCurrentWifiSsid()
        } else if (!wifiSsidAutoFillPermissionRequested) {
            wifiSsidAutoFillPermissionRequested = true
            pendingWifiSsidAutoFill = true
            wifiScanPermissionLauncher.launch(missingPermissions)
        }
    }

    fun requestWifiConnectionTest() {
        val missingPermissions = context.missingWifiScanPermissions()
        Log.d(WifiLogTag, "Request admin connection test missingPermissions=${missingPermissions.joinToString()}")
        if (missingPermissions.isNotEmpty()) {
            pendingWifiConnectionTest = true
            wifiConnectionMessage = "接続確認に必要な権限を確認しています。"
            wifiScanPermissionLauncher.launch(missingPermissions)
        } else {
            startWifiConnectionTest()
        }
    }

    fun requestWifiSsidScan() {
        val missingPermissions = context.missingWifiScanPermissions()
        Log.d(WifiLogTag, "Request SSID scan missingPermissions=${missingPermissions.joinToString()}")
        if (missingPermissions.isNotEmpty()) {
            pendingWifiConnectionTest = false
            pendingWifiSsidListDisplay = true
            wifiScanPermissionLauncher.launch(missingPermissions)
        } else {
            isWifiScanning = context.requestWifiScan()
            Log.d(WifiLogTag, "SSID scan requested started=$isWifiScanning")
            pendingWifiSsidListDisplay = isWifiScanning
            val emptyMessage = if (isWifiScanning) {
                "SSIDを取得中です。しばらく待ってから一覧を確認してください。"
            } else {
                "SSID取得を開始できませんでした。端末の位置情報とWiFiを有効にしてください。"
            }
            updateWifiSsidList(
                emptyMessage = emptyMessage,
                showList = !isWifiScanning,
            )
        }
    }

    DisposableEffect(uiState.isAdminAuthenticated) {
        if (!uiState.isAdminAuthenticated) {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                    isWifiScanning = false
                    if (pendingWifiSsidListDisplay) {
                        pendingWifiSsidListDisplay = false
                        updateWifiSsidList(
                            emptyMessage = "SSIDを取得できませんでした。端末の位置情報とWiFiを有効にしてください。",
                            showList = true,
                        )
                    } else {
                        wifiSsids = context.availableWifiSsids()
                    }
                }
            }
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            onDispose {
                runCatching { context.unregisterReceiver(receiver) }
                pendingWifiSsidListDisplay = false
                stopWifiConnectionTest()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "admin-header") {
            BackHeader(title = "管理画面", onBack = onBack)
        }

        if (!uiState.isAdminAuthenticated) {
            item(key = "admin-auth") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "パスワードを入力してください",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("パスワード") },
                            singleLine = true,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            trailingIcon = {
                                PasswordVisibilityButton(
                                    visible = showPassword,
                                    onClick = { showPassword = !showPassword },
                                )
                            },
                        )
                        uiState.adminAuthError?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = { onUnlock(password) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(text = "開く", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "admin-settings") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    OperationAvailabilitySelector(
                        enabledOperations = uiState.enabledOperations,
                        operationProgress = uiState.operationProgress,
                        onOperationEnabledChanged = onOperationEnabledChanged,
                    )

                    InAppYoutubeSetting(
                        enabled = uiState.isInAppYoutubeEnabled,
                        onEnabledChanged = onInAppYoutubeEnabledChanged,
                    )

                    YoutubeRewardSlider(
                        minutes = uiState.youtubeMinutesPer100Correct,
                        onMinutesChanged = onYoutubeMinutesPer100CorrectChanged,
                    )

                    YoutubeAvailableTimeSlider(
                        availableSeconds = uiState.youtubeRewardAvailableSeconds,
                        onAvailableSecondsChanged = onYoutubeRewardAvailableSecondsChanged,
                    )

                    Text(
                        text = "総得点 ${uiState.youtubeRewardTotalScore}点 / YouTube残り ${uiState.youtubeRewardTimeText()}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Black,
                    )

                    Text(
                        text = "YouTube WiFi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    if (uiState.savedYoutubeWifiNetworks.isNotEmpty()) {
                        Text(
                            text = "保存済みWiFi",
                            color = Color(0xFF666666),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            uiState.savedYoutubeWifiNetworks.forEach { network ->
                                val isSelected = network.ssid == uiState.youtubeWifiSsid
                                Surface(
                                    modifier = Modifier.width(220.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFE5EEFF) else Color.White,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            onClick = {
                                                applyWifiNetwork(network)
                                                onSelectYoutubeWifiSettings(network.ssid)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            colors = if (isSelected) {
                                                ButtonDefaults.buttonColors(containerColor = Color(0xFF16408F))
                                            } else {
                                                ButtonDefaults.buttonColors()
                                            },
                                        ) {
                                            Text(
                                                text = network.ssid,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                if (youtubeWifiSsid == network.ssid) {
                                                    youtubeWifiSsid = ""
                                                    youtubeWifiPassword = ""
                                                    wifiConnectionMessage = null
                                                    isWifiConnectionConfirmed = false
                                                }
                                                onDeleteYoutubeWifiSettings(network.ssid)
                                            },
                                            modifier = Modifier.height(44.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                        ) {
                                            Text(
                                                text = "削除",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(180.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "SSID",
                                    color = Color(0xFF666666),
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                )
                                Text(
                                    text = youtubeWifiSsid.ifBlank { "未選択" },
                                    color = Color(0xFF16408F),
                                    fontSize = 16.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Button(
                            onClick = { requestWifiSsidScan() },
                            modifier = Modifier
                                .width(96.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = if (isWifiScanning) "取得中" else "SSID取得",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        OutlinedTextField(
                            value = youtubeWifiPassword,
                            onValueChange = {
                                youtubeWifiPassword = it
                                isWifiConnectionConfirmed = false
                            },
                            modifier = Modifier.width(180.dp),
                            label = { Text("WiFiパスワード") },
                            singleLine = true,
                            visualTransformation = if (showYoutubeWifiPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                PasswordVisibilityButton(
                                    visible = showYoutubeWifiPassword,
                                    onClick = { showYoutubeWifiPassword = !showYoutubeWifiPassword },
                                )
                            },
                        )
                        Button(
                            onClick = { requestWifiConnectionTest() },
                            enabled = !isWifiConnectionTesting,
                            modifier = Modifier
                                .width(86.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = if (isWifiConnectionTesting) "確認中" else if (isWifiConnectionConfirmed) "確認済" else "確認",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        Button(
                            onClick = {
                                onSaveYoutubeWifiSettings(youtubeWifiSsid, youtubeWifiPassword)
                                requestWifiConnectionTest()
                            },
                            modifier = Modifier
                                .width(72.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = "保存",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    wifiScanMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    wifiConnectionMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (message.contains("できました")) {
                                Color(0xFF047857)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item(key = "admin-password") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "パスワード変更",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("現在") },
                            singleLine = true,
                            visualTransformation = if (showPasswordChangeFields) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("新規") },
                            singleLine = true,
                            visualTransformation = if (showPasswordChangeFields) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("確認") },
                            singleLine = true,
                            visualTransformation = if (showPasswordChangeFields) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            trailingIcon = {
                                PasswordVisibilityButton(
                                    visible = showPasswordChangeFields,
                                    onClick = { showPasswordChangeFields = !showPasswordChangeFields },
                                )
                            },
                        )
                        Button(
                            onClick = {
                                if (newPassword != confirmPassword) {
                                    localPasswordError = "確認用のパスワードが一致しません。"
                                    return@Button
                                }
                                localPasswordError = null
                                if (onSavePassword(currentPassword, newPassword)) {
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(text = "保存", fontWeight = FontWeight.Bold)
                        }
                    }
                    localPasswordError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    uiState.parentPasswordMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (message.contains("保存")) Color(0xFF047857) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showWifiSsidList) {
        AlertDialog(
            onDismissRequest = { showWifiSsidList = false },
            title = {
                Text(text = "SSIDを選択")
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    wifiSsids.forEach { ssid ->
                        item(key = ssid) {
                            Button(
                                onClick = {
                                    val savedNetwork = uiState.savedYoutubeWifiNetworks
                                        .firstOrNull { network -> network.ssid == ssid }
                                    if (savedNetwork == null) {
                                        youtubeWifiSsid = ssid
                                        isWifiConnectionConfirmed = false
                                    } else {
                                        applyWifiNetwork(savedNetwork)
                                    }
                                    showWifiSsidList = false
                                    wifiScanMessage = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = ssid,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showWifiSsidList = false },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "閉じる", fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun OperationAvailabilitySelector(
    enabledOperations: Set<Operation>,
    operationProgress: List<OperationProgress>,
    onOperationEnabledChanged: (Operation) -> Unit,
) {
    val progressByOperation = operationProgress.associateBy { progress -> progress.operation }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "出題できる四則演算",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFF16408F),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Operation.values().forEach { operation ->
                val enabled = operation in enabledOperations
                val canDisable = enabledOperations.size > 1 || !enabled
                val progress = progressByOperation[operation]
                Button(
                    onClick = { onOperationEnabledChanged(operation) },
                    enabled = canDisable,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (enabled) Color(0xFF86DC23) else Color(0xFFE5E7EB),
                        contentColor = if (enabled) Color(0xFF16408F) else Color(0xFF6B7280),
                        disabledContainerColor = Color(0xFF86DC23),
                        disabledContentColor = Color(0xFF16408F),
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = operation.symbol,
                            fontSize = 22.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "${progress?.solvedCount ?: 0}問 / ${progress?.percentage ?: 0}%",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InAppYoutubeSetting(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "アプリ内YouTube",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFF16408F),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onEnabledChanged(true) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) Color(0xFF86DC23) else Color(0xFFE5E7EB),
                    contentColor = if (enabled) Color(0xFF16408F) else Color(0xFF6B7280),
                ),
            ) {
                Text(text = "有効", fontWeight = FontWeight.Black)
            }

            Button(
                onClick = { onEnabledChanged(false) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!enabled) Color(0xFF86DC23) else Color(0xFFE5E7EB),
                    contentColor = if (!enabled) Color(0xFF16408F) else Color(0xFF6B7280),
                ),
            ) {
                Text(text = "無効", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun YoutubeAvailableTimeSlider(
    availableSeconds: Int,
    onAvailableSecondsChanged: (Int) -> Unit,
) {
    val minutes = (availableSeconds / 60).coerceIn(0, 120)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "既に視聴できる時間",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
            Text(
                text = (minutes * 60).formatRewardTimeText(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
        }
        Slider(
            value = minutes.toFloat(),
            onValueChange = { value ->
                onAvailableSecondsChanged(value.toInt().coerceIn(0, 120) * 60)
            },
            valueRange = 0f..120f,
            steps = 119,
        )
    }
}

@Composable
private fun PasswordVisibilityButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = if (visible) "隠す" else "表示",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun YoutubeRewardSlider(
    minutes: Int,
    onMinutesChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "100問正解につき可能な視聴時間",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
            Text(
                text = "${minutes}分",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
        }
        Slider(
            value = minutes.toFloat(),
            onValueChange = {
                onMinutesChanged(it.toInt().coerceIn(0, 120))
            },
            valueRange = 0f..120f,
            steps = 119,
        )
    }
}

@Composable
private fun ActiveDrillScreen(
    uiState: DrillUiState,
    onChoiceSelected: (AnswerChoice) -> Unit,
) {
    val problem = uiState.currentProblem

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DrillHeader(uiState = uiState)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = problem?.expression.orEmpty(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 56.sp,
            )
        }

        ChoicePanel(
            choices = uiState.choices,
            onChoiceSelected = onChoiceSelected,
        )
    }
}

@Composable
private fun DrillHeader(uiState: DrillUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${uiState.currentQuestionNumber} / ${uiState.activeQuestionCount}問目",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "${uiState.remainingSecondsText}秒",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = if (uiState.timerProgress < 0.25f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { uiState.timerProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = if (uiState.timerProgress < 0.25f) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun ChoicePanel(
    choices: List<AnswerChoice>,
    onChoiceSelected: (AnswerChoice) -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.chunked(2).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowChoices.forEach { choice ->
                        ChoiceButton(
                            choice = choice,
                            onClick = { onChoiceSelected(choice) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowChoices.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    choice: AnswerChoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(93.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = choice.label,
            fontSize = if (choice.label.length > 8) 18.sp else 28.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun RetryResultScreen(
    uiState: DrillUiState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "間違いチェック",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "${uiState.reviews.size}問をもう一度",
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        ReviewList(
            reviews = uiState.reviews,
            showCorrectAnswer = false,
            showRetryCount = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = "間違えた問題を解く", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultScreen(
    uiState: DrillUiState,
    onBackToSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "結果",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "${uiState.correctCount} / ${uiState.reviews.size} 点",
            fontSize = 52.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        ReviewList(
            reviews = uiState.reviews,
            showCorrectAnswer = true,
            showRetryCount = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Button(
            onClick = onBackToSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = "もう一度", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryScreen(
    uiState: DrillUiState,
    onBack: () -> Unit,
    onDetail: (DrillHistory) -> Unit,
    onDelete: (DrillHistory) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<DrillHistory?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader(
            title = "履歴",
            onBack = onBack,
        )

        if (uiState.history.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "履歴はまだありません",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.history.forEach { history ->
                    HistoryRow(
                        history = history,
                        onDetail = { onDetail(history) },
                        onDelete = { deleteTarget = history },
                    )
                }
            }
        }
    }

    deleteTarget?.let { history ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    text = "履歴を削除",
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Text(text = "${history.completedAtText} の履歴を削除しますか？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(history)
                        deleteTarget = null
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { deleteTarget = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(text = "キャンセル", fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    history: DrillHistory,
    onDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = history.completedAtText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = history.scoreText(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (history.hasWrongAnswers()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFF222222)
                    },
                )
                Text(
                    text = history.settingsText(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF444444),
                )
            }

            Button(
                onClick = onDetail,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(text = "詳細", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onDelete,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(text = "削除", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryDetailScreen(
    history: DrillHistory?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader(
            title = "詳細",
            onBack = onBack,
        )

        if (history == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "履歴が見つかりません",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = history.completedAtText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = history.scoreText(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (history.hasWrongAnswers()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFF222222)
                    },
                )
                Text(
                    text = history.settingsText(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        ReviewList(
            reviews = history.reviews,
            showCorrectAnswer = true,
            showRetryCount = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

private fun DrillHistory.settingsText(): String {
    return "${leftNumberRange.label} ${operation.symbol} ${rightNumberRange.label}  " +
        "${questionCount}問  ${timeLimitSeconds}秒"
}

private fun DrillHistory.scoreText(): String {
    return "$correctCount/${totalQuestionCount()}"
}

private fun DrillHistory.hasWrongAnswers(): Boolean {
    return correctCount < totalQuestionCount()
}

private fun DrillHistory.totalQuestionCount(): Int {
    return reviews.size.takeIf { it > 0 } ?: questionCount
}

@Composable
private fun BackHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onBack,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = "戻る", fontWeight = FontWeight.Bold)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ReviewList(
    reviews: List<AnswerReview>,
    showCorrectAnswer: Boolean,
    showRetryCount: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reviews.forEach { review ->
                ReviewRow(
                    review = review,
                    showCorrectAnswer = showCorrectAnswer,
                    showRetryCount = showRetryCount,
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(
    review: AnswerReview,
    showCorrectAnswer: Boolean,
    showRetryCount: Boolean,
) {
    val textColor = if (review.isCorrect) Color.Black else Color(0xFFD00000)
    val selectedText = review.selectedAnswer ?: "未回答"
    val retryText = if (showRetryCount && review.retryCount > 0) {
        "   リトライ: ${review.retryCount}回"
    } else {
        ""
    }
    val unrecoverableText = if (showRetryCount && review.isUnrecoverable) {
        "   不正解"
    } else {
        ""
    }
    val answerText = if (showCorrectAnswer) {
        "選択: $selectedText   正解: ${review.correctAnswer}$retryText$unrecoverableText"
    } else {
        "選択: $selectedText"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${review.questionNumber}問目",
                color = if (review.isCorrect) Color(0xFF666666) else Color(0xFFD00000),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(
                text = review.expression,
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
            )
        }
        Text(
            text = answerText,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun rememberYoutubeWifiStatus(
    uiState: DrillUiState,
    hasWifiPermission: Boolean,
    isScreenActive: Boolean,
): String {
    val context = LocalContext.current
    val wifiCandidates = remember(
        uiState.youtubeWifiSsid,
        uiState.youtubeWifiPassword,
        uiState.savedYoutubeWifiNetworks,
    ) {
        uiState.youtubeWifiCandidates()
    }
    var status by remember(wifiCandidates) {
        mutableStateOf(
            if (wifiCandidates.isEmpty()) {
                "YouTube WiFiが未設定です。現在のネットワークで開きます。"
            } else {
                "WiFi接続中: ${wifiCandidates.first().ssid}"
            },
        )
    }

    DisposableEffect(
        wifiCandidates,
        hasWifiPermission,
        isScreenActive,
    ) {
        if (!isScreenActive) {
            Log.d(WifiLogTag, "YouTube wifi paused: screen inactive")
            status = "アプリを表示するとWiFi接続を開始します。"
            onDispose { }
        } else if (wifiCandidates.isEmpty()) {
            Log.d(WifiLogTag, "YouTube wifi skipped: no saved ssid")
            onDispose { }
        } else if (!hasWifiPermission) {
            Log.d(WifiLogTag, "YouTube wifi blocked: missing fine location permission")
            status = "WiFi接続には位置情報権限が必要です。"
            onDispose { }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d(WifiLogTag, "YouTube wifi blocked: unsupported sdk=${Build.VERSION.SDK_INT}")
            status = "この端末ではアプリからのWiFi接続リクエストに対応していません。"
            onDispose { }
        } else {
            val connectivityManager = context.getSystemService(
                Context.CONNECTIVITY_SERVICE,
            ) as ConnectivityManager
            val currentWifi = context.currentWifiConnection()
            Log.d(
                WifiLogTag,
                "YouTube wifi start candidates=${wifiCandidates.map { it.ssid }} current=${currentWifi?.ssid} hasInternet=${currentWifi?.hasInternet} validated=${currentWifi?.isValidated}",
            )
            val connectedCandidate = wifiCandidates.firstOrNull { candidate ->
                currentWifi?.ssid == candidate.ssid
            }
            if (connectedCandidate != null && currentWifi?.isValidated == true) {
                connectivityManager.bindProcessToNetwork(currentWifi.network)
                status = "WiFi接続完了: ${connectedCandidate.ssid}"
                Log.d(WifiLogTag, "YouTube wifi success: already connected and validated")
                onDispose {
                    connectivityManager.bindProcessToNetwork(null)
                }
            } else {
                val mainHandler = Handler(Looper.getMainLooper())
                var registeredCallback: ConnectivityManager.NetworkCallback? = null
                var bound = false
                var finished = false

                fun unregisterActiveCallback() {
                    registeredCallback?.let { callback ->
                        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    }
                    registeredCallback = null
                }

                fun startConnectionAttempt(index: Int) {
                    if (finished || index >= wifiCandidates.size) {
                        if (!finished) {
                            status = "保存済みWiFiに接続できませんでした。"
                        }
                        return
                    }
                    unregisterActiveCallback()
                    val candidate = wifiCandidates[index]
                    val ssid = candidate.ssid
                    val attemptText = if (wifiCandidates.size == 1) {
                        ""
                    } else {
                        " (${index + 1}/${wifiCandidates.size})"
                    }
                    status = "WiFi接続中: $ssid$attemptText"
                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            Log.d(WifiLogTag, "YouTube request onAvailable ssid=$ssid network=$network")
                            mainHandler.post {
                                status = "WiFi接続中: $ssid / インターネット確認中"
                            }
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            val hasInternet = networkCapabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                            )
                            val validated = networkCapabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                            )
                            Log.d(
                                WifiLogTag,
                                "YouTube request onCapabilitiesChanged ssid=$ssid network=$network hasInternet=$hasInternet validated=$validated",
                            )
                            mainHandler.post {
                                if (finished) return@post
                                if (validated) {
                                    if (!bound) {
                                        connectivityManager.bindProcessToNetwork(network)
                                        bound = true
                                    }
                                    finished = true
                                    status = "WiFi接続完了: $ssid"
                                } else if (hasInternet) {
                                    status = "WiFi接続中: $ssid / インターネット確認中"
                                } else {
                                    status = "WiFi接続中: $ssid / インターネット確認中"
                                }
                            }
                        }

                        override fun onUnavailable() {
                            Log.d(WifiLogTag, "YouTube request onUnavailable ssid=$ssid")
                            mainHandler.post {
                                if (!finished) {
                                    startConnectionAttempt(index + 1)
                                }
                            }
                        }

                        override fun onLost(network: Network) {
                            Log.d(WifiLogTag, "YouTube request onLost ssid=$ssid network=$network")
                            mainHandler.post {
                                if (finished) {
                                    status = "WiFi接続が切れました。"
                                } else {
                                    startConnectionAttempt(index + 1)
                                }
                            }
                        }
                    }

                    runCatching {
                        val request = context.wifiNetworkRequest(
                            ssid = ssid,
                            password = candidate.password,
                        )
                        Log.d(
                            WifiLogTag,
                            "YouTube requestNetwork start ssid=$ssid passwordLength=${candidate.password.length}",
                        )
                        connectivityManager.requestNetwork(request, callback)
                        registeredCallback = callback
                    }.onFailure { throwable ->
                        Log.e(WifiLogTag, "YouTube requestNetwork failed to start ssid=$ssid", throwable)
                        status = throwable.message ?: "WiFi接続リクエストを開始できませんでした。"
                        startConnectionAttempt(index + 1)
                    }
                }

                startConnectionAttempt(0)

                onDispose {
                    finished = true
                    connectivityManager.bindProcessToNetwork(null)
                    unregisterActiveCallback()
                }
            }
        }
    }

    return status
}

private fun DrillUiState.youtubeWifiCandidates(): List<SavedWifiNetwork> {
    return (
        listOf(SavedWifiNetwork(youtubeWifiSsid, youtubeWifiPassword)) + savedYoutubeWifiNetworks
        )
        .map { network -> network.copy(ssid = network.ssid.trim()) }
        .filter { network -> network.ssid.isNotBlank() }
        .distinctBy { network -> network.ssid }
}

private fun Context.hasFineLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

private fun Context.missingWifiScanPermissions(): Array<String> {
    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    return requiredPermissions
        .filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        .toTypedArray()
}

@Suppress("DEPRECATION")
private fun Context.currentConnectedWifiSsid(): String? {
    return currentWifiConnection()?.ssid
}

@Suppress("DEPRECATION")
private fun Context.currentWifiConnection(): CurrentWifiConnection? {
    val missingPermissions = missingWifiScanPermissions()
    if (missingPermissions.isNotEmpty()) {
        Log.d(WifiLogTag, "Current wifi unavailable: missingPermissions=${missingPermissions.joinToString()}")
        return null
    }

    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return null
    val activeNetwork = connectivityManager.activeNetwork ?: return null
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        Log.d(WifiLogTag, "Current network is not wifi capabilities=$capabilities")
        return null
    }

    val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (capabilities.transportInfo as? WifiInfo)?.ssid
    } else {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.connectionInfo?.ssid
    }
        return CurrentWifiConnection(
            ssid = ssid?.normalizedSsid() ?: return null,
            network = activeNetwork,
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )
}

private fun Context.wifiNetworkRequest(
    ssid: String,
    password: String,
): NetworkRequest {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        error("この端末ではアプリからのWiFi接続リクエストに対応していません。")
    }

    val specifierBuilder = WifiNetworkSpecifier.Builder()
        .setSsid(ssid)
    val security = wifiSecurityForSsid(ssid)
    Log.d(WifiLogTag, "Build wifi request ssid=$ssid security=$security passwordLength=${password.length}")
    when {
        security == WifiSecurity.Open -> Unit
        password.length < 8 -> error("WiFiパスワードは8文字以上で入力してください。")
        security == WifiSecurity.Wpa3 -> specifierBuilder.setWpa3Passphrase(password)
        else -> specifierBuilder.setWpa2Passphrase(password)
    }
    return NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .setNetworkSpecifier(specifierBuilder.build())
        .build()
}

@Suppress("DEPRECATION")
private fun Context.wifiSecurityForSsid(ssid: String): WifiSecurity {
    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return WifiSecurity.Wpa2
    val scanResults = wifiManager.scanResults
    val capabilities = scanResults
        .firstOrNull { scanResult -> scanResult.SSID.normalizedSsid() == ssid }
        ?.capabilities
        .orEmpty()
    val security = when {
        "SAE" in capabilities -> WifiSecurity.Wpa3
        "PSK" in capabilities -> WifiSecurity.Wpa2
        "EAP" in capabilities || "WEP" in capabilities -> WifiSecurity.Wpa2
        capabilities.isBlank() -> WifiSecurity.Wpa2
        else -> WifiSecurity.Open
    }
    Log.d(
        WifiLogTag,
        "Security detected ssid=$ssid security=$security capabilities=$capabilities scanCount=${scanResults.size}",
    )
    return security
}

private enum class WifiSecurity {
    Open,
    Wpa2,
    Wpa3,
}

private data class CurrentWifiConnection(
    val ssid: String,
    val network: Network,
    val hasInternet: Boolean,
    val isValidated: Boolean,
)

@Suppress("DEPRECATION")
private fun Context.requestWifiScan(): Boolean {
    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return false
    return runCatching { wifiManager.startScan() }
        .onFailure { throwable -> Log.e(WifiLogTag, "startScan failed", throwable) }
        .getOrDefault(false)
}

@Suppress("DEPRECATION")
private fun Context.availableWifiSsids(): List<String> {
    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return emptyList()
    val scanResults = wifiManager.scanResults
    Log.d(WifiLogTag, "Read scanResults count=${scanResults.size}")
    return scanResults
        .mapNotNull { scanResult -> scanResult.SSID.normalizedSsid() }
        .distinct()
        .sorted()
}

private fun String.normalizedSsid(): String? {
    val ssid = trim().trim('"')
    return ssid.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
}

private fun DrillUiState.youtubeRewardTimeText(): String {
    return if (isYoutubeRewardUnlimited) {
        "無制限"
    } else {
        youtubeRewardAvailableSeconds.formatRewardTimeText()
    }
}

private fun Int.formatRewardTimeText(): String {
    val safeSeconds = coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return when {
        minutes == 0 -> "${seconds}秒"
        seconds == 0 -> "${minutes}分"
        else -> "${minutes}分${seconds}秒"
    }
}
