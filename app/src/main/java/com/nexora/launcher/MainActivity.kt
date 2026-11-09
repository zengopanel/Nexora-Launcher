package com.nexora.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) vm.homeSignal.value++
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val apps by vm.apps.collectAsState()
    val iconVersion by vm.iconVersion.collectAsState()
    val homeSignal by vm.homeSignal.collectAsState()
    val selectedPack by vm.selectedPack.collectAsState()

    var drawerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showPacks by remember { mutableStateOf(false) }

    LaunchedEffect(homeSignal) {
        drawerOpen = false
        query = ""
    }
    BackHandler(enabled = drawerOpen) {
        drawerOpen = false
        query = ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(drawerOpen) {
                if (!drawerOpen) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = { if (total < -100f) drawerOpen = true },
                    )
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = { if (!drawerOpen) showPacks = true },
            )
    ) {
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Drawer(
                apps = apps,
                query = query,
                iconVersion = iconVersion,
                icons = vm.icons,
                onQuery = { query = it },
                onLaunch = { app ->
                    vm.launch(app)
                    drawerOpen = false
                    query = ""
                },
            )
        }
    }

    if (showPacks) {
        val packs = remember { vm.icons.installedPacks() }
        AlertDialog(
            onDismissRequest = { showPacks = false },
            confirmButton = { TextButton(onClick = { showPacks = false }) { Text("OK") } },
            title = { Text("Icon pack") },
            text = {
                LazyColumn {
                    item {
                        Text(
                            text = (if (selectedPack == null) "✓  " else "") + "System icons (default)",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectPack(null); showPacks = false }
                                .padding(vertical = 12.dp),
                        )
                    }
                    items(packs) { p ->
                        Text(
                            text = (if (selectedPack == p.pkg) "✓  " else "") + p.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectPack(p.pkg); showPacks = false }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun Drawer(
    apps: List<AppInfo>,
    query: String,
    iconVersion: Int,
    icons: IconProvider,
    onQuery: (String) -> Unit,
    onLaunch: (AppInfo) -> Unit,
) {
    val focus = LocalFocusManager.current
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) apps else apps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6101114))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Search apps") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                filtered.singleOrNull()?.let(onLaunch)
                focus.clearFocus()
            }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color(0x33FFFFFF),
                unfocusedContainerColor = Color(0x22FFFFFF),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.key }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunch(app) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    val bmp = remember(app.key, iconVersion) { icons.icon(app) }
                    Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
