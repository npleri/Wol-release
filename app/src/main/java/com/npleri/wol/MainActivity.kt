package com.npleri.wol

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

private const val WAKE_TIMEOUT_MS = 120_000L
private const val DOTS = 16

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WolTheme { App() } }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val c = LocalPalette.current
    val scope = rememberCoroutineScope()
    val pc by remember { context.pcFlow() }.collectAsState(initial = null)
    var editing by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        val current = pc ?: return@Box
        if (editing || current.mac.isBlank()) {
            SettingsScreen(
                pc = current,
                canCancel = current.mac.isNotBlank(),
                onSave = { scope.launch { context.savePc(it); editing = false } },
                onCancel = { editing = false },
            )
        } else {
            HomeScreen(current, onEdit = { editing = true })
        }
    }
}

@Composable
private fun HomeScreen(pc: Pc, onEdit: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPalette.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var online by remember { mutableStateOf<Boolean?>(null) }
    var wakeAt by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(pc.host) {
        online = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                online = isOnline(pc.host)
                if (wakeAt > 0 && online == true) {
                    wakeAt = 0
                    message = ""
                } else if (wakeAt > 0 && SystemClock.elapsedRealtime() - wakeAt > WAKE_TIMEOUT_MS) {
                    wakeAt = 0
                    message = "Sin respuesta tras 2 min. Revisá BIOS e Inicio rápido (Fase 0)."
                }
                delay(if (wakeAt > 0) 1500 else 4000)
            }
        }
    }

    val waking = wakeAt > 0
    val status = when {
        waking -> "BOOT"
        online == true -> "ON"
        online == false -> "OFF"
        else -> "--"
    }

    Column(Modifier.fillMaxSize()) {
        Header("WOL · ${BuildConfig.VERSION_NAME}", "CONFIG", onEdit)
        Spacer(Modifier.height(32.dp))
        BasicText("01 · EQUIPO", style = labelStyle(c.ink2))
        BasicText(pc.name, Modifier.padding(top = 8.dp), style = TextStyle(fontFamily = Grotesk, fontSize = 32.sp, color = c.ink))
        Spacer(Modifier.height(16.dp))
        Hairline()
        Spec("MAC", pc.mac)
        Spec("HOST", pc.host)
        Spec("PUERTO", pc.port.toString())

        Spacer(Modifier.weight(1f))
        BasicText("02 · ESTADO", style = labelStyle(c.ink2))
        BasicText(
            status,
            maxLines = 1,
            style = TextStyle(fontFamily = Doto, fontSize = 96.sp, letterSpacing = (-0.02).em, color = c.ink),
        )
        DotRow(waking = waking, on = online == true && !waking)
        Spacer(Modifier.weight(1f))

        if (message.isNotEmpty()) {
            BasicText(message.uppercase(), Modifier.padding(bottom = 16.dp), style = labelStyle(c.ink2))
        }
        PillButton("ENCENDER", enabled = online != true && !waking, accent = true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                message = try {
                    val targets = sendWake(context, parseMac(pc.mac)!!, pc.port)
                    wakeAt = SystemClock.elapsedRealtime()
                    "Paquete enviado 3× → " + targets.joinToString { it.hostAddress.orEmpty() }
                } catch (e: IOException) {
                    "Error al enviar: ${e.message}"
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(pc: Pc, canCancel: Boolean, onSave: (Pc) -> Unit, onCancel: () -> Unit) {
    val c = LocalPalette.current
    var name by rememberSaveable { mutableStateOf(pc.name) }
    var mac by rememberSaveable { mutableStateOf(pc.mac) }
    var host by rememberSaveable { mutableStateOf(pc.host) }
    var port by rememberSaveable { mutableStateOf(pc.port.toString()) }
    BackHandler(enabled = canCancel, onBack = onCancel)

    val macBytes = parseMac(mac)
    val portNumber = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val valid = macBytes != null && host.isNotBlank() && portNumber != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("WOL · CONFIG", if (canCancel) "CERRAR" else null, onCancel)
        Spacer(Modifier.height(32.dp))
        BasicText("Equipo", style = TextStyle(fontFamily = Grotesk, fontSize = 32.sp, color = c.ink))
        Field("01 · NOMBRE", name, { name = it }, hint = "PC")
        Field(
            "02 · MAC", mac, { mac = it }, hint = "AA:BB:CC:DD:EE:FF",
            error = mac.isNotBlank() && macBytes == null,
            keyboard = KeyboardOptions(KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
        )
        Field(
            "03 · IP O HOST", host, { host = it }, hint = "192.168.1.10",
            keyboard = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
        )
        Field(
            "04 · PUERTO WOL", port, { port = it }, hint = "9",
            error = port.isNotBlank() && portNumber == null,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        BasicText(
            "La MAC y la IP las muestra tools/diagnostico-wol.ps1 en la PC.".uppercase(),
            Modifier.padding(top = 24.dp),
            style = labelStyle(c.ink2),
        )
        Spacer(Modifier.height(40.dp))
        PillButton("GUARDAR", enabled = valid, accent = false) {
            onSave(Pc(name.trim().ifBlank { "PC" }, formatMac(macBytes!!), host.trim(), portNumber!!))
        }
    }
}

@Composable
private fun Header(title: String, action: String?, onAction: () -> Unit) {
    val c = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, Modifier.weight(1f), style = labelStyle(c.ink2))
        if (action != null) {
            BasicText(action, Modifier.clickable(onClick = onAction).padding(12.dp), style = labelStyle(c.ink))
        }
    }
}

@Composable
private fun Hairline(color: Color = LocalPalette.current.hairline) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
private fun Spec(key: String, value: String) {
    val c = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        BasicText(key, Modifier.weight(1f), style = labelStyle(c.ink2))
        BasicText(value, style = labelStyle(c.ink, 13.sp))
    }
    Hairline()
}

/** Fila de puntos: encendidos si la PC responde, barrido por pasos mientras arranca. */
@Composable
private fun DotRow(waking: Boolean, on: Boolean) {
    val c = LocalPalette.current
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(waking) {
        step = 0
        while (waking) {
            delay(90)
            step = (step + 1) % (DOTS + 4)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(DOTS) { i ->
            val lit = on || (waking && i in step - 3..step)
            Box(Modifier.size(8.dp).background(if (lit) c.ink else c.ink3, CircleShape))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    error: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = LocalPalette.current
    val textStyle = TextStyle(fontFamily = Mono, fontSize = 18.sp, color = c.ink)
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        BasicText(label, style = labelStyle(if (error) c.red else c.ink2))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = keyboard,
            cursorBrush = SolidColor(c.ink),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(hint, style = textStyle.copy(color = c.ink3))
                    inner()
                }
            },
        )
        Hairline(if (error) c.red else c.hairline)
    }
}

/** Botón píldora; al presionarlo se invierte (sin sombras ni ripple). */
@Composable
private fun PillButton(text: String, enabled: Boolean, accent: Boolean, onClick: () -> Unit) {
    val c = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = when {
        !enabled -> c.ink3
        accent -> c.red
        else -> c.ink
    }
    val textColor = when {
        pressed -> c.ink
        !enabled -> c.ink2
        accent -> Color.White
        else -> c.bg
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(if (pressed) c.bg else fill)
            .border(1.dp, if (pressed) c.ink else fill, CircleShape)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = labelStyle(textColor, 14.sp).copy(fontWeight = FontWeight.Bold))
    }
}
