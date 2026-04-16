package jp.komame.stopwatchgame

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import jp.komame.stopwatchgame.PlayerScore
import jp.komame.stopwatchgame.TitleScreen
import jp.komame.stopwatchgame.SetupScreen
import jp.komame.stopwatchgame.GameScreen
import jp.komame.stopwatchgame.ResultScreen

// --- 1. タイトル画面 (StartScreen) ---
@Preview(showSystemUi = true, device = Devices.PIXEL_7, name = "1_Start_Phone")
@Preview(showSystemUi = true, device = Devices.NEXUS_7, name = "1_Start_Tablet_7")
@Preview(showSystemUi = true, device = Devices.NEXUS_10, name = "1_Start_Tablet_10")
@Composable
fun PreviewTitleScreen() {
    TitleScreen(onStart = {})
}

// --- 2. セットアップ画面 (SetupScreen) ---
// 人数が多く、広告解放済みの「見栄えが良い」状態をプレビュー
@Preview(showSystemUi = true, device = Devices.PIXEL_7, name = "2_Setup_Phone")
@Preview(showSystemUi = true, device = Devices.NEXUS_7, name = "2_Setup_Tablet_7")
@Preview(showSystemUi = true, device = Devices.NEXUS_10, name = "2_Setup_Tablet_10")
@Composable
fun PreviewSetupScreen() {
    SetupScreen(
        playerCount = 10f,        // 10人設定を表示
        isLimitReleased = true,   // 広告解放済みの状態
        onPlayerCountChange = {},
        onReleaseLimit = {},
        onGoToSave = {}
    )
}

// --- 3. ゲーム中画面 (GameScreen) ---
// 計測中の緊張感が伝わる画面
@Preview(showSystemUi = true, device = Devices.PIXEL_7, name = "3_Game_Phone")
@Preview(showSystemUi = true, device = Devices.NEXUS_7, name = "3_Game_Tablet_7")
@Preview(showSystemUi = true, device = Devices.NEXUS_10, name = "3_Game_Tablet_10")
@Composable
fun PreviewGameScreen() {
    GameScreen(
        name = "プレイヤー 1",
        target = 12.0,
        hint = false,   // ヒント（秒数）が出ている状態
        onStop = {}
    )
}

// --- 4. 結果画面 (ResultScreen) ---
// 1位の「優勝」タブを表示して華やかに
@Preview(showSystemUi = true, device = Devices.PIXEL_7, name = "4_Result_Phone")
@Preview(showSystemUi = true, device = Devices.NEXUS_7, name = "4_Result_Tablet_7")
@Preview(showSystemUi = true, device = Devices.NEXUS_10, name = "4_Result_Tablet_10")
@Composable
fun PreviewResultScreen() {
    val mockScores = listOf(
        PlayerScore("こまめ", 10.0, 10.02),
        PlayerScore("ゲストA", 10.0, 11.50),
        PlayerScore("ゲストB", 10.0, 8.20)
    )
    ResultScreen(
        scores = mockScores,
        initialTab = 0, // 「🏆 1位」タブを選択状態にする
        onRestart = {}
    )
}

// --- 「ルール設定画面」を直接表示するプレビュー ---
@Preview(showSystemUi = true, device = Devices.PIXEL_7, name = "SaveConfig_Phone")
@Preview(showSystemUi = true, device = Devices.NEXUS_7, name = "SaveConfig_Tablet_7")
@Preview(showSystemUi = true, device = Devices.NEXUS_10, name = "SaveConfig_Tablet_10")
@Composable
fun PreviewSaveConfigScreen() {
    // 画像の状態を再現するためのダミーデータ
    SaveConfigScreen(
        playerNames = listOf("プレイヤー 1", "プレイヤー 2", "プレイヤー 3"),
        onNameChange = { _, _ -> },
        isRandomTime = true, // ランダムスイッチON
        onRandomTimeChange = {},
        isIndividualRandom = true, // 「バラバラ」を選択
        onIndividualRandomChange = {},
        randomRange = 20f..30f, // 範囲を20-30に設定
        onRandomRangeChange = {},
        manualTargetTime = 10f,
        onManualTargetTimeChange = {},
        showHint = true, // チェックボックスON
        onShowHintChange = {},
        onBack = {},
        onStartGame = {}
    )
}