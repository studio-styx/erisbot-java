package studio.styx.erisbot.functions.imageGenerator.wordle

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

object WordleImageGenerator {

    private val joanFont: Font by lazy {
        try {
            Font.createFont(Font.TRUETYPE_FONT, File("assets/fonts/Joan-Regular.ttf"))
                .deriveFont(96f)
        } catch (e: Exception) {
            // Fallback caso não ache a fonte
            Font("Serif", Font.BOLD, 96)
        }
    }

    fun createWordleImage(word: String, attempts: List<String>): ByteArray {
        // Validação
        if (word.length !in 4..6) {
            throw IllegalArgumentException("A palavra deve ter entre 4 e 6 letras.")
        }

        val width = 800
        val height = 1200

        // Cria a imagem (Buffer)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        // Ativa Anti-aliasing para texto e formas suaves (igual ao Canvas do navegador)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Fundo
        g2d.color = Color.decode("#74351A")
        g2d.fillRect(0, 0, width, height)

        // Título
        g2d.color = Color.WHITE
        g2d.font = joanFont
        // O Canvas original usa textBaseline 'top', aqui ajustamos a posição manualmente
        drawCenteredText(g2d, "Termo", 400, 65 + g2d.fontMetrics.ascent)

        // Configurações
        val boxPerLine = word.length
        val totalLines = 5
        val boxSizeY = 155
        val paddingY = 30
        val lineBaseY = 206
        val boxRadius = 21.0

        val boxSizeX: Int
        val paddingX: Int
        val lineBaseX: Int

        when (boxPerLine) {
            4 -> { boxSizeX = 120; paddingX = 80; lineBaseX = 40 }
            5 -> { boxSizeX = 115; paddingX = 38; lineBaseX = 36 }
            6 -> { boxSizeX = 100; paddingX = 20; lineBaseX = 40 }
            else -> throw IllegalArgumentException("Número inválido de letras.")
        }

        // Lógica de coloração
        for (line in 0 until totalLines) {
            // Garante que a string tenha o tamanho certo preenchendo com vazios
            val attempt = (attempts.getOrNull(line) ?: "").take(boxPerLine).padEnd(boxPerLine, ' ')
            val wordArray = word.toCharArray().toMutableList() // Mutável para a lógica de exclusão
            val types = MutableList(boxPerLine) { "neutral" }

            // Contagem de letras
            val letterCounts = mutableMapOf<Char, Int>()
            word.forEach { char ->
                val lower = char.lowercaseChar()
                letterCounts[lower] = (letterCounts[lower] ?: 0) + 1
            }

            // Passo 1: Letras Corretas (Verde)
            for (i in 0 until boxPerLine) {
                if (attempt[i] == ' ') continue

                val attemptLetter = handleCedilla(attempt[i].toString(), wordArray[i].toString(), word)
                val wordLetter = wordArray[i].lowercaseChar()

                val normalizedAttempt = normalizeLetter(attemptLetter)[0]
                val normalizedWord = normalizeLetter(wordLetter.toString())[0]

                if (normalizedAttempt == normalizedWord) {
                    types[i] = "success"
                    letterCounts[wordLetter] = letterCounts[wordLetter]!! - 1
                    wordArray[i] = ' ' // Marca como usada
                }
            }

            // Passo 2: Letra existente mas lugar errado (Amarelo/Different)
            for (i in 0 until boxPerLine) {
                if (attempt[i] == ' ' || types[i] == "success") continue

                val attemptLetter = handleCedilla(attempt[i].toString(), wordArray[i].toString(), word)
                val normalizedAttempt = normalizeLetter(attemptLetter)

                // Procura na palavra original se existe essa letra sobrando
                val wordCharIndex = wordArray.indexOfFirst {
                    it != ' ' && normalizeLetter(it.toString()) == normalizedAttempt
                }

                if (wordCharIndex != -1) {
                    val foundChar = wordArray[wordCharIndex].lowercaseChar()
                    if ((letterCounts[foundChar] ?: 0) > 0) {
                        types[i] = "different"
                        letterCounts[foundChar] = letterCounts[foundChar]!! - 1
                        wordArray[wordCharIndex] = ' ' // Marca como usada
                    }
                }
            }

            // Desenhar
            for (box in 0 until boxPerLine) {
                val x = lineBaseX + (boxSizeX + paddingX) * box
                val y = lineBaseY + (boxSizeY + paddingY) * line

                drawRoundedBox(g2d, x, y, boxSizeX, boxSizeY, boxRadius, types[box])

                val letter = attempt[box]
                if (letter != ' ') {
                    drawLetter(g2d, x, y, boxSizeX, boxSizeY, letter.toString())
                }
            }
        }

        g2d.dispose() // Libera recursos gráficos

        // Exportar para ByteArray (PNG)
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    private fun drawRoundedBox(
        g2d: Graphics2D, x: Int, y: Int, w: Int, h: Int, radius: Double, type: String
    ) {
        val color = when (type) {
            "success" -> Color.decode("#74dd6b")
            "different" -> Color.decode("#f72c2c")
            else -> Color.decode("#402116") // neutral
        }

        val shape = RoundRectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), radius, radius)

        g2d.color = color
        g2d.fill(shape)

        g2d.color = Color.WHITE
        g2d.stroke = BasicStroke(0.5f) // LineWidth
        g2d.draw(shape)
    }

    private fun drawLetter(g2d: Graphics2D, x: Int, y: Int, w: Int, h: Int, letter: String) {
        g2d.color = Color.WHITE
        // Usar fonte padrão sans-serif se não quiser carregar outra
        g2d.font = Font("SansSerif", Font.PLAIN, 48)

        // Centralizar vertical e horizontalmente
        val metrics = g2d.fontMetrics
        val textX = x + (w - metrics.stringWidth(letter.uppercase())) / 2
        val textY = y + ((h - metrics.height) / 2) + metrics.ascent

        g2d.drawString(letter.uppercase(), textX, textY)
    }

    // Função auxiliar para centralizar texto baseada num ponto central X
    private fun drawCenteredText(g2d: Graphics2D, text: String, centerX: Int, y: Int) {
        val metrics = g2d.fontMetrics
        val x = centerX - (metrics.stringWidth(text) / 2)
        g2d.drawString(text, x, y)
    }

    private fun normalizeLetter(letter: String): String {
        val map = mapOf(
            'á' to 'a', 'à' to 'a', 'ã' to 'a', 'â' to 'a', 'ä' to 'a',
            'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
            'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
            'ó' to 'o', 'ò' to 'o', 'õ' to 'o', 'ô' to 'o', 'ö' to 'o',
            'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u'
        )
        return letter.lowercase().map { map[it] ?: it }.joinToString("")
    }

    private fun handleCedilla(attemptLetter: String, wordLetter: String, word: String): String {
        val att = attemptLetter.lowercase()
        val wdL = wordLetter.lowercase()
        val wd = word.lowercase()

        if (att == "c" && wdL == "ç") return "ç"
        if (att == "c" && wd.contains("ç") && !wd.contains("c")) return "ç"

        return att
    }
}