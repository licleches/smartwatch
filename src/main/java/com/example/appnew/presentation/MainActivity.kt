package com.example.appnew.presentation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import java.time.Duration
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private var overlayOk by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.createChannel(this)
        setContent { MedApp(overlayOk) { openOverlaySettings() } }
    }

    override fun onResume() {
        super.onResume()
        overlayOk = Settings.canDrawOverlays(this)   // ¿puede mostrar la alarma sobre otras pantallas?
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            Toast.makeText(this, "Activa \"Mostrar sobre otras apps\" en Ajustes", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun MedApp(overlayOk: Boolean, onGrant: () -> Unit) {
    val ctx = LocalContext.current
    var meds by remember { mutableStateOf(MedStore.load(ctx)) }
    var adding by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler(enabled = adding) { adding = false }

    MaterialTheme {
        if (adding) {
            AddScreen { name, h, m ->
                Reminders.schedule(ctx, MedStore.add(ctx, name, h, m))
                meds = MedStore.load(ctx)
                adding = false
            }
        } else {
            ListScreen(
                meds = meds,
                overlayOk = overlayOk,
                onGrant = onGrant,
                onAdd = { adding = true },
                onTaken = { MedStore.markTaken(ctx, it.id); meds = MedStore.load(ctx) },
                onDelete = {
                    Reminders.cancel(ctx, it); MedStore.delete(ctx, it.id); meds = MedStore.load(ctx)
                }
            )
        }
    }
}

/** Texto con la próxima toma pendiente del día. */
fun nextText(meds: List<Medication>): String {
    if (meds.isEmpty()) return "Agrega tu primera medicina"
    val now = LocalTime.now()
    val next = meds.firstOrNull { !it.takenToday && !LocalTime.of(it.hour, it.minute).isBefore(now) }
    return when {
        next != null -> {
            val mins = Duration.between(now, LocalTime.of(next.hour, next.minute)).toMinutes()
            "Próxima: ${next.name} en " + if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
        }
        meds.all { it.takenToday } -> "Todo al día"
        else -> "Tienes pendientes"
    }
}

@Composable
fun ListScreen(
    meds: List<Medication>,
    overlayOk: Boolean,
    onGrant: () -> Unit,
    onAdd: () -> Unit,
    onTaken: (Medication) -> Unit,
    onDelete: (Medication) -> Unit
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val done = meds.count { it.takenToday }
    Box(Modifier.fillMaxSize()) {
        // Anillo de progreso del día alrededor de la pantalla
        CircularProgressIndicator(
            progress = if (meds.isEmpty()) 0f else done.toFloat() / meds.size,
            modifier = Modifier.fillMaxSize().padding(2.dp)
        )
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { ListHeader { Text("Hoy $done/${meds.size}") } }
            item { Text(nextText(meds), textAlign = TextAlign.Center) }
            if (!overlayOk) item {
                Chip(
                    onClick = onGrant, label = { Text("Activar pantalla de alarma") },
                    secondaryLabel = { Text("Toca para permitir") },
                    colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()
                )
            }
            items(meds, key = { it.id }) { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val racha = if (m.activeStreak > 1) " · Racha ${m.activeStreak} días" else ""
                    Chip(
                        onClick = { selected = if (selected == m.id) null else m.id },
                        label = { Text(m.name) },
                        secondaryLabel = { Text((if (m.takenToday) "${m.timeText} · Tomado" else m.timeText) + racha) },
                        colors = if (m.takenToday) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selected == m.id) {   // al tocar un medicamento aparecen las acciones
                        Chip(
                            onClick = { onTaken(m); selected = null }, label = { Text("Marcar tomado") },
                            colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()
                        )
                        Chip(
                            onClick = { onDelete(m); selected = null }, label = { Text("Eliminar") },
                            colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item { Chip(onClick = onAdd, label = { Text("+ Agregar") }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun AddScreen(onSave: (String, Int, Int) -> Unit) {
    var type by remember { mutableStateOf<String?>(null) }
    var h by remember { mutableIntStateOf(8) }          // hora en formato 1-12
    var m by remember { mutableIntStateOf(0) }
    var pm by remember { mutableStateOf(false) }        // false = AM, true = PM
    var step by remember { mutableIntStateOf(1) }       // salto de minutos: 1, 5 o 10
    BackHandler(enabled = type != null) { type = null }

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        val t = type
        if (t == null) {
            item { ListHeader { Text("¿Qué medicina?") } }
            items(MED_TYPES) { name ->
                Chip(onClick = { type = name }, label = { Text(name) }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            item { ListHeader { Text(t) } }
            item { ValueStepper("Hora", h) { h = (h + it - 1 + 12) % 12 + 1 } }
            item { ValueStepper("Min", m) { m = (m + it * step + 60) % 60 } }
            item {
                Chip(
                    onClick = { pm = !pm },
                    label = { Text(if (pm) "PM" else "AM") },
                    secondaryLabel = { Text("Toca para cambiar") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = { step = when (step) { 1 -> 5; 5 -> 10; else -> 1 } },
                    label = { Text("Salto: $step min") },
                    secondaryLabel = { Text("Toca para cambiar") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = { onSave(t, (h % 12) + if (pm) 12 else 0, m) },
                    label = { Text("Guardar") }, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ValueStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onChange(-1) }, modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            colors = ButtonDefaults.secondaryButtonColors()) { Text("-") }
        Text("%s %02d".format(label, value))
        Button(onClick = { onChange(1) }, modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            colors = ButtonDefaults.secondaryButtonColors()) { Text("+") }
    }
}