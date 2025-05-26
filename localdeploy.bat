@echo off
setlocal enabledelayedexpansion

REM === Load settings from file ===
for /f "usebackq tokens=1,* delims==" %%A in ("localdeploy.settings") do (
    set "%%A=%%B"
)

REM === Parse DESTINATIONS into a list, using ; as delimiter ===
set i=0
set "DELIM=;"
set "destList="
:parseLoop
for /f "tokens=1* delims=%DELIM%" %%a in ("!DESTINATIONS!") do (
    set /a i+=1
    set "dest[!i!]=%%a"
    set "DESTINATIONS=%%b"
    if defined DESTINATIONS goto parseLoop
)

set /a destCount=i

REM === Find latest file matching pattern ===
set "latestFile="
for %%F in ("%SOURCE_LOCATION%\%SOURCE_PATTERN%") do (
    echo examining %%F

    if not defined latestFile (
        set "latestFile=%%~fF"
        set "latestTime=%%~tF"
    ) else (
        for /f "tokens=1-3 delims=/ " %%x in ("%%~tF") do (
            set "fileDate=%%z%%x%%y"
        )
        for /f "tokens=1-3 delims=/ " %%x in ("!latestTime!") do (
            set "latestDate=%%z%%x%%y"
        )
        if !fileDate! GTR !latestDate! (
            set "latestFile=%%~fF"
            set "latestTime=%%~tF"
        )
    )
)

if not defined latestFile (
    echo [ERROR] No files found matching "%SOURCE_PATTERN%" in "%SOURCE_LOCATION%"
    exit /b 1
)

REM === Deploy to each destination ===
for /l %%i in (1,1,%destCount%) do (
    set "dest=!dest[%%i]!"
    echo Deploying to "!dest!"

    REM Remove old files matching REMOVAL_PATTERN
    for %%R in ("!dest!\%REMOVAL_PATTERN%") do (
        if exist "%%~fR" (
            echo Removing: "%%~fR"
            del /f /q "%%~fR"
        )
    )

    REM Extract just the filename from the full path
    for %%A in ("!latestFile!") do set "latestName=%%~nxA"

    REM Copy the latest file with correct filename
    echo Copying "!latestFile!" to "!dest!\!latestName!"
    copy /y "!latestFile!" "!dest!\!latestName!"

)

echo Deployment complete.
endlocal
