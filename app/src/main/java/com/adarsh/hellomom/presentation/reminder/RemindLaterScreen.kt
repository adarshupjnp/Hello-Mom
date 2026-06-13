package com.adarsh.hellomom.presentation.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindLaterScreen(
    navController: NavController,
    reminderId: Int,
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val timePickerState = rememberTimePickerState()
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Reschedule Reminder") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Select a new time for this reminder", style = MaterialTheme.typography.titleMedium)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            TimePicker(state = timePickerState)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    viewModel.updateReminderTime(reminderId, calendar.timeInMillis)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set New Time")
            }
        }
    }
}
