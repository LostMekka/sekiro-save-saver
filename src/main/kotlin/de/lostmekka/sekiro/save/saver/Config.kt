package de.lostmekka.sekiro.save.saver

import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class PropertyFileProperty(
    private val properties: Properties,
    private val propertyName: String? = null
) : ReadWriteProperty<Any, String> {

    private var cachedValue: String? = null

    private fun name(property: KProperty<*>) = propertyName ?: property.name

    override fun getValue(thisRef: Any, property: KProperty<*>): String {
        if (cachedValue == null) cachedValue =
            System.getenv(name(property).uppercase(Locale.getDefault()))?.trim()?.takeIf { it.isNotBlank() }
        if (cachedValue == null) cachedValue = properties.getProperty(name(property))
        return cachedValue ?: ""
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
        cachedValue = value
        properties[name(property)] = value
    }
}

private fun Properties.prop(propertyName: String? = null) = PropertyFileProperty(this, propertyName)

class Config(private val configFileName: String) {
    private var properties = Properties()
    var watchDir by properties.prop()
    var fileNameBlacklist by properties.prop()
    var playSounds by properties.prop()
    var maxBackupFileCount by properties.prop()
    var backupBlockTimeAfterBackupInMs by properties.prop()
    var backupBlockTimeAfterRestoreInMs by properties.prop()

    init {
        load()
    }

    fun load() {
        try {
            properties.load(FileInputStream(configFileName))
        } catch (e: Exception){
            println("ERROR: could not read config from disk: ${e.javaClass.name} - ${e.message}")
        }
    }

    fun save() {
        try {
            properties.store(FileOutputStream(configFileName), "Sekiro Save Saver by Lostmekka")
        } catch (e: Exception){
            println("ERROR: could not write config to disk: ${e.javaClass.name} - ${e.message}")
        }
    }
}
