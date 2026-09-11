package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ImagesCarousel
import com.example.ui.components.LivePreviewStage
import com.example.ui.components.MovementsCarousel
import com.example.ui.components.ProgressLogModal
import com.example.ui.components.SyntaxConfigDialog
import com.example.ui.components.VideoOutputSettingsCard
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: Long,
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(projectId) {
        viewModel.initProject(projectId)
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val project by viewModel.project.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val selectedMovement by viewModel.selectedMovement.collectAsStateWithLifecycle()
    val syntaxText by viewModel.syntaxText.collectAsStateWithLifecycle()
    val syntaxError by viewModel.syntaxError.collectAsStateWithLifecycle()
    val isSyntaxModalOpen by viewModel.isSyntaxModalOpen.collectAsStateWithLifecycle()
    val isProgressModalOpen by viewModel.isProgressModalOpen.collectAsStateWithLifecycle()
    val isTestPlaying by viewModel.isTestPlaying.collectAsStateWithLifecycle()
    val renderingState by viewModel.renderingState.collectAsStateWithLifecycle()
    val selectedResolution by viewModel.selectedResolution.collectAsStateWithLifecycle()
    val selectedFps by viewModel.selectedFps.collectAsStateWithLifecycle()
    val selectedBitrateMbps by viewModel.selectedBitrateMbps.collectAsStateWithLifecycle()

    // ActivityResultLaunchers para seleção de arquivos

    // 1. Upload Direto (MIME permitidos: image/jpeg, image/png, image/webp)
    val directImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importDirectImages(context, uris)
        }
    }

    // 2. Upload em ZIP
    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importZipFile(context, uri)
        }
    }

    // 3. Seleção de Diretório de Destino personalizado via SAF
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persiste permissão de leitura/escrita no SAF
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.updateCustomOutputDir(uri.toString())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = project?.name ?: "Editor de Vídeo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${images.size} imagens importadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    // Se estiver renderizando em background, mostra chip de status
                    if (renderingState.isRunning) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.reopenProgressModal() }
                                .padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${renderingState.progressPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Botão "Configurações" no canto superior direito para abrir Pop-up da Sintaxe
                    IconButton(
                        onClick = { viewModel.openSyntaxModal() },
                        modifier = Modifier.testTag("open_syntax_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações de Sintaxe"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Seletor de Arquivos & Opções de Destino
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Importação e Destino",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { directImagesLauncher.launch("image/*") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_images_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Imagens", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_zip_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importar ZIP", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { directoryPickerLauncher.launch(null) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_output_folder_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Destino", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (project?.customOutputDirUri != null) {
                            "Pasta personalizada configurada (SAF)"
                        } else {
                            "Salvar padrão: /Movies/AppAnimador/"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Palco de Teste Live (A primeira imagem da lista serve de palco de teste)
            LivePreviewStage(
                firstImage = images.firstOrNull(),
                selectedMovement = selectedMovement,
                isPlaying = isTestPlaying,
                onTogglePlay = { viewModel.toggleTestPlaying() },
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Carrossel Horizontal de Seleção de Animação de Movimento (0 a 10)
            MovementsCarousel(
                selectedMovement = selectedMovement,
                onSelectMovement = { viewModel.selectMovement(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Carrossel de Imagens com ContentScale.Fit, numeração 1..N e Long-Press para excluir
            ImagesCarousel(
                images = images,
                onDeleteImage = { viewModel.deleteImage(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Configurações de Saída do Vídeo (Resolução, FPS e Bitrate)
            VideoOutputSettingsCard(
                selectedResolution = selectedResolution,
                onResolutionChange = { viewModel.selectResolution(it) },
                selectedFps = selectedFps,
                onFpsChange = { viewModel.selectFps(it) },
                selectedBitrateMbps = selectedBitrateMbps,
                onBitratePresetChange = { viewModel.selectBitratePreset(it) },
                onBitrateSliderChange = { viewModel.setBitrateMbps(it) },
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Botão Iniciar Renderização
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = { viewModel.startRendering(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_rendering_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Iniciar Renderização de Vídeo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Modal de Configurações via Sintaxe Textual
    SyntaxConfigDialog(
        isOpen = isSyntaxModalOpen,
        syntaxText = syntaxText,
        errorMessage = syntaxError,
        totalImages = images.size,
        onSyntaxChange = { viewModel.updateSyntaxText(it) },
        onAutoGenerate = { viewModel.autoGenerateSyntax() },
        onSaveAndValidate = { viewModel.validateAndSaveSyntax() },
        onDismiss = { viewModel.closeSyntaxModal() }
    )

    // Modal de Acompanhamento de Progresso e Logs em Tempo Real
    ProgressLogModal(
        isOpen = isProgressModalOpen,
        renderingState = renderingState,
        onCancel = { viewModel.cancelRendering(context) },
        onMinimize = { viewModel.closeProgressModal() }
    )
}
