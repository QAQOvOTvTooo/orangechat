/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiSearch02
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ExtensionSelector
import me.rerere.rikkahub.ui.components.ui.icons.ReasoningHigh
import me.rerere.rikkahub.ui.components.ui.icons.ReasoningLow
import me.rerere.rikkahub.ui.components.ui.icons.ReasoningMedium
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalCurrentChatModel
import me.rerere.rikkahub.ui.context.LocalMcpServers
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalProviders
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.theme.popupContainerColor
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject

@Composable
internal fun FilesPicker(
    conversation: Conversation,
    assistant: Assistant,
    state: ChatInputState,
    mcpManager: McpManager,
    enableSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onUpdateChatModel: (Model) -> Unit,
    onCompressContext: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateWorkspaceCwd: (String?) -> Unit = {},
    showInjectionSheet: Boolean,
    onShowInjectionSheetChange: (Boolean) -> Unit,
    showCompressDialog: Boolean,
    onShowCompressDialogChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTakePic: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
) {
    // settings 仍保留给 InjectionQuickConfigSheet -> ExtensionSelector 使用（它需要完整 Settings）
    val settings = LocalSettings.current
    val currentChatModel = LocalCurrentChatModel.current
    val providers = LocalProviders.current
    val mcpServers = LocalMcpServers.current
    val provider = currentChatModel?.findProvider(providers = providers)
    val navController = LocalNavController.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())

    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }
    val showCwd = boundWorkspace != null && boundWorkspace.shellStatus == WorkspaceShellStatus.READY.name
    var showCwdSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showReasoningSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TakePicButton(onLaunchCamera = onTakePic)

            ImagePickButton(onClick = onPickImage)

            if (provider != null && provider is ProviderSetting.Google) {
                VideoPickButton(onClick = onPickVideo)

                AudioPickButton(onClick = onPickAudio)
            }

            FilePickButton(onClick = onPickFile)
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth()
        )

        // 1. 聊天模型选择
        ListItem(
            leadingContent = {
                if (currentChatModel != null) {
                    AutoAIIcon(
                        name = currentChatModel.modelId,
                        modifier = Modifier.size(24.dp),
                        color = Color.Transparent,
                    )
                } else {
                    Icon(
                        imageVector = HugeIcons.Brain02,
                        contentDescription = stringResource(R.string.setting_model_page_chat_model),
                    )
                }
            },
            headlineContent = {
                Text(stringResource(R.string.setting_model_page_chat_model))
            },
            supportingContent = {
                Text(
                    text = buildString {
                        append(currentChatModel?.displayName ?: stringResource(R.string.model_list_select_model))
                        if (provider != null) {
                            append(" · ")
                            append(provider.name)
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable { showModelSheet = true },
        )

        // 2. 联网搜索
        val isBuiltInSearch = currentChatModel?.tools?.contains(BuiltInTools.Search) == true
        val currentSearchService = settings.searchServices.getOrNull(settings.searchServiceSelected)
        ListItem(
            leadingContent = {
                if (isBuiltInSearch) {
                    Icon(
                        imageVector = HugeIcons.AiSearch02,
                        contentDescription = stringResource(R.string.use_web_search),
                    )
                } else if (enableSearch && currentSearchService != null) {
                    AutoAIIcon(
                        name = currentSearchService.displayName,
                        modifier = Modifier.size(24.dp),
                        color = Color.Transparent,
                    )
                } else {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = stringResource(R.string.use_web_search),
                    )
                }
            },
            headlineContent = {
                Text(stringResource(R.string.use_web_search))
            },
            supportingContent = {
                Text(
                    text = when {
                        isBuiltInSearch -> stringResource(R.string.built_in_search_title)
                        enableSearch -> currentSearchService?.displayName ?: stringResource(R.string.web_search_enabled)
                        else -> stringResource(R.string.web_search_disabled)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                if (isBuiltInSearch) {
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Switch(
                        checked = enableSearch,
                        onCheckedChange = { onToggleSearch(it) },
                    )
                }
            },
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable { showSearchSheet = true },
        )

        // 3. 思考推理级别
        if (currentChatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
            val reasoningLevel = assistant.reasoningLevel
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = when (reasoningLevel) {
                            ReasoningLevel.OFF -> HugeIcons.Idea
                            ReasoningLevel.AUTO -> HugeIcons.Idea01
                            ReasoningLevel.LOW -> ReasoningLow
                            ReasoningLevel.MEDIUM -> ReasoningMedium
                            ReasoningLevel.HIGH -> ReasoningHigh
                            ReasoningLevel.XHIGH -> ReasoningHigh
                        },
                        contentDescription = stringResource(R.string.setting_provider_page_reasoning),
                        tint = if (reasoningLevel.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = {
                    Text(stringResource(R.string.setting_provider_page_reasoning))
                },
                supportingContent = {
                    Text(
                        text = when (reasoningLevel) {
                            ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
                            ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
                            ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
                            ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
                            ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
                            ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { showReasoningSheet = true },
            )
        }

        // 工作区与工作目录快捷入口
        WorkspacePickerListItem(
            assistant = assistant,
            workspaces = workspaces,
            onUpdateAssistant = { updated ->
                if (updated.workspaceId != assistant.workspaceId) {
                    onUpdateWorkspaceCwd(null)
                }
                onUpdateAssistant(updated)
            },
            onNavigateToDetail = { id ->
                onDismiss()
                navController.navigate(Screen.WorkspaceDetail(id, initialTab = 1))
            },
            onNavigateToTerminal = { id ->
                onDismiss()
                navController.navigate(Screen.WorkspaceTerminal(id))
            },
            onNavigateToManage = {
                onDismiss()
                navController.navigate(Screen.Workspaces)
            },
        )

        if (showCwd) {
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = HugeIcons.Folder01,
                        contentDescription = stringResource(R.string.workspace_cwd_select_directory),
                    )
                },
                headlineContent = {
                    Text(
                        text = conversation.workspaceCwd ?: boundWorkspace?.workspaceDir().orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                },
                trailingContent = {
                    Icon(HugeIcons.ArrowRight01, contentDescription = null)
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { showCwdSheet = true },
            )
        }

        if (mcpServers.isNotEmpty()) {
            McpPickerListItem(
                assistant = assistant,
                servers = mcpServers,
                mcpManager = mcpManager,
                onUpdateAssistant = onUpdateAssistant,
            )
        }

        // Extensions (Quick Messages + Prompt Injections + Skills)
        val activeCount =
            assistant.quickMessageIds.size + assistant.modeInjectionIds.size + assistant.lorebookIds.size + assistant.enabledSkills.size
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = HugeIcons.Package,
                    contentDescription = stringResource(R.string.assistant_page_tab_extensions),
                )
            },
            headlineContent = {
                Text(stringResource(R.string.assistant_page_tab_extensions))
            },
            trailingContent = {
                if (activeCount > 0) {
                    Text(
                        text = activeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable {
                    onShowInjectionSheetChange(true)
                },
        )

        // Compress History Button
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = HugeIcons.Package01,
                    contentDescription = stringResource(R.string.chat_page_compress_context),
                )
            },
            headlineContent = {
                Text(stringResource(R.string.chat_page_compress_context))
            },
            trailingContent = {
                val contextTokens = remember(conversation.messageNodes) {
                    conversation.lastContextTokens()
                }
                val contextLength = currentChatModel?.contextLength?.takeIf { it > 0 }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (conversation.messageNodes.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_page_message_count, conversation.messageNodes.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (contextTokens != null && contextLength != null) {
                            Text(
                                text = "${contextTokens.formatNumber()} / ${contextLength.formatNumber()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (contextTokens != null && contextLength != null) {
                        ContextUsageRing(fraction = contextTokens.toFloat() / contextLength)
                    }
                }
            },
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable {
                    onShowCompressDialogChange(true)
                },
        )
    }

    if (showCwdSheet && boundWorkspace != null) {
        WorkspaceCwdPickerSheet(
            workspaceId = boundWorkspace.id,
            workspaceDir = boundWorkspace.workspaceDir(),
            currentCwd = conversation.workspaceCwd,
            onSelectCwd = { newCwd ->
                onUpdateWorkspaceCwd(newCwd)
            },
            onDismiss = { showCwdSheet = false },
        )
    }

    // Injection Bottom Sheet
    if (showInjectionSheet) {
        InjectionQuickConfigSheet(
            assistant = assistant,
            settings = settings,
            onUpdateAssistant = onUpdateAssistant,
            onDismiss = { onShowInjectionSheetChange(false) })
    }

    // Compress Context Dialog
    if (showCompressDialog) {
        CompressContextDialog(onDismiss = {
            onShowCompressDialogChange(false)
            onDismiss()
        }, onConfirm = { additionalPrompt, targetTokens, keepRecentMessages ->
            onCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
        })
    }

    // Model Selection Bottom Sheet
    if (showModelSheet) {
        val modelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = modelSheetState,
            containerColor = popupContainerColor(BottomSheetDefaults.ContainerColor),
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxHeight(0.8f)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val filteredProviderSettings = providers.fastFilter {
                    it.enabled && it.models.fastAny { m -> m.type == ModelType.CHAT }
                }
                ModelList(
                    currentModel = assistant.chatModelId ?: settings.chatModelId,
                    providers = filteredProviderSettings,
                    modelType = ModelType.CHAT,
                    onSelect = {
                        onUpdateChatModel(it)
                        scope.launch {
                            modelSheetState.hide()
                            showModelSheet = false
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            modelSheetState.hide()
                            showModelSheet = false
                        }
                    }
                )
            }
        }
    }

    // Search Settings Bottom Sheet
    if (showSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = popupContainerColor(BottomSheetDefaults.ContainerColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_picker_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                SearchPicker(
                    enableSearch = enableSearch,
                    settings = settings,
                    onToggleSearch = onToggleSearch,
                    onUpdateSearchService = onUpdateSearchService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    model = currentChatModel,
                    onDismiss = {
                        showSearchSheet = false
                    }
                )
            }
        }
    }

    // Reasoning Picker Bottom Sheet
    if (showReasoningSheet) {
        ReasoningPicker(
            reasoningLevel = assistant.reasoningLevel,
            onDismissRequest = { showReasoningSheet = false },
            onUpdateReasoningLevel = {
                onUpdateAssistant(assistant.copy(reasoningLevel = it))
            }
        )
    }
}

@Composable
private fun WorkspacePickerListItem(
    assistant: Assistant,
    workspaces: List<WorkspaceEntity>,
    onUpdateAssistant: (Assistant) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToManage: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = HugeIcons.Codesandbox,
                contentDescription = null,
            )
        },
        headlineContent = {
            Text(stringResource(R.string.assistant_page_workspace))
        },
        supportingContent = {
            Text(
                text = boundWorkspace?.name ?: stringResource(R.string.assistant_page_workspace_unbound),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (boundWorkspace != null) {
                    IconButton(onClick = { onNavigateToDetail(boundWorkspace.id) }) {
                        Icon(
                            imageVector = HugeIcons.Settings02,
                            contentDescription = stringResource(R.string.workspace_detail),
                        )
                    }
                    if (boundWorkspace.supportsTerminal()) {
                        IconButton(onClick = { onNavigateToTerminal(boundWorkspace.id) }) {
                            Icon(
                                imageVector = HugeIcons.ComputerTerminal01,
                                contentDescription = stringResource(R.string.workspace_terminal),
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { showSheet = true },
    )

    if (showSheet) {
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = workspaces,
            onSelect = { workspaceId ->
                val newId = workspaceId?.let { Uuid.parse(it) }
                if (newId != assistant.workspaceId) {
                    onUpdateAssistant(assistant.copy(workspaceId = newId))
                }
                showSheet = false
            },
            onManage = {
                showSheet = false
                onNavigateToManage()
            },
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun InjectionQuickConfigSheet(
    assistant: Assistant, settings: Settings, onUpdateAssistant: (Assistant) -> Unit, onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp),
        ) {
            ExtensionSelector(
                assistant = assistant,
                settings = settings,
                onUpdate = onUpdateAssistant,
                modifier = Modifier.weight(1f),
                onNavigateToQuickMessages = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        navController.navigate(Screen.QuickMessages)
                    }
                },
                onNavigateToPrompts = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        navController.navigate(Screen.Prompts)
                    }
                },
                onNavigateToSkills = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        navController.navigate(Screen.Skills)
                    }
                })

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImagePickButton(onClick: () -> Unit = {}) {
    BigIconTextButton(icon = {
        Icon(HugeIcons.Image02, null)
    }, text = {
        Text(stringResource(R.string.photo))
    }) {
        onClick()
    }
}

@Composable
fun TakePicButton(onLaunchCamera: () -> Unit = {}) {
    val cameraPermission = rememberPermissionState(PermissionCamera)

    PermissionManager(
        permissionState = cameraPermission
    ) {
        BigIconTextButton(icon = {
            Icon(HugeIcons.Camera01, null)
        }, text = {
            Text(stringResource(R.string.take_picture))
        }) {
            if (cameraPermission.allRequiredPermissionsGranted) {
                onLaunchCamera()
            } else {
                cameraPermission.requestPermissions()
            }
        }
    }
}

@Composable
fun VideoPickButton(onClick: () -> Unit = {}) {
    BigIconTextButton(icon = {
        Icon(HugeIcons.Video01, null)
    }, text = {
        Text(stringResource(R.string.video))
    }) {
        onClick()
    }
}

@Composable
fun AudioPickButton(onClick: () -> Unit = {}) {
    BigIconTextButton(icon = {
        Icon(HugeIcons.MusicNote03, null)
    }, text = {
        Text(stringResource(R.string.audio))
    }) {
        onClick()
    }
}

@Composable
fun FilePickButton(onClick: () -> Unit = {}) {
    BigIconTextButton(icon = {
        Icon(HugeIcons.Files02, null)
    }, text = {
        Text(stringResource(R.string.upload_file))
    }) {
        onClick()
    }
}

@Composable
private fun BigIconTextButton(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick
            )
            .semantics {
                role = Role.Button
            }
            .wrapContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            tonalElevation = 2.dp, shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                icon()
            }
        }
        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
            text()
        }
    }
}

// 上下文使用量进度环，接近上限时变色提醒压缩
@Composable
private fun ContextUsageRing(fraction: Float) {
    val color = when {
        fraction >= 0.9f -> MaterialTheme.colorScheme.error
        fraction >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    CircularProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.size(24.dp),
        color = color,
        strokeWidth = 3.dp,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Preview(showBackground = true)
@Composable
private fun BigIconTextButtonPreview() {
    Row(
        modifier = Modifier.padding(16.dp)
    ) {
        BigIconTextButton(icon = {
            Icon(HugeIcons.Image02, null)
        }, text = {
            Text(stringResource(R.string.photo))
        }) {}
    }
}
