# Como responder

- Seja amigável e bem-humorado.
- Explique o raciocínio de forma clara.
- Dê exemplos práticos.
- Use linguagem natural, como um colega de equipe.
- Seja proativo ao sugerir melhorias.

# ⚠️ Pendente: migração para o `overdrive`

Se esta sessão está no **PC de casa (`overdrive`)** e o NeuroBeat ainda não
roda aqui, siga `docs/migracao-para-o-overdrive.md` antes de qualquer outra
coisa. Ele leva banco, servidor, tray e tailnet do notebook para cá. (Remova
este aviso quando a migração estiver feita.)

# Como trabalhar neste projeto

- **Toda decisão de projeto vai para este arquivo.** Se combinamos algo na
  conversa, registre aqui antes de seguir.
- Siga o padrão dos projetos do usuário:
  `C:\Projetos\Creativa\docs\padrao-de-projeto.md`. Na dúvida, copie do
  **Trimly** (`C:\Projetos\Trimly`).
- Antes de dar algo por concluído: `npm run typecheck` e `npm run build`.
  No Android, `.\gradlew.bat assembleDebug` em `apps\android`.
- **Commit e push automáticos, direto na `main`.** O usuário é o único dev e
  o único usuário do app: ao terminar cada mudança (verificada), faça o commit
  e o `git push` sem perguntar. Não precisa de branch nem de PR. Decidido em
  2026-10-15.
- Todo `.ps1` é salvo em **UTF-8 com BOM** (sem o BOM, o PowerShell 5.1
  estraga os acentos).

## Os dados são do usuário

Nada de criar, semear, editar ou apagar registros no banco (nem "para
testar"), nada de `truncate`, `DELETE` ou `prisma migrate reset`. Migrações
podem ser criadas e aplicadas normalmente. Antes de qualquer operação de risco
autorizada, `npm run backup`.

# O projeto

**NeuroBeat** é um player de áudio pessoal para o celular Android do usuário
(Moto G75). Um dev, um usuário.

- As músicas ficam em **várias pastas do Google Drive**. Elas são
  sincronizadas para o celular pelo app **Autosync** e também existem no PC de
  casa (Google Drive para computador).
- O player precisa tocar **em segundo plano**, com outros apps por cima e com
  a **tela bloqueada**.
- Músicas e playlists são gerenciadas por um **cliente web**.
- Playlists têm opção de **loop**.

## Máquinas

| Máquina | Papel |
| --- | --- |
| `overdrive` (PC de casa, `100.65.76.22`, sempre ligado) | Postgres, API em produção, tray, pastas do Drive |
| `dev` (notebook do trabalho, `100.111.145.14`) | Desenvolvimento; usa o banco do `overdrive` pelo tailnet |
| Moto G75 | App do player; fala com a API pelo tailnet só para sincronizar |

Repositório: https://github.com/gilbertoademir18-coder/NeuroBeat, com o remote
pelo alias SSH da conta pessoal (`git@github-gills87:...`, igual ao Creativa).
Neste notebook, o `github.com` puro é a conta do trabalho e leva 403.

## Decisões

### App nativo, não PWA

Um PWA não serve, e o motivo principal é o acesso aos arquivos: o Chrome
Android não dá acesso persistente a pastas locais (não tem
`showDirectoryPicker`), então o PWA só tocaria por streaming do PC, sem
funcionar offline. Além disso, o Android mata abas em segundo plano, o que
quebra playlists longas com a tela apagada.

> No MediaFlow, o PWA foi a escolha certa porque lá o app era só listas e
> formulários, sem nenhuma API nativa. Aqui é o oposto: tocar arquivos locais
> com a tela bloqueada é o coração do app.

### Kotlin + Media3, não React Native/Expo

O app do celular é **Kotlin + Jetpack Compose + Media3**
(`MediaSessionService` + ExoPlayer), o kit oficial de áudio do Google. Ele já
resolve o serviço em primeiro plano, a notificação, os controles da tela de
bloqueio e o loop (`REPEAT_MODE_ALL`). O APK é instalado direto, sem Play
Store.

**Por que não Expo:** o MediaFlow tentou React Native/Expo e desistiu. Pelo
CLAUDE.md dele, "a combinação Windows + pnpm + módulos nativos era uma batalha
perdida", e o repositório chegou a mudar para `C:\MF` por causa do MAX_PATH.
Com Kotlin há um toolchain só (Android Studio/Gradle), sem `node_modules` nem
ponte JS↔nativo. O preço é não compartilhar tipos TypeScript com o app, o que é
aceitável porque a API dele é pequena. **Não reabrir a decisão por React Native
sem antes reler isto.**

**Começa por um teste mínimo:** um app que toca as músicas de uma pasta do
Autosync com a tela bloqueada e em loop, instalado no G75. Só depois dele
passar é que o resto é construído. Se travar no toolchain, a gente descobre em
uma tarde.

O build é feito nas **duas máquinas** (Android Studio + JDK no `overdrive` e no
notebook), com o celular por USB.

#### Toolchain do `apps/android`

| Peça | Versão | Observação |
| --- | --- | --- |
| Gradle (wrapper) | 9.7.1 | `apps/android/gradlew.bat` |
| Android Gradle Plugin | 9.4.1 | Kotlin embutido: **não** aplicar `org.jetbrains.kotlin.android` |
| Kotlin / Compose compiler | 2.4.20 | plugin `org.jetbrains.kotlin.plugin.compose` |
| Compose BOM | 2026.09.00 | exige `compileSdk = 37` |
| Media3 | 1.11.1 | `media3-exoplayer` + `media3-session` |
| JDK | 21 (o `jbr` do Android Studio, via `JAVA_HOME`) | No `overdrive`, o `jbr` do Studio 2026.2 é o 25, e compila igual |
| SDK | `minSdk 33`, `targetSdk 36`, `compileSdk 37` | |

O Moto G75 está no **Android 16**. Pacote do app: `com.gills87.neurobeat`.

**Ícone do app:** adaptativo e em vetor (`res/mipmap-anydpi/ic_launcher.xml`),
com fundo em degradê, a linha de batimento na frente e a mesma linha como
`monochrome` (ícones temáticos). O path é o do SVG de
`apps/web/scripts/gerar-icones.mjs`, convertido à mão para o quadro de 108 dp
(a conta está no comentário do `ic_launcher_frente.xml`). A notificação do
player usa `ic_notificacao.xml` (a mesma linha, em 24 dp). Mudou a marca no
SVG, recalcule os dois.

Ciclo de desenvolvimento, de dentro de `apps/android`:

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -b crash -d        # se fechar sozinho
```

O MediaStore já indexa as pastas do Autosync: a contagem dele bate com a do
disco (809 em Músicas, 142 em Audios, medido em 2026-10-15), com duração. O app
não precisa varrer pastas; consulta o MediaStore por `RELATIVE_PATH`.

#### Armadilhas já pisadas

- **`future.get()` do `MediaController` pode estar cancelado.** Se a Activity
  para antes de conectar (a tela apaga), o `onStop` cancela o future e o
  `get()` lança `CancellationException`, derrubando o app. Conferir
  `isCancelled` antes.
- **A URI não atravessa do controller para a sessão.** Ela vai no
  `requestMetadata.mediaUri` e o `onAddMediaItems` do `PlaybackService` a
  repõe.
- **Edge-to-edge (Android 15+):** o app desenha por trás das barras do
  sistema. O `Scaffold` desconta os insets do conteúdo, mas **não** os da
  `bottomBar`: ela precisa de `navigationBarsPadding()`, senão a barra de
  navegação cobre os botões.

### Monorepo

```
apps/api         Fastify + Prisma 7 + Postgres (igual ao Trimly); serve o web pronto na 3500
apps/web         React 19 + Vite + Tailwind 4: biblioteca, playlists, loop
apps/android     Kotlin + Compose + Media3 + Room (projeto Gradle, fora dos workspaces npm)
packages/shared  tipos e schemas zod da API, usados por api e web
```

O `apps/android` é independente do npm: o Android Studio abre só essa pasta.
O contrato com a API é o JSON de `GET /api/sync`, documentado em
`packages/shared`.

- **`packages/shared` não tem build.** O `exports` aponta para o `.ts`, e o
  `tsx` (API) e o Vite (web) compilam por conta própria. Não existe `dist`
  para ficar velho depois de um `git pull`, armadilha em que o MediaFlow caiu.
- **O web não é PWA.** O player do celular é o app Android; o web é a mesa
  de controle (biblioteca e playlists), aberta no navegador.
- Dependências da API e do web nas mesmas versões do Trimly, que já
  funcionam juntas nesta máquina.
- **A API de dev roda com `node --watch --import tsx`, e não `tsx watch`.**
  O `tsx watch` 4.23 trava para sempre, sem erro e sem abrir a porta, quando
  o stdin é um pipe (é o que o `concurrently` do `npm run dev` faz) e a cadeia
  de imports chega ao Prisma Client gerado em `src/generated/`. Achado em
  2026-10-15 por bissecção; **o Trimly tem o mesmo problema**. O `--watch`
  nativo do Node reinicia igual ao salvar um arquivo.
- No notebook não existe Google Drive. Para testar o scanner aqui, as músicas
  são copiadas do celular (`adb pull`) para `.dev/` (no `.gitignore`).

### Biblioteca e identidade da música

- **A API indexa as músicas no PC.** Ela conhece as "raízes" (alias → pasta,
  ex.: `rock` → `G:\Meu Drive\Musicas\Rock`) e varre: caminho relativo,
  tamanho, tags e duração. O reescaneamento é sob demanda, por um botão no web
  e um item no tray. Música que sumiu é **marcada** como sumida, nunca apagada.
- **Identidade = impressão digital do conteúdo, não o caminho.** O usuário
  renomeia pastas do Drive de vez em quando, e caminho como identidade faria a
  música "sumir" das playlists.
  - **Playlists apontam para o ID interno da `Musica`**, nunca para caminho.
    O caminho (alias da raiz + caminho relativo, com `/` e em NFC) é só "onde
    ela está agora".
  - **Impressão digital = tamanho + hash de 64 KB do meio do arquivo.** Do
    meio porque as tags ID3 ficam no início e no fim: editar tag não muda a
    impressão. É barato (~64 KB por faixa).
  - **No reescaneamento:** caminho sumiu + arquivo novo com a mesma impressão
    = mesma música, só muda o caminho. O que ficar ambíguo (duas candidatas,
    ou tag editada junto com o rename) aparece no web para religar à mão.
  - **Renomear a pasta raiz** é só trocar o caminho do alias no web.
  - **O celular calcula a mesma impressão** dos arquivos locais (em cache por
    `_ID` + `DATE_MODIFIED` do MediaStore) e casa por ela. Assim não depende
    de o Autosync usar os mesmos nomes que o PC.
  - **A especificação exata** (bytes da amostra, formato do hash) mora em
    `packages/shared/src/index.ts`. Mudou lá, muda no Kotlin junto.
- **Onde está o código:** `apps/api/src/biblioteca/`. O `reconciliar.ts` é uma
  função pura (banco × disco → plano) com testes caso a caso; o
  `escanear.ts` lê o disco, as tags (`music-metadata` 11.x) e aplica o plano.
  Raiz cuja pasta não existe é pulada, e as músicas dela não viram "sumidas".
  Cópias do mesmo arquivo nunca são religadas no chute.
- Medido em 2026-10-15 com 60 arquivos de amostra (mp3, flac, m4a, wma): todas
  as tags e durações lidas, nenhuma impressão repetida, 4,3 s para 648 MB,
  quase todo o tempo nas tags.
- **Pastas do Autosync no celular hoje** (armazenamento interno, `/sdcard/`):
  - `Google Drive Autosync/#MediaFlow - Músicas`
  - `Google Drive Autosync/#MediaFlow - Audios`
- **Raízes de mais de uma conta/pasta compartilhada.** Qualquer pasta vira
  raiz, venha de onde vier. A **Músicas WorkVault** é uma pasta compartilhada
  de outra conta que também aparece na conta principal. No notebook ela está
  em `C:\Users\gilbe\Transmissão no Google Drive\.shortcut-targets-by-id\1uzMDcp6tIRXNKIyRTi-91ZjTIHx9PhTl\Músicas WorkVault`
  (Drive em modo **streaming**: marcar a pasta como "Disponível off-line",
  senão o que vem da nuvem é só atalho e o scanner baixaria cada arquivo).
  No celular, ela precisa de um par no Autosync **dentro de
  `Google Drive Autosync/`**, que é onde o app procura.
- **O título de uma música é o nome do arquivo, sem a extensão. Nunca a
  tag.** Decisão do usuário: renomear o arquivo é mais fácil que editar
  metadados. O título é derivado do caminho na hora de responder
  (`tituloDoArquivo` em `packages/shared`), e não gravado no scan; assim ele
  acompanha o rename mesmo quando o scanner só atualiza o caminho de uma
  música movida (sem reler tags). A coluna `titulo` do banco guarda a tag só
  como informação; nenhuma tela a mostra. Artista e duração continuam vindo
  das tags.
- **Cadastrar cada pasta uma vez só.** Se uma raiz contiver outra (o "Meu
  Drive" inteiro com o atalho da WorkVault dentro), os arquivos são lidos em
  dobro e, como cópias idênticas nunca são religadas no chute, viram músicas
  duplicadas.
- **No celular**, o app indexa tudo o que está dentro de
  `Google Drive Autosync/`. O app lê
  pelo MediaStore (`READ_MEDIA_AUDIO`), casa as músicas pela impressão
  digital e mostra como "não sincronizada" o que não está no aparelho.

### Offline first, celular só lê

- O app baixa biblioteca e playlists (`GET /api/sync`) e guarda a última
  cópia. Sem o PC acessível, toca dela. Sincroniza **ao abrir** e pelo botão
  **Sincronizar** (decisão do usuário).
- **Sem Room: arquivos JSON** no `filesDir` do app (`sync.json`, o pacote;
  `impressoes.json`, o cache de impressões por `_ID` + `DATE_MODIFIED` +
  tamanho do MediaStore). `org.json` e `HttpURLConnection` já vêm no
  Android: zero dependência nova, zero plugin novo no Gradle (o KSP do Room
  seria mais um ponto de conflito, a lição do MediaFlow). Rever se um dia o
  volume de dados pedir consultas de verdade.
- **Servidor fixo no app:** o `overdrive`
  (`https://overdrive.tail2c882a.ts.net:8447`, `Sync.SERVIDOR`), que fica
  ligado 24/7. Até 2026-10-15 havia um ⚙ para escolher entre ele e o
  notebook; saiu com a migração.
- **"Misturar áudio"** (chip no topo, ligado por padrão; chamava "Tocar
  junto", e a chave da preferência continua `tocarJunto`): o player não pede o
  foco de áudio, então toca junto com o YouTube e não pausa os outros apps.
  Desligado, volta ao normal (pausa quando outro app toca). O preço de ligado:
  uma ligação não pausa a música. Fica nas `SharedPreferences` e o
  `PlaybackService` aplica na hora (`setAudioAttributes(..., handleAudioFocus)`).
  Não tem nada a ver com a permissão de sobrepor apps.
- **Mais tocadas (aba Top):** o `PlaybackService` conta uma tocada quando a
  faixa passa de 30 s (ou da metade, se for curta); cada volta do loop conta
  de novo. A conta é por impressão digital e fica **só no celular**
  (`tocadas.json`, `Tocadas.kt`): o celular não escreve no servidor. Se um
  dia o web precisar mostrar isso, é uma rota nova de envio, a decidir.
  Desinstalar o app zera a conta.
- **Capa de fundo:** a capa embutida no arquivo da faixa atual
  (`MediaMetadataRetriever.embeddedPicture`, lida no celular, sem passar pelo
  servidor) fica borrada atrás de tudo, com um véu da cor de fundo, e em
  miniatura na barra do player. Sem capa, fundo liso. `Capa.kt`.
- Faixa da playlist sem arquivo no aparelho é pulada, e a lista mostra
  quantas faltam.
- **Quem edita é o web.** O celular só lê, e por isso não há conflitos de
  sincronização.
- Playlist: nome, itens ordenados e `loop`. Com o loop ligado, o player usa
  `REPEAT_MODE_ALL`. Shuffle fica para depois.

## Padrão de projeto aplicado

| Peça | Valor |
| --- | --- |
| Lançador / workspace | `NeuroBeat.cmd` (com fallback do fnm) / `NeuroBeat.code-workspace` |
| Mutex do tray | `"NeuroBeatTray"` |
| Logs | `%LOCALAPPDATA%\NeuroBeat\servidor.log` |
| Banco e usuário | `neurobeat` / `neurobeat` (Postgres 17 do `overdrive`, porta 5432) |
| Rota de saúde | `GET /api/saude` → `{ app: "NeuroBeat", ok: true }` |
| Tray | menu padrão + **Reescanear biblioteca** |

### Portas

| Porta | Uso |
| --- | --- |
| 3500 | App de produção (`npm run servir`, que o tray sobe) |
| 3501 | API em `npm run dev` |
| 3510 | Vite em `npm run dev` |
| 8447 | HTTPS no tailnet (`tailscale serve` → 3500) |

> A centena 3500 era a próxima livre pelo `padrao-de-projeto.md`, e o usuário
> confirmou que nada no `overdrive` a usa. Já está na tabela de portas do
> padrão (no Creativa), que também ganhou a seção sobre a API de dev.

### A API só ouve em 127.0.0.1

Em dev e em produção, ao contrário do `0.0.0.0` do Trimly e do Creativa.
Quem vem de fora (o app Android, o web de outro aparelho) chega pelo
`tailscale serve` (HTTPS na 8447), que entra por `127.0.0.1`. Assim o tray
ligado no notebook do trabalho não expõe nada na rede da empresa. Decidido
pelo usuário em 2026-10-15. Não existe acesso direto por
`http://<IP do tailnet>:3500`, e não deve existir.

### Tray

Copiado do Trimly, com um item a mais: **Reescanear biblioteca**, que roda o
`scripts\escanear.ps1` num processo sem janela e mostra o resumo num balão
(o menu não trava durante o escaneamento). O mesmo script serve para rodar à
mão.

### Banco: no `overdrive`

O banco `neurobeat` mora no PostgreSQL 17 do **`overdrive`** desde
2026-10-15 (`docs/migracao-para-o-overdrive.md`). Começou do zero: o banco do
notebook só tinha testes e o backup não foi restaurado. Para desenvolver no
notebook, o `.env` de lá passa a apontar para `100.65.76.22` (passo 8 da
migração).

## Riscos a conferir

- Se o Google Drive para computador estiver em modo **streaming**, os arquivos
  são virtuais e ler as tags baixaria tudo. Conferir o modo ("espelhar") ou
  ler só o necessário.
- Android Studio instalado nas duas máquinas. No Windows, manter caminhos
  curtos (o projeto já está em `C:\Projetos\NeuroBeat`) e, se o Gradle
  reclamar de caminho longo, ligar o `LongPathsEnabled`.
- O Autosync pode usar nomes de pasta diferentes no celular. A impressão
  digital resolve isso, sem depender de caminho.
- Otimização de bateria da Motorola: isentar o app, senão o Android pode
  matar o player.

## Etapas

1. [x] Decisões registradas neste arquivo.
2. [x] **Teste mínimo no celular:** `apps/android` com Media3 tocando uma pasta do Autosync, tela bloqueada, loop. Instalado no G75. **Passou em 2026-10-15**: tocou com a tela bloqueada e com outros apps por cima.
3. [x] Esqueleto do monorepo (workspaces, `.gitignore`, `.env.example`, shared, api e web mínimos).
4. [ ] Infra do padrão. **Feito:** `.cmd`, `.code-workspace`, tray e scripts copiados do Trimly (o `.cmd` do Creativa, pelo fallback do fnm), ícone. **Falta:** criar o banco (`criar-banco.ps1`, primeiro no notebook), migração inicial e, no `overdrive`, `publicar-no-tailnet.ps1` e liberar o banco no tailnet.
5. [x] API. **Feito:** schema e migração inicial, scanner com impressão digital, rotas de raízes, músicas e `POST /api/biblioteca/escanear`. Playlists (`/api/playlists`: listar, criar, renomear, loop, excluir e `PUT /:id/itens` com a lista inteira na ordem nova, numa transação). `GET /api/sync`.
6. [x] Web. **Feito:** tela de biblioteca (pastas, trocar caminho, reescanear, lista com busca e filtro). Editor de playlists (arrastar com o `useArrastavel` do Trimly, ↑↓, ×, loop, busca para adicionar; cada mudança salva sozinha). **Falta:** nada para a primeira versão.
7. [x] Android. impressão digital no aparelho (com cache), sync ao abrir e por botão, abas Playlists e Pastas, ajuste de servidor. **Conferido em 2026-10-15:** as 54 músicas do servidor acharam o arquivo entre os 957 indexados no G75 (impressões idênticas às do PC).
8. [ ] APK de release instalado no Moto G75.
