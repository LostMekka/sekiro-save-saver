package de.lostmekka.sekiro.save.saver

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import tornadofx.launch
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import javax.swing.JFileChooser

val TerminationChannel = Channel<FileWatcherTerminationSignal>(1)
val FileChangedEventChannel = Channel<FileEvent>(10)
val GuiChannel = Channel<BackupData>(1)
val RestoreRequestChannel = Channel<BackupRequest>(1)

data class FileEvent(val filePath: Path, val type: FileEventType)
enum class FileEventType { Created, Modified, Deleted }
object FileWatcherTerminationSignal
data class BackupData(val data: Map<String, List<Long>>)
data class BackupRequest(val targetFileName: String, val backupTimestamp: Long)

data class Settings(
    val playSounds: Boolean,
    val maxBackupFileCount: Int,
    val backupBlockTimeAfterBackupInMs: Long,
    val backupBlockTimeAfterRestoreInMs: Long
)

fun Config.toSettings() = Settings(
    playSounds = playSounds == "true" || playSounds.isEmpty(),
    maxBackupFileCount = maxBackupFileCount.toIntOrNull() ?: 25,
    backupBlockTimeAfterBackupInMs = backupBlockTimeAfterBackupInMs.toLongOrNull() ?: 2000,
    backupBlockTimeAfterRestoreInMs = backupBlockTimeAfterRestoreInMs.toLongOrNull() ?: 2000
)

fun Config.copyFrom(settings: Settings) {
    playSounds = if (settings.playSounds) "true" else "false"
    maxBackupFileCount = settings.maxBackupFileCount.toString()
    backupBlockTimeAfterBackupInMs = settings.backupBlockTimeAfterBackupInMs.toString()
    backupBlockTimeAfterRestoreInMs = settings.backupBlockTimeAfterRestoreInMs.toString()
}

fun main() {
    val config = Config("config.properties")
    val settings = config.toSettings()
    config.copyFrom(settings)
    config.fileNameBlacklist = config.fileNameBlacklist.takeIf { it.isNotBlank() } ?: """.*\.bak"""
    config.save()

    println("asking for target directory...")
    val watchDir = askForTargetDirectory(config.watchDir)
    if (watchDir == null) {
        println("directory selection was cancelled. exiting program.")
        return
    }

    config.watchDir = watchDir
    config.save()

    runBlocking {
        println("launching gui...")
        GlobalScope.launch { launch<SekiroSaveSaverApp>() }

        println("launching file watcher job...")
        val watcherJob = launch { directoryWatcher(watchDir) }

        println("launching backup management job...")
        val backupMgrJob = launch { backupManager(watchDir, config.fileNameBlacklist, settings) }

        println("waiting for termination signal...")
        TerminationChannel.receive()
        println("termination signal received")

        println("cancelling file watcher job...")
        watcherJob.cancelAndJoin()
        println("cancelling backup management job...")
        backupMgrJob.cancelAndJoin()
        println("all done. app should exit when gui is exiting.")
    }
}

fun askForTargetDirectory(pathFromLastTime: String): String? {
    val last = File(pathFromLastTime)
    val startingDir = if (last.isDirectory) {
        last
    } else {
        val username = System.getProperty("user.name")
        val sekiroBaseDir = File("C:/Users/$username/AppData/Roaming/Sekiro/")
        if (sekiroBaseDir.isDirectory) {
            val profileDirs = sekiroBaseDir.listFiles()
                .filter { it.isDirectory && it.name.matches(Regex("""\d+""")) }
            if (profileDirs.size == 1) profileDirs.first() else sekiroBaseDir
        } else null
    }
    val fileChooser = JFileChooser().apply {
        currentDirectory = startingDir
        this.dialogTitle = "Choose a file inside the directory to watch"
    }
    val dialogAnswer = fileChooser.showOpenDialog(null)
    if (dialogAnswer != JFileChooser.APPROVE_OPTION) return null
    return fileChooser.currentDirectory.absolutePath
}

suspend fun directoryWatcher(directoryPath: String) {
    try {
        println("file watcher initializing")
        val watcher = withContext(Dispatchers.IO) {
            val watcher = FileSystems.getDefault().newWatchService()
            val path = Paths.get(directoryPath)
            path.register(
                watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
            watcher
        }
        println("file watcher starting")
        while (true) {
            val key = watcher.poll()
            if (key == null) {
                delay(250)
                continue
            }

            keyLoop@ for (event in key.pollEvents()) {
                val kind = event.kind()
                val type = when (kind) {
                    StandardWatchEventKinds.ENTRY_CREATE -> FileEventType.Created
                    StandardWatchEventKinds.ENTRY_DELETE -> FileEventType.Deleted
                    StandardWatchEventKinds.ENTRY_MODIFY -> FileEventType.Modified
                    else -> continue@keyLoop
                }
                val filePath = event.context() as? Path ?: continue@keyLoop
                FileChangedEventChannel.send(FileEvent(filePath, type))
            }
            key.reset()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun backupManager(directoryPath: String, fileNameBlacklist: String, settings: Settings) {
    println("backup management initializing")
    val blacklistNames = fileNameBlacklist
        .split(';')
        .filter { it.isNotBlank() }
        .map { it.toRegex() }
    fun String.isBlacklisted() = blacklistNames.any { pattern -> matches(pattern) }
    val backupManagers = File(directoryPath)
        .listFiles()
        .filter { it.isFile && !it.name.isBlacklisted() }
        .associateBy(
            { it.name },
            { BackupManager(it) }
        )
        .toMutableMap()
    suspend fun updateGui() {
        backupManagers
            .mapValues { it.value.backupTimestamps }
            .also { GuiChannel.send(BackupData(it)) }
    }
    println("backup management starting")
    updateGui()
    while (true) {
        @Suppress("EXPERIMENTAL_API_USAGE")
        if (!FileChangedEventChannel.isEmpty) {
            val fileEvent = FileChangedEventChannel.receive()
            val fileName = fileEvent.filePath.fileName.toString()
            if (fileName.isBlacklisted()) continue
            val targetFile = File("$directoryPath/$fileName")
            val timestamp = System.currentTimeMillis()
            println("received event: FILE CHANGED: '$fileName' ${fileEvent.type} at ${timestamp.toTimeString()}")
            if (fileEvent.type == FileEventType.Deleted) continue
            backupManagers
                .getOrPut(targetFile.name) { BackupManager(targetFile) }
                .makeBackup(timestamp, settings)
            updateGui()
            continue
        }

        @Suppress("EXPERIMENTAL_API_USAGE")
        if (!RestoreRequestChannel.isEmpty) {
            val backupRequest = RestoreRequestChannel.receive()
            val fileName = backupRequest.targetFileName
            val timestamp = backupRequest.backupTimestamp
            println("received event: RESTORE FILE: '$fileName' restore backup to ${timestamp.toTimeString()}")
            backupManagers[fileName]
                .also { it ?: println("WARNING: no backup manager for file '$fileName' found") }
                ?.restoreBackup(timestamp, settings)
            continue
        }

        delay(100)
    }
}
