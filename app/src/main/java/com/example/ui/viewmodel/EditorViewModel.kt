package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.model.MovementEffect
import com.example.data.model.Project
import com.example.data.model.ProjectImage
import com.example.data.model.TransitionEffect
import com.example.data.model.VideoAspectRatio
import com.example.data.model.VideoBitratePreset
import com.example.data.model.VideoFps
import com.example.data.model.VideoResolution
import com.example.data.repository.ProjectRepository
import com.example.engine.ImageImportHelper
import com.example.engine.ImageImportResult
import com.example.engine.RandomPromptResult
import com.example.engine.RenderingManager
import com.example.engine.RenderingState
import com.example.engine.SyntaxParseResult
import com.example.engine.SyntaxParser
import com.example.engine.TransitionValidationResult
import com.example.engine.ZipExtractResult
import com.example.engine.ZipExtractor
import com.example.service.VideoRenderingService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository = ProjectRepository(
        AppDatabase.getInstance(application).projectDao(),
        application
    )

    private val appPreferences: AppPreferences = AppPreferences.getInstance(application)

    private var currentProjectId: Long = -1L

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _images = MutableStateFlow<List<ProjectImage>>(emptyList())
    val images: StateFlow<List<ProjectImage>> = _images.asStateFlow()

    // Navegação de imagem para o palco
    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()

    private val _selectedMovement = MutableStateFlow(MovementEffect.ALL_EFFECTS[1]) // Pan Left default
    val selectedMovement: StateFlow<MovementEffect> = _selectedMovement.asStateFlow()

    // Configurações de Saída do Vídeo
    private val _selectedResolution = MutableStateFlow(VideoResolution.DEFAULT)
    val selectedResolution: StateFlow<VideoResolution> = _selectedResolution.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(VideoAspectRatio.RATIO_16_9)
    val selectedAspectRatio: StateFlow<VideoAspectRatio> = _selectedAspectRatio.asStateFlow()

    private val _selectedFps = MutableStateFlow(VideoFps.DEFAULT.fps)
    val selectedFps: StateFlow<Int> = _selectedFps.asStateFlow()

    private val _selectedBitrateMbps = MutableStateFlow(VideoBitratePreset.DEFAULT.mbps)
    val selectedBitrateMbps: StateFlow<Float> = _selectedBitrateMbps.asStateFlow()

    // Transições Suaves CapCut (Requisito 2 e 3)
    private val _selectedTransition = MutableStateFlow(TransitionEffect.DEFAULT)
    val selectedTransition: StateFlow<TransitionEffect> = _selectedTransition.asStateFlow()

    private val _transitionIdsText = MutableStateFlow("1")
    val transitionIdsText: StateFlow<String> = _transitionIdsText.asStateFlow()

    private val _transitionError = MutableStateFlow<String?>(null)
    val transitionError: StateFlow<String?> = _transitionError.asStateFlow()

    private val _isPreviewingTransition = MutableStateFlow(false)
    val isPreviewingTransition: StateFlow<Boolean> = _isPreviewingTransition.asStateFlow()

    private val _syntaxText = MutableStateFlow("")
    val syntaxText: StateFlow<String> = _syntaxText.asStateFlow()

    private val _syntaxError = MutableStateFlow<String?>(null)
    val syntaxError: StateFlow<String?> = _syntaxError.asStateFlow()

    private val _isSyntaxModalOpen = MutableStateFlow(false)
    val isSyntaxModalOpen: StateFlow<Boolean> = _isSyntaxModalOpen.asStateFlow()

    private val _isProgressModalOpen = MutableStateFlow(false)
    val isProgressModalOpen: StateFlow<Boolean> = _isProgressModalOpen.asStateFlow()

    private val _isTestPlaying = MutableStateFlow(true)
    val isTestPlaying: StateFlow<Boolean> = _isTestPlaying.asStateFlow()

    private val _messageEvents = MutableSharedFlow<String>()
    val messageEvents: SharedFlow<String> = _messageEvents.asSharedFlow()

    val renderingState: StateFlow<RenderingState> = RenderingManager.state

    fun initProject(projectId: Long) {
        if (currentProjectId == projectId) return
        currentProjectId = projectId

        viewModelScope.launch {
            repository.getProject(projectId).collect { proj ->
                _project.value = proj
                if (proj != null && _syntaxText.value.isEmpty()) {
                    _syntaxText.value = proj.syntaxConfig
                }
            }
        }

        viewModelScope.launch {
            repository.getImages(projectId).collect { imgList ->
                _images.value = imgList
                // Ajusta o índice da imagem exibida se necessário
                if (_currentImageIndex.value >= imgList.size) {
                    _currentImageIndex.value = (imgList.size - 1).coerceAtLeast(0)
                }
                // Detecta a proporção da primeira imagem como padrão inicial (Requisito 5)
                if (imgList.isNotEmpty()) {
                    try {
                        val firstFile = File(imgList[0].filePath)
                        if (firstFile.exists()) {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(firstFile.absolutePath, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) {
                                val detected = VideoAspectRatio.detectFromDimensions(opts.outWidth, opts.outHeight)
                                _selectedAspectRatio.value = detected
                            }
                        }
                    } catch (_: Exception) {}
                }
                // Se o texto de sintaxe estiver vazio, inicializa com template automático
                if (_syntaxText.value.isBlank() && imgList.isNotEmpty()) {
                    val defaultText = SyntaxParser.generateDefaultSyntax(imgList.size, _selectedMovement.value.id)
                    _syntaxText.value = defaultText
                    repository.updateProjectSyntax(projectId, defaultText)
                }
            }
        }
    }

    fun selectMovement(effect: MovementEffect) {
        _selectedMovement.value = effect
        _isPreviewingTransition.value = false
        _isTestPlaying.value = true
    }

    fun selectResolution(resolution: VideoResolution) {
        _selectedResolution.value = resolution
    }

    fun selectAspectRatio(aspectRatio: VideoAspectRatio) {
        _selectedAspectRatio.value = aspectRatio
    }

    fun selectFps(fps: Int) {
        _selectedFps.value = fps
    }

    fun selectBitratePreset(preset: VideoBitratePreset) {
        _selectedBitrateMbps.value = preset.mbps
    }

    fun setBitrateMbps(mbps: Float) {
        _selectedBitrateMbps.value = (Math.round(mbps * 10f) / 10f).coerceIn(0.5f, 20.0f)
    }

    fun selectTransition(transition: TransitionEffect) {
        _selectedTransition.value = transition
        _transitionIdsText.value = transition.id.toString()
        _transitionError.value = null
        _isPreviewingTransition.value = true
        _isTestPlaying.value = true
    }

    fun updateTransitionIdsText(newText: String) {
        _transitionIdsText.value = newText
        when (val result = SyntaxParser.validateTransitionIds(newText)) {
            is TransitionValidationResult.Success -> {
                _transitionError.value = null
                val firstId = result.transitionIds.firstOrNull() ?: 1
                _selectedTransition.value = TransitionEffect.getOrCut(firstId)
            }
            is TransitionValidationResult.Error -> {
                _transitionError.value = result.message
            }
        }
    }

    fun togglePreviewMode() {
        _isPreviewingTransition.value = !_isPreviewingTransition.value
    }

    fun previousImage() {
        if (_currentImageIndex.value > 0) {
            _currentImageIndex.value -= 1
        }
    }

    fun nextImage() {
        if (_currentImageIndex.value < _images.value.size - 1) {
            _currentImageIndex.value += 1
        }
    }

    fun setImageIndex(index: Int) {
        _currentImageIndex.value = index.coerceIn(0, (_images.value.size - 1).coerceAtLeast(0))
    }

    fun generateAutomaticPrompts() {
        val count = _images.value.size
        if (count == 0) {
            viewModelScope.launch {
                _messageEvents.emit("Importe fotos antes de gerar os prompts automáticos.")
            }
            return
        }

        val result = SyntaxParser.generateRandomPrompts(count)
        _syntaxText.value = result.movementSyntaxText
        _transitionIdsText.value = result.transitionIdsText
        _syntaxError.value = null
        _transitionError.value = null

        viewModelScope.launch {
            repository.updateProjectSyntax(currentProjectId, result.movementSyntaxText)
            _messageEvents.emit("Prompts automáticos gerados com movimentos (0-10), durações (5-10s) e transições (0-20)!")
        }
    }

    fun toggleTestPlaying() {
        _isTestPlaying.value = !_isTestPlaying.value
    }

    fun openSyntaxModal() {
        // Se estiver vazio e houver imagens, pré-carrega sugestão
        if (_syntaxText.value.isBlank() && _images.value.isNotEmpty()) {
            _syntaxText.value = SyntaxParser.generateDefaultSyntax(_images.value.size, _selectedMovement.value.id)
        }
        _syntaxError.value = null
        _isSyntaxModalOpen.value = true
    }

    fun closeSyntaxModal() {
        _isSyntaxModalOpen.value = false
        _syntaxError.value = null
    }

    fun updateSyntaxText(newText: String) {
        _syntaxText.value = newText
        _syntaxError.value = null
    }

    fun autoGenerateSyntax() {
        val count = _images.value.size
        if (count == 0) {
            _syntaxError.value = "Importe imagens para o projeto antes de gerar a sintaxe."
            return
        }
        val generated = SyntaxParser.generateDefaultSyntax(count, _selectedMovement.value.id)
        _syntaxText.value = generated
        _syntaxError.value = null
        viewModelScope.launch {
            repository.updateProjectSyntax(currentProjectId, generated)
            _messageEvents.emit("Sintaxe automática gerada para $count imagens.")
        }
    }

    fun validateAndSaveSyntax(): Boolean {
        val totalImages = _images.value.size
        when (val result = SyntaxParser.parseAndValidate(_syntaxText.value, totalImages)) {
            is SyntaxParseResult.Success -> {
                _syntaxError.value = null
                viewModelScope.launch {
                    repository.updateProjectSyntax(currentProjectId, _syntaxText.value)
                    _messageEvents.emit("Sintaxe validada com sucesso!")
                }
                _isSyntaxModalOpen.value = false
                return true
            }
            is SyntaxParseResult.Error -> {
                _syntaxError.value = result.message
                return false
            }
        }
    }

    fun importDirectImages(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            when (val result = ImageImportHelper.importImages(context, uris, currentProjectId)) {
                is ImageImportResult.Success -> {
                    repository.addImages(currentProjectId, result.imported)
                    _messageEvents.emit("${result.imported.size} imagem(ns) adicionada(s) com sucesso.")
                    refreshDefaultSyntaxIfNeeded()
                }
                is ImageImportResult.Error -> {
                    _messageEvents.emit(result.message)
                }
            }
        }
    }

    fun importZipFile(context: Context, zipUri: Uri) {
        viewModelScope.launch {
            when (val result = ZipExtractor.extractZip(context, zipUri, currentProjectId)) {
                is ZipExtractResult.Success -> {
                    repository.addImages(currentProjectId, result.extractedFiles)
                    _messageEvents.emit("${result.extractedFiles.size} imagens extraídas do ZIP com sucesso.")
                    refreshDefaultSyntaxIfNeeded()
                }
                is ZipExtractResult.Error -> {
                    _messageEvents.emit(result.message)
                }
            }
        }
    }

    private suspend fun refreshDefaultSyntaxIfNeeded() {
        val updatedImages = repository.getImagesSync(currentProjectId)
        if (updatedImages.isNotEmpty()) {
            val defaultText = SyntaxParser.generateDefaultSyntax(updatedImages.size, _selectedMovement.value.id)
            _syntaxText.value = defaultText
            repository.updateProjectSyntax(currentProjectId, defaultText)
        }
    }

    fun deleteImage(imageId: Long) {
        viewModelScope.launch {
            repository.deleteImage(currentProjectId, imageId)
            _messageEvents.emit("Imagem excluída com sucesso.")
            val remaining = repository.getImagesSync(currentProjectId)
            if (remaining.isNotEmpty()) {
                val updatedSyntax = SyntaxParser.generateDefaultSyntax(remaining.size, _selectedMovement.value.id)
                _syntaxText.value = updatedSyntax
                repository.updateProjectSyntax(currentProjectId, updatedSyntax)
            } else {
                _syntaxText.value = ""
                repository.updateProjectSyntax(currentProjectId, "")
            }
        }
    }

    fun updateCustomOutputDir(uriString: String?) {
        viewModelScope.launch {
            repository.updateCustomOutputDir(currentProjectId, uriString)
            appPreferences.setDefaultOutputDirUri(uriString)
            _messageEvents.emit(
                if (uriString != null) "Pasta de destino personalizada definida e salva como padrão para projetos futuros."
                else "Diretório padrão (/Movies/AppAnimador/) redefinido."
            )
        }
    }

    fun startRendering(context: Context) {
        val totalImages = _images.value.size
        if (totalImages == 0) {
            viewModelScope.launch {
                _messageEvents.emit("Nenhuma imagem importada. Adicione imagens antes de iniciar.")
            }
            return
        }

        // Se o texto de sintaxe estiver vazio, gera automático
        if (_syntaxText.value.isBlank()) {
            _syntaxText.value = SyntaxParser.generateDefaultSyntax(totalImages, _selectedMovement.value.id)
        }

        // Valida sintaxe antes de iniciar
        val parseResult = SyntaxParser.parseAndValidate(_syntaxText.value, totalImages)
        if (parseResult is SyntaxParseResult.Error) {
            _syntaxError.value = parseResult.message
            _isSyntaxModalOpen.value = true
            viewModelScope.launch {
                _messageEvents.emit("Erro na validação da sintaxe. Verifique as configurações.")
            }
            return
        }

        // Valida as IDs de transições
        val transValidation = SyntaxParser.validateTransitionIds(_transitionIdsText.value)
        val transitionIds = when (transValidation) {
            is TransitionValidationResult.Success -> transValidation.transitionIds
            is TransitionValidationResult.Error -> {
                _transitionError.value = transValidation.message
                viewModelScope.launch {
                    _messageEvents.emit("Aviso: Transição inválida (${transValidation.message}). Usando padrão ID 1.")
                }
                listOf(1)
            }
        }

        val configs = (parseResult as SyntaxParseResult.Success).configs

        val resolution = _selectedResolution.value
        val aspectRatio = _selectedAspectRatio.value
        val (finalWidth, finalHeight) = aspectRatio.calculateDimensions(resolution)
        val fps = _selectedFps.value
        val bitrateBps = (_selectedBitrateMbps.value * 1_000_000).toInt()

        // Abre o modal de progresso
        _isProgressModalOpen.value = true

        val effectiveDir = _project.value?.customOutputDirUri ?: appPreferences.getDefaultOutputDirUri()

        // Dispara Foreground Service com parâmetros rigorosamente configurados para exportação unificada
        VideoRenderingService.start(
            context = context,
            projectId = currentProjectId,
            configs = configs,
            customDirUri = effectiveDir,
            videoWidth = finalWidth,
            videoHeight = finalHeight,
            videoFps = fps,
            videoBitrateBps = bitrateBps,
            resolutionLabel = "${resolution.label} • ${aspectRatio.label} (${finalWidth}x${finalHeight})",
            transitionIds = transitionIds
        )
    }

    fun cancelRendering(context: Context) {
        VideoRenderingService.cancel(context)
    }

    fun closeProgressModal() {
        _isProgressModalOpen.value = false
    }

    fun reopenProgressModal() {
        _isProgressModalOpen.value = true
    }
}
