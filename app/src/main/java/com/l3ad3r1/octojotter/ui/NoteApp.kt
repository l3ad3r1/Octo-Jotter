package com.l3ad3r1.octojotter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import com.l3ad3r1.octojotter.data.local.NoteEntity
import com.l3ad3r1.octojotter.ui.theme.OctoStatusColors
import com.l3ad3r1.octojotter.ui.theme.LightStatusColors
import com.l3ad3r1.octojotter.ui.theme.DarkStatusColors
import com.l3ad3r1.octojotter.ui.theme.octoStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteApp(viewModel: NoteViewModel) {
    val navController = rememberNavController()

    // Settings is a pushed screen reached from the top-bar gear, not a bottom-nav
    // destination — a 2-item bottom bar wasn't worth the permanent vertical cost.
    NavHost(
        navController = navController,
        startDestination = "notes_list",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("notes_list") {
            NotesListScreen(
                viewModel = viewModel,
                onNavigateToEditor = { noteId ->
                    navController.navigate("editor/$noteId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings") { launchSingleTop = true }
                }
            )
        }
        composable("editor/{noteId}") { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")?.toIntOrNull() ?: -1
            EditorScreen(
                noteId = noteId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEditor = { newNoteId ->
                    navController.navigate("editor/$newNoteId")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(
    viewModel: NoteViewModel,
    onNavigateToEditor: (Int) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val notes by viewModel.filteredNotes.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }

    // Swipe-to-delete with a non-destructive Undo window: the note is hidden
    // immediately and only permanently deleted if the Snackbar times out.
    val requestDelete: (NoteEntity) -> Unit = { note ->
        viewModel.markPendingDeletion(note.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Note deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoPendingDeletion(note.id)
            } else {
                viewModel.commitPendingDeletion(note)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notebook Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.testTag("drawer_add_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Create folder",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // All Notes selection
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    label = { Text("All Notes") },
                    selected = selectedFolder == null,
                    onClick = {
                        viewModel.selectFolder(null)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("folder_item_all")
                )

                // Uncategorized selection
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    label = { Text("Uncategorized") },
                    selected = selectedFolder == "Uncategorized",
                    onClick = {
                        viewModel.selectFolder("Uncategorized")
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("folder_item_uncategorized")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Custom folders list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allFolders) { folder ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(folder, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.deleteFolder(folder) },
                                        modifier = Modifier.testTag("delete_folder_$folder")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Folder",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            selected = selectedFolder == folder,
                            onClick = {
                                viewModel.selectFolder(folder)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp).testTag("folder_item_$folder")
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(selectedFolder ?: "Octo Jotter", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            },
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open folders menu"
                            )
                        }
                    },
                    actions = {
                    IconButton(
                        onClick = { viewModel.syncNow() },
                        modifier = Modifier.testTag("sync_action_button")
                    ) {
                        when (syncState) {
                            SyncState.Syncing -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            SyncState.Synced -> {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Synced",
                                    tint = MaterialTheme.octoStatus.syncOk
                                )
                            }
                            SyncState.Offline -> {
                                // Offline is a normal state for an offline-first app,
                                // so use a neutral tint rather than alarming error red.
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Offline",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.createNewNote { newId ->
                        onNavigateToEditor(newId)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar Component
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search notes by title or content...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Notes"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_bar_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sort by:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = sortBy == "LAST_MODIFIED",
                        onClick = { viewModel.updateSortBy("LAST_MODIFIED") },
                        label = { Text("Last Modified") },
                        leadingIcon = if (sortBy == "LAST_MODIFIED") {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("sort_by_modified_chip")
                    )
                    FilterChip(
                        selected = sortBy == "TITLE",
                        onClick = { viewModel.updateSortBy("TITLE") },
                        label = { Text("Title") },
                        leadingIcon = if (sortBy == "TITLE") {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("sort_by_title_chip")
                    )
                }

                val isFolderTreeView by viewModel.isFolderTreeView.collectAsState()
                FilterChip(
                    selected = isFolderTreeView,
                    onClick = { viewModel.toggleFolderTreeView() },
                    label = { Text(if (isFolderTreeView) "Folder View" else "Flat View") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFolderTreeView) Icons.Default.Folder else Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("folder_tree_view_toggle")
                )
            }

            AnimatedVisibility(visible = availableTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Filter by tag:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { viewModel.selectTag(null) },
                                label = { Text("All") },
                                modifier = Modifier.testTag("tag_filter_all_chip")
                            )
                        }
                        items(availableTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { viewModel.selectTag(tag) },
                                label = { Text(tag) },
                                leadingIcon = if (selectedTag == tag) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("tag_filter_${tag}_chip")
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (notes.isEmpty()) {
                    if (searchQuery.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "No results",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No matching notes found",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try typing a different keyword or check for spelling errors.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = "No notes",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No notes saved locally",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Jot your first markdown note, or connect GitHub to import your Gists.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    viewModel.createNewNote { newId -> onNavigateToEditor(newId) }
                                },
                                modifier = Modifier.testTag("create_first_note_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create your first note")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.testTag("go_to_settings_button")
                            ) {
                                Text("Connect GitHub")
                            }
                        }
                    }
                } else {
                    val renderNoteCard: @Composable (NoteEntity) -> Unit = { note ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    requestDelete(note)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Note",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onNavigateToEditor(note.id) },
                                        onLongClick = { noteToDelete = note }
                                    )
                                    .testTag("note_item_card_${note.id}"),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = note.displayTitle.ifBlank { "Untitled Note" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { viewModel.togglePinNote(note) },
                                            modifier = Modifier
                                                .testTag("pin_button_${note.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (note.pinned) "Unpin Note" else "Pin Note",
                                                tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        SyncStatusBadge(note = note)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = note.content.ifBlank { "No content..." },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (note.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Label,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            note.tags.take(4).forEach { tag ->
                                                SuggestionChip(
                                                    onClick = { viewModel.selectTag(tag) },
                                                    label = { Text(tag, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)) },
                                                    modifier = Modifier.height(24.dp).testTag("note_card_tag_${note.id}_$tag")
                                                )
                                            }
                                            if (note.tags.size > 4) {
                                                Text(
                                                    text = "+${note.tags.size - 4}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = formatRelativeTimestamp(note.lastModifiedLocally),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            val foldersDisplay = note.folderPath
                                            if (foldersDisplay.isNotEmpty()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = "Folder",
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = foldersDisplay.joinToString(" / "),
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val isFolderTreeView by viewModel.isFolderTreeView.collectAsState()

                    if (isFolderTreeView) {
                        val groupedNotes = remember(notes) {
                            notes.groupBy { note ->
                                if (note.folderPath.isEmpty()) "Root Notes" else note.folderPath.joinToString(" ➔ ")
                            }
                        }
                        val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            groupedNotes.forEach { (folderName, folderNotes) ->
                                val isExpanded = expandedStates[folderName] ?: true
                                item(key = "folder_group_$folderName") {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .testTag("folder_accordion_$folderName"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expandedStates[folderName] = !isExpanded }
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = "Folder",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = folderName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ) {
                                                        Text("${folderNotes.size}")
                                                    }
                                                }
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                                                )
                                            }

                                            AnimatedVisibility(visible = isExpanded) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    folderNotes.forEach { note ->
                                                        renderNoteCard(note)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(notes, key = { note -> note.id }) { note ->
                                renderNoteCard(note)
                            }
                        }
                    }
                }
            }
        }
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note locally? This will not delete the Gist from GitHub.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        noteToDelete?.let { viewModel.deleteNote(it) }
                        noteToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_folder_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.addFolder(newFolderName)
                            viewModel.selectFolder(newFolderName)
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_folder_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newFolderName = ""
                        showCreateFolderDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
}

@Composable
fun SyncStatusBadge(note: NoteEntity) {
    val status = MaterialTheme.octoStatus
    val (text, color, icon) = when {
        note.needsSync -> Triple("Pending Sync", status.syncPending, Icons.Default.CloudQueue)
        !note.gistId.isNullOrEmpty() -> Triple("Synced", status.syncOk, Icons.Default.CloudDone)
        else -> Triple("Local Only", status.localOnly, Icons.Default.CloudOff)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: Int,
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (Int) -> Unit
) {
    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    val note by viewModel.editingNote.collectAsState()
    val editorTitle by viewModel.editorTitle.collectAsState()
    val editorContent by viewModel.editorContent.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    val pendingDraft by viewModel.pendingDraft.collectAsState()
    val availableFolders by viewModel.allFolders.collectAsState()

    val handleExit = {
        viewModel.clearDraftForCurrentNote()
        onNavigateBack()
    }

    BackHandler(onBack = handleExit)

    pendingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { /* Force explicit user choice */ },
            title = {
                Text(
                    text = "Recover Unsaved Draft?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "We found an unsaved draft from a previous session. Would you like to recover your unsaved changes or discard them?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.recoverDraft(draft) },
                    modifier = Modifier.testTag("recover_draft_button")
                ) {
                    Text("Recover")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.discardDraft(draft) },
                    modifier = Modifier.testTag("discard_draft_button")
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Edit, 1 = Preview

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = editorContent,
                selection = androidx.compose.ui.text.TextRange(editorContent.length)
            )
        )
    }

    LaunchedEffect(editorContent) {
        if (textFieldValue.text != editorContent) {
            textFieldValue = textFieldValue.copy(
                text = editorContent,
                selection = if (textFieldValue.selection.start <= editorContent.length && textFieldValue.selection.end <= editorContent.length) {
                    textFieldValue.selection
                } else {
                    androidx.compose.ui.text.TextRange(editorContent.length)
                }
            )
        }
    }

    fun insertMarkdown(syntax: String, suffix: String = "") {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.start
        val end = selection.end

        val selectedText = text.substring(start, end)
        val actualSuffix = if (suffix.isEmpty() && syntax != "- ") syntax else suffix
        val newText = text.substring(0, start) + syntax + selectedText + actualSuffix + text.substring(end)
        
        val newSelectionStart = if (start == end) {
            start + syntax.length
        } else {
            start + syntax.length + selectedText.length + actualSuffix.length
        }
        
        val newSelectionEnd = if (start == end) {
            start + syntax.length
        } else {
            newSelectionStart
        }
        
        textFieldValue = TextFieldValue(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(newSelectionStart, newSelectionEnd)
        )
        viewModel.onNoteTextChanged(editorTitle, newText)
    }

    val scope = rememberCoroutineScope()
    var showCreateWikiNoteDialog by remember { mutableStateOf(false) }
    var wikiLinkToCreate by remember { mutableStateOf("") }

    val onWikiLinkClick: (String) -> Unit = { targetTitle ->
        scope.launch {
            val existingNote = viewModel.getNoteByTitle(targetTitle)
            if (existingNote != null) {
                onNavigateToEditor(existingNote.id)
            } else {
                wikiLinkToCreate = targetTitle
                showCreateWikiNoteDialog = true
            }
        }
    }

    if (showCreateWikiNoteDialog) {
        AlertDialog(
            onDismissRequest = { showCreateWikiNoteDialog = false },
            title = { Text("Create Note") },
            text = { Text("The note \"$wikiLinkToCreate\" does not exist. Would you like to create it?") },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateWikiNoteDialog = false
                        viewModel.createNewNoteWithTitle(wikiLinkToCreate) { newId ->
                            onNavigateToEditor(newId)
                        }
                    },
                    modifier = Modifier.testTag("create_wiki_note_confirm_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateWikiNoteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val backlinksFlow = remember(editorTitle, noteId) {
        viewModel.getBacklinksForNote(editorTitle, noteId)
    }
    val backlinksList by backlinksFlow.collectAsState(initial = emptyList())

    val backlinksSection: @Composable () -> Unit = {
        if (backlinksList.isNotEmpty()) {
            var showBacklinks by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("backlinks_section"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBacklinks = !showBacklinks }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Backlinks",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Backlinks",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text("${backlinksList.size}")
                            }
                        }
                        Icon(
                            imageVector = if (showBacklinks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showBacklinks) "Collapse Backlinks" else "Expand Backlinks"
                        )
                    }
                    
                    AnimatedVisibility(visible = showBacklinks) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(backlinksList) { backlinkNote ->
                                Card(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clickable { onNavigateToEditor(backlinkNote.id) }
                                        .testTag("backlink_item_${backlinkNote.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = backlinkNote.displayTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = backlinkNote.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val isWideScreen = LocalConfiguration.current.screenWidthDp.dp > 600.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Editor",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        SaveStatusIndicator(saveStatus = saveStatus)
                        Spacer(modifier = Modifier.width(8.dp))
                        note?.let { currentNote ->
                            IconButton(
                                onClick = { viewModel.togglePinNote(currentNote) },
                                modifier = Modifier.testTag("editor_pin_button")
                            ) {
                                Icon(
                                    imageVector = if (currentNote.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (currentNote.pinned) "Unpin Note" else "Pin Note",
                                    tint = if (currentNote.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (activeTab == 0 || isWideScreen) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 4.dp,
                    // Lift the toolbar above the soft keyboard so formatting is
                    // reachable exactly when the user is typing.
                    modifier = Modifier
                        .height(64.dp)
                        .imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Heading
                        IconButton(
                            onClick = { insertMarkdown("# ", "") },
                            modifier = Modifier.testTag("format_heading_button")
                        ) {
                            Icon(Icons.Default.Title, contentDescription = "Heading")
                        }
                        // Bold
                        IconButton(
                            onClick = { insertMarkdown("**") },
                            modifier = Modifier.testTag("format_bold_button")
                        ) {
                            Icon(Icons.Default.FormatBold, contentDescription = "Format Bold")
                        }
                        // Italic
                        IconButton(
                            onClick = { insertMarkdown("*") },
                            modifier = Modifier.testTag("format_italic_button")
                        ) {
                            Icon(Icons.Default.FormatItalic, contentDescription = "Format Italic")
                        }
                        // Strikethrough
                        IconButton(
                            onClick = { insertMarkdown("~~") },
                            modifier = Modifier.testTag("format_strikethrough_button")
                        ) {
                            Icon(Icons.Default.FormatStrikethrough, contentDescription = "Format Strikethrough")
                        }
                        // Inline code
                        IconButton(
                            onClick = { insertMarkdown("`") },
                            modifier = Modifier.testTag("format_code_button")
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "Inline Code")
                        }
                        // Unordered List
                        IconButton(
                            onClick = { insertMarkdown("- ", "") },
                            modifier = Modifier.testTag("format_list_button")
                        ) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = "Format Unordered List")
                        }
                        // Task checkbox
                        IconButton(
                            onClick = { insertMarkdown("- [ ] ", "") },
                            modifier = Modifier.testTag("format_checkbox_button")
                        ) {
                            Icon(Icons.Default.CheckBox, contentDescription = "Task checkbox")
                        }
                        // Wiki link
                        IconButton(
                            onClick = { insertMarkdown("[[", "]]") },
                            modifier = Modifier.testTag("format_wikilink_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Wiki link")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        note?.let {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val isWideScreenFromConstraints = maxWidth > 600.dp

                if (isWideScreenFromConstraints) {
                    // Wide screen: Split-screen (Edit on left, Preview on right)
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(16.dp)
                        ) {
                            EditorInputs(
                                title = editorTitle,
                                textFieldValue = textFieldValue,
                                onTitleChanged = { newTitle ->
                                    viewModel.onNoteTextChanged(newTitle, textFieldValue.text)
                                },
                                onContentChanged = { newValue ->
                                    textFieldValue = newValue
                                    viewModel.onNoteTextChanged(editorTitle, newValue.text)
                                },
                                tags = note?.tags ?: emptyList(),
                                onTagsChanged = { newTags ->
                                    viewModel.updateTags(newTags)
                                },
                                currentFolder = note?.folder,
                                availableFolders = availableFolders,
                                onFolderChanged = { folder ->
                                    note?.let { viewModel.setNoteFolder(it, folder) }
                                },
                                backlinksSection = backlinksSection
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "Live Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                            MarkdownPreview(
                                markdown = editorContent,
                                modifier = Modifier.fillMaxSize(),
                                onWikiLinkClick = onWikiLinkClick,
                                onHashtagClick = { hashtag ->
                                    viewModel.selectTag(hashtag)
                                    handleExit()
                                }
                            )
                        }
                    }
                } else {
                    // Compact screen: Tabs (Edit vs Preview)
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = activeTab) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                text = { Text("Edit") },
                                modifier = Modifier.testTag("tab_edit")
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                text = { Text("Preview") },
                                modifier = Modifier.testTag("tab_preview")
                            )
                        }

                        if (activeTab == 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                EditorInputs(
                                    title = editorTitle,
                                    textFieldValue = textFieldValue,
                                    onTitleChanged = { newTitle ->
                                        viewModel.onNoteTextChanged(newTitle, textFieldValue.text)
                                    },
                                    onContentChanged = { newValue ->
                                        textFieldValue = newValue
                                        viewModel.onNoteTextChanged(editorTitle, newValue.text)
                                    },
                                    tags = note?.tags ?: emptyList(),
                                    onTagsChanged = { newTags ->
                                        viewModel.updateTags(newTags)
                                    },
                                    currentFolder = note?.folder,
                                    availableFolders = availableFolders,
                                    onFolderChanged = { folder ->
                                        note?.let { viewModel.setNoteFolder(it, folder) }
                                    },
                                    backlinksSection = backlinksSection
                                )
                            }
                        } else {
                            MarkdownPreview(
                                markdown = editorContent,
                                modifier = Modifier.fillMaxSize(),
                                onWikiLinkClick = onWikiLinkClick,
                                onHashtagClick = { hashtag ->
                                    viewModel.selectTag(hashtag)
                                    handleExit()
                                }
                            )
                        }
                    }
                }
            }
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorInputs(
    title: String,
    textFieldValue: TextFieldValue,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (TextFieldValue) -> Unit,
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    currentFolder: String?,
    availableFolders: List<String>,
    onFolderChanged: (String?) -> Unit,
    backlinksSection: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = title,
            onValueChange = onTitleChanged,
            placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("note_title_input")
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Label,
                contentDescription = "Tags",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            
            var showAddTagDialog by remember { mutableStateOf(false) }
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                items(tags) { tag ->
                    InputChip(
                        selected = true,
                        // Chip body is a no-op; only the trailing ✕ removes the tag,
                        // so an accidental tap can't silently delete it.
                        onClick = { },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { onTagsChanged(tags - tag) }
                                    .testTag("remove_tag_${tag}")
                            )
                        },
                        modifier = Modifier.testTag("editor_tag_chip_$tag")
                    )
                }
                
                item {
                    IconButton(
                        onClick = { showAddTagDialog = true },
                        modifier = Modifier
                            .testTag("add_tag_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Tag",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            if (showAddTagDialog) {
                var newTagText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showAddTagDialog = false },
                    title = { Text("Add Tag") },
                    text = {
                        OutlinedTextField(
                            value = newTagText,
                            onValueChange = { newTagText = it.trim().lowercase() },
                            placeholder = { Text("e.g. work, personal, idea") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_tag_text_input")
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (newTagText.isNotEmpty() && !tags.contains(newTagText)) {
                                    onTagsChanged(tags + newTagText)
                                }
                                showAddTagDialog = false
                            },
                            modifier = Modifier.testTag("add_tag_confirm_button")
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showAddTagDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            
            var folderMenuExpanded by remember { mutableStateOf(false) }
            var showNewFolderDialogInEditor by remember { mutableStateOf(false) }

            Box {
                SuggestionChip(
                    onClick = { folderMenuExpanded = true },
                    label = { Text(currentFolder ?: "Uncategorized", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("editor_folder_chip")
                )
                
                DropdownMenu(
                    expanded = folderMenuExpanded,
                    onDismissRequest = { folderMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Uncategorized") },
                        onClick = {
                            onFolderChanged(null)
                            folderMenuExpanded = false
                        },
                        modifier = Modifier.testTag("editor_folder_item_uncategorized")
                    )
                    
                    availableFolders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder) },
                            onClick = {
                                onFolderChanged(folder)
                                folderMenuExpanded = false
                            },
                            modifier = Modifier.testTag("editor_folder_item_$folder")
                        )
                    }
                    
                    HorizontalDivider()
                    
                    DropdownMenuItem(
                        text = { Text("+ New Folder...") },
                        onClick = {
                            folderMenuExpanded = false
                            showNewFolderDialogInEditor = true
                        },
                        modifier = Modifier.testTag("editor_folder_item_new")
                    )
                }
            }

            if (showNewFolderDialogInEditor) {
                var newFolderNameText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showNewFolderDialogInEditor = false },
                    title = { Text("Add Folder") },
                    text = {
                        OutlinedTextField(
                            value = newFolderNameText,
                            onValueChange = { newFolderNameText = it },
                            placeholder = { Text("Folder Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_new_folder_input")
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (newFolderNameText.isNotBlank()) {
                                    onFolderChanged(newFolderNameText.trim())
                                }
                                showNewFolderDialogInEditor = false
                            },
                            modifier = Modifier.testTag("editor_new_folder_confirm")
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showNewFolderDialogInEditor = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val isDark = isSystemInDarkTheme()
        val markdownTransformation = remember(isDark) { MarkdownVisualTransformation(isDark) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TextField(
                value = textFieldValue,
                onValueChange = onContentChanged,
                placeholder = { Text("Type your markdown here...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                visualTransformation = markdownTransformation,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("note_content_input")
            )
        }
        if (backlinksSection != null) {
            backlinksSection()
        }
    }
}

@Composable
fun SaveStatusIndicator(saveStatus: SaveStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(
                color = when (saveStatus) {
                    SaveStatus.Saving -> MaterialTheme.colorScheme.secondaryContainer
                    SaveStatus.Saved -> MaterialTheme.colorScheme.primaryContainer
                    SaveStatus.Idle -> Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        when (saveStatus) {
            SaveStatus.Saving -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Saving...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            SaveStatus.Saved -> {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Saved locally",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            SaveStatus.Idle -> {
                // Keep it clean and show nothing
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: NoteViewModel, onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lastExportedPath by viewModel.lastExportedPath.collectAsState()
    val token by viewModel.githubToken.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    var inputToken by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    var showClearTokenConfirm by remember { mutableStateOf(false) }

    // Synchronize local input field with stored token on first load
    LaunchedEffect(token) {
        inputToken = token
    }

    if (showClearTokenConfirm) {
        AlertDialog(
            onDismissRequest = { showClearTokenConfirm = false },
            title = { Text("Disconnect GitHub?") },
            text = { Text("This removes your saved access token from this device. Your notes stay here — only cloud sync stops until you reconnect.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearToken()
                        inputToken = ""
                        showClearTokenConfirm = false
                    },
                    modifier = Modifier.testTag("confirm_clear_token_button")
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTokenConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "To synchronize your offline notes with your GitHub Gists, you need to configure a GitHub Personal Access Token (PAT) with Gist permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = inputToken,
                onValueChange = { inputToken = it },
                label = { Text("GitHub Personal Access Token") },
                placeholder = { Text("ghp_...") },
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (tokenVisible) "Hide Token" else "Show Token"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_token_input")
            )

            // Primary action is full-width; the destructive "Disconnect" is
            // demoted to a text button and guarded by a confirmation dialog.
            Button(
                onClick = { viewModel.saveToken(inputToken.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_token_button")
            ) {
                Text("Save Securely")
            }

            if (token.isNotEmpty()) {
                TextButton(
                    onClick = { showClearTokenConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_token_button")
                ) {
                    Text("Disconnect GitHub", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Manual Synchronization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { viewModel.syncNow() },
                enabled = token.isNotEmpty() && !isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sync_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Synchronizing...")
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Now (Push & Pull)")
                }
            }

            AnimatedVisibility(
                visible = syncMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                syncMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.contains("failed", ignoreCase = true) || msg.contains("Error", ignoreCase = true)) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearSyncMessage() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().testTag("theme_settings_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "system" to "System Default",
                            "light" to "Light",
                            "dark" to "Dark"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label) },
                                leadingIcon = if (themeMode == mode) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_chip_$mode")
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().testTag("backup_settings_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Database Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export all notes, drafts, and tags as a JSON file, then share it to Drive, Downloads, or any app you choose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.exportDatabase() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_db_button")
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Database to JSON")
                    }

                    lastExportedPath?.let { path ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val file = java.io.File(path)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(shareIntent, "Share backup")
                                )
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_backup_button")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share backup file")
                        }
                    }

                    exportStatus?.let { status ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (status.startsWith("Failed")) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("export_status_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status.startsWith("Failed")) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.weight(1f).testTag("export_status_text")
                                )
                                IconButton(
                                    onClick = { viewModel.clearExportStatus() },
                                    modifier = Modifier.testTag("clear_export_status_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (status.startsWith("Failed")) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "How to generate a Personal Access Token (Classic):",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "1. Log in to GitHub.com\n" +
                        "2. Go to Settings -> Developer Settings\n" +
                        "3. Select Personal Access Tokens -> Tokens (classic)\n" +
                        "4. Click Generate new token -> Generate new token (classic)\n" +
                        "5. Give it a name and check the 'gist' scope checkbox\n" +
                        "6. Generate, copy, and paste it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    onWikiLinkClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null
) {
    val lines = markdown.lines()
    val status = MaterialTheme.octoStatus
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (markdown.isBlank()) {
            Text(
                text = "Nothing to preview yet. Start typing in the Editor!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        } else {
            lines.forEach { line ->
                when {
                    line.startsWith("# ") -> {
                        Text(
                            text = line.substring(2),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    line.startsWith("## ") -> {
                        Text(
                            text = line.substring(3),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    line.startsWith("### ") -> {
                        Text(
                            text = line.substring(4),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    line.startsWith("> ") -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(IntrinsicSize.Min)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(vertical = 8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AutolinkText(
                                text = autoLinkUrls(parseInlineStyles(line.substring(2), status), MaterialTheme.colorScheme.primary),
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                onWikiLinkClick = onWikiLinkClick,
                                onHashtagClick = onHashtagClick
                            )
                        }
                    }
                    line.startsWith("- [ ] ") || line.startsWith("- [x] ") || line.startsWith("- [X] ") -> {
                        val checked = !line.startsWith("- [ ] ")
                        Row(
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = if (checked) "Completed" else "Incomplete",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AutolinkText(
                                text = autoLinkUrls(parseInlineStyles(line.substring(6), status), MaterialTheme.colorScheme.primary),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                onWikiLinkClick = onWikiLinkClick,
                                onHashtagClick = onHashtagClick
                            )
                        }
                    }
                    line.startsWith("- ") || line.startsWith("* ") -> {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "• ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            AutolinkText(
                                text = autoLinkUrls(parseInlineStyles(line.substring(2), status), MaterialTheme.colorScheme.primary),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                onWikiLinkClick = onWikiLinkClick,
                                onHashtagClick = onHashtagClick
                            )
                        }
                    }
                    else -> {
                        if (line.isNotBlank()) {
                            AutolinkText(
                                text = autoLinkUrls(parseInlineStyles(line, status), MaterialTheme.colorScheme.primary),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 2.dp),
                                onWikiLinkClick = onWikiLinkClick,
                                onHashtagClick = onHashtagClick
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

fun autoLinkUrls(annotatedString: AnnotatedString, linkColor: Color): AnnotatedString {
    val text = annotatedString.text
    val urlRegex = """https?://[a-zA-Z0-9\-._~:/?#\[\]@!$&'()*+,;=]+""".toRegex()
    val matches = urlRegex.findAll(text)
    if (matches.none()) return annotatedString

    return buildAnnotatedString {
        append(annotatedString)
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            val url = match.value
            addStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = "URL",
                annotation = url,
                start = start,
                end = end
            )
        }
    }
}

@Composable
fun AutolinkText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontStyle: FontStyle? = null,
    onWikiLinkClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val mergedStyle = style.copy(
        color = color,
        fontStyle = fontStyle ?: style.fontStyle
    )
    
    val hasAnnotations = remember(text) {
        text.getStringAnnotations(tag = "URL", start = 0, end = text.length).isNotEmpty() ||
        text.getStringAnnotations(tag = "WIKILINK", start = 0, end = text.length).isNotEmpty() ||
        text.getStringAnnotations(tag = "HASHTAG", start = 0, end = text.length).isNotEmpty()
    }
    
    if (hasAnnotations) {
        ClickableText(
            text = text,
            style = mergedStyle,
            onClick = { offset ->
                text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            uriHandler.openUri(annotation.item)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                text.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        onWikiLinkClick?.invoke(annotation.item)
                    }
                text.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        onHashtagClick?.invoke(annotation.item)
                    }
            },
            modifier = modifier
        )
    } else {
        Text(
            text = text,
            style = mergedStyle,
            modifier = modifier
        )
    }
}

fun parseInlineStyles(text: String, colors: OctoStatusColors): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(parseInlineStyles(text.substring(i + 2, end), colors))
                    }
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            } else if (text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(parseInlineStyles(text.substring(i + 2, end), colors))
                    }
                    i = end + 2
                } else {
                    append("~~")
                    i += 2
                }
            } else if (text.startsWith("*", i)) {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(parseInlineStyles(text.substring(i + 1, end), colors))
                    }
                    i = end + 1
                } else {
                    append("*")
                    i += 1
                }
            } else if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = colors.codeBackground,
                            color = colors.code
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append("`")
                    i += 1
                }
            } else if (text.startsWith("[[", i)) {
                val end = text.indexOf("]]", i + 2)
                if (end != -1) {
                    val targetTitle = text.substring(i + 2, end)
                    pushStringAnnotation(tag = "WIKILINK", annotation = targetTitle)
                    withStyle(
                        SpanStyle(
                            color = colors.wikiLink,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(targetTitle)
                    }
                    pop()
                    i = end + 2
                } else {
                    append("[[")
                    i += 2
                }
            } else if (text.startsWith("#", i) && (i == 0 || text[i - 1].isWhitespace())) {
                var end = i + 1
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '-')) {
                    end++
                }
                val tagName = text.substring(i + 1, end)
                if (tagName.isNotEmpty()) {
                    pushStringAnnotation(tag = "HASHTAG", annotation = tagName)
                    withStyle(
                        SpanStyle(
                            color = colors.hashtag,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append("#$tagName")
                    }
                    pop()
                    i = end
                } else {
                    append('#')
                    i += 1
                }
            } else {
                append(text[i])
                i += 1
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// Compact, locale-aware relative time for list cards ("2 hours ago", "Yesterday").
fun formatRelativeTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    if (timestamp <= 0L || timestamp > now) return formatTimestamp(timestamp)
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

@Composable
fun SyncStatusIndicator(syncState: SyncState) {
    val backgroundColor by animateColorAsState(
        targetValue = when (syncState) {
            SyncState.Synced -> Color(0xFFE8F5E9)
            SyncState.Syncing -> Color(0xFFE3F2FD)
            SyncState.Offline -> Color(0xFFFFEBEE)
        },
        label = "syncBgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = when (syncState) {
            SyncState.Synced -> Color(0xFF2E7D32)
            SyncState.Syncing -> Color(0xFF1565C0)
            SyncState.Offline -> Color(0xFFC62828)
        },
        label = "syncContentColor"
    )
    val icon = when (syncState) {
        SyncState.Synced -> Icons.Default.CloudDone
        SyncState.Syncing -> Icons.Default.Sync
        SyncState.Offline -> Icons.Default.CloudOff
    }
    val text = when (syncState) {
        SyncState.Synced -> "Synced"
        SyncState.Syncing -> "Syncing..."
        SyncState.Offline -> "Offline"
    }

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("sync_status_indicator")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (syncState == SyncState.Syncing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

class MarkdownVisualTransformation(private val isDarkTheme: Boolean) : VisualTransformation {
    // Cache the last highlight so cursor moves / recompositions that don't change
    // the text skip the regex passes (filter() is called on every keystroke).
    private var cachedInput: String? = null
    private var cachedOutput: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = if (text.text == cachedInput) {
            cachedOutput!!
        } else {
            highlightMarkdown(text.text, isDarkTheme).also {
                cachedInput = text.text
                cachedOutput = it
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

fun highlightMarkdown(text: String, isDark: Boolean): AnnotatedString {
    val sc = if (isDark) DarkStatusColors else LightStatusColors
    return buildAnnotatedString {
        append(text)
        
        // 1. Headings (Lines starting with #)
        val lines = text.lines()
        var currentOffset = 0
        for (line in lines) {
            if (line.startsWith("# ")) {
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2F80ED),
                        fontSize = 20.sp
                    ),
                    start = currentOffset,
                    end = currentOffset + line.length
                )
            } else if (line.startsWith("## ")) {
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                        fontSize = 18.sp
                    ),
                    start = currentOffset,
                    end = currentOffset + line.length
                )
            } else if (line.startsWith("### ")) {
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00BCD4),
                        fontSize = 16.sp
                    ),
                    start = currentOffset,
                    end = currentOffset + line.length
                )
            }
            currentOffset += line.length + 1 // +1 for the newline character
        }

        // 2. Bold (**text**)
        val boldRegex = """\*\*.*?\*\*""".toRegex()
        boldRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.Bold),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 3. Italic (*text*) — single asterisks only, so it doesn't match inside **bold**
        val italicRegex = """(?<!\*)\*(?!\*)([^*\n]+)\*(?!\*)""".toRegex()
        italicRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(fontStyle = FontStyle.Italic),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 4. Strikethrough (~~text~~)
        val strikeRegex = """~~.*?~~""".toRegex()
        strikeRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(textDecoration = TextDecoration.LineThrough),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 5. Code (`text`)
        val codeRegex = """`.*?`""".toRegex()
        codeRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = sc.codeBackground,
                    color = sc.code
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 6. WikiLinks ([[Note Title]])
        val wikiRegex = """\[\[.*?\]\]""".toRegex()
        wikiRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(
                    color = sc.wikiLink,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 7. Hashtags (#tag)
        val tagRegex = """(?<=\s|^)#([a-zA-Z0-9_-]+)""".toRegex()
        tagRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(
                    color = sc.hashtag,
                    fontWeight = FontWeight.Medium
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }
}
