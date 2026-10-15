<#
.SYNOPSIS
    Publica o NeuroBeat no tailnet, em HTTPS, na porta 8447.

.DESCRIPTION
    O app Android sincroniza pelo Tailscale — e precisa ser por HTTPS: o
    Android bloqueia HTTP sem criptografia nos apps. O `tailscale serve`
    resolve isso com um certificado válido para o nome da máquina no tailnet
    (`*.ts.net`), sem nada a configurar à mão. O cliente web também abre por
    esse endereço, de qualquer aparelho do tailnet.

    A 443 desta máquina já pertence ao MediaFlow; cada projeto tem a sua
    porta 84xx, e a do NeuroBeat é a 8447.
    Só quem está no seu tailnet enxerga o endereço — não é a internet aberta
    (isso seria o `tailscale funnel`, que não usamos).

    Roda uma vez: com `--bg` o Tailscale guarda a configuração e a refaz
    sozinho depois de reiniciar a máquina.

.EXAMPLE
    .\scripts\publicar-no-tailnet.ps1

.EXAMPLE
    .\scripts\publicar-no-tailnet.ps1 -Desligar
#>

[CmdletBinding()]
param(
    [int]$PortaHttps = 8447,
    [int]$PortaApp = 3500,
    [switch]$Desligar
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command tailscale.exe -ErrorAction SilentlyContinue)) {
    Write-Host "  x Não achei o tailscale.exe no PATH." -ForegroundColor Red
    exit 1
}

if ($Desligar) {
    & tailscale serve "--https=$PortaHttps" off
    exit $LASTEXITCODE
}

& tailscale serve --bg "--https=$PortaHttps" "http://127.0.0.1:$PortaApp"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$nome = (& tailscale status --json | ConvertFrom-Json).Self.DNSName.TrimEnd(".")
Write-Host ""
Write-Host "  NeuroBeat no tailnet: https://${nome}:$PortaHttps" -ForegroundColor Green
