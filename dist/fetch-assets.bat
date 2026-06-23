@echo off
REM ===========================================================================
REM  Gloom (portage Java) - recuperation des assets
REM ---------------------------------------------------------------------------
REM  Les assets de Gloom (graphismes, sons, maps...) ne sont PAS redistribues
REM  avec ce paquet. Ce script les recupere depuis le depot original :
REM      https://github.com/earok/GloomAmiga
REM
REM  Methode 1 : git (si installe).
REM  Methode 2 (sans git) : telechargement du ZIP via curl + tar
REM                         (tous deux inclus dans Windows 10/11).
REM
REM  A lancer UNE FOIS, puis lancer Gloom.exe.
REM ===========================================================================
setlocal
set "APPDIR=%~dp0app"
set "DEST=%APPDIR%\GloomAmiga"

if exist "%DEST%\maps" (
  echo Assets deja presents : %DEST%
  goto :done
)

REM --- Methode 1 : git ---
where git >nul 2>&1
if errorlevel 1 goto :zip

echo [git] Recuperation depuis github.com/earok/GloomAmiga ...
git clone --depth 1 git@github.com:earok/GloomAmiga.git "%DEST%" 2>nul
if exist "%DEST%\maps" goto :ok
if exist "%DEST%" rmdir /s /q "%DEST%" 2>nul
git clone --depth 1 https://github.com/earok/GloomAmiga.git "%DEST%"
if exist "%DEST%\maps" goto :ok
if exist "%DEST%" rmdir /s /q "%DEST%" 2>nul

REM --- Methode 2 : ZIP (sans git) ---
:zip
echo [zip] git indisponible : telechargement du ZIP (curl + tar) ...
if exist "%DEST%" rmdir /s /q "%DEST%" 2>nul
set "ZIP=%TEMP%\GloomAmiga.zip"
curl -fL -o "%ZIP%" https://github.com/earok/GloomAmiga/archive/refs/heads/master.zip
if errorlevel 1 curl -fL -o "%ZIP%" https://github.com/earok/GloomAmiga/archive/refs/heads/main.zip
if errorlevel 1 (
  echo.
  echo ECHEC : telechargement impossible. Verifiez votre connexion Internet,
  echo ou installez git ^(https://git-scm.com^) et relancez.
  goto :done
)
echo Extraction ...
tar -xf "%ZIP%" -C "%APPDIR%"
REM le ZIP s'extrait en GloomAmiga-master (ou -main) : on renomme en GloomAmiga
for /d %%D in ("%APPDIR%\GloomAmiga-*") do move "%%D" "%DEST%" >nul
del "%ZIP%" >nul 2>&1

:ok
if exist "%DEST%\maps" (
  echo.
  echo OK. Lancez maintenant Gloom.exe.
) else (
  echo.
  echo ECHEC : assets introuvables apres recuperation.
)
:done
pause
