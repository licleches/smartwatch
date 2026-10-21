package com.example.appnew.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*

/** Pantalla que se abre cuando llega la hora (incluso con el reloj bloqueado). */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("id", -1)
        val name = intent.getStringExtra("name") ?: "Medicamento"
        val time = intent.getStringExtra("time") ?: ""

        setContent {
            MaterialTheme {
                ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    item { Text("Hora de tu medicamento", textAlign = TextAlign.Center) }
                    item { Text(name, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center) }
                    item { Text(time, textAlign = TextAlign.Center) }
                    item {
                        Chip(
                            onClick = { MedStore.markTaken(this@AlarmActivity, id); Reminders.stopAlarm(this@AlarmActivity, id); finish() },
                            label = { Text("Tomé") }, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Chip(
                            onClick = { Reminders.stopAlarm(this@AlarmActivity, id); Reminders.snooze(this@AlarmActivity, id); finish() },
                            label = { Text("Posponer 10 min") },
                            colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}