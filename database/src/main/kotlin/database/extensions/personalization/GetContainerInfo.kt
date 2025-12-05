package database.extensions.personalization

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import database.dtos.personalization.containers.*
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import studio.styx.erisbot.generated.enums.Actionrowcomponenttype
import studio.styx.erisbot.generated.tables.references.*

// Instância global
private val mapper = jacksonObjectMapper()

fun DSLContext.getContainerInfo(containerId: Int): List<ContainerComponent> {

    // 1. Definimos o multiset para as OPÇÕES do Select Menu (Nível mais profundo)
    val selectOptionsMultiset = multiset(
        select(
            ACTIONROWSELECTOPTION.LABEL,
            ACTIONROWSELECTOPTION.VALUE,
            ACTIONROWSELECTOPTION.EMOJI,
            ACTIONROWSELECTOPTION.DEFAULT
        )
            .from(ACTIONROWSELECTOPTION)
            .where(ACTIONROWSELECTOPTION.ACTIONROWSELECTID.eq(ACTIONROWSELECT.ID))
    ).convertFrom { r ->
        r.map { record ->
            SelectOption(
                label = record[ACTIONROWSELECTOPTION.LABEL]!!,
                value = record[ACTIONROWSELECTOPTION.VALUE]!!,
                emoji = record[ACTIONROWSELECTOPTION.EMOJI],
                isDefault = record[ACTIONROWSELECTOPTION.DEFAULT] == true
            )
        }
    }

    // 2. Definimos o multiset para os COMPONENTES da ActionRow (Botões ou Selects)
    // Fazemos LEFT JOIN com Button e Select para pegar os dados de quem estiver presente
    val rowComponentsMultiset = multiset(
        select(
            ACTIONROWCOMPONENT.CUSTOMID,
            ACTIONROWCOMPONENT.TYPE,
            // Campos de Botão
            ACTIONROWBUTTON.LABEL,
            ACTIONROWBUTTON.STYLE,
            ACTIONROWBUTTON.EMOJI,
            ACTIONROWBUTTON.URL,
            ACTIONROWBUTTON.DISABLED,
            // Campos de Select
            ACTIONROWSELECT.PLACEHOLDER,
            ACTIONROWSELECT.MINVALUES,
            ACTIONROWSELECT.MAXVALUES,
            // Aninhamos as opções aqui
            selectOptionsMultiset
        )
            .from(ACTIONROWCOMPONENT)
            .leftJoin(ACTIONROWBUTTON).on(ACTIONROWBUTTON.ACTIONROWCOMPONENTID.eq(ACTIONROWCOMPONENT.ID))
            .leftJoin(ACTIONROWSELECT).on(ACTIONROWSELECT.ACTIONROWCOMPONENTID.eq(ACTIONROWCOMPONENT.ID))
            .where(ACTIONROWCOMPONENT.ACTIONROWID.eq(ACTIONROW.ID))
    ).convertFrom { r ->
        r.mapNotNull { record ->
            val type = record[ACTIONROWCOMPONENT.TYPE]
            val customId = record[ACTIONROWCOMPONENT.CUSTOMID]!!

            when (type) {
                Actionrowcomponenttype.BUTTON -> {
                    // O jOOQ traz null se o join falhar, então verificamos se label existe
                    if (record[ACTIONROWBUTTON.LABEL] != null) {
                        ActionButton(
                            customId = customId,
                            label = record[ACTIONROWBUTTON.LABEL]!!,
                            style = record[ACTIONROWBUTTON.STYLE].toString(),
                            emoji = record[ACTIONROWBUTTON.EMOJI],
                            url = record[ACTIONROWBUTTON.URL],
                            disabled = record[ACTIONROWBUTTON.DISABLED] == true
                        )
                    } else null
                }
                Actionrowcomponenttype.SELECT_MENU -> {
                    if (record[ACTIONROWSELECT.PLACEHOLDER] != null) {
                        ActionSelectMenu(
                            customId = customId,
                            placeholder = record[ACTIONROWSELECT.PLACEHOLDER]!!,
                            minValues = record[ACTIONROWSELECT.MINVALUES] ?: 1,
                            maxValues = record[ACTIONROWSELECT.MAXVALUES] ?: 1,
                            options = record[selectOptionsMultiset] // Lista de opções já convertida
                        )
                    } else null
                }
                else -> null
            }
        }
    }

    // 3. Definimos o multiset da ACTION ROW (Pai dos componentes)
    val actionRowMultiset = multiset(
        select(
            rowComponentsMultiset
        )
            .from(ACTIONROW)
            .where(ACTIONROW.CONTAINERCOMPONENTID.eq(CONTAINERCOMPONENT.ID))
    ).convertFrom { r ->
        // Retorna o primeiro ActionRow encontrado ou null, encapsulado no DTO
        r.firstOrNull()?.let { row ->
            ActionRowData(components = row[rowComponentsMultiset])
        }
    }

    // ========================================================================
    // QUERY PRINCIPAL (Executada apenas 1 vez)
    // ========================================================================
    return select(
        CONTAINERCOMPONENT.ID,
        CONTAINERCOMPONENT.CONTAINERID,
        CONTAINERCOMPONENT.ISACTIONROW,
        CONTAINERCOMPONENT.DETAILS,
        actionRowMultiset
    )
        .from(CONTAINERCOMPONENT)
        .where(CONTAINERCOMPONENT.CONTAINERID.eq(containerId))
        .fetch { record ->
            // Lógica de mapeamento final (na memória)
            val isActionRow = record[CONTAINERCOMPONENT.ISACTIONROW] == true

            val componentData: ComponentData = if (isActionRow) {
                // Pega o resultado do multiset (já veio do banco pronto)
                record[actionRowMultiset] ?: ActionRowData(components = emptyList())
            } else {
                // Caminho JSON
                val jsonDetails = record[CONTAINERCOMPONENT.DETAILS]?.data()
                if (jsonDetails != null) {
                    mapper.readValue<ComponentData>(jsonDetails)
                } else {
                    TextDisplayData(content = "Error: Missing Data")
                }
            }

            ContainerComponent(
                id = record[CONTAINERCOMPONENT.ID]!!,
                containerId = record[CONTAINERCOMPONENT.CONTAINERID]!!,
                data = componentData
            )
        }
}