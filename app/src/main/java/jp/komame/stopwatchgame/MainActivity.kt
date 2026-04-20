package jp.komame.stopwatchgame

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ==========================================
// データモデル
// ==========================================
data class PlayerScore(val name: String, val targetTime: Double, val actualTime: Double) {
    val diff: Double get() = abs(targetTime - actualTime)
}
data class GameHistory(val date: String, val results: List<HistoryDetail>)
data class HistoryDetail(val name: String, val target: Double, val actual: Double, val diff: Double)

class MainActivity : ComponentActivity() {
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 【修正】画面を縦向きに固定
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        MobileAds.initialize(this) {}
        loadRewardedAd()

        setContent {
            val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainApp(showRewardedAd = { onAdWatched -> showRewardedAdLogic(onAdWatched) })
                }
            }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
        })
    }

    private fun showRewardedAdLogic(onAdWatched: () -> Unit) {
        rewardedAd?.let { ad -> ad.show(this) { onAdWatched(); loadRewardedAd() } } ?: run {
            Toast.makeText(this, "広告準備中...", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }
}

// ==========================================
// 履歴保存
// ==========================================
fun saveHistory(context: Context, sortedScores: List<PlayerScore>) {
    val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    val historyList = prefs.getStringSet("history_v6", emptySet())?.toMutableList() ?: mutableListOf()
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val resultsStr = sortedScores.joinToString(",") { "${it.name}:${"%.1f".format(it.targetTime)}:${"%.2f".format(it.actualTime)}:${"%.2f".format(it.diff)}" }
    val newData = "${sdf.format(Date())};$resultsStr"
    historyList.add(0, newData)
    prefs.edit().putStringSet("history_v6", historyList.take(5).toSet()).apply()
}

fun getHistory(context: Context): List<GameHistory> {
    val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    return (prefs.getStringSet("history_v6", emptySet()) ?: emptySet()).mapNotNull { entry ->
        val mainParts = entry.split(";")
        if (mainParts.size == 2) {
            val results = mainParts[1].split(",").mapNotNull { res ->
                val p = res.split(":")
                if (p.size == 4) HistoryDetail(p[0], p[1].toDoubleOrNull() ?: 0.0, p[2].toDoubleOrNull() ?: 0.0, p[3].toDoubleOrNull() ?: 0.0) else null
            }
            GameHistory(mainParts[0], results)
        } else null
    }.sortedByDescending { it.date }
}

// ==========================================
// アプリ本体
// ==========================================
@Composable
fun MainApp(showRewardedAd: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("title") }
    var isLimitReleased by remember { mutableStateOf(false) }

    var playerCount by remember { mutableIntStateOf(3) }
    var playerNames by remember { mutableStateOf(List(10) { "プレイヤー ${it + 1}" }) }
    var isRnd by remember { mutableStateOf(false) }
    var isIndiv by remember { mutableStateOf(false) }
    var minT by remember { mutableIntStateOf(10) }
    var maxT by remember { mutableIntStateOf(30) }
    var manualT by remember { mutableIntStateOf(10) }
    var hintT by remember { mutableIntStateOf(5) }

    var scores by remember { mutableStateOf(listOf<PlayerScore>()) }
    var currentPlayerIdx by remember { mutableIntStateOf(0) }
    var currentTargetTime by remember { mutableDoubleStateOf(10.0) }
    var selectedResultTab by remember { mutableIntStateOf(0) }

    Scaffold(bottomBar = { AdmobBanner() }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                "title" -> TitleScreen { currentScreen = "setup" }
                "setup" -> SetupScreen(count = playerCount, isReleased = isLimitReleased, onCountChange = { playerCount = it }, onRelease = { showRewardedAd { isLimitReleased = true } }, onNext = { currentScreen = "config" })
                "config" -> SaveConfigScreen(
                    names = playerNames.take(playerCount),
                    onNameChange = { i, n -> val u = playerNames.toMutableList(); u[i] = n; playerNames = u },
                    isRnd = isRnd, onRndChange = { isRnd = it },
                    isIndiv = isIndiv, onIndivChange = { isIndiv = it },
                    minT = minT, onMinChange = { minT = it },
                    maxT = maxT, onMaxChange = { maxT = it },
                    manualT = manualT, onManualChange = { manualT = it },
                    hintT = hintT, onHintChange = { hintT = it },
                    onBack = { currentScreen = "setup" },
                    onStart = {
                        scores = emptyList(); currentPlayerIdx = 0
                        // 最小 > 最大 の場合に入れ替えるロジック
                        var finalMin = minT
                        var finalMax = maxT
                        if (isRnd && minT > maxT) {
                            finalMin = maxT
                            finalMax = minT
                        }
                        currentTargetTime = if (isRnd) (finalMin..finalMax).random().toDouble() else manualT.toDouble()
                        currentScreen = "ready"
                    }
                )
                "ready" -> ReadyScreen(playerNames[currentPlayerIdx], currentTargetTime) { currentScreen = "game" }
                "game" -> GameScreen(playerNames[currentPlayerIdx], currentTargetTime, hintT.toFloat()) { actual ->
                    val newScores = scores + PlayerScore(playerNames[currentPlayerIdx], currentTargetTime, actual)
                    scores = newScores
                    if (currentPlayerIdx < playerCount - 1) {
                        currentPlayerIdx++
                        if (isRnd && isIndiv) {
                            val finalMin = minOf(minT, maxT)
                            val finalMax = maxOf(minT, maxT)
                            currentTargetTime = (finalMin..finalMax).random().toDouble()
                        }
                        currentScreen = "ready"
                    } else {
                        saveHistory(context, newScores.sortedBy { it.diff })
                        currentScreen = "calculating"
                    }
                }
                "calculating" -> CalculatingScreen { currentScreen = "result_menu" }
                "result_menu" -> ResultMenuScreen { tab -> selectedResultTab = tab; currentScreen = "result" }
                "result" -> ResultScreen(scores, selectedResultTab) { isLimitReleased = false; playerCount = 3; currentScreen = "setup" }
            }
        }
    }
}

// ==========================================
// 画面パーツ
// ==========================================

@Composable
fun TitleScreen(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(0.3f, 1.0f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "alpha")
    Box(modifier = Modifier.fillMaxSize().clickable { onStart() }) {
        Image(painter = painterResource(id = R.drawable.bg_start), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Text("TAP TO START", Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).alpha(alpha), Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SetupScreen(count: Int, isReleased: Boolean, onCountChange: (Int) -> Unit, onRelease: () -> Unit, onNext: () -> Unit) {
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) HistoryDialog(onDismiss = { showHistory = false }, history = getHistory(LocalContext.current))

    Box(Modifier.fillMaxSize()) {
        TextButton(onClick = { showHistory = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.DateRange, null); Text("対戦履歴")
        }
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("参加人数を選択", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            DropdownSelector("人数", count, if (isReleased) (1..10).toList() else (1..3).toList(), "人", onCountChange)
            if (!isReleased) Button(onClick = onRelease, Modifier.padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Text("動画広告を見て10人まで解放", color = Color.White) }
            Button(onClick = onNext, Modifier.fillMaxWidth().height(64.dp).padding(top = 16.dp)) { Text("設定へ") }
        }
    }
}

@Composable
fun SaveConfigScreen(
    names: List<String>, onNameChange: (Int, String) -> Unit,
    isRnd: Boolean, onRndChange: (Boolean) -> Unit,
    isIndiv: Boolean, onIndivChange: (Boolean) -> Unit,
    minT: Int, onMinChange: (Int) -> Unit,
    maxT: Int, onMaxChange: (Int) -> Unit,
    manualT: Int, onManualChange: (Int) -> Unit,
    hintT: Int, onHintChange: (Int) -> Unit,
    onBack: () -> Unit, onStart: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("設定", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        itemsIndexed(names) { i, n -> OutlinedTextField(value = n, onValueChange = { onNameChange(i, it) }, label = { Text("プレイヤー ${i+1}") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("秒数をランダムにする", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f)); Switch(isRnd, onRndChange)
                    }
                    if (isRnd) {
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            DropdownSelector("最小", minT, (10..60).toList(), "秒", onMinChange)
                            DropdownSelector("最大", maxT, (10..60).toList(), "秒", onMaxChange)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(!isIndiv, { onIndivChange(false) }); Text("全員共通")
                            Spacer(Modifier.width(16.dp)); RadioButton(isIndiv, { onIndivChange(true) }); Text("バラバラ")
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        DropdownSelector("目標秒数", manualT, (10..60).toList(), "秒", onManualChange)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                // 【修正】中央に配置するために fillMaxWidth と Alignment を設定
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DropdownSelector("ヒント表示時間", hintT, (0..10).toList(), "秒", onHintChange)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onStart, Modifier.fillMaxWidth().height(60.dp)) { Text("ゲーム開始！", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("戻る") }
        }
    }
}

@Composable
fun GameScreen(name: String, target: Double, hintT: Float, onStop: (Double) -> Unit) {
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var isRunning by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRunning) { if (isRunning) { start = System.currentTimeMillis(); while (isRunning) { elapsed = (System.currentTimeMillis() - start) / 1000.0; delay(10) } } }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(name, fontSize = 24.sp); Text("目標: ${"%.0f".format(target)}秒", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        val displayTime = if (!isRunning && elapsed == 0.0) "0.00" else if (isRunning && elapsed <= hintT.toDouble()) "%.2f".format(elapsed) else "??.??"
        Text(displayTime, fontSize = 100.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = if (displayTime == "??.??") Color.Gray else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(60.dp))
        Button(onClick = { if (!isRunning) isRunning = true else onStop(elapsed) }, Modifier.size(220.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else Color.Green)) { Text(if (isRunning) "STOP" else "START", fontSize = 32.sp) }
    }
}

@Composable
fun DropdownSelector(label: String, current: Int, options: List<Int>, suffix: String, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Box(modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(150.dp)) {
                Text("$current$suffix"); Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text("$option$suffix") }, onClick = { onSelect(option); expanded = false }) }
            }
        }
    }
}

@Composable
fun ReadyScreen(name: String, target: Double, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("次は $name さん", fontSize = 24.sp); Text("${"%.0f".format(target)}秒", fontSize = 72.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(48.dp)); Button(onClick = onStart, Modifier.size(220.dp)) { Text("OK", fontSize = 32.sp) }
    }
}

@Composable
fun CalculatingScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(2000); onFinished() }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(24.dp)); Text("集計中...") }
}

@Composable
fun ResultMenuScreen(onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("✨ 結果発表 ✨", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp))
        Button(onClick = { onSelect(0) }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))) { Text("🏆 1位を発表！", fontSize = 20.sp, color = Color.Black) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSelect(1) }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2))) { Text("💀 最下位を発表...", fontSize = 20.sp, color = Color.Red) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { onSelect(2) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("📊 全員の順位を確認", fontSize = 18.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(scores: List<PlayerScore>, initialTab: Int, onRestart: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val sorted = scores.sortedBy { it.diff }
    Column(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (selectedTab) {
                0 -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("👑 優勝 👑", fontSize = 28.sp, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text(sorted.first().name, fontSize = 48.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    ResultCard(1, sorted.first(), isSpecial = true)
                }
                1 -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("😱 ドベ 😱", fontSize = 28.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text(sorted.last().name, fontSize = 48.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    ResultCard(scores.size, sorted.last(), isSpecial = true)
                }
                2 -> LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("📊 全員の順位", Modifier.padding(vertical = 16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    itemsIndexed(sorted) { i, s -> ResultCard(i + 1, s) }
                }
            }
        }
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            listOf("🏆 1位", "💀 最下位", "📊 全員").forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) }) }
        }
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)) { Text("トップに戻る") }
    }
}

@Composable
fun ResultCard(rank: Int, score: PlayerScore, isSpecial: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = if (isSpecial) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${rank}位", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
            Column(Modifier.weight(1f)) {
                if (!isSpecial) Text(score.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("目標: ${"%.1f".format(score.targetTime)}s / 計測: ${"%.2f".format(score.actualTime)}s", fontSize = 14.sp)
            }
            Text("${"%.2f".format(score.diff)}s差", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.Red)
        }
    }
}

@Composable
fun HistoryDialog(onDismiss: () -> Unit, history: List<GameHistory>) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(Modifier.padding(16.dp)) {
                Text("📊 履歴 (直近5試合)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                if (history.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("履歴なし") }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(history) { _, game ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("📅 ${game.date}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    game.results.forEachIndexed { i, p ->
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("${i + 1}位: ${p.name}", fontSize = 14.sp)
                                            Text("${"%.2f".format(p.diff)}s差", color = Color.Red, fontSize = 14.sp)
                                        }
                                        Text(" (目標: ${"%.1f".format(p.target)}s / 計測: ${"%.2f".format(p.actual)}s)", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, Modifier.align(Alignment.End)) { Text("閉じる") }
            }
        }
    }
}

@Composable
fun AdmobBanner() {
    AndroidView(modifier = Modifier.fillMaxWidth().height(50.dp), factory = { context ->
        AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId = "ca-app-pub-3940256099942544/6300978111"; loadAd(AdRequest.Builder().build()) }
    })
}