package translates.commands.economy.cassino

import shared.utils.Icon

interface HorseRacingTranslateInterface {
    val notEnoughMoney: String

    val playing: Playing
    val end: End

    val horses: Horses

    fun logWinner(horse: String, amount: Double, winMultiplier: Double): String
    fun logLoser(horse: String, amount: Double): String

    interface Playing {
        val title: String
    }

    interface End {
        val title: String
        val fields: Fields

        interface Fields {
            val winner: Field
            val bet: Field
            val result: ResultField

            interface Field {
                val name: String
                fun value(emoji: String, winner: String): String
            }

            interface ResultField {
                val name: String
                fun value(isWinner: Boolean, amount: Double, multiplier: Double): String
            }
        }
    }

    interface Horses {
        val purple: Horse
        val blue: Horse
        val green: Horse
        val yellow: Horse
        val orange: Horse
        val red: Horse
        val pink: Horse
        val brown: Horse

        interface Horse {
            val name: String
            val emoji: String
            val colorEmoji: String
        }
    }
}

class HorseRacingTranslate {
    companion object {
        @JvmStatic
        fun ptbr() = PtBrHorseRacing()

        @JvmStatic
        fun enus() = EnUsHorseRacing()

        @JvmStatic
        fun eses() = EsEsHorseRacing()
    }
}

class PtBrHorseRacing : HorseRacingTranslateInterface {
    override val notEnoughMoney = "${Icon.static.get("Eris_cry")} | Você precisa ter no mínimo 50 STX para apostar."

    override val playing = object : HorseRacingTranslateInterface.Playing {
        override val title = "🏇 Corrida de Cavalos"
    }

    override val end = object : HorseRacingTranslateInterface.End {
        override val title = "🏁 Corrida Finalizada!"
        override val fields = object : HorseRacingTranslateInterface.End.Fields {
            override val winner = object : HorseRacingTranslateInterface.End.Fields.Field {
                override val name = "Vencedor"
                override fun value(emoji: String, winner: String) = "$emoji $winner"
            }

            override val bet = object : HorseRacingTranslateInterface.End.Fields.Field {
                override val name = "Sua aposta"
                override fun value(emoji: String, winner: String) = "$emoji $winner"
            }

            override val result = object : HorseRacingTranslateInterface.End.Fields.ResultField {
                override val name = "Resultado"
                override fun value(isWinner: Boolean, amount: Double, multiplier: Double) =
                    if (isWinner)
                        "${Icon.static.get("success")} | Você apostou **$amount** e ganhou **${amount * multiplier}** stx!"
                    else
                        "${Icon.static.get("denied")} | Você apostou **$amount** e infelizmente perdeu ${Icon.static.get("Eris_cry_left")}"
            }
        }
    }

    override val horses = object : HorseRacingTranslateInterface.Horses {
        override val purple = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Roxo"
            override val emoji = "🐎"
            override val colorEmoji = "🟣"
        }

        override val blue = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Azul"
            override val emoji = "🐎"
            override val colorEmoji = "🔵"
        }

        override val green = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Verde"
            override val emoji = "🐎"
            override val colorEmoji = "🟢"
        }

        override val yellow = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Amarelo"
            override val emoji = "🐎"
            override val colorEmoji = "🟡"
        }

        override val orange = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Laranja"
            override val emoji = "🐎"
            override val colorEmoji = "🟠"
        }

        override val red = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Vermelho"
            override val emoji = "🐎"
            override val colorEmoji = "🔴"
        }

        override val pink = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Rosa"
            override val emoji = "🐎"
            override val colorEmoji = "🌸"
        }

        override val brown = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Marrom"
            override val emoji = "🐎"
            override val colorEmoji = "🟤"
        }
    }

    override fun logWinner(horse: String, amount: Double, winMultiplier: Double) =
        "Apostou no cavalo $horse e ganhou ${amount * winMultiplier} stx"

    override fun logLoser(horse: String, amount: Double) =
        "Apostou no cavalo $horse e perdeu $amount stx"
}

class EnUsHorseRacing : HorseRacingTranslateInterface {
    override val notEnoughMoney = "${Icon.static.get("Eris_cry")} | You need to have at least 50 STX to bet."

    override val playing = object : HorseRacingTranslateInterface.Playing {
        override val title = "🏇 Horse Race"
    }

    override val end = object : HorseRacingTranslateInterface.End {
        override val title = "🏁 Race Finished!"
        override val fields = object : HorseRacingTranslateInterface.End.Fields {
            override val winner = object : HorseRacingTranslateInterface.End.Fields.Field {
                override val name = "Winner"
                override fun value(emoji: String, winner: String) = "$emoji $winner"
            }

            override val bet = object : HorseRacingTranslateInterface.End.Fields.Field {
                override val name = "Your bet"
                override fun value(emoji: String, winner: String) = "$emoji $winner"
            }

            override val result = object : HorseRacingTranslateInterface.End.Fields.ResultField {
                override val name = "Result"
                override fun value(isWinner: Boolean, amount: Double, multiplier: Double) =
                    if (isWinner)
                        "${Icon.static.get("success")} | You bet **$amount** and won **${amount * multiplier}** stx!"
                    else
                        "${Icon.static.get("denied")} | You bet **$amount** and unfortunately lost ${Icon.static.get("Eris_cry_left")}"
            }
        }
    }

    override val horses = object : HorseRacingTranslateInterface.Horses {
        override val purple = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Purple"
            override val emoji = "🐎"
            override val colorEmoji = "🟣"
        }

        override val blue = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Blue"
            override val emoji = "🐎"
            override val colorEmoji = "🔵"
        }

        override val green = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Green"
            override val emoji = "🐎"
            override val colorEmoji = "🟢"
        }

        override val yellow = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Yellow"
            override val emoji = "🐎"
            override val colorEmoji = "🟡"
        }

        override val orange = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Orange"
            override val emoji = "🐎"
            override val colorEmoji = "🟠"
        }

        override val red = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Red"
            override val emoji = "🐎"
            override val colorEmoji = "🔴"
        }

        override val pink = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Pink"
            override val emoji = "🐎"
            override val colorEmoji = "🌸"
        }

        override val brown = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Brown"
            override val emoji = "🐎"
            override val colorEmoji = "🟤"
        }
    }

    override fun logWinner(horse: String, amount: Double, winMultiplier: Double) =
        "Bet on the $horse horse and won ${amount * winMultiplier} stx"

    override fun logLoser(horse: String, amount: Double) =
        "Bet on the $horse horse and lost $amount stx"
}

class EsEsHorseRacing : HorseRacingTranslateInterface {
    override val notEnoughMoney = "${Icon.static.get("Eris_cry")} | Necesitas tener al menos 50 STX para apostar."

    override val playing = object : HorseRacingTranslateInterface.Playing {
        override val title = "🏇 Carrera de Caballos"
    }

    override val end = object : HorseRacingTranslateInterface.End {
        override val title = "🏁 ¡Carrera Finalizada!"
        override val fields = object : HorseRacingTranslateInterface.End.Fields {
            override val winner = object : HorseRacingTranslateInterface.End.Fields.Field {
                override val name = "Ganador"
                override fun value(emoji: String, winner: String) = "$emoji $winner"
            }

            override val bet = object : HorseRacingTranslateInterface.End.Fields.Field {
                override val name = "Tu apuesta"
                override fun value(emoji: String, winner: String) = "$emoji $winner"
            }

            override val result = object : HorseRacingTranslateInterface.End.Fields.ResultField {
                override val name = "Resultado"
                override fun value(isWinner: Boolean, amount: Double, multiplier: Double) =
                    if (isWinner)
                        "${Icon.static.get("success")} | Apostaste **$amount** y ganaste **${amount * multiplier}** stx!"
                    else
                        "${Icon.static.get("denied")} | Apostaste **$amount** y desafortunadamente perdiste ${Icon.static.get("Eris_cry_left")}"
            }
        }
    }

    override val horses = object : HorseRacingTranslateInterface.Horses {
        override val purple = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Púrpura"
            override val emoji = "🐎"
            override val colorEmoji = "🟣"
        }

        override val blue = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Azul"
            override val emoji = "🐎"
            override val colorEmoji = "🔵"
        }

        override val green = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Verde"
            override val emoji = "🐎"
            override val colorEmoji = "🟢"
        }

        override val yellow = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Amarillo"
            override val emoji = "🐎"
            override val colorEmoji = "🟡"
        }

        override val orange = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Naranja"
            override val emoji = "🐎"
            override val colorEmoji = "🟠"
        }

        override val red = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Rojo"
            override val emoji = "🐎"
            override val colorEmoji = "🔴"
        }

        override val pink = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Rosa"
            override val emoji = "🐎"
            override val colorEmoji = "🌸"
        }

        override val brown = object : HorseRacingTranslateInterface.Horses.Horse {
            override val name = "Marrón"
            override val emoji = "🐎"
            override val colorEmoji = "🟤"
        }
    }

    override fun logWinner(horse: String, amount: Double, winMultiplier: Double) =
        "Apostó en el caballo $horse y ganó ${amount * winMultiplier} stx"

    override fun logLoser(horse: String, amount: Double) =
        "Apostó en el caballo $horse y perdió $amount stx"
}