package com.tuapp.recordatorio

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { TaskRepository(context) }
    var tasks by remember { mutableStateOf(repo.getAll()) }
    var showApiKeyDialog by remember { mutableStateOf(repo.getApiKey().isNullOrBlank()) }
    var pendingExtracted by remember { mutableStateOf<List<ClaudeVisionClient.ExtractedTask>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // --- Permisos necesarios ---
    val permissionsToRequest = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    var hasPermissions by remember {
        mutableStateOf(
            permissionsToRequest.all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
    }

    // --- Captura de foto ---
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            isProcessing = true
            errorMsg = null
            scope.launch {
                try {
                    val bitmap = uriToBitmap(context, photoUri!!)
                    val base64 = bitmapToBase64(bitmap)
                    val apiKey = repo.getApiKey() ?: throw Exception("Falta la clave de API")
                    val extracted = ClaudeVisionClient.extractTasks(base64, apiKey, System.currentTimeMillis())
                    pendingExtracted = extracted
                } catch (e: Exception) {
                    errorMsg = "No se pudo leer la imagen: ${e.message}"
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "guia_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        photoUri = uri
        cameraLauncher.launch(uri)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mis tareas pendientes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (!hasPermissions) {
            Button(onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) }) {
                Text("Conceder permisos (cámara y calendario)")
            }
            Spacer(Modifier.height(12.dp))
        }

        Row {
            Button(
                onClick = {
                    if (repo.getApiKey().isNullOrBlank()) {
                        showApiKeyDialog = true
                    } else if (hasPermissions) {
                        launchCamera()
                    } else {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                },
                enabled = !isProcessing
            ) {
                Text("📷 Tomar foto de guía")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { showApiKeyDialog = true }) {
                Text("Config. API")
            }
        }

        if (isProcessing) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Leyendo la imagen con IA...")
            }
        }

        errorMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tasks) { task ->
                TaskRow(
                    task = task,
                    onToggleDone = {
                        val updated = task.copy(completed = !task.completed)
                        repo.update(updated)
                        tasks = repo.getAll()
                    },
                    onDelete = {
                        task.calendarEventId?.let { CalendarHelper.deleteEvent(context, it) }
                        repo.delete(task.id)
                        tasks = repo.getAll()
                    }
                )
            }
        }
    }

    // --- Diálogo para ingresar la clave de API ---
    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = repo.getApiKey() ?: "",
            onSave = { key ->
                repo.saveApiKey(key)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // --- Diálogo para confirmar tareas detectadas por la IA ---
    if (pendingExtracted.isNotEmpty()) {
        ConfirmExtractedTasksDialog(
            extracted = pendingExtracted,
            onConfirm = { confirmed ->
                confirmed.forEach { ex ->
                    val eventId = CalendarHelper.insertEvent(
                        context = context,
                        title = ex.title,
                        description = ex.description,
                        startMillis = ex.dueDateMillis
                    )
                    repo.add(
                        Task(
                            id = TaskRepository.newId(),
                            title = ex.title,
                            description = ex.description,
                            dueDateMillis = ex.dueDateMillis,
                            calendarEventId = eventId
                        )
                    )
                }
                tasks = repo.getAll()
                pendingExtracted = emptyList()
            },
            onDismiss = { pendingExtracted = emptyList() }
        )
    }
}

@Composable
fun TaskRow(task: Task, onToggleDone: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.completed, onCheckedChange = { onToggleDone() })
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                if (task.description.isNotBlank()) {
                    Text(task.description, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Vence: ${dateFormat.format(task.dueDateMillis)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButtonDelete(onDelete)
        }
    }
}

@Composable
fun IconButtonDelete(onDelete: () -> Unit) {
    TextButton(onClick = onDelete) { Text("Eliminar") }
}

@Composable
fun ApiKeyDialog(currentKey: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clave de API de Claude") },
        text = {
            Column {
                Text("Consíguela gratis en console.anthropic.com y pégala aquí. Solo se guarda en tu teléfono.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("sk-ant-...") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ConfirmExtractedTasksDialog(
    extracted: List<ClaudeVisionClient.ExtractedTask>,
    onConfirm: (List<ClaudeVisionClient.ExtractedTask>) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tareas detectadas") },
        text = {
            Column {
                Text("Se agregarán a tu calendario con recordatorio:")
                Spacer(Modifier.height(8.dp))
                extracted.forEach {
                    Text("• ${it.title} — ${dateFormat.format(it.dueDateMillis)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(extracted) }) { Text("Agendar todo") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// --- Utilidades de imagen ---
private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap {
    val input = context.contentResolver.openInputStream(uri)
    return BitmapFactory.decodeStream(input)
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    // Reducimos tamaño para no exceder límites de la API y ahorrar datos
    val maxDim = 1200
    val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else bitmap

    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
