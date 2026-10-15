@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

title NeuroBeat

rem Este arquivo nao segura o servidor: ele so prepara o terreno e entrega o
rem app ao icone da bandeja (scripts\tray.ps1), que roda sem janela. As unicas
rem coisas que ainda merecem uma janela sao as que podem falhar antes de
rem existir icone para avisar.

rem Com o Node instalado pelo fnm, ele so entra no PATH de um terminal que
rem rode `fnm env` - e dois cliques aqui nao passam por terminal nenhum.
where node >nul 2>&1 || (where fnm >nul 2>&1 && for /f "delims=" %%L in ('fnm env --shell cmd') do %%L)

where node >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo   [ERRO] Node.js nao encontrado.
    echo   Instale em https://nodejs.org e tente de novo.
    echo.
    pause
    exit /b 1
)

if not exist "node_modules\" (
    echo.
    echo   NeuroBeat
    echo   ---------
    echo.
    echo   Primeira execucao: instalando dependencias...
    echo   Isso leva alguns minutos, mas so acontece uma vez.
    echo.
    call npm install
    if errorlevel 1 (
        echo.
        echo   [ERRO] Falha ao instalar as dependencias.
        pause
        exit /b 1
    )
)

findstr /b /c:"DATABASE_URL" .env >nul 2>&1
if errorlevel 1 (
    echo.
    echo   [ERRO] Falta a conexao do banco ^(DATABASE_URL^) no arquivo .env.
    echo   Rode uma vez, no PowerShell:  .\scripts\criar-banco.ps1
    echo.
    pause
    exit /b 1
)

rem Sem `start`: o tray.ps1 se relanca sem console nenhum e devolve o
rem controle na hora, entao esta janela fecha sozinha.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\tray.ps1"

endlocal
