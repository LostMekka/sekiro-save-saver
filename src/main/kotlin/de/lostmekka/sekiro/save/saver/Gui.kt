package de.lostmekka.sekiro.save.saver

import javafx.application.Platform
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.concurrent.Task
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.runBlocking
import tornadofx.*

class SekiroSaveSaverApp : App(MainView::class)

class FileModel(fileName: String, backupTimestamps: List<BackupModel>) {
    val fileNameProperty = SimpleStringProperty(this, "fileName", fileName)
    var fileName by fileNameProperty

    val backupTimestampsProperty =
        SimpleListProperty<BackupModel>(this, "backupTimestamps", backupTimestamps.observable())
    var backupTimestamps by backupTimestampsProperty

    override fun toString() = fileName.takeUnless { it.isEmpty() } ?: "NO FILE NAME"
}

class BackupModel(timestamp: Long) {
    val timestampProperty = SimpleLongProperty(this, "timestamp", timestamp)
    val timestamp by timestampProperty
}

class MainView : View() {
    private lateinit var communicationTask: Task<Unit>

    private val files = mutableListOf<FileModel>().observable()
    private val selectedFile = SimpleObjectProperty<FileModel>()
    private val timestamps = mutableListOf<BackupModel>().observable()

    private var fileView: ListView<FileModel> by singleAssign()
    override val root = borderpane {
        title = "Sekiro Save Saver by Lostmekka"
        center {
            vbox {
                label("Watched files:")
                label("(select to see individual backups)")
                listview(files) {
                    fileView = this
                    selectionModel.selectionMode = SelectionMode.SINGLE
                    bindSelected(selectedFile)
                    selectionModel.selectedItemProperty().onChange { fileModel ->
                        if (fileModel != null) timestamps.bind(fileModel.backupTimestamps) { it }
                    }
                }
            }
        }
        right {
            tableview(timestamps) {
                selectionModel.selectionMode = SelectionMode.SINGLE
                readonlyColumn("Time", BackupModel::timestampProperty).apply {
                    cellFormat { text = it.value.toTimeString() }
                    remainingWidth()
                }
                readonlyColumn("", BackupModel::timestampProperty).cellFormat {
                    graphic = button("Restore") {
                        action {
                            val fileName = selectedFile.value.fileName
                            val timestamp = it.value
                            runAsync {
                                println("sending restore request: $fileName -> ${timestamp.toTimeString()}")
                                runBlocking {
                                    RestoreRequestChannel.send(BackupRequest(fileName, timestamp))
                                }
                                println("sent request")
                            }
                        }
                    }
                }
                smartResize()
            }
        }
    }

    override fun onDock() {
        communicationTask = runAsync {
            while (!isCancelled) {
                val state = runBlocking { GuiChannel.receive() }
                Platform.runLater {
                    val selectedName = selectedFile.value?.fileName
                    files.clear()
                    state.data
                        .mapEach { FileModel(key, value.sortedDescending().map { BackupModel(it) }) }
                        .also { files.addAll(it) }
                    val selectedIndex = files.indexOfFirst { it.fileName == selectedName }
                    if (selectedIndex >= 0) {
                        fileView.scrollTo(selectedIndex)
                        fileView.selectionModel.select(selectedIndex)
                    }
                }
            }
        }
    }

    override fun onUndock() {
        println("stopping communication gui task")
        communicationTask.cancel()
        println("sending termination signal")
        TerminationChannel.sendBlocking(FileWatcherTerminationSignal)
        println("sent termination signal")
    }
}