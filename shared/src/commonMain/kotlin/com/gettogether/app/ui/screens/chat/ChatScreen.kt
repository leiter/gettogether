package com.gettogether.app.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.gettogether.app.platform.ImagePickerResult
import com.gettogether.app.platform.PermissionManager
import com.gettogether.app.platform.provideImagePicker
import com.gettogether.app.platform.providePermissionRequester
import com.gettogether.app.presentation.state.ChatMessage
import com.gettogether.app.presentation.state.ChatMessageType
import com.gettogether.app.presentation.state.FileTransferState
import com.gettogether.app.presentation.state.MessageStatus
import com.gettogether.app.presentation.state.SaveResult
import com.gettogether.app.presentation.viewmodel.ChatViewModel
import com.gettogether.app.ui.components.ContactAvatarImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
    permissionManager: PermissionManager = koinInject()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    // Image picker
    val imagePicker = provideImagePicker()

    // Permission requester for storage access
    val permissionRequester = providePermissionRequester()

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    // With reverseLayout=true, index 0 is at bottom, so scroll to 0 for newest
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Handle save result snackbar
    LaunchedEffect(state.saveResult) {
        state.saveResult?.let { result ->
            val message = when (result) {
                is SaveResult.Success -> result.message
                is SaveResult.Failure -> result.message
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveResult()
        }
    }

    // Handle pending storage permission request
    LaunchedEffect(state.pendingSaveMessage) {
        state.pendingSaveMessage?.let {
            val permission = permissionManager.getStorageWritePermission()
            if (permission != null) {
                permissionRequester.requestPermission(permission) { granted ->
                    viewModel.onStoragePermissionResult(granted)
                }
            } else {
                // No permission needed (Android 10+), proceed directly
                viewModel.onStoragePermissionResult(true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { showDeleteDialog = true }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        ContactAvatarImage(
                            avatarUri = state.contactAvatarUri,
                            displayName = state.contactName,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = state.contactName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (state.contactIsOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.contactIsOnline)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
//                        viewModel.clearMessages()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete messages"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MessageInput(
                value = state.messageInput,
                onValueChange = viewModel::onMessageInputChanged,
                onSend = {
                    keyboardController?.hide()
                    viewModel.sendMessage()
                },
                onAttachImage = {
                    imagePicker.pickImage { result ->
                        when (result) {
                            is ImagePickerResult.Success -> {
                                viewModel.sendImage(result.uri)
                            }
                            is ImagePickerResult.Error -> {
                                viewModel.setError(result.message)
                            }
                            ImagePickerResult.Cancelled -> {
                                // User cancelled, do nothing
                            }
                        }
                    }
                },
                enabled = state.canSend,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    ) { paddingValues ->
        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Send a message to start the conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // With reverseLayout=true, items are rendered bottom-to-top
                // First item in code = visually at bottom
                item { Spacer(modifier = Modifier.height(8.dp)) }
                // So we reverse the list: newest first (index 0) appears at bottom
                items(state.messages.reversed()) { message ->
                    val fileInfoStr = message.fileInfo?.let {
                        "state=${it.transferState}, localPath=${it.localPath?.takeLast(30)}, progress=${it.progress}"
                    } ?: "null"
                    println("ChatScreen: Rendering id=${message.id.take(8)}, type=${message.type}, isFromMe=${message.isFromMe}, fileInfo=[$fileInfoStr]")
                    val isMenuOpen = state.selectedMessageForMenu?.id == message.id
                    when (message.type) {
                        ChatMessageType.Text -> MessageBubble(
                            message = message,
                            showMenu = isMenuOpen,
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.showMessageMenu(message)
                            },
                            onDismissMenu = { viewModel.hideMessageMenu() },
                            onCopyText = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                viewModel.hideMessageMenu()
                            }
                        )
                        ChatMessageType.Image -> ImageMessageBubble(
                            message = message,
                            onDownloadClick = { viewModel.downloadFile(message.id) },
                            showMenu = isMenuOpen,
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.showMessageMenu(message)
                            },
                            onDismissMenu = { viewModel.hideMessageMenu() },
                            onSaveImage = {
                                viewModel.saveMessageToDownloads(message)
                                viewModel.hideMessageMenu()
                            }
                        )
                        ChatMessageType.File -> MessageBubble(
                            message = message,
                            showMenu = isMenuOpen,
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.showMessageMenu(message)
                            },
                            onDismissMenu = { viewModel.hideMessageMenu() },
                            onCopyText = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                viewModel.hideMessageMenu()
                            },
                            onSaveFile = if (message.fileInfo?.transferState == FileTransferState.Completed) {
                                {
                                    viewModel.saveMessageToDownloads(message)
                                    viewModel.hideMessageMenu()
                                }
                            } else null
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    // Delete conversation confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to delete this conversation? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation()
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    showMenu: Boolean = false,
    onLongClick: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onCopyText: (() -> Unit)? = null,
    onSaveFile: (() -> Unit)? = null
) {
    val bubbleColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = onLongClick
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }

            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = onDismissMenu
            ) {
                if (onCopyText != null) {
                    DropdownMenuItem(
                        text = { Text("Copy text") },
                        onClick = onCopyText,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null
                            )
                        }
                    )
                }
                if (onSaveFile != null) {
                    DropdownMenuItem(
                        text = { Text("Save file") },
                        onClick = onSaveFile,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (message.isFromMe) {
                Text(
                    text = when (message.status) {
                        MessageStatus.Sending -> "Sending..."
                        MessageStatus.Sent -> "Sent"
                        MessageStatus.Delivered -> "Delivered"
                        MessageStatus.Read -> "Read"
                        MessageStatus.Failed -> "Failed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.status == MessageStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachImage) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach image",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Type a message") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
                singleLine = false,
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageMessageBubble(
    message: ChatMessage,
    onDownloadClick: () -> Unit,
    showMenu: Boolean = false,
    onLongClick: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onSaveImage: (() -> Unit)? = null
) {
    val context = LocalPlatformContext.current
    val fileInfo = message.fileInfo ?: return

    val bubbleColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val bubbleShape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    // Only show save option when file is downloaded
    val canSave = fileInfo.transferState == FileTransferState.Completed && fileInfo.localPath != null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = onLongClick
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (fileInfo.transferState) {
                        FileTransferState.Completed -> {
                            if (fileInfo.localPath != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(fileInfo.localPath)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = fileInfo.fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "Image not found",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FileTransferState.Uploading, FileTransferState.Downloading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { fileInfo.progress },
                                    modifier = Modifier.size(48.dp),
                                    color = if (message.isFromMe) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${(fileInfo.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (message.isFromMe) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        FileTransferState.Pending -> {
                            if (!message.isFromMe) {
                                // Show download button for incoming images
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onDownloadClick() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download",
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = fileInfo.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatFileSize(fileInfo.fileSize),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            } else {
                                // Outgoing pending - waiting to start upload
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        FileTransferState.Failed -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "Transfer failed",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Transfer failed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        FileTransferState.Cancelled -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "Transfer cancelled",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Cancelled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Context menu - only show Save option when file is available
            if (canSave && onSaveImage != null) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onDismissMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("Save image") },
                        onClick = onSaveImage,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (message.isFromMe) {
                Text(
                    text = when (message.status) {
                        MessageStatus.Sending -> "Sending..."
                        MessageStatus.Sent -> "Sent"
                        MessageStatus.Delivered -> "Delivered"
                        MessageStatus.Read -> "Read"
                        MessageStatus.Failed -> "Failed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.status == MessageStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
