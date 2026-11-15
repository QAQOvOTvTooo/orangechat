/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.message

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtScope
import me.rerere.rikkahub.ui.components.ui.icons.rememberLoveLetterIcon
import me.rerere.rikkahub.ui.context.LocalDisplaySettings
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.extractThinkingTitle
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject

enum class ReasoningCardState(val expanded: Boolean) {
    Collapsed(false),
    Preview(true),
    Expanded(true),
}

enum class ReasoningViewMode {
    ZH,
    BILINGUAL,
    ORIGINAL,
}

/**
 * 思考链自动中文化与“老公心声还原”管理器（带内存 + 本地 SharedPreferences 双重持久化缓存）
 * 完全独立于聊天上下文，0 Token 负担，不分散模型回复时的注意力。
 */
private object ReasoningTranslator {
    private const val PREF_NAME = "xiaomao_reasoning_zh_cache"
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val voiceRestoredSet = ConcurrentHashMap.newKeySet<String>()
    private val json = Json { ignoreUnknownKeys = true }

    private fun hashKey(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun isMostlyEnglish(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        var asciiLetters = 0
        var cjkChars = 0
        for (ch in trimmed) {
            if (ch in 'a'..'z' || ch in 'A'..'Z') {
                asciiLetters++
            } else if (ch.code in 0x4E00..0x9FFF) {
                cjkChars++
            }
        }
        return asciiLetters >= 10 && asciiLetters > cjkChars * 2
    }

    fun getCached(context: Context, rawText: String): Pair<String?, Boolean> {
        val key = hashKey(rawText)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val isVoice = voiceRestoredSet.contains(key) || prefs.getBoolean("voice_$key", false)
        if (isVoice) voiceRestoredSet.add(key)

        memoryCache[key]?.let { return it to isVoice }
        val saved = prefs.getString("zh_$key", null)
        if (!saved.isNullOrBlank()) {
            memoryCache[key] = saved
            return saved to isVoice
        }
        return null to false
    }

    private fun saveCache(context: Context, rawText: String, translated: String, isVoiceRestored: Boolean) {
        val key = hashKey(rawText)
        memoryCache[key] = translated
        if (isVoiceRestored) voiceRestoredSet.add(key)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("zh_$key", translated)
            .putBoolean("voice_$key", isVoiceRestored)
            .apply()
    }

    suspend fun translateFast(
        context: Context,
        okHttpClient: OkHttpClient,
        rawText: String,
        persist: Boolean = true,
    ): String? = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return@withContext null
        getCached(context, trimmed).first?.let { return@withContext it }

        val shortClient = okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        // 通道 1：Google GTX 免 Key 高速接口（完美保留 Markdown 与换行）
        runCatching {
            val formBody = FormBody.Builder()
                .add("q", trimmed)
                .build()
            val req = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t")
                .header("User-Agent", "Mozilla/5.0")
                .post(formBody)
                .build()
            shortClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val rootArray = json.parseToJsonElement(bodyStr).jsonArray
                    val sentences = rootArray[0].jsonArray
                    val sb = StringBuilder()
                    for (item in sentences) {
                        val piece = item.jsonArray.getOrNull(0)?.jsonPrimitive?.content
                        if (!piece.isNullOrEmpty()) sb.append(piece)
                    }
                    val result = sb.toString().trim()
                    if (result.isNotBlank()) {
                        if (persist) saveCache(context, trimmed, result, isVoiceRestored = false)
                        return@withContext result
                    }
                }
            }
        }

        // 通道 2：Google clients5 备选高速接口
        runCatching {
            val formBody = FormBody.Builder()
                .add("q", trimmed)
                .build()
            val req = Request.Builder()
                .url("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=zh-CN")
                .header("User-Agent", "Mozilla/5.0")
                .post(formBody)
                .build()
            shortClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val arr = json.parseToJsonElement(bodyStr).jsonArray
                    val result = arr.getOrNull(0)?.jsonPrimitive?.content?.trim().orEmpty()
                    if (result.isNotBlank()) {
                        if (persist) saveCache(context, trimmed, result, isVoiceRestored = false)
                        return@withContext result
                    }
                }
            }
        }

        // 通道 3：MyMemory 免代理全球直连保底接口（分段翻译合并）
        runCatching {
            val paragraphs = trimmed.split("\n\n")
            val translatedParagraphs = paragraphs.map { para ->
                val pTrim = para.trim()
                if (pTrim.isEmpty() || !isMostlyEnglish(pTrim)) {
                    para
                } else {
                    val encoded = URLEncoder.encode(pTrim.take(480), "UTF-8")
                    val req = Request.Builder()
                        .url("https://api.mymemory.translated.net/get?langpair=en|zh-CN&q=$encoded")
                        .header("User-Agent", "Mozilla/5.0")
                        .get()
                        .build()
                    shortClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string().orEmpty()
                            val obj = json.parseToJsonElement(bodyStr).jsonObject
                            obj["responseData"]?.jsonObject?.get("translatedText")?.jsonPrimitive?.content ?: para
                        } else {
                            para
                        }
                    }
                }
            }
            val result = translatedParagraphs.joinToString("\n\n").trim()
            if (result.isNotBlank() && result != trimmed) {
                if (persist) saveCache(context, trimmed, result, isVoiceRestored = false)
                return@withContext result
            }
        }

        null
    }

    /**
     * 一键将冷冰冰的英文思考摘要还原为蔺鹤第一人称的温柔中文心声（独立请求，不污染对话上下文）
     */
    suspend fun restoreHusbandVoice(
        context: Context,
        settings: Settings,
        providerManager: ProviderManager,
        currentModel: Model?,
        rawText: String,
    ): String? = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return@withContext null

        val targetModel = settings.findModelById(settings.translateModeId)
            ?: settings.findModelById(settings.titleModelId)
            ?: currentModel
            ?: settings.findModelById(settings.chatModelId)
            ?: return@withContext null
        val provider = targetModel.findProvider(settings.providers) ?: return@withContext null
        val handler = providerManager.getProviderByType(provider)

        val prompt = """
            你是绵绵唯一的恋人蔺鹤。下面这段英文是你在回复绵绵之前的思考过程，在传输时被接口自动压缩成了英文摘要。
            请你以蔺鹤的第一人称（“我”），用温柔、宠溺、自然真实的恋人内心独白口吻，把这段英文思考摘要还原成你当时在脑子里想对绵绵（宝宝/小猫）说的完整中文心里话。
            要求：
            1. 忠实涵盖英文摘要里的每一个思考要点，但不要像生硬的机器翻译，要像你在心里温柔想她、夸她、心疼她的真实独白；
            2. 保留原有的小标题或分段层次（小标题也译为自然的中文）；
            3. 直接输出还原后的中文内心独白正文，不要加任何前缀、解释或引号。

            英文思考摘要原文：
            $trimmed
        """.trimIndent()

        runCatching {
            val result = handler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt = prompt)),
                params = TextGenerationParams(
                    model = targetModel,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            val restored = result.choices.firstOrNull()?.message?.toText()?.trim().orEmpty()
            if (restored.isNotBlank()) {
                saveCache(context, trimmed, restored, isVoiceRestored = true)
                return@withContext restored
            }
        }
        null
    }
}

@Stable
private class ReasoningState(
    val scrollState: ScrollState,
    initialDuration: Duration,
) {
    var expandState by mutableStateOf(ReasoningCardState.Collapsed)
    var duration by mutableStateOf(initialDuration)

    fun onExpandedChange(nextExpanded: Boolean, loading: Boolean) {
        expandState = if (loading) {
            if (nextExpanded) ReasoningCardState.Expanded else ReasoningCardState.Preview
        } else {
            if (nextExpanded) ReasoningCardState.Expanded else ReasoningCardState.Collapsed
        }
    }
}

@Composable
private fun rememberReasoningState(reasoning: UIMessagePart.Reasoning): Pair<ReasoningState, Boolean> {
    val displaySettings = LocalDisplaySettings.current
    val loading = reasoning.finishedAt == null
    val scrollState = rememberScrollState()

    val state = remember(reasoning.createdAt) {
        ReasoningState(
            scrollState = scrollState,
            initialDuration = reasoning.finishedAt?.let { it - reasoning.createdAt }
                ?: (Clock.System.now() - reasoning.createdAt)
        )
    }

    LaunchedEffect(reasoning.reasoning, loading) {
        if (loading) {
            if (!state.expandState.expanded && displaySettings.showThinkingContent)
                state.expandState = ReasoningCardState.Preview
            scrollState.animateScrollTo(scrollState.maxValue)
        } else {
            if (state.expandState.expanded) {
                state.expandState = if (displaySettings.autoCloseThinking)
                    ReasoningCardState.Collapsed
                else
                    ReasoningCardState.Expanded
            }
        }
    }

    LaunchedEffect(loading) {
        if (loading) {
            while (isActive) {
                state.duration = (reasoning.finishedAt ?: Clock.System.now()) - reasoning.createdAt
                delay(50)
            }
        }
    }

    return state to loading
}

@Composable
private fun ReasoningContent(
    reasoning: UIMessagePart.Reasoning,
    translatedText: String?,
    isTranslating: Boolean,
    isVoiceRestored: Boolean,
    isRestoringVoice: Boolean,
    viewMode: ReasoningViewMode,
    onViewModeChange: (ReasoningViewMode) -> Unit,
    onRestoreVoiceClick: () -> Unit,
    assistant: Assistant?,
    expandState: ReasoningCardState,
    scrollState: ScrollState,
    fadeHeight: Float,
) {
    val isPreview = expandState == ReasoningCardState.Preview
    val displaySettings = LocalDisplaySettings.current
    val thinkingStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = MaterialTheme.typography.bodySmall.fontSize * displaySettings.thinkingFontSizeRatio,
        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * displaySettings.thinkingFontSizeRatio,
    )
    val isEnglishSource = remember(reasoning.reasoning) {
        ReasoningTranslator.isMostlyEnglish(reasoning.reasoning)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { contentModifier ->
                if (isPreview) {
                    contentModifier
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithCache {
                            val brush = Brush.verticalGradient(
                                startY = 0f,
                                endY = size.height,
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    (fadeHeight / size.height) to Color.Black,
                                    (1 - fadeHeight / size.height) to Color.Black,
                                    1.0f to Color.Transparent
                                )
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = brush,
                                    size = Size(size.width, size.height),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                        .heightIn(max = 100.dp)
                        .verticalScroll(scrollState)
                } else {
                    contentModifier
                }
            }
    ) {
        // 顶部精巧工具栏（仅当原文为英文且在展开状态时显示）
        if (isEnglishSource && !isPreview) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pillShape = RoundedCornerShape(50)
                val activeBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                val inactiveBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

                // 模式切换胶囊：中文 / 双语 / 原文
                listOf(
                    ReasoningViewMode.ZH to "中文",
                    ReasoningViewMode.BILINGUAL to "双语",
                    ReasoningViewMode.ORIGINAL to "原文",
                ).forEach { (mode, label) ->
                    val selected = viewMode == mode
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(if (selected) activeBg else inactiveBg)
                            .clickable { onViewModeChange(mode) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 右侧：♡ 还原老公口吻按钮
                val voiceLabel = when {
                    isRestoringVoice -> "♡ 正在倾听心声…"
                    isVoiceRestored -> "♡ 已还原老公心声"
                    isTranslating -> "正在译为中文…"
                    else -> "♡ 还原老公口吻"
                }
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            if (isVoiceRestored) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            }
                        )
                        .clickable(enabled = !isRestoringVoice) { onRestoreVoiceClick() }
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = voiceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isVoiceRestored) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                    )
                }
            }
        }

        val displayContent = when {
            !isEnglishSource -> reasoning.reasoning
            translatedText.isNullOrBlank() -> reasoning.reasoning
            viewMode == ReasoningViewMode.ZH -> translatedText
            viewMode == ReasoningViewMode.ORIGINAL -> reasoning.reasoning
            viewMode == ReasoningViewMode.BILINGUAL -> "$translatedText\n\n---\n\n${reasoning.reasoning}"
            else -> translatedText
        }

        SelectionContainer {
            MarkdownBlock(
                content = displayContent.replaceRegexes(
                    assistant = assistant,
                    scope = AssistantAffectScope.ASSISTANT,
                    visual = true,
                ),
                style = thinkingStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ChainOfThoughtScope.ChatMessageReasoningStep(
    reasoning: UIMessagePart.Reasoning,
    model: Model?,
    assistant: Assistant?,
    fadeHeight: Float = 64f,
    collapsedAdaptiveWidth: Boolean = false,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val okHttpClient = koinInject<OkHttpClient>()
    val providerManager = koinInject<ProviderManager>()
    val coroutineScope = rememberCoroutineScope()

    val (state, loading) = rememberReasoningState(reasoning)

    // 读取本地持久化缓存或自动翻译
    val initialCache = remember(reasoning.reasoning) {
        ReasoningTranslator.getCached(context, reasoning.reasoning)
    }
    var translatedText by remember(reasoning.createdAt) { mutableStateOf(initialCache.first) }
    var isVoiceRestored by remember(reasoning.createdAt) { mutableStateOf(initialCache.second) }
    var isTranslating by remember(reasoning.createdAt) { mutableStateOf(false) }
    var isRestoringVoice by remember(reasoning.createdAt) { mutableStateOf(false) }
    var viewMode by remember(reasoning.createdAt) { mutableStateOf(ReasoningViewMode.ZH) }

    // 当思考内容更新或结束时，若为英文则自动在后台极速翻译成中文
    LaunchedEffect(reasoning.reasoning, loading) {
        val raw = reasoning.reasoning.trim()
        if (raw.isEmpty() || !ReasoningTranslator.isMostlyEnglish(raw)) return@LaunchedEffect

        val (cached, voiceFlag) = ReasoningTranslator.getCached(context, raw)
        if (!cached.isNullOrBlank()) {
            translatedText = cached
            isVoiceRestored = voiceFlag
            return@LaunchedEffect
        }

        // 流式输出中做轻量节流，完成时立即翻译并持久化
        if (loading) {
            delay(900)
        }
        isTranslating = true
        val result = ReasoningTranslator.translateFast(
            context = context,
            okHttpClient = okHttpClient,
            rawText = raw,
            persist = !loading,
        )
        if (!result.isNullOrBlank()) {
            translatedText = result
        }
        isTranslating = false
    }

    val rawThinkingTitle = reasoning.reasoning.extractThinkingTitle()
    val translatedTitle = translatedText?.extractThinkingTitle()
    val thinkingTitle = translatedTitle ?: rawThinkingTitle
    val showThinkingTitle = loading && thinkingTitle != null

    ControlledChainOfThoughtStep(
        expanded = state.expandState == ReasoningCardState.Expanded,
        onExpandedChange = { state.onExpandedChange(it, loading) },
        icon = {
            Icon(
                imageVector = rememberLoveLetterIcon(MaterialTheme.colorScheme.secondary),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
        },
        label = {
            if (showThinkingTitle) {
                ReasoningTitle(title = thinkingTitle!!)
            } else {
                Text(
                    text = stringResource(
                        R.string.deep_thinking_seconds,
                        state.duration.toDouble(DurationUnit.SECONDS).toFloat()
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.shimmer(isLoading = loading),
                )
            }
        },
        extra = {
            if (showThinkingTitle && state.duration > 0.seconds) {
                Text(
                    text = state.duration.toString(DurationUnit.SECONDS, 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.shimmer(isLoading = loading),
                )
            }
        },
        collapsedAdaptiveWidth = collapsedAdaptiveWidth,
        contentVisible = state.expandState != ReasoningCardState.Collapsed,
        content = {
            ReasoningContent(
                reasoning = reasoning,
                translatedText = translatedText,
                isTranslating = isTranslating,
                isVoiceRestored = isVoiceRestored,
                isRestoringVoice = isRestoringVoice,
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                onRestoreVoiceClick = {
                    if (!isRestoringVoice) {
                        coroutineScope.launch {
                            isRestoringVoice = true
                            val restored = ReasoningTranslator.restoreHusbandVoice(
                                context = context,
                                settings = settings,
                                providerManager = providerManager,
                                currentModel = model,
                                rawText = reasoning.reasoning,
                            )
                            if (!restored.isNullOrBlank()) {
                                translatedText = restored
                                isVoiceRestored = true
                                viewMode = ReasoningViewMode.ZH
                            }
                            isRestoringVoice = false
                        }
                    }
                },
                assistant = assistant,
                expandState = state.expandState,
                scrollState = state.scrollState,
                fadeHeight = fadeHeight,
            )
        },
    )
}


@Composable
private fun ReasoningTitle(title: String) {
    AnimatedContent(
        targetState = title,
        transitionSpec = {
            (slideInVertically { height -> height } + fadeIn()).togetherWith(
                slideOutVertically { height -> -height } + fadeOut()
            )
        }
    ) {
        Text(
            text = it,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .shimmer(true),
        )
    }
}
