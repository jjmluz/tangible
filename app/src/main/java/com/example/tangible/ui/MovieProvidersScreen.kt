package com.example.tangible.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.tangible.data.MovieProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieProvidersScreen(
    onBackClick: () -> Unit,
    region: String,
    providers: List<MovieProvider>,
    onProvidersChanged: (List<MovieProvider>) -> Unit,
    onGetServicesForRegion: suspend (String) -> List<MovieProvider>
) {
    var isLoading by remember { mutableStateOf(providers.isEmpty()) }
    val currentProviders = remember(providers) { providers.toMutableStateList() }

    LaunchedEffect(region) {
        if (providers.isEmpty()) {
            isLoading = true
            val fetched = onGetServicesForRegion(region)
            onProvidersChanged(fetched)
            isLoading = false
        }
    }

    // Drag and drop state
    val lazyListState = rememberLazyListState()
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in currentProviders.indices || to !in currentProviders.indices) return
        val item = currentProviders.removeAt(from)
        currentProviders.add(to, item)
        onProvidersChanged(currentProviders.toList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Movie streaming services ($region)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TangibleBackground),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = TangibleBackground
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                Text(
                    text = "Enabled providers will be used to resolve streaming links. Long-press to drag and reorder (priority).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val item = lazyListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { item ->
                                            val top = item.offset
                                            val bottom = item.offset + item.size
                                            offset.y.toInt() in top..bottom
                                        }
                                    item?.let {
                                        draggingItemIndex = it.index
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    
                                    val draggingIndex = draggingItemIndex ?: return@detectDragGesturesAfterLongPress
                                    val layoutInfo = lazyListState.layoutInfo
                                    val draggingItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingIndex } ?: return@detectDragGesturesAfterLongPress
                                    
                                    val currentOffset = draggingItem.offset + dragOffset
                                    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                        val top = item.offset
                                        val bottom = item.offset + item.size
                                        item.index != draggingIndex && currentOffset.toInt() in top..bottom
                                    }
                                    
                                    targetItem?.let {
                                        moveItem(draggingIndex, it.index)
                                        draggingItemIndex = it.index
                                        dragOffset -= (it.offset - draggingItem.offset)
                                    }
                                },
                                onDragEnd = {
                                    draggingItemIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingItemIndex = null
                                    dragOffset = 0f
                                }
                            )
                        },
                    state = lazyListState
                ) {
                    items(currentProviders, key = { it.id }) { provider ->
                        val index = currentProviders.indexOf(provider)
                        val isDragging = index == draggingItemIndex
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                                .animateItem(),
                            tonalElevation = elevation,
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else TangibleBackground
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp) // Slightly smaller than original 56dp
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = provider.iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = provider.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Checkbox(
                                    checked = provider.isEnabled,
                                    onCheckedChange = { checked ->
                                        val newList = currentProviders.toMutableList()
                                        val itemIndex = newList.indexOfFirst { it.id == provider.id }
                                        if (itemIndex != -1) {
                                            newList[itemIndex] = provider.copy(isEnabled = checked)
                                            onProvidersChanged(newList.toList())
                                        }
                                    }
                                )
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        if (!isDragging) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
