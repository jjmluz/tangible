package com.example.tangible.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Movie streaming services ($region)") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                Text(
                    text = "Enabled providers will be used to resolve streaming links. Drag to reorder (priority).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = rememberLazyListState()
                ) {
                    itemsIndexed(currentProviders) { index, provider ->
                        ListItem(
                            headlineContent = { Text(provider.name) },
                            leadingContent = {
                                AsyncImage(
                                    model = provider.iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = provider.isEnabled,
                                        onCheckedChange = { checked ->
                                            val newList = currentProviders.toMutableList()
                                            newList[index] = provider.copy(isEnabled = checked)
                                            onProvidersChanged(newList)
                                        }
                                    )
                                    Column {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val newList = currentProviders.toMutableList()
                                                    val item = newList.removeAt(index)
                                                    newList.add(index - 1, item)
                                                    onProvidersChanged(newList)
                                                }
                                            },
                                            enabled = index > 0
                                        ) {
                                            Text("▲")
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index < currentProviders.size - 1) {
                                                    val newList = currentProviders.toMutableList()
                                                    val item = newList.removeAt(index)
                                                    newList.add(index + 1, item)
                                                    onProvidersChanged(newList)
                                                }
                                            },
                                            enabled = index < currentProviders.size - 1
                                        ) {
                                            Text("▼")
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
