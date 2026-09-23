package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.VideoBitratePreset
import com.example.ui.components.LivePreviewStage
import com.example.ui.components.MasterConfigDialog
import com.example.ui.components.MovementsCarousel
import com.example.ui.components.ProgressLogModal
import com.example.ui.components.SyntaxConfigDialog
import com.example.ui.components.TransitionsCarousel
import com.example.ui.components.VideoTimelineTrack
import com.example.ui.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: Long,
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
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
    val currentImageIndex by viewModel.currentImageIndex.collectAsStateWithLifecycle()
    val selectedMovement by viewModel.selectedMovement.collectAsStateWithLifecycle()
    val selectedAspectRatio by viewModel.selectedAspectRatio.collectAsStateWithLifecycle()
    val selectedTransition by viewModel.selectedTransition.collectAsStateWithLifecycle()
    val transitionIdsText by viewModel.transitionIdsText.collectAsStateWithLifecycle()
    val transitionError by viewModel.transitionError.collectAsStateWithLifecycle()
    val isPreviewingTransition by viewModel.isPreviewingTransition.collectAsStateWithLifecycle()
    val syntaxText by viewModel.syntaxText.collectAsStateWithLifecycle()
    val syntaxError by viewModel.syntaxError.collectAsStateWithLifecycle()
    val isSyntaxModalOpen by viewModel.isSyntaxModalOpen.collectAsStateWithLifecycle()
    val isProgressModalOpen by viewModel.isProgressModalOpen.collectAsStateWithLifecycle()
    val isMasterConfigOpen by viewModel.isMasterConfigOpen.collectAsStateWithLifecycle()
    val isTestPlaying by viewModel.isTestPlaying.collectAsStateWithLifecycle()
    val renderingState by viewModel.renderingState.collectAsStateWithLifecycle()
    val selectedResolution by viewModel.selectedResolution.collectAsStateWithLifecycle()
    val selectedFps by viewModel.selectedFps.collectAsStateWithLifecycle()
    val selectedBitrateMbps by viewModel.selectedBitrateMbps.collectAsStateWithLifecycle()

    // ActivityResultLaunchers para seleção de arquivos

    // 1. Upload Direto de Imagens
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${images.size} fotos • Modo Profissional",
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
                    // Badge de progresso se houver renderização ativa em segundo plano
                    if (renderingState.isRunning) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.openMasterConfig() }
                                .padding(end = 4.dp)
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

                    // Botão "Configurar Tudo" de destaque no topo
                    Button(
                        onClick = { viewModel.openMasterConfig() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("top_master_config_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configurar Tudo",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Configurar Tudo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
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
            // Barra rápida de Adição de Mídia e Ações Rápidas
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { directImagesLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_images_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Fotos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_zip_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ZIP", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.generateAutomaticPrompts() },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("quick_auto_prompts_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sortear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =========================================================================
            // 1. UMA E ÚNICA ÁREA DE PRÉ-VISUALIZAÇÃO DE ANIMAÇÕES E TRANSIÇÕES
            // (Com Proporções instantâneas: 16:9, 9:16, 1:1, 4:5 e moldura de estúdio)
            // =========================================================================
            LivePreviewStage(
                images = images,
                currentImageIndex = currentImageIndex,
                onPreviousImage = { viewModel.previousImage() },
                onNextImage = { viewModel.nextImage() },
                selectedMovement = selectedMovement,
                selectedAspectRatio = selectedAspectRatio,
                onAspectRatioChange = { viewModel.selectAspectRatio(it) },
                selectedTransition = selectedTransition,
                isPreviewingTransition = isPreviewingTransition,
                onTogglePreviewMode = { viewModel.togglePreviewMode() },
                isPlaying = isTestPlaying,
                onTogglePlay = { viewModel.toggleTestPlaying() },
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // =========================================================================
            // 2. LINHA DO TEMPO DE VÍDEO PROFISSIONAL (TIMELINE TRACK)
            // (Exatamente como nos grandes editores de vídeo: régua, playhead, clipes e nós)
            // =========================================================================
            VideoTimelineTrack(
                images = images,
                currentImageIndex = currentImageIndex,
                onSelectImage = { viewModel.setImageIndex(it) },
                selectedMovement = selectedMovement,
                selectedTransition = selectedTransition,
                onSelectTransition = {
                    viewModel.selectTransition(it)
                },
                isPlaying = isTestPlaying,
                onAddMediaClick = { directImagesLauncher.launch("image/*") },
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 3. LINHA HORIZONTAL ROLÁVEL DE ANIMAÇÕES DE MOVIMENTO DE CÂMERA
            // (27 Movimentos Profissionais: IDs 0 a 26)
            // =========================================================================
            MovementsCarousel(
                selectedMovement = selectedMovement,
                onSelectMovement = { viewModel.selectMovement(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 4. LINHA HORIZONTAL ROLÁVEL DE EFEITOS DE TRANSIÇÕES SUAVES
            // (20 Transições Profissionais estilo CapCut + 0 Sem Transição)
            // =========================================================================
            TransitionsCarousel(
                selectedTransition = selectedTransition,
                onTransitionSelected = { viewModel.selectTransition(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Botão de Rodapé Central: Exportar Vídeo Final Unificado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = { viewModel.openMasterConfig() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("bottom_export_action_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Configurar Tudo & Exportar Vídeo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Defina qualidade, transições e inicie a exportação unificada",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // =========================================================================
    // POP-UP CENTRALIZADOR: "CONFIGURAR TUDO" & EXPORTAR
    // (Aba de Transições com validação 0-20, Qualidade de Saída, Sintaxe,
    //  Área de Logs em tempo real, Banner do Diretório de Salvamento e Botão Iniciar)
    // =========================================================================
    MasterConfigDialog(
        isOpen = isMasterConfigOpen,
        project = project,
        images = images,
        selectedTransition = selectedTransition,
        transitionIdsText = transitionIdsText,
        transitionError = transitionError,
        onTransitionIdsChange = { viewModel.updateTransitionIdsText(it) },
        selectedResolution = selectedResolution,
        onResolutionChange = { viewModel.selectResolution(it) },
        selectedFps = selectedFps,
        onFpsChange = { viewModel.selectFps(it) },
        selectedBitrateMbps = selectedBitrateMbps,
        onBitratePresetChange = { viewModel.selectBitratePreset(it) },
        onBitrateSliderChange = { viewModel.setBitrateMbps(it) },
        syntaxText = syntaxText,
        syntaxError = syntaxError,
        onSyntaxChange = { viewModel.updateSyntaxText(it) },
        onAutoGeneratePrompts = { viewModel.generateAutomaticPrompts() },
        onSaveAndValidateSyntax = { viewModel.validateAndSaveSyntax() },
        renderingState = renderingState,
        onStartRendering = { viewModel.startRendering(context) },
        onCancelRendering = { viewModel.cancelRendering(context) },
        onChangeDirectoryClick = { directoryPickerLauncher.launch(null) },
        onDismiss = { viewModel.closeMasterConfig() }
    )

    // Modal de Sintaxe Textual Rápido
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

    // Modal de Progresso em Tempo Real
    ProgressLogModal(
        isOpen = isProgressModalOpen,
        renderingState = renderingState,
        onCancel = { viewModel.cancelRendering(context) },
        onMinimize = { viewModel.closeProgressModal() }
    )
}
