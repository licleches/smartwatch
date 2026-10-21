package com.example.appnew.presentation

import android.content.Context
import java.time.LocalDate

/** Tipos de medicamento disponibles. Agrega los que quieras aquí. */
val MED_TYPES = listOf(
    "Presión arterial", "Glucosa (azúcar)", "Insulina", "Colesterol", "Analgésico",
    "Antibiótico", "Antiinflamatorio", "Antialérgico", "Antiácido", "Tiroides", "Vitamina", "Pastilla"
)

data class Medication(
    val id: Int,
    val name: String,
    val hour: Int,               // 0-23 (se guarda en 24 h)
    val minute: Int,
    val lastTaken: String = "",  // fecha (yyyy-MM-dd) de la última toma
    val streak: Int = 0          // días seguidos tomado
) {
    val takenToday: Boolean get() = lastTaken == LocalDate.now().toString()
    /** Hora en formato 12 h con AM/PM, por ejemplo "8:05 PM". */
    val timeText: String get() {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(h12, minute, if (hour < 12) "AM" else "PM")
    }
    /** La racha solo cuenta si se tomó hoy o ayer. */
    val activeStreak: Int get() =
        if (takenToday || lastTaken == LocalDate.now().minusDays(1).toString()) streak else 0
}

object MedStore {
    private fun prefs(c: Context) = c.getSharedPreferences("meds", Context.MODE_PRIVATE)

    fun load(c: Context): List<Medication> =
        prefs(c).getString("data", "")!!.lines().filter { it.isNotBlank() }.mapNotNull {
            val p = it.split("|")
            if (p.size < 5) null
            else Medication(p[0].toInt(), p[1], p[2].toInt(), p[3].toInt(), p[4], p.getOrNull(5)?.toIntOrNull() ?: 0)
        }.sortedWith(compareBy({ it.hour }, { it.minute }))

    private fun save(c: Context, meds: List<Medication>) {
        val text = meds.joinToString("\n") {
            "${it.id}|${it.name}|${it.hour}|${it.minute}|${it.lastTaken}|${it.streak}"
        }
        prefs(c).edit().putString("data", text).commit() // guarda al instante
    }

    fun add(c: Context, name: String, hour: Int, minute: Int): Medication {
        val list = load(c)
        val med = Medication((list.maxOfOrNull { it.id } ?: 0) + 1, name, hour, minute)
        save(c, list + med)
        return med
    }

    fun delete(c: Context, id: Int) = save(c, load(c).filter { it.id != id })

    fun markTaken(c: Context, id: Int) = save(c, load(c).map {
        if (it.id != id || it.takenToday) it
        else it.copy(
            lastTaken = LocalDate.now().toString(),
            streak = if (it.lastTaken == LocalDate.now().minusDays(1).toString()) it.streak + 1 else 1
        )
    })
}