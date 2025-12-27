<div align="center">
  <img src="assets/readme/eris_avatar.png" alt="Avatar da Éris" width="150"/>
  <h1>Éris — JVM Edition</h1>
  
  <p>
    <strong>Reescrita de Performance em Kotlin & Spring Boot</strong><br />
    <em>Status: Projeto Descontinuado (~80% concluído)</em>
  </p>

  [![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](./LICENSE)
  ![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
  ![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
  ![Postgres](https://img.shields.io/badge/postgres-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)
  ![JDA](https://img.shields.io/badge/JDA-5865F2?style=for-the-badge&logo=discord&logoColor=white)

</div>

<br />

## 🚀 Sobre a Versão JVM

Esta é a evolução da **Éris**, originalmente escrita em TypeScript. Esta versão foi reconstruída do zero para explorar o ecossistema JVM, focando em tipagem estática forte, injeção de dependência e uma arquitetura baseada em microsserviços/módulos.

Embora o desenvolvimento tenha sido encerrado com cerca de 80% das funcionalidades migradas, o projeto demonstra padrões avançados de desenvolvimento, como o uso de **jOOQ** para queries seguras e **Spring Boot** para gerenciamento de ciclo de vida.

## 🛠️ Tecnologias de Destaque

- **Linguagem Principal:** Kotlin (90%) com Java (10% - Bootstrap/Legacy).
- **Framework:** Spring Boot (Injeção de dependência e Auto-registro).
- **Biblioteca Discord:** JDA (Java Discord API) + JDA-KTX.
- **Banco de Dados:** PostgreSQL.
- **Query Builder:** jOOQ (Código gerado via Type-safe DSL).
- **Workflow de Schema:** Prisma (Utilizado exclusivamente para modelagem de schema e migrations).
- **Inteligência Artificial:** Integração com Google Gemini API.

## 🏗️ Arquitetura Multi-Módulos (Gradle)

O projeto é dividido em módulos independentes para facilitar a manutenção:

| Módulo | Descrição |
| :--- | :--- |
| `:erisbot` | Módulo principal. Gerencia comandos, interações e eventos do Discord. |
| `:api-server` | Servidor REST integrado para comunicação externa. |
| `:database` | Core de persistência, Repositórios e instâncias do jOOQ DSL. |
| `:games` | Lógica de minigames (Blackjack, Trivia) isolada da interface do bot. |
| `:gemini-service` | Integração com LLM para funcionalidades inteligentes. |
| `:scheduler` | Gerenciamento de tarefas agendadas (Sorteios, Expirações). |
| `:prismaProject` | Projeto Node.js interno que gerencia o schema via Prisma. |
| `:common-*` | Bibliotecas de utilidades e funções compartilhadas. |

## 🗄️ Workflow de Banco de Dados

Uma das curiosidades técnicas deste projeto é o uso híbrido de ferramentas:
1. O schema é definido no arquivo `prisma/schema.prisma`.
2. As migrations são executadas via Prisma.
3. O comando `./gradlew clean generateJooq` lê o banco de dados e gera classes Kotlin/Java automaticamente.
4. O Spring Boot injeta o `DSLContext` do jOOQ nos repositórios para queries seguras.

## ⚙️ Estrutura de Features (Discord)

Diferente da versão TS, esta versão impõe uma estrutura rígida de pastas em `/discord/features` para garantir a organização via Spring:

- `commands/`: Definições de comandos slash.
- `interactions/`: Handlers para botões, select menus e modais.
- `listeners/`: Registro automático de ouvintes do Spring.
- `events/`: Lógica de processamento de eventos brutos do JDA.

> **Nota técnica:** A inicialização e configuração inicial permanecem em Java devido a requisitos específicos do Spring Boot no início da migração, garantindo estabilidade no auto-registro dos listeners.

## 📊 Comparativo de Funcionalidades

| Feature | Status (Versão Kotlin) |
| :--- | :--- |
| **🎮 Minigames (Cassino, Trivia)** | ✅ Completo (Mais robusto que a versão TS) |
| **💰 Economia (STX)** | ✅ Completo |
| **⚽ Apostas de Futebol** | ⚠️ Parcial |
| **🤖 Integração Gemini** | ✅ Implementado |
| **📨 Sistema de Cartas** | ❌ Não portado |
| **⚙️ Painel de Gestão** | ❌ Não portado |
| **🔌 API REST** | ⚠️ Parcial |

## 🏁 Post-Mortem

O projeto Éris foi uma jornada de aprendizado imensa. A migração para Kotlin foi motivada pela busca por um ambiente mais seguro para o desenvolvimento de sistemas complexos. 

O encerramento ocorreu devido ao pivô de prioridades e ao esforço necessário para manter uma infraestrutura de bot de grande porte sem o retorno esperado da comunidade. O código aqui presente reflete o ápice técnico do projeto, servindo como uma excelente referência de como estruturar bots JDA modernos com Spring Boot.

## 🤝 Licença

Este projeto está licenciado sob a **Licença MIT**. Sinta-se à vontade para clonar, estudar e reutilizar partes do código, mantendo os créditos originais para **BirdTool / Studio Styx**.