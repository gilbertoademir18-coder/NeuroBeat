# Pendências no `overdrive` (PC de casa)

Coisas feitas no notebook em 2026-10-16 que só ficam completas depois de um
passo no PC de casa. Quem executa é a sessão no `overdrive`; o usuário está
na frente do PC. Leia o `CLAUDE.md` antes (regra "os dados são do usuário").

Ao terminar tudo, apague o aviso do topo do `CLAUDE.md` e marque este arquivo
como concluído (ou apague-o).

## 1. Mover a chave de assinatura do app

O app Android passou a ser assinado com uma chave única do projeto, a mesma
nas duas máquinas (ver "Assinatura" no `CLAUDE.md`). Os dois arquivos foram
enviados do notebook por **Taildrop** e chegam na pasta **Downloads**:

- `neurobeat.jks`
- `neurobeat-assinatura.properties`

```powershell
$d = Join-Path $env:USERPROFILE '.android'
# Se não estiverem em Downloads: tailscale file get $env:USERPROFILE\Downloads
Move-Item "$env:USERPROFILE\Downloads\neurobeat.jks", "$env:USERPROFILE\Downloads\neurobeat-assinatura.properties" $d
```

**Como saber que funcionou:** em `apps\android`, `.\gradlew.bat assembleDebug`
não deve mais avisar `NeuroBeat: sem ~/.android/neurobeat-assinatura.properties`,
e o certificado do APK deve ser o do projeto:

```powershell
$bt = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
& "$($bt.FullName)\apksigner.bat" verify --print-certs app\build\outputs\apk\debug\app-debug.apk | Select-String SHA-256
# Esperado: 01409d83e96f2ae4dc4b1c9b79aebdfdec0cba61bed441b5387427bcf9d760ea
```

O app no celular **já está assinado com essa chave** (reinstalado do notebook
em 2026-10-16). Então um `adb install -r` daqui atualiza sem desinstalar. Se
ainda assim aparecer `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a chave não está no
lugar: **não desinstale** sem antes conferir a fila de tocadas pendentes
(`adb exec-out run-as com.gills87.neurobeat cat files/tocadas-pendentes.json`
deve ser `[]`).

Sugira ao usuário guardar uma cópia dos dois arquivos junto das senhas dele.

## 2. Publicar a versão nova do servidor

O servidor de casa ainda roda o código de 2026-10-15. Desde então, no
notebook:

- **Loop global** (o web não tem mais a caixa "Loop" por playlist).
- **`ultimasTocadas` no `GET /api/sync`**: a última vez que cada música
  tocou. É o que ordena a aba Busca do app (tocadas por último primeiro).
  Sem a versão nova no ar, a Busca do celular fica só em ordem alfabética.

```powershell
cd C:\Projetos\NeuroBeat
git pull
npm install          # o package-lock mudou; o postinstall regenera o Prisma Client
```

Depois, no tray: **Publicar a versão nova** (confere typecheck, testes e
build, faz backup do banco e reinicia). Não há migração nova de banco nessas
mudanças.

> Sobre o `package-lock.json`: o npm daqui e o do notebook escrevem as
> marcas `"peer": true` de jeitos diferentes, e o lock "pisca" entre as
> máquinas. Não muda nenhuma dependência; se o `git status` mostrar o lock
> modificado só por isso, pode commitar.

**Como saber que funcionou:**

```powershell
$r = Invoke-RestMethod http://127.0.0.1:3500/api/sync
($r.ultimasTocadas.PSObject.Properties | Measure-Object).Count   # > 0
```

No celular: **Sincronizar**, e na aba **Busca** (sem digitar nada) as músicas
já tocadas aparecem no topo com "há X min", "ontem"...
