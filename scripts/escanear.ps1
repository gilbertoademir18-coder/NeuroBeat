<#
.SYNOPSIS
    Pede ao NeuroBeat no ar que releia as pastas de músicas.

.DESCRIPTION
    Faz o mesmo que o botão "Reescanear" do web: a API lê as raízes, acha o
    que é novo, renomeado, movido ou sumido, e atualiza o banco. Imprime um
    resumo de uma linha.

    O item "Reescanear biblioteca" do tray roda este script num processo sem
    janela, com -Saida, e mostra o resumo num balão quando ele termina.

.EXAMPLE
    .\scripts\escanear.ps1
#>

[CmdletBinding()]
param(
    [int]$Porta = 3500,
    # Grava o resumo neste arquivo em vez de imprimir (uso do tray).
    [string]$Saida
)

$ErrorActionPreference = "Stop"

function Resumir($r) {
    $partes = @()
    if ($r.novas)       { $partes += "$($r.novas) novas" }
    if ($r.movidas)     { $partes += "$($r.movidas) renomeadas ou movidas" }
    if ($r.atualizadas) { $partes += "$($r.atualizadas) com tags alteradas" }
    if ($r.voltaram)    { $partes += "$($r.voltaram) voltaram" }
    if ($r.sumidas)     { $partes += "$($r.sumidas) sumiram" }
    $tempo = "{0:N1} s" -f ($r.duracaoMs / 1000)
    $texto = if ($partes.Count) { "$($partes -join ', ') ($tempo)." } else { "Nada mudou ($tempo)." }
    foreach ($raiz in $r.raizes) {
        if ($raiz.erro) { $texto += "`n$($raiz.alias): $($raiz.erro)" }
    }
    return $texto
}

$ok = $true
try {
    # Prazo longo: o primeiro escaneamento da biblioteca inteira lê as tags de
    # todos os arquivos e leva perto de um minuto.
    $r = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Porta/api/biblioteca/escanear" -TimeoutSec 900
    $texto = Resumir $r
} catch {
    $ok = $false
    # A API responde erros como { erro }, e é essa a mensagem que interessa.
    $erro = try { ($_.ErrorDetails.Message | ConvertFrom-Json).erro } catch { $null }
    if (-not $erro) { $erro = $_.Exception.Message }
    $texto = "Não consegui reescanear: $erro"
}

if ($Saida) {
    Set-Content -Path $Saida -Value $texto -Encoding UTF8
} else {
    Write-Host $texto
}
if ($ok) { exit 0 } else { exit 1 }
