package settings

import java.util.Properties

object AppConfig {
    private val properties = Properties()

    init {
        try {
            val inputStream = javaClass.classLoader.getResourceAsStream("application.properties")
            if (inputStream == null) {
                println("❌ Arquivo application.properties não encontrado no classpath!")
                // Vamos listar os recursos disponíveis para debug
                val resources = javaClass.classLoader.getResources("")
                while (resources.hasMoreElements()) {
                    println("Recurso: ${resources.nextElement()}")
                }
            } else {
                inputStream.use {
                    properties.load(it)
                }
            }
        } catch (e: Exception) {
            println("❌ Erro ao carregar properties: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getVersion(): String {
        val version = properties.getProperty("app.version", "1.0.0")
        return version
    }
}