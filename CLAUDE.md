# Como responder

- Seja amigável e bem-humorado.
- Explique o raciocínio de forma clara.
- Dê exemplos práticos.
- Use linguagem natural, como um colega de equipe.
- Seja proativo ao sugerir melhorias.

# Como trabalhar neste projeto

- **Toda decisão de projeto vai para este arquivo.** Se combinamos algo na
  conversa, registre aqui antes de seguir.
- Siga o padrão dos projetos do usuário:
  `C:\Projetos\Creativa\docs\padrao-de-projeto.md`. Na dúvida, copie do
  **Trimly** (`C:\Projetos\Trimly`).
- Antes de dar algo por concluído: `npm run typecheck` e `npm run build`.
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

Repositório: https://github.com/gilbertoademir18-coder/NeuroBeat

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

### Biblioteca e identidade da música

- **A API indexa as músicas no PC.** Ela conhece as "raízes" (alias → pasta,
  ex.: `rock` → `G:\Meu Drive\Musicas\Rock`) e varre: caminho relativo,
  tamanho, tags e duração. O reescaneamento é sob demanda, por um botão no web
  e um item no tray. Música que sumiu é **marcada** como sumida, nunca apagada.
- **Identidade = alias da raiz + caminho relativo**, com `/` e normalizado em
  NFC.
- **No celular**, cada alias aponta para a pasta correspondente do Autosync
  (configurado nos ajustes do app). O app lê pelo MediaStore
  (`READ_MEDIA_AUDIO`), casa as músicas pelo alias + caminho e mostra como
  "não sincronizada" o que não está no aparelho.

### Offline first, celular só lê

- O app baixa biblioteca e playlists (`GET /api/sync`) e guarda em SQLite
  (Room). Sem o PC acessível, toca a última cópia.
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

> Pendente: conferir no `overdrive` se estão livres e acrescentar o NeuroBeat
> à tabela de portas do `padrao-de-projeto.md`.

## Riscos a conferir

- Se o Google Drive para computador estiver em modo **streaming**, os arquivos
  são virtuais e ler as tags baixaria tudo. Conferir o modo ("espelhar") ou
  ler só o necessário.
- Android Studio instalado nas duas máquinas. No Windows, manter caminhos
  curtos (o projeto já está em `C:\Projetos\NeuroBeat`) e, se o Gradle
  reclamar de caminho longo, ligar o `LongPathsEnabled`.
- O Autosync pode usar nomes de pasta diferentes no celular, e por isso o
  mapeamento alias → pasta é explícito.
- Otimização de bateria da Motorola: isentar o app, senão o Android pode
  matar o player.

## Etapas

1. [x] Decisões registradas neste arquivo.
2. [ ] **Teste mínimo no celular:** `apps/android` com Media3 tocando uma pasta do Autosync, tela bloqueada, loop. Instalado no G75.
3. [ ] Esqueleto do monorepo (workspaces, `.gitignore`, `.env.example`, shared, api e web mínimos).
4. [ ] Infra do padrão (`.cmd`, tray, scripts de banco, publicação e tailnet).
5. [ ] API: schema Prisma (`Raiz`, `Musica`, `Playlist`, `PlaylistItem`), scanner, CRUD, `/api/sync`.
6. [ ] Web: biblioteca com busca e filtro por raiz, editor de playlists (ordenar, loop).
7. [ ] Android: permissões, alias → pasta, sync em Room, telas de playlists e player.
8. [ ] APK de release instalado no Moto G75.
