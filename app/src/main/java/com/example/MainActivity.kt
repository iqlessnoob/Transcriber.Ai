package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ChatEntity
import com.example.data.MessageEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.DownloadState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val allChats by viewModel.allChats.collectAsState(emptyList())
    val selectedChatId by viewModel.selectedChatId.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Filter chats based on search query
    val filteredChats = remember(allChats, searchQuery) {
        if (searchQuery.isBlank()) {
            allChats
        } else {
            allChats.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Selected file properties
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFileSize by viewModel.selectedFileSize.collectAsState()

    // Model setup and transcription states
    val isModelReady by viewModel.isModelReady.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val transcriptionStep by viewModel.transcriptionStep.collectAsState()
    val transcriptionProgress by viewModel.transcriptionProgress.collectAsState()
    val transcriptionStatusText by viewModel.transcriptionStatusText.collectAsState()
    val liveSrtText by viewModel.liveSrtText.collectAsState()

    // File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.handleFileSelected(uri)
        }
    }

    // Modal Drawer for saved transcription chats
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = Color(0xFFFDF7FF)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Saved Subtitles",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Search Bar in Drawer
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search sessions...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF625B71)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("drawer_search"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Saved Chats List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    if (filteredChats.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) "No transcription history" else "No matches found",
                                    fontSize = 14.sp,
                                    color = Color(0xFF625B71)
                                )
                            }
                        }
                    } else {
                        items(filteredChats, key = { it.id }) { chat ->
                            val isSelected = selectedChatId == chat.id
                            var showRenameDialog by remember { mutableStateOf(false) }
                            var renameText by remember { mutableStateOf(chat.title) }

                            if (showRenameDialog) {
                                AlertDialog(
                                    onDismissRequest = { showRenameDialog = false },
                                    title = { Text("Rename Session") },
                                    text = {
                                        OutlinedTextField(
                                            value = renameText,
                                            onValueChange = { renameText = it },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("rename_input")
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (renameText.isNotBlank()) {
                                                viewModel.renameChat(chat.id, renameText)
                                            }
                                            showRenameDialog = false
                                        }) {
                                            Text("RENAME", color = Color(0xFF6750A4))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showRenameDialog = false }) {
                                            Text("CANCEL")
                                        }
                                    }
                                )
                            }

                            NavigationDrawerItem(
                                label = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = chat.title,
                                            maxLines = 1,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = { showRenameDialog = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Rename",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(0xFF625B71)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteChat(chat.id) },
                                                modifier = Modifier.size(32.dp).testTag("delete_chat_${chat.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(0xFF7D5260)
                                                )
                                            }
                                        }
                                    }
                                },
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectChat(chat.id)
                                    coroutineScope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .testTag("chat_item_${chat.id}"),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFFEADDFF),
                                    unselectedContainerColor = Color.Transparent,
                                    selectedTextColor = Color(0xFF21005D),
                                    unselectedTextColor = Color(0xFF1D1B20)
                                )
                            )
                        }
                    }
                }

                Divider(color = Color(0xFFCAC4D0), modifier = Modifier.padding(horizontal = 16.dp))

                // Bottom actions in Drawer
                Button(
                    onClick = {
                        viewModel.createNewChat("Untitled Transcription")
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("drawer_new_chat_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Transcription")
                }
            }
        }
    ) {
        // Main Screen Scaffold
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Transcribe AI",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF1D1B20))
                        }
                    },
                    actions = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF1D1B20))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color(0xFF1D1B20)
                    ),
                    modifier = Modifier.border(width = 1.dp, color = Color(0xFFCAC4D0))
                )
            },
            bottomBar = {
                Surface(
                    color = Color(0xFFF7F2FA),
                    modifier = Modifier
                        .border(width = 1.dp, color = Color(0xFFCAC4D0))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // NEW CHAT / IMPORT Button
                        OutlinedButton(
                            onClick = {
                                viewModel.createNewChat("Transcribed Audio")
                                filePickerLauncher.launch("video/*,audio/*")
                            },
                            border = BorderStroke(1.dp, Color(0xFF79747E)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF6750A4)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_media_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("IMPORT MEDIA", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Floating Action Download / Save Button
                        val hasSubtitles = currentMessages.any { it.isSubtitles }
                        val activeSubtitles = currentMessages.find { it.isSubtitles }?.text ?: ""

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (hasSubtitles) Color(0xFF6750A4) else Color(0xFF625B71).copy(alpha = 0.4f)
                                )
                                .clickable(enabled = hasSubtitles) {
                                    shareSrtFile(context, selectedFileName ?: "subtitles.srt", activeSubtitles)
                                }
                                .testTag("export_floating_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export SRT",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFFFDF7FF)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main Content View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Chat/Message sequence
                    if (currentMessages.isEmpty() && !isTranscribing) {
                        // Empty Welcome Screen with dynamic visual flair
                        item {
                            EmptyStateCard {
                                filePickerLauncher.launch("video/*,audio/*")
                            }
                        }
                    } else {
                        // List past selections and generated outputs
                        items(currentMessages) { message ->
                            if (!message.isSubtitles) {
                                // Selected File message bubble
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 0.dp))
                                            .background(Color(0xFFEADDFF))
                                            .padding(14.dp)
                                            .widthIn(max = 280.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "Selected video / audio:",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF21005D)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = message.text.replace("Selected video:\n", ""),
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF21005D).copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Subtitles Result / Live Monospace Editor
                                var editedSrtText by remember { mutableStateOf(message.text) }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(width = 1.dp, color = Color(0xFFCAC4D0), shape = RoundedCornerShape(24.dp))
                                        .background(Color.White, shape = RoundedCornerShape(24.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "SRT SUBTITLES",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6750A4),
                                            letterSpacing = 1.5.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF3F0F5))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("UTF-8", fontSize = 10.sp, color = Color(0xFF49454F))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Editable subtitle content
                                    OutlinedTextField(
                                        value = editedSrtText,
                                        onValueChange = { editedSrtText = it },
                                        textStyle = LocalTextStyle.current.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 160.dp, max = 320.dp)
                                            .testTag("srt_editor"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF6750A4),
                                            unfocusedBorderColor = Color.Transparent,
                                            disabledBorderColor = Color.Transparent
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Interactive buttons for copying/sharing
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                copyToClipboard(context, editedSrtText)
                                                Toast.makeText(context, "Copied subtitles to clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.testTag("copy_button")
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("COPY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                shareSrtFile(context, selectedFileName ?: "subtitles.srt", editedSrtText)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.height(36.dp).testTag("share_button")
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("SHARE SRT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active processing or Live Preview section
                    if (isTranscribing) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(width = 1.dp, color = Color(0xFFCAC4D0), shape = RoundedCornerShape(24.dp))
                                    .background(Color.White, shape = RoundedCornerShape(24.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PROCESSING ENGINE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6750A4),
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = "Step $transcriptionStep of 3",
                                        fontSize = 11.sp,
                                        color = Color(0xFF625B71),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    // Pulse Dot Animation
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val alphaVal by infiniteTransition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .alpha(alphaVal)
                                            .background(Color(0xFF6750A4), shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = transcriptionStatusText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1D1B20)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Sleek Material Progress Bar
                                LinearProgressIndicator(
                                    progress = transcriptionProgress,
                                    color = Color(0xFF6750A4),
                                    trackColor = Color(0xFFE6E1E5),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Offline local inference",
                                        fontSize = 10.sp,
                                        color = Color(0xFF49454F)
                                    )
                                    Text(
                                        text = "${(transcriptionProgress * 100).toInt()}% processed",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF49454F)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedButton(
                                    onClick = { viewModel.stopTranscription() },
                                    border = BorderStroke(1.dp, Color(0xFF7D5260)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7D5260)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.End).height(32.dp)
                                ) {
                                    Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Live SRT Preview Box (Dashed-style borders with live timing updates)
                        if (liveSrtText.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            BorderStroke(1.dp, Color(0xFFCAC4D0)),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .background(Color(0xFFF3F0F5), shape = RoundedCornerShape(24.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "LIVE SRT PREVIEW",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF49454F),
                                            letterSpacing = 1.2.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(Color.White, RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("UTF-8", fontSize = 9.sp, color = Color(0xFF49454F))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Subtitle box scrolling list
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 140.dp)
                                    ) {
                                        Text(
                                            text = liveSrtText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            color = Color(0xFF1D1B20).copy(alpha = 0.8f),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // OVERLAY: "Setting up AI Engine" Dialog
                if (!isModelReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f))
                            .clickable(enabled = false) {}, // Intercept clicks
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(28.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Setting up AI Engine",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF1D1B20)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Downloading Moonshine-Tiny STT model files from Hugging Face (50MB)...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF49454F),
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                when (val state = downloadState) {
                                    null -> {
                                        // Start state button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { viewModel.skipModelDownload() }) {
                                                Text("SKIP / DEMO MODE", color = Color(0xFF7D5260))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { viewModel.startModelDownload() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                                            ) {
                                                Text("DOWNLOAD MODEL")
                                            }
                                        }
                                    }
                                    is DownloadState.Progress -> {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = state.message,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF6750A4),
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "${state.percent.toInt()}%",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1D1B20)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = state.percent / 100f,
                                                color = Color(0xFF6750A4),
                                                trackColor = Color(0xFFE6E1E5),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(onClick = { viewModel.skipModelDownload() }) {
                                                    Text("SKIP TO DEMO", color = Color(0xFF7D5260))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                TextButton(
                                                    onClick = { viewModel.cancelModelDownload() },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF6750A4))
                                                ) {
                                                    Text("CANCEL")
                                                }
                                            }
                                        }
                                    }
                                    is DownloadState.Error -> {
                                        Text(
                                            text = state.message,
                                            fontSize = 12.sp,
                                            color = Color(0xFF7D5260)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { viewModel.skipModelDownload() }) {
                                                Text("USE DEMO ENGINE", color = Color(0xFF6750A4))
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Button(
                                                onClick = { viewModel.startModelDownload() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                                            ) {
                                                Text("RETRY")
                                            }
                                        }
                                    }
                                    DownloadState.Success -> {
                                        Text("AI Engine ready!", fontSize = 14.sp, color = Color(0xFF6750A4))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(onSelectClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0xFFCAC4D0), shape = RoundedCornerShape(28.dp))
            .background(Color.White, shape = RoundedCornerShape(28.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFEADDFF), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF21005D),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Generate Local Subtitles",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Import any audio or video file to generate high-density SRT subtitles completely offline. Everything runs locally on device to keep your data private.",
            fontSize = 13.sp,
            color = Color(0xFF49454F),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSelectClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("select_file_primary_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("SELECT MEDIA FILE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Utility function to copy subtitles to clipboard
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("subtitles", text)
    clipboard.setPrimaryClip(clip)
}

// Utility function to share SRT file content
fun shareSrtFile(context: Context, fileName: String, text: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, fileName)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export SRT Subtitles")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share subtitles file.", Toast.LENGTH_SHORT).show()
    }
}
