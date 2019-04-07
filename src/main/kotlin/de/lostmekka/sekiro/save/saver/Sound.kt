package de.lostmekka.sekiro.save.saver

import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

private fun load(soundFileName: String) =
    try {
        AudioSystem.getClip().apply {
            open(AudioSystem.getAudioInputStream(File("./sounds/$soundFileName")))
            addLineListener { if (it.type == LineEvent.Type.STOP) close() }
        }
    } catch (e: Exception) {
        println("could not load sound '$soundFileName': ${e.javaClass.name} - ${e.message}")
        null
    }

fun Clip?.play() = this?.apply {
    start()
}

val saveSound get() = load("save.wav")
val errorSound get() = load("error.wav")
val restoreSound get() = load("restore.wav")
