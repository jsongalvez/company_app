package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSearchScreen(
    clientViewModel: ClientViewModel,
    onBack: () -> Unit,
) {
    val searchResultsState by clientViewModel.searchResults.collectAsState()
    val isSearching by clientViewModel.isSearching.collectAsState()
    val searchError by clientViewModel.searchErrorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }

    val hasPriorResults = searchResultsState is UiState.Success
    val priorResults =
        if (hasPriorResults) {
            (searchResultsState as UiState.Success<List<ClientResponse>>).data
        } else {
            emptyList()
        }

    LaunchedEffect(Unit) {
        logInfo("ClientSearchScreen", "composable entered (first composition)")
    }

    LaunchedEffect(searchError) {
        val err = searchError
        if (err != null) {
            logInfo("ClientSearchScreen", "search error: $err")
            snackbarHostState.showSnackbar(err)
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            delay(300)
            logInfo("ClientSearchScreen", "debounced search: query=$query")
            clientViewModel.search(query)
        } else {
            clientViewModel.clearSearch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Client Search") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                navigationIcon = {
                    TextButton(onClick = {
                        logInfo("ClientSearchScreen", "back button onClick")
                        onBack()
                    }) {
                        Text("Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search clients by name or phone") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (isSearching) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            val errorMessage = searchError

            if (hasPriorResults && !isSearching && errorMessage == null) {
                Text(
                    text =
                        if (priorResults.isNotEmpty()) {
                            "${priorResults.size} results for \"$query\""
                        } else {
                            "No results for \"$query\". Try a different spelling."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (errorMessage != null && hasPriorResults) {
                Text(
                    text = "Search failed for \"$query\"",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    query.isBlank() && !hasPriorResults && errorMessage == null -> {
                        Text(
                            text = "Start typing to search by name or phone",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    isSearching && !hasPriorResults -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Searching...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    errorMessage != null && !hasPriorResults && !isSearching -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Search failed for \"$query\"",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { clientViewModel.search(query) }) {
                                Text("Retry")
                            }
                        }
                    }

                    hasPriorResults && priorResults.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(priorResults) { client ->
                                ClientSearchResultCard(client = client)
                            }
                        }
                    }

                    else -> {
                        // transition states: debounce waiting, empty results (header already visible)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientSearchResultCard(client: ClientResponse) {
    val fullName =
        buildString {
            append(client.firstName)
            val middle = client.middleName
            if (!middle.isNullOrBlank()) {
                append(" ")
                append(middle)
            }
            append(" ")
            append(client.lastName)
            val suf = client.suffix
            if (!suf.isNullOrBlank()) {
                append(", ")
                append(suf)
            }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fullName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = client.gender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${client.age} yrs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val phone = client.phoneNumber
                if (!phone.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
