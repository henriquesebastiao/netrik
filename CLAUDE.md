# Projeto: Netrik — desenvolvimento do app Android

## Contexto
Netrik é um app Android de ferramentas de rede para analistas de redes, pentesters e entusiastas. O design completo (ícone, design system e telas) foi criado no Claude Design e está neste link:

https://claude.ai/design/p/a79c09a3-0c10-41a4-97e4-8fde20361378?file=Netrik+Prot%C3%B3tipo+naveg%C3%A1vel.dc.html

Esse protótipo navegável é a fonte da verdade visual: siga cores, tipografia, espaçamentos, componentes e fluxos de navegação dele. Se algo no design for inviável ou ambíguo em Compose, escolha a solução mais fiel possível e registre a decisão no relatório da etapa.

### Como acessar o design
- O link exige login; WebFetch retorna 403. Use o MCP `claude_design` (servidor `https://api.anthropic.com/v1/design/mcp`, autenticação via `/design-login`), projeto `a79c09a3-0c10-41a4-97e4-8fde20361378`: `list_files` e `read_file`.
- Arquivos principais: `Netrik Protótipo navegável.dc.html` (fluxo e regras de navegação), `Netrik Etapa 1.dc.html` (ícone, seed, design system), `NetrikHub`, `NetrikTool` (Ping/Traceroute), `NetrikDevices`, `NetrikWifi`, `NetrikPorts`, `NetrikSSH`, `NetrikOUI`. A numeração de etapas nos nomes de arquivo do design difere do plano abaixo; vale o plano abaixo.
- Antes de implementar cada tela, releia o arquivo correspondente no design (estados, textos, espaçamentos).
- Se não conseguir acessar o design, PARE e avise. Não adivinhe nem recrie o design por conta própria.

### Decisões de design já aprovadas
- Ícone 1a ("Traço"), seed azul-petróleo `#136B79`, hub em lista agrupada (Diagnóstico, Descoberta, Acesso remoto).
- Esquema de fallback gerado com material-color-utilities (SchemeTonalSpot) em `core/designsystem/theme/Color.kt`; success/warning em `ExtendedColors` (harmonizados com o seed).
- Roboto Flex na interface e JetBrains Mono em todo dado técnico (`NetrikTheme.dataTypography`), embarcadas em `res/font` (subconjunto latino).
- Ícones: Material Symbols Rounded como vector drawables em `res/drawable` (`ic_<nome>` e `ic_<nome>_filled`). Para um ícone novo: `scripts/material_symbol.py nome [--filled nome]`.
- Tema segue o sistema; o botão de tema do protótipo não vai para o app.
- IP público: só consultado quando o usuário toca em "Mostrar IP público" (api.ipify.org).
- OUI: a base IEEE vai em `assets/oui/*.csv.gzip` (extensão `.gzip`, não `.gz`: o AGP descompacta `.gz` no build) e é importada no Room no primeiro uso. Downloads do IEEE precisam de User-Agent próprio (`Netrik/<versão>`): o padrão do Android leva HTTP 418. Os CSVs do IEEE não têm data de registro, então ela não é exibida.

## Regras de Git (OBRIGATÓRIAS)
- NUNCA execute `git commit`, `git add`, `git push`, `git stash`, `git reset`, `git checkout`, `git rebase`, `git merge` ou qualquer comando que altere o histórico, o índice ou as branches. Comandos somente leitura (`git status`, `git diff`, `git log`) são permitidos.
- Ao concluir cada etapa, escreva no arquivo `commit-message.md` (na raiz) a mensagem de commit das mudanças daquela etapa, para que eu mesmo faça o commit.
- Se o arquivo já tiver conteúdo, sobrescreva-o: ele deve sempre descrever apenas as mudanças ainda não commitadas da etapa atual.
- Formato: Conventional Commits (`feat:`, `fix:`, `refactor:`, `chore:`, `build:`, `test:`, `docs:`), título com no máximo 72 caracteres e corpo com uma lista objetiva do que mudou e por quê, em português.
- Se as mudanças da etapa ficarem melhores em mais de um commit, escreva cada mensagem separadamente no arquivo, indicando quais arquivos pertencem a cada uma.
- Adicione `commit-message.md` ao `.gitignore`.

## Stack e arquitetura
- Kotlin, Jetpack Compose, Material 3 (com cores dinâmicas e o esquema de cores próprio do design como fallback), edge-to-edge.
- minSdk 26, target e compile no SDK estável mais recente.
- Gradle Kotlin DSL com version catalog (`libs.versions.toml`).
- Arquitetura MVVM com fluxo unidirecional de dados: UI (Compose) → ViewModel (StateFlow de UI state imutável) → camada de dados (repositories/data sources).
- Coroutines e Flow para tudo que é assíncrono; operações de rede em `Dispatchers.IO`, com concorrência controlada (Semaphore) nos scanners e cancelamento correto ao sair da tela ou apertar "Parar".
- Hilt para injeção de dependência, Room para dados estruturados (hosts e grupos SSH, históricos), DataStore para preferências, Navigation Compose com rotas type-safe.
- Organize por feature (`feature/ping`, `feature/traceroute`, `feature/lan`, `feature/wifi`, `feature/portscan`, `feature/ssh`, `feature/oui`) mais `core/` (design system, rede, banco, utilitários). A estrutura deve facilitar adicionar novas ferramentas no futuro.
- Para cada nova dependência, justifique a escolha no relatório da etapa e prefira bibliotecas mantidas e com licença compatível com open source.

## Regras de qualidade
- Nunca invente dados nem simule resultados. Se o Android não permitir obter alguma informação, mostre isso honestamente na UI (ex.: "MAC indisponível") e explique no relatório.
- Implemente os estados do design: vazio, em execução, resultado, erro e sem conexão.
- Escreva testes unitários para a lógica pura: parsing da saída do ping/traceroute, normalização de MAC, lookup OUI, cálculo de CIDR/faixa de IPs, parsing de listas de portas (ex.: `22,80,8000-8100`), conversão canal ↔ frequência.
- Antes de encerrar cada etapa, rode `./gradlew assembleDebug` e `./gradlew test` e corrija o que falhar.
- Nunca registre em log senhas, chaves privadas ou conteúdo de sessões SSH.

## Notas técnicas por funcionalidade
Pesquise e valide cada ponto abaixo contra as APIs atuais do Android antes de implementar.

- **Ping**: apps sem root não abrem sockets ICMP raw. Use o binário `/system/bin/ping` via ProcessBuilder, lendo a saída em tempo real e fazendo parsing de seq, TTL e tempo. Calcule as estatísticas (perda, mín/méd/máx, jitter) no app.
- **Traceroute**: geralmente não existe binário no Android. Implemente com `ping` usando TTL incremental (um salto por TTL) e capture o IP que responde "Time to live exceeded". Faça a resolução reversa de hostname de forma assíncrona.
- **Scanner LAN**: desde o Android 10 o app não lê `/proc/net/arp`, e versões mais recentes também restringem `ip neigh`. Faça a descoberta por sondagem concorrente (ICMP via ping e/ou TCP connect em portas comuns) e obtenha nomes via mDNS (NsdManager) e, se viável, NetBIOS/SSDP. Investigue o que é realmente possível para obter MACs nas versões suportadas e trate a indisponibilidade com elegância na UI.
- **Scanner Wi-Fi**: use WifiManager/ScanResult (frequência, centerFreq0/1, channelWidth, capabilities e, no API 33+, os tipos de segurança). Verifique a combinação correta de permissões por nível de API (localização, NEARBY_WIFI_DEVICES) e a exigência de localização ativa. Respeite o throttling de scans do Android e informe-o na UI. Desenhe o gráfico de espectro com Canvas do Compose.
- **Port scanner**: TCP via connect com timeout configurável. Em UDP, ausência de resposta não significa porta aberta: classifique como "aberta|filtrada" quando não houver resposta e como "fechada" quando houver erro de porta inalcançável, deixando isso claro na UI. Inclua listas embutidas de Top 100 e Top 1000 portas com nomes de serviço.
- **SSH**: avalie sshj ou o fork mantido do JSch (mwiede/jsch), garantindo suporte a chaves Ed25519, RSA e ECDSA no Android. Para o terminal, avalie bibliotecas de emulação existentes (ex.: os módulos terminal-emulator/terminal-view do Termux), verificando a licença, em vez de escrever um emulador do zero. Implemente a verificação de host key com tela de confirmação de fingerprint e armazenamento de known_hosts. Criptografe senhas e chaves privadas salvas com Android Keystore.
- **OUI**: embarque a base IEEE (MA-L, MA-M e MA-S) processada em um formato eficiente (Room ou asset indexado), funcionando offline, com opção de atualizar a partir dos arquivos públicos do IEEE. Aceite MAC em vários formatos e prefixos parciais. Esse módulo é compartilhado pelos scanners LAN e Wi-Fi.

## Plano de etapas
Trabalhe UMA etapa por vez. Ao fim de cada uma: garanta build e testes passando, escreva `commit-message.md`, me envie um relatório curto (o que foi feito, decisões, limitações encontradas, como testar) e PARE. Aguarde minha aprovação para seguir.

- **Etapa 0 — Fundação**: criar o projeto, version catalog, Hilt, tema Material 3 a partir do design system (cores claro/escuro, tipografia incluindo a fonte monoespaçada, cores semânticas de status), ícone adaptativo (foreground, background, monocromático), Navigation Bar, hub de Ferramentas com card "Rede atual", `.gitignore` e um `CLAUDE.md` com as regras deste prompt (em especial as de Git, o plano de etapas e o link do design no Claude Design como fonte da verdade visual) para que valham nas próximas sessões.
- **Etapa 1 — OUI Lookup**: base de dados, repositório compartilhado e tela de consulta.
- **Etapa 2 — Ping e Traceroute**.
- **Etapa 3 — Scanner Wi-Fi**: lista, espectro e fluxo de permissões.
- **Etapa 4 — Scanner de dispositivos LAN**: lista, detalhes e ações rápidas.
- **Etapa 5 — Port Scanner**: TCP/UDP, host único e rede.
- **Etapa 6 — SSH**: hosts, grupos, formulário, armazenamento seguro e verificação de host key.
- **Etapa 7 — Terminal SSH**: emulação, barra de teclas extras e múltiplas sessões.

## Ambiente de build
- JDK 17 via `gradle/gradle-daemon-jvm.properties` (o JDK padrão da máquina pode ser mais novo que o suportado pelo AGP).
- `./gradlew assembleDebug` e `./gradlew test`.
- Emulador: AVD `Pixel_9`; inicie com `-memory 4096`, porque com 2 GB o app é morto por falta de memória logo após o boot.

## Andamento
- Etapa 0 — Fundação: concluída.
- Etapa 1 — OUI Lookup: concluída.
- Etapa 2 — Ping e Traceroute: concluída. O traceroute ainda precisa de validação num aparelho físico: no emulador o ICMP é simulado (TTL sempre 255, sem "TTL excedido").
- Etapa 3 — Scanner Wi-Fi: concluída (aguardando commit/aprovação). O emulador só tem a rede "AndroidWifi" (2,4 GHz, sem 6 GHz).
- Próxima: Etapa 4 — Scanner de dispositivos LAN. Comece relendo `NetrikDevices.dc.html` no design e apresente o plano antes de codar.