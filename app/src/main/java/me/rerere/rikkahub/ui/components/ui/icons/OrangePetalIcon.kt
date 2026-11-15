/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.ui.icons

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

val OrangePetalIcon: ImageVector
    get() {
        if (_orangePetalIcon != null) {
            return _orangePetalIcon!!
        }
        _orangePetalIcon = ImageVector.Builder(
            name = "LoveLetterPixelIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 21f,
            viewportHeight = 21f,
        ).apply {
            val bodyColor = Color(0xFF1A1718)
            val borderColor = Color(0xFFC8A6AE)
            val cornerColor = Color(0xFF9E7E86)
            val topFlapColor = Color(0xFF7D6268)
            val bottomFoldColor = Color(0xFF46373B)
            val heartPink = Color(0xFFF4A6B8)
            val heartHighlight = Color(0xFFFFE0E9)

            // 1. 像素信封深色底面
            pixelRect(2f, 4f, 17f, 13f, bodyColor)

            // 2. 像素信封圆角外框
            pixelRect(2f, 3f, 17f, 1f, borderColor)
            pixelRect(2f, 17f, 17f, 1f, borderColor)
            pixelRect(1f, 4f, 1f, 13f, borderColor)
            pixelRect(19f, 4f, 1f, 13f, borderColor)

            // 内侧四角过渡像素
            pixelRect(2f, 4f, 1f, 1f, cornerColor)
            pixelRect(18f, 4f, 1f, 1f, cornerColor)
            pixelRect(2f, 16f, 1f, 1f, cornerColor)
            pixelRect(18f, 16f, 1f, 1f, cornerColor)

            // 3. 信封盖折线（左上、右上向中心汇聚）
            for (i in 0 until 5) {
                pixelRect(3f + i, 5f + i, 1f, 1f, topFlapColor)
                pixelRect(17f - i, 5f + i, 1f, 1f, topFlapColor)
            }

            // 4. 信封底部暗折线（左下、右下）
            for (i in 0 until 4) {
                pixelRect(3f + i, 15f - i, 1f, 1f, bottomFoldColor)
                pixelRect(17f - i, 15f - i, 1f, 1f, bottomFoldColor)
            }

            // 5. 正中央封口像素小爱心（5x4 像素网格）
            pixelRect(9f, 8f, 1f, 1f, heartPink)
            pixelRect(11f, 8f, 1f, 1f, heartPink)
            pixelRect(8f, 9f, 5f, 1f, heartPink)
            pixelRect(9f, 10f, 3f, 1f, heartPink)
            pixelRect(10f, 11f, 1f, 1f, heartPink)

            // 爱心左上角与右上角浅粉白高光像素
            pixelRect(8f, 8f, 1f, 1f, heartHighlight)
            pixelRect(12f, 8f, 1f, 1f, heartHighlight)
        }.build()
        return _orangePetalIcon!!
    }

private var _orangePetalIcon: ImageVector? = null
