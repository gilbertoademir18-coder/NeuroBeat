# Migração para o `overdrive` (PC de casa)

> **Feita em 2026-10-15.** Diferença do roteiro: o banco começou **do zero**
> (o do notebook só tinha testes), então o passo 3 não foi executado e as
> raízes foram cadastradas com "Adicionar pasta", não "Trocar pasta". Raízes
> no `overdrive`: `musicas` (809), `audios` (142) e `workvault` (1), todas em
> `C:\Users\Gills87\Meu Drive\`. Tailnet:
> `https://overdrive.tail2c882a.ts.net:8447`. **Passo 8 feito no notebook em 2026-10-16:** `.env` apontando para `100.65.76.22` (`migrate status` em dia, 952 músicas lidas), `tailscale serve` da 8447 desligado e atalho de Iniciar com o Windows removido. **Migração concluída.**

Roteiro para levar o NeuroBeat do notebook (`dev`) para o PC de casa
(`overdrive`), onde ele vai morar de vez: banco, servidor de produção, tray e
o endereço que o celular sincroniza. Escrito no notebook em 2026-10-15, no fim
da sessão que construiu o projeto; quem executa é a sessão no `overdrive`.

> Leia o `CLAUDE.md` antes. As regras de lá valem aqui, em especial **"os
> dados são do usuário"**: nada de criar, editar ou apagar registros por
> conta própria. Cadastrar e mudar raízes é com o usuário, pelo web.

## Onde as coisas estão hoje (no notebook)

| O quê | Estado |
| --- | --- |
| Banco `neurobeat` | Postgres 17 do **notebook** (`127.0.0.1`) |
| Backup | `neurobeat-20261015-164322.dump` (18 KB), enviado ao `overdrive` por **Taildrop** |
| Servidor (tray) | rodando no notebook, `127.0.0.1:3500` |
| Tailnet | `https://dev.tail2c882a.ts.net:8447` → 3500 do notebook |
| App no G75 | ⚙ apontando para **Notebook** |

O que está no banco (dados do usuário, para conferir depois do restore):

| Raiz | Músicas | Caminho no notebook |
| --- | --- | --- |
| `musicas` | 54 | `C:\Projetos\NeuroBeat\.dev\#MediaFlow - Músicas` (amostra copiada do celular) |
| `workvault` | 1 | `C:\Users\gilbe\Transmissão no Google Drive\.shortcut-targets-by-id\1uzMDcp6tIRXNKIyRTi-91ZjTIHx9PhTl\Músicas WorkVault` |

Playlists: **Playlist Teste** (2 faixas) e **CTRL** (1 faixa).

A raiz `audios` (pasta `#MediaFlow - Audios`) **nunca foi cadastrada**; entra
no passo 5.

## Por que levar o banco em vez de começar do zero

A identidade das músicas é a impressão digital do conteúdo, não o caminho.
As 54 músicas da raiz `musicas` são cópias exatas de arquivos da pasta real
`#MediaFlow - Músicas` do Drive, com os mesmos caminhos relativos
(`3 Doors Down/Changes.mp3`...). Trocando o caminho da raiz para a pasta real
e reescaneando, as 54 são reencontradas com os **mesmos ids**, as outras ~750
entram como novas, e as playlists sobrevivem. É a prova de que a troca de
caminho funciona na prática.

## Passo a passo

### 1. Código e dependências

```powershell
cd C:\Projetos
# Se ainda não existir. O alias SSH da conta pessoal é o mesmo do Creativa:
# confira com `git -C C:\Projetos\Creativa remote -v`.
git clone git@github-gills87:gilbertoademir18-coder/NeuroBeat.git
cd NeuroBeat
npm install
```

Confira as portas antes (devem estar livres; o padrão reservou 3500 e 8447):

```powershell
Get-NetTCPConnection -State Listen | Where-Object LocalPort -in 3500,3501,3510,8447
```

### 2. Banco

O `criar-banco.ps1` pede a senha do `postgres` (o usuário digita; não fica
gravada). Ele cria usuário e banco `neurobeat`, escreve o `.env` e aplica as
migrações.

```powershell
.\scripts\criar-banco.ps1
```

### 3. Restaurar o backup

O Taildrop entrega o arquivo na pasta **Downloads** do usuário. Se não estiver
lá: `tailscale file get $env:USERPROFILE\Downloads`.

Copie para `backups\` (no `.gitignore`) e restaure **como o usuário
`neurobeat`**, que é dono do banco. O `--clean --if-exists` troca as tabelas
vazias que o `criar-banco` criou pelas do backup, incluindo o histórico de
migrações do Prisma.

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
Copy-Item "$env:USERPROFILE\Downloads\neurobeat-20261015-164322.dump" backups\
$pg = (Get-ChildItem "C:\Program Files\PostgreSQL" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
# A senha do usuário neurobeat está no DATABASE_URL do .env.
& "$pg\bin\pg_restore.exe" -h 127.0.0.1 -U neurobeat -d neurobeat --clean --if-exists --no-owner backups\neurobeat-20261015-164322.dump
cd apps\api; npx prisma migrate status; cd ..\..
```

O `migrate status` deve dizer que está tudo em dia. Se houver migração nova no
repositório que o backup não tinha, `npm run db:deploy` aplica.

### 4. Achar as pastas reais do Drive

No `overdrive` o Google Drive está em modo **espelho** (arquivos reais em
disco). Ache o caminho das três pastas e confira que têm arquivos de verdade:

- `#MediaFlow - Músicas` (~809 áudios no celular, mp3/flac/wma)
- `#MediaFlow - Audios` (~142 áudios, mp3/m4a)
- `Músicas WorkVault` (pasta **compartilhada** de outra conta, que aparece na
  conta principal como atalho). Atalho de pasta compartilhada às vezes vira só
  um link no disco: confira que há arquivos `.m4a`/`.mp3` lá dentro.

Cuidado para não cadastrar uma pasta que contenha outra (ver "Cadastrar cada
pasta uma vez só" no `CLAUDE.md`).

### 5. Subir o tray e ajustar as raízes (o usuário, pelo web)

```powershell
.\NeuroBeat.cmd
```

No menu do tray, ligue **Iniciar com o Windows**. Depois, em **Abrir no Edge**
→ Biblioteca, o usuário:

1. Em `musicas`, **Trocar pasta** para o caminho real de `#MediaFlow - Músicas`.
2. Em `workvault`, **Trocar pasta** para o caminho real da WorkVault.
3. **Adicionar pasta** `audios` com o caminho real de `#MediaFlow - Audios`.
4. **Reescanear.** O primeiro escaneamento lê as tags de ~950 arquivos e leva
   perto de um minuto.

Resultado esperado: ~950 músicas, **as 54 antigas mantidas** (não aparecem
como novas), 0 sumidas, e as duas playlists intactas com as mesmas faixas.

### 6. Publicar no tailnet

```powershell
.\scripts\publicar-no-tailnet.ps1
```

Deve imprimir `https://overdrive.tail2c882a.ts.net:8447`. Teste:
`Invoke-WebRequest https://overdrive.tail2c882a.ts.net:8447/api/saude`.

### 7. Celular

No app: ⚙ → **PC de casa** → Salvar (sincroniza sozinho). As playlists devem
aparecer sem "não estão no aparelho".

### 8. Desligar o notebook como servidor (na próxima vez que estiver nele)

- No tray do notebook: desmarcar **Iniciar com o Windows** e **Sair**.
- `.\scripts\publicar-no-tailnet.ps1 -Desligar`.
- Para desenvolver no notebook com o banco de casa, no `overdrive`, como
  Administrador: `C:\Projetos\Creativa\scripts\liberar-banco-no-tailnet.ps1 -Banco neurobeat`.
  Depois, no `.env` do notebook, troque o host do `DATABASE_URL` para
  `100.65.76.22` (mesma senha do `.env` do `overdrive`) e teste com
  `npx prisma migrate status` em `apps\api`.
- O banco local do notebook vira sobra. Não apague sem o usuário pedir.

### 9. App Android no `overdrive` (quando for compilar lá)

O `CLAUDE.md` tem a tabela do toolchain (Gradle 9.7.1 pelo wrapper, AGP
9.4.1, JDK 21, SDK com `compileSdk 37`). Precisa do Android Studio (ou só
JDK + SDK) e do celular com depuração USB autorizada para este PC. Em
`apps\android`: `.\gradlew.bat assembleDebug` e
`adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## Ao terminar

Atualize o `CLAUDE.md`: a seção "Banco: primeiro no notebook" passa a dizer
que o banco mora no `overdrive`, e o default de servidor do app
(`Sync.NOTEBOOK` em `apps/android/.../Sync.kt`) pode passar a ser o
`PC_DE_CASA`. Marque esta migração como feita aqui no topo.
