@echo off
xcopy /E /I "%~dp0target\generated-sources\kotlinx-serialization" "%~dp0web-app\secure-docs-share\src\app\models"