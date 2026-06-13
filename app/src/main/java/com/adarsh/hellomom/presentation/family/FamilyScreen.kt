package com.adarsh.hellomom.presentation.family

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.core.utils.ShareInviteUtil
import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity
import com.adarsh.hellomom.presentation.components.ListShimmer
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    navController: NavController,
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showInviteDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FamilyEffect.ShareInviteLink -> {
                    ShareInviteUtil.shareInviteToWhatsApp(context, effect.inviteText)
                }
                is FamilyEffect.ShowError -> {
                    // Show error snackbar if needed
                }
            }
        }
    }

    if (showInviteDialog && state.isOwner) {
        InviteMemberDialog(
            onDismiss = { showInviteDialog = false },
            onSave = { name, email, role ->
                viewModel.sendIntent(FamilyIntent.OnInviteMember(name, email, role))
                showInviteDialog = false
            }
        )
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Family Access") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            ) 
        },
        floatingActionButton = {
            // Inviting / managing members is an owner-only admin action.
            if (state.isOwner) {
                FloatingActionButton(onClick = { showInviteDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Invite")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Authorized Family Members",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (state.isLoading) {
                ListShimmer(modifier = Modifier.weight(1f))
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }
            } else if (state.members.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No family members added yet.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.members) { member ->
                        FamilyMemberItem(member = member)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteMemberDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Husband") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Family Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email, 
                    onValueChange = { email = it }, 
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(text = "Select Role", style = MaterialTheme.typography.labelLarge)
                
                val roles = listOf("Husband", "Parent", "Caretaker", "Viewer")
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    roles.forEach { r ->
                        val isSelected = role == r
                        FilterChip(
                            selected = isSelected,
                            onClick = { role = r },
                            label = { Text(r) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, email, role) }) { Text("Invite") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FamilyMemberItem(member: FamilyMemberEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = member.name, fontWeight = FontWeight.Bold)
                Text(text = member.role, style = MaterialTheme.typography.bodySmall)
                Text(text = member.email, style = MaterialTheme.typography.bodySmall)
            }
            Badge { Text(member.status) }
        }
    }
}


