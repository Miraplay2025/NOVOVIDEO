package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.Project
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository

    val projects: StateFlow<List<Project>>

    init {
        val db = AppDatabase.getInstance(application)
        repository = ProjectRepository(db.projectDao(), application)
        projects = repository.getAllProjects().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun createProject(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createProject(name)
            onCreated(id)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }
}
