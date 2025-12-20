package shared.utils

class CustomIdHelper(customId: String, returnedCustomId: String) {
    val customId = customId
    val returnedCustomId = returnedCustomId

    fun getAllParams(): Map<String, String> {
        // CustomId é o id esperado, ex: menu/help/:page
        // :page é um valor dinâmico, é ele que vamos extrair
        // Depois é retornar um map com todos, baseado no returnedCustomId

        val params = hashMapOf<String, String>() // valores dinâmicos obtidos

        val patternParts = customId.split("/") // separa por / o customId experado
        val actualParts = returnedCustomId.split("/") // separa por / o customId obtido

        // se o id obtido não for do mesmo tamanho do esperado, retorna vazio
        if (patternParts.size != actualParts.size) {
            return params
        }

        // buscar por cada item do id experado e verificar se começa com ":"
        for (i in patternParts.indices) {
            if (patternParts[i].startsWith(":")) {
                val paramName = patternParts[i].substring(1) // nome do parâmetro cortando o ":"
                params[paramName] = actualParts[i] // adicionando aos parâmetros
            }
        }

        return params
    }

    fun get(param: String): String? {
        return getAllParams()[param]
    }

    fun getAsInt(param: String): Int? {
        return get(param)?.toIntOrNull()
    }

    fun getAsDouble(param: String): Double? {
        return get(param)?.toDoubleOrNull()
    }

    fun getAsLong(param: String): Long? {
        return get(param)?.toLongOrNull()
    }
}