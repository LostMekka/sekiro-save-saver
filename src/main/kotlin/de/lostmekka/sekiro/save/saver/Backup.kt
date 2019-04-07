package de.lostmekka.sekiro.save.saver

import java.io.File
import java.util.*

private const val backupsSubDir = "backup-data"

private fun getOrCreateBackupDir(targetFile: File): File {
    val dir = File(targetFile.backupDir)
    if (!dir.isDirectory) dir.mkdirs()
    return dir
}

private val File.backupDir get() = "./$backupsSubDir/$name"

data class BackupFile(
    val handle: File,
    val timestamp: Long
)

private fun File.listBackupFiles(): List<BackupFile> {
    return listFiles()
        .mapNotNull {
            val timestamp = it.name.toLongOrNull()
            if (timestamp == null) null else BackupFile(it, timestamp)
        }
        .sortedBy { it.timestamp }
}

private fun File.createBackup(timestamp: Long): BackupFile? {
    val backupFile = File("$backupDir/$timestamp")
    try {
        copyTo(backupFile, overwrite = true)
    } catch (e: Exception) {
        println("WARNING: could not copy managed file to create backup: ${e.javaClass.name} - ${e.message}")
        return null
    }
    return BackupFile(backupFile, timestamp)
}

private fun BackupFile.delete() = handle.delete()

private fun BackupFile.restore(targetFile: File) =
    try {
        handle.copyTo(targetFile, overwrite = true)
        true
    } catch (e: Exception) {
        println("WARNING: could not copy backup file to restore the managed file: ${e.javaClass.name} - ${e.message}")
        false
    }

class BackupManager(private val targetFile: File) {
    private val dir = getOrCreateBackupDir(targetFile)
    private val backupFiles = dir.listBackupFiles().let { LinkedList<BackupFile>().apply { addAll(it) } }
    private var refuseBackupUntil = 0L

    val backupTimestamps get() = backupFiles.map { it.timestamp }

    fun makeBackup(timestamp: Long, settings: Settings) {
        if (refuseBackupUntil >= timestamp) return
        val backup = targetFile.createBackup(timestamp)
        if (backup != null) {
            backupFiles.addLast(backup)
            while (backupFiles.size > settings.maxBackupFileCount) backupFiles.removeFirst().delete()
            refuseBackupUntil = System.currentTimeMillis() + settings.backupBlockTimeAfterBackupInMs
            if (settings.playSounds) saveSound.play()
        } else {
            if (settings.playSounds) errorSound.play()
        }
    }

    fun restoreBackup(timestamp: Long, settings: Settings) {
        val isSuccess = backupFiles
            .find { it.timestamp == timestamp }
            .also { it ?: println("WARNING: backup not found") }
            ?.restore(targetFile)
            ?: false
        if (isSuccess) {
            refuseBackupUntil = System.currentTimeMillis() + settings.backupBlockTimeAfterRestoreInMs
            if (settings.playSounds) restoreSound.play()
        } else {
            if (settings.playSounds) errorSound.play()
        }
    }
}
