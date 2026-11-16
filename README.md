# RadarDisco Android

Analisador visual de espaço em disco em tempo real para Android (Kotlin + Jetpack Compose + Material 3), espelhando os recursos do [RadarDisco](https://github.com/LucasMGamerPlay/RadarDisco) para Windows — com **Consultor de IA operando exclusivamente via APIs na nuvem (HTTPS)**.

## Recursos Principais
- **Varredura concorrente em tempo real**: fila priorizada pela pasta em foco, métricas ao vivo (velocidade, arquivos/pastas mapeados, tempo), pausar/retomar/cancelar e opção de recolher o painel superior para dar mais espaço às pastas.
- **Lista de Pastas & Mapa Visual (Treemap)**: navegação hierárquica com breadcrumbs, filtro rápido, proporção visual por categoria e Squarified Treemap interativo em Canvas nativo.
- **Top & Apps**: ranking dos maiores arquivos (`Top 50`), pastas vilãs (`>10 MB`) e detalhamento de **Apps, Dados e Cache** via `StorageStatsManager` (opcional, com permissão *Acesso de Uso*).
- **Consultor de IA na Nuvem (Somente API)**: análise estruturada com classificação de risco (`Seguro Apagar`, `Avaliar com Cuidado`, `Não Recomendado Apagar`) usando exclusivamente APIs HTTPS externas:
  - Google Gemini API (`v1beta/interactions`)
  - OpenAI API
  - OpenRouter API
  - Groq Cloud API
  - Outra API OpenAI-compatível na nuvem (`https://`) — endereços locais/LAN são bloqueados.
- **Exclusão Segura**: bloqueio de raízes e pastas críticas do sistema (`/`, `/system`, `/storage/emulated/0`, `/Android/data`, `/Android/obb`), suporte a SAF (`OpenDocumentTree`), confirmação reforçada e limpeza parcial quando há arquivos bloqueados em uso.

## Permissões
- **Acesso a Todos os Arquivos (`MANAGE_EXTERNAL_STORAGE`)**: necessária para varrer diretamente o armazenamento interno/SD (ou use o botão **Pasta** via SAF para conceder acesso pontual a uma pasta específica). No Android 11+, `/Android/data` e `/Android/obb` continuam protegidos pelo sistema operacional (Scoped Storage).
- **Acesso de Uso (`PACKAGE_USAGE_STATS`)** *(opcional)*: libera a inspeção do tamanho de APK, Dados e Cache dos aplicativos instalados na aba **Top & Apps**.

## Como Compilar
```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```
