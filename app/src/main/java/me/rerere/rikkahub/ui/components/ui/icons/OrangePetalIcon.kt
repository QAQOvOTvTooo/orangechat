/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun ImageVector.Builder.pixelRect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    color: Color,
) {
    path(fill = SolidColor(color)) {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x + w, y + h)
        lineTo(x, y + h)
        close()
    }
}

/**
 * 透明底自适应像素情书图标：
 * - 信封内部完全透明，透出毛玻璃思考框底色；
 * - 信封外框与折线跟随思维链文字颜色 [envelopeColor] 自动适配；
 * - 正中央封口小爱心保持樱花粉与浅粉白高光。
 * - 19x15 零留白撑满画布，饱满清晰。
 */
fun buildLoveLetterIcon(envelopeColor: Color): ImageVector {
    val borderColor = envelopeColor.copy(alpha = 0.92f)
    val cornerColor = envelopeColor.copy(alpha = 0.75f)
    val topFlapColor = envelopeColor.copy(alpha = 0.68f)
    val bottomFoldColor = envelopeColor.copy(alpha = 0.38f)
    val heartPink = Color(0xFFF49AB0)
    val heartHighlight = Color(0xFFFFE2EB)

    return ImageVector.Builder(
        name = "LoveLetterPixelIcon",
        defaultWidth = 20.dp,
        defaultHeight = 16.dp,
        viewportWidth = 19f,
        viewportHeight = 15f,
    ).apply {
        // 1. 像素信封圆角外框（透明底，不填充黑色背景）
        pixelRect(1f, 0f, 17f, 1f, borderColor)
        pixelRect(1f, 14f, 17f, 1f, borderColor)
        pixelRect(0f, 1f, 1f, 13f, borderColor)
        pixelRect(18f, 1f, 1f, 13f, borderColor)

        // 内侧四角圆润过渡像素
        pixelRect(1f, 1f, 1f, 1f, cornerColor)
        pixelRect(17f, 1f, 1f, 1f, cornerColor)
        pixelRect(1f, 13f, 1f, 1f, cornerColor)
        pixelRect(17f, 13f, 1f, 1f, cornerColor)

        // 2. 信封盖折线（左上、右上向中心汇聚）
        for (i in 0 until 5) {
            pixelRect(2f + i, 2f + i, 1f, 1f, topFlapColor)
            pixelRect(16f - i, 2f + i, 1f, 1f, topFlapColor)
        }

        // 3. 信封底部折线（左下、右下）
        for (i in 0 until 4) {
            pixelRect(2f + i, 12f - i, 1f, 1f, bottomFoldColor)
            pixelRect(16f - i, 12f - i, 1f, 1f, bottomFoldColor)
        }

        // 4. 正中央封口像素小爱心（5x4 像素网格，居中于 x=7..12, y=5..9）
        pixelRect(8f, 5f, 1f, 1f, heartPink)
        pixelRect(10f, 5f, 1f, 1f, heartPink)
        pixelRect(7f, 6f, 5f, 1f, heartPink)
        pixelRect(8f, 7f, 3f, 1f, heartPink)
        pixelRect(9f, 8f, 1f, 1f, heartPink)

        // 爱心左上角与右上角浅粉白高光像素
        pixelRect(7f, 5f, 1f, 1f, heartHighlight)
        pixelRect(11f, 5f, 1f, 1f, heartHighlight)
    }.build()
}

@Composable
fun rememberLoveLetterIcon(envelopeColor: Color): ImageVector {
    return remember(envelopeColor) {
        buildLoveLetterIcon(envelopeColor)
    }
}

val OrangePetalIcon: ImageVector
    get() {
        if (_orangePetalIcon != null) {
            return _orangePetalIcon!!
        }
        _orangePetalIcon = buildLoveLetterIcon(Color(0xFFC8A6AE))
        return _orangePetalIcon!!
    }

private var _orangePetalIcon: ImageVector? = null
