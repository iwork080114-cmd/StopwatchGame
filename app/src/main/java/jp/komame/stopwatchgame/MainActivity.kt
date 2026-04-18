package jp.komame.stopwatchgame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
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
import kotlin.math.round

// --- データモデル ---
data class PlayerScore(val name: String, val targetTime: Double, val actualTime: Double) {
    val diff: Double get() = abs(targetTime - actualTime)
}

data class GameHistory(val date: String, val results: List<Pair<String, Double>>)

class MainActivity : ComponentActivity() {
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        loadRewardedAd()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
        rewardedAd?.let { ad ->
            ad.show(this) { onAdWatched(); loadRewardedAd() }
        } ?: run {
            Toast.makeText(this, "広告準備中...", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }
}

// --- 履歴管理ロジック (全員の順位を保存するように変更) ---
fun saveHistory(context: Context, sortedScores: List<PlayerScore>) {
    val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    val historySet = prefs.getStringSet("history_v2", emptySet())?.toMutableList() ?: mutableListOf()
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    // 形式: 日付;名前:差,名前:差...
    val resultsStr = sortedScores.joinToString(",") { "${it.name}:${"%.2f".format(it.diff)}" }
    val newData = "${sdf.format(Date())};$resultsStr"

    historySet.add(0, newData)
    // 直近5試合分を保持
    prefs.edit().putStringSet("history_v2", historySet.take(5).toSet()).apply()
}

fun getHistory(context: Context): List<GameHistory> {
    val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    val historySet = prefs.getStringSet("history_v2", emptySet()) ?: emptySet()
    return historySet.mapNotNull {
        val mainParts = it.split(";")
        if (mainParts.size == 2) {
            val date = mainParts[0]
            val results = mainParts[1].split(",").mapNotNull { res ->
                val pair = res.split(":")
                if (pair.size == 2) pair[0] to pair[1].toDouble() else null
            }
            GameHistory(date, results)
        } else null
    }.sortedByDescending { it.date }
}

@Composable
fun MainApp(showRewardedAd: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("title") }
    var isLimitReleased by remember { mutableStateOf(false) }

    var playerCount by remember { mutableFloatStateOf(3f) }
    var playerNames by remember { mutableStateOf(List(3) { "プレイヤー ${it + 1}" }) }
    var isRandomTime by remember { mutableStateOf(false) }
    var isIndividualRandom by remember { mutableStateOf(false) }
    var randomRange by remember { mutableStateOf(10f..30f) }
    var manualTargetTime by remember { mutableFloatStateOf(10f) }
    var hintDuration by remember { mutableFloatStateOf(5f) }

    var scores by remember { mutableStateOf(listOf<PlayerScore>()) }
    var currentPlayerIdx by remember { mutableIntStateOf(0) }
    var currentTargetTime by remember { mutableDoubleStateOf(10.0) }
    var selectedResultTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = { AdmobBanner(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(top = 2.dp)) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                "title" -> TitleScreen { currentScreen = "setup" }
                "setup" -> SetupScreen(
                    playerCount = playerCount,
                    isLimitReleased = isLimitReleased,
                    onPlayerCountChange = {
                        playerCount = it
                        val newCount = it.toInt()
                        if (newCount > playerNames.size) {
                            playerNames = playerNames + List(newCount - playerNames.size) { i -> "プレイヤー ${playerNames.size + i + 1}" }
                        }
                    },
                    onReleaseLimit = { showRewardedAd { isLimitReleased = true } },
                    onGoToSave = { currentScreen = "save_config" }
                )
                "save_config" -> SaveConfigScreen(
                    playerNames = playerNames.take(playerCount.toInt()),
                    onNameChange = { i, n -> val l = playerNames.toMutableList(); l[i] = n; playerNames = l },
                    isRandomTime = isRandomTime, onRandomTimeChange = { isRandomTime = it },
                    isIndividualRandom = isIndividualRandom, onIndividualRandomChange = { isIndividualRandom = it },
                    randomRange = randomRange, onRandomRangeChange = { randomRange = it },
                    manualTargetTime = manualTargetTime, onManualTargetTimeChange = { manualTargetTime = it },
                    hintDuration = hintDuration, onHintDurationChange = { hintDuration = it },
                    onBack = { currentScreen = "setup" },
                    onStartGame = {
                        scores = emptyList(); currentPlayerIdx = 0
                        currentTargetTime = if (isRandomTime) (randomRange.start.toInt()..randomRange.endInclusive.toInt()).random().toDouble() else manualTargetTime.toDouble()
                        currentScreen = "ready"
                    }
                )
                "ready" -> ReadyScreen(playerNames[currentPlayerIdx], currentTargetTime) { currentScreen = "game" }
                "game" -> GameScreen(playerNames[currentPlayerIdx], currentTargetTime, hintDuration) { actual ->
                    val newScores = scores + PlayerScore(playerNames[currentPlayerIdx], currentTargetTime, actual)
                    scores = newScores
                    if (currentPlayerIdx < playerCount.toInt() - 1) {
                        currentPlayerIdx++
                        if (isRandomTime && isIndividualRandom) currentTargetTime = (randomRange.start.toInt()..randomRange.endInclusive.toInt()).random().toDouble()
                        currentScreen = "ready"
                    } else {
                        // 全員のスコアを順位順にして履歴保存
                        saveHistory(context, newScores.sortedBy { it.diff })
                        currentScreen = "calculating"
                    }
                }
                "calculating" -> CalculatingScreen { currentScreen = "result_menu" }
                "result_menu" -> ResultMenuScreen { selectedResultTab = it; currentScreen = "result" }
                "result" -> ResultScreen(scores, selectedResultTab) { isLimitReleased = false; playerCount = 3f; currentScreen = "setup" }
            }
        }
    }
}

@Composable
fun TitleScreen(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse), label = "alpha"
    )
    Box(modifier = Modifier.fillMaxSize().clickable { onStart() }) {
        Image(painter = painterResource(id = R.drawable.bg_start), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 60.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TAP TO START", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.alpha(alpha), style = MaterialTheme.typography.headlineMedium.copy(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 15f)))
        }
    }
}

@Composable
fun SetupScreen(playerCount: Float, isLimitReleased: Boolean, onPlayerCountChange: (Float) -> Unit, onReleaseLimit: () -> Unit, onGoToSave: () -> Unit) {
    var showHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (showHistory) HistoryDialog(onDismiss = { showHistory = false }, history = getHistory(context))

    Box(Modifier.fillMaxSize()) {
        IconButton(onClick = { showHistory = true }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = "History", modifier = Modifier.size(28.dp))
        }
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("参加人数を選択", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(48.dp))
            val maxVal = if (isLimitReleased) 10f else 3f
            Text("参加人数: ${playerCount.toInt()}人", fontSize = 20.sp)
            Slider(value = playerCount, onValueChange = onPlayerCountChange, valueRange = 1f..maxVal, steps = if (maxVal > 1f) (maxVal - 2).toInt() else 0)
            if (!isLimitReleased) Button(onClick = onReleaseLimit, Modifier.padding(vertical = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) { Text("動画広告を見て10人まで解放") }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGoToSave, Modifier.fillMaxWidth().height(64.dp)) { Text("名前とルールの設定へ") }
        }
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 20.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/komame-stopwatch-pp/"))) }) {
                Text("プライバシーポリシー", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
fun HistoryDialog(onDismiss: () -> Unit, history: List<GameHistory>) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Column(Modifier.padding(16.dp)) {
                Text("📊 直近5試合の結果", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                if (history.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("まだ履歴がありません") }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(history) { _, game ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text("📅 ${game.date}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                game.results.forEachIndexed { index, player ->
                                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${index + 1}位: ${player.first}", fontSize = 14.sp, fontWeight = if(index == 0) FontWeight.Bold else FontWeight.Normal)
                                        Text("${"%.2f".format(player.second)}s差", fontSize = 14.sp)
                                    }
                                }
                                HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 0.5.dp)
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
fun SaveConfigScreen(playerNames: List<String>, onNameChange: (Int, String) -> Unit, isRandomTime: Boolean, onRandomTimeChange: (Boolean) -> Unit, isIndividualRandom: Boolean, onIndividualRandomChange: (Boolean) -> Unit, randomRange: ClosedFloatingPointRange<Float>, onRandomRangeChange: (ClosedFloatingPointRange<Float>) -> Unit, manualTargetTime: Float, onManualTargetTimeChange: (Float) -> Unit, hintDuration: Float, onHintDurationChange: (Float) -> Unit, onBack: () -> Unit, onStartGame: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("ルール設定", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        itemsIndexed(playerNames) { i, n -> OutlinedTextField(value = n, onValueChange = { onNameChange(i, it) }, label = { Text("プレイヤー ${i+1}") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("秒数をランダムにする"); Spacer(Modifier.weight(1f)); Switch(isRandomTime, onRandomTimeChange) }
                    if (isRandomTime) {
                        Text("範囲：${randomRange.start.toInt()}〜${randomRange.endInclusive.toInt()}秒")
                        RangeSlider(value = randomRange, onValueChange = { onRandomRangeChange(round(it.start)..round(it.endInclusive)) }, valueRange = 10f..60f, steps = 49)
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!isIndividualRandom, { onIndividualRandomChange(false) }); Text("全員共通"); Spacer(Modifier.width(8.dp)); RadioButton(isIndividualRandom, { onIndividualRandomChange(true) }); Text("バラバラ") }
                    } else {
                        Text("目標：${manualTargetTime.toInt()}秒")
                        Slider(value = manualTargetTime, onValueChange = { onManualTargetTimeChange(round(it)) }, valueRange = 10f..60f, steps = 49)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("ヒント表示時間: ${hintDuration.toInt()}秒", fontWeight = FontWeight.Bold)
                    Slider(value = hintDuration, onValueChange = { onHintDurationChange(round(it)) }, valueRange = 0f..10f, steps = 9)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onStartGame, Modifier.fillMaxWidth().height(60.dp)) { Text("この設定でゲーム開始！") }
            TextButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("人数設定に戻る") }
        }
    }
}

@Composable
fun ReadyScreen(name: String, target: Double, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("次は $name さんの番", fontSize = 24.sp)
        Text("${"%.0f".format(target)}秒", fontSize = 72.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onStart, Modifier.size(220.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("準備OK!", fontSize = 32.sp); Text("※スタートではありません", fontSize = 12.sp) } }
    }
}

@Composable
fun GameScreen(name: String, target: Double, hintDuration: Float, onStop: (Double) -> Unit) {
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var isRunning by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRunning) { if (isRunning) { start = System.currentTimeMillis(); while (isRunning) { elapsed = (System.currentTimeMillis() - start) / 1000.0; delay(10) } } }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(name, fontSize = 24.sp); Text("目標: ${"%.0f".format(target)}秒", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        val show = isRunning && elapsed <= hintDuration
        Text(if (show) "%.2f".format(elapsed) else "??.??", fontSize = 100.sp, color = if (show) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(60.dp))
        Button(onClick = { if (!isRunning) isRunning = true else onStop(elapsed) }, Modifier.size(200.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else Color.Green)) { Text(if (isRunning) "STOP" else "START", fontSize = 32.sp, color = Color.White) }
    }
}

@Composable
fun CalculatingScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(2000); onFinished() }
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator(modifier = Modifier.size(64.dp)); Spacer(Modifier.height(24.dp)); Text("結果を集計中...", fontSize = 20.sp) }
}

@Composable
fun ResultMenuScreen(onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("✨ 結果発表 ✨", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp))
        Button(onClick = { onSelect(0) }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))) { Text("🏆 1位（優勝）を発表！", fontSize = 20.sp, color = Color.Black) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSelect(1) }, modifier = Modifier.fillMaxWidth().height(80.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2))) { Text("💀 最下位（ドベ）を発表...", fontSize = 20.sp, color = Color.Red) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { onSelect(2) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("📊 全員の順位を確認", fontSize = 18.sp) }
    }
}

@Composable
fun ResultScreen(scores: List<PlayerScore>, initialTab: Int, onRestart: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val sorted = scores.sortedBy { it.diff }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("🏆 1位", "💀 最下位", "📊 全員").forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) }) }
            }
        }
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (selectedTab) {
                0 -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text("👑 優勝 👑", fontSize = 36.sp, color = Color(0xFFD4AF37), fontWeight = FontWeight.Black); Spacer(Modifier.height(16.dp)); ResultCard(1, sorted.first(), isSpecial = true) }
                1 -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text("😱 ドベ 😱", fontSize = 36.sp, color = Color.Red, fontWeight = FontWeight.Black); Spacer(Modifier.height(16.dp)); ResultCard(scores.size, sorted.last(), isSpecial = true) }
                2 -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(sorted) { i, s -> ResultCard(i + 1, s) } }
            }
        }
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("タイトルへ戻る", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ResultCard(rank: Int, score: PlayerScore, isSpecial: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isSpecial) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(if (isSpecial) 8.dp else 2.dp), border = if (isSpecial) BorderStroke(2.dp, Color(0xFFFFD700)) else null) {
        Row(Modifier.padding(if (isSpecial) 24.dp else 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("${rank}位: ${score.name}", fontWeight = FontWeight.Black, fontSize = if (isSpecial) 24.sp else 18.sp); Text("目標: ${"%.0f".format(score.targetTime)}s / 実測: ${"%.2f".format(score.actualTime)}s", fontSize = 14.sp) }
            Text("${"%.2f".format(score.diff)}s差", fontWeight = FontWeight.Bold, color = if (score.diff < 0.5) Color(0xFF4CAF50) else Color.Red, fontSize = if (isSpecial) 22.sp else 16.sp)
        }
    }
}

@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    AndroidView(modifier = modifier.fillMaxWidth().height(50.dp), factory = { context -> AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId = "ca-app-pub-3940256099942544/6300978111"; loadAd(AdRequest.Builder().build()) } })
}