package jp.komame.stopwatchgame

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import jp.komame.stopwatchgame.deviceName

const val deviceName =
    Devices.PIXEL_TABLET
    //Devices.NEXUS_5
    //Devices.NEXUS_5

@Preview(showSystemUi = true, device = deviceName, name = "1_タイトル画面")
@Composable
fun PreviewTitleScreen() {
    TitleScreen(onStart = {})
}

//@Preview(showSystemUi = true, device = deviceName, name = "2_人数選択画面")
//@Composable
//fun PreviewSetupScreen() {
//    SetupScreen(
//        count = 3,
//        isReleased = true,
//        onCountChange = {},
//        onRelease = {},
//        onNext = {}
//    )
//}

@Preview(showSystemUi = true, device = deviceName, name = "3_詳細設定画面")
@Composable
fun PreviewSaveConfigScreen() {
    SaveConfigScreen(
        names = listOf("プレイヤー 1", "プレイヤー 2", "プレイヤー 3"),
        onNameChange = { _, _ -> },
        isRnd = true,
        onRndChange = {},
        isIndiv = true,
        onIndivChange = {},
        minT = 10,
        onMinChange = {},
        maxT = 30,
        onMaxChange = {},
        manualT = 10,
        onManualChange = {},
        hintT = 5,
        onHintChange = {},
        onBack = {},
        onStart = {}
    )
}

//@Preview(showSystemUi = true, device = deviceName, name = "4_準備画面")
//@Composable
//fun PreviewReadyScreen() {
//    ReadyScreen(name = "プレイヤー 1", target = 10.0, onStart = {})
//}

@Preview(showSystemUi = true, device = deviceName, name = "5_計測画面")
@Composable
fun PreviewGameScreen() {
    GameScreen(
        name = "プレイヤー 1",
        target = 10.0,
        hintT = 5f,
        onStop = {}
    )
}

@Preview(showSystemUi = true, device = deviceName, name = "7_結果メニュー画面")
@Composable
fun PreviewResultMenuScreen() {
    ResultMenuScreen(onSelect = {})
}

@Preview(showSystemUi = true, device = deviceName, name = "8_結果詳細画面")
@Composable
fun PreviewResultScreen() {
    ResultScreen(
        scores = listOf(
            PlayerScore("プレイヤー 1", 10.0, 10.02),
            PlayerScore("プレイヤー 2", 10.0, 10.50),
            PlayerScore("プレイヤー 3", 10.0, 11.18),
        ),
        initialTab = 0,
        onRestart = {}
    )
}

@Preview(showSystemUi = true, device = deviceName, name = "8_結果詳細画面(どべ)")
@Composable
fun PreviewResultScreen2() {
    ResultScreen(
        scores = listOf(
            PlayerScore("プレイヤー 1", 10.0, 10.02),
            PlayerScore("プレイヤー 2", 10.0, 10.50),
            PlayerScore("プレイヤー 3", 10.0, 11.18),
        ),
        initialTab = 1,
        onRestart = {}
    )
}

@Preview(showSystemUi = true, device = deviceName, name = "8_結果詳細画面(全員)")
@Composable
fun PreviewResultScreen3() {
    ResultScreen(
        scores = listOf(
            PlayerScore("プレイヤー 1", 10.0, 10.02),
            PlayerScore("プレイヤー 2", 10.0, 10.50),
            PlayerScore("プレイヤー 3", 10.0, 11.18),
        ),
        initialTab = 2,
        onRestart = {}
    )
}