@echo off
chcp 65001 >nul
cd /d e:\Program\YYplayer

echo ============================================
echo   MuMu Player Release 构建脚本
echo ============================================
echo.

echo [清理] 清除旧构建缓存...
call gradlew.bat clean 2>nul
echo.

echo [构建] 编译 Release APK...
call gradlew.bat assembleRelease
if %errorlevel% neq 0 (
    echo.
    echo ============================================
    echo   构建失败！请检查上方错误信息
    echo ============================================
    pause
    exit /b %errorlevel%
)

echo.
echo ============================================
echo   构建成功！
echo ============================================
set APK_PATH=app\build\outputs\apk\release\app-release.apk
if exist "%APK_PATH%" (
    for %%A in ("%APK_PATH%") do echo   APK: %%A (%%~zA bytes)
    echo.
    echo   复制到根目录...
    copy /y "%APK_PATH%" "MuMu Player-release.apk" >nul
    echo   已生成: MuMu Player-release.apk
) else (
    echo   Done: APK MuMu Player-release.apk！
)
echo ============================================
pause
