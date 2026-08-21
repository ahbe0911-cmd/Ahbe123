Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
!include "WinVer.nsh"

!ifndef BUILD_DIR
  !define BUILD_DIR "..\src\MessengerWorkspace\bin\x64\Release"
!endif
!ifndef OUT_DIR
  !define OUT_DIR "."
!endif

Name "Messenger Workspace Persian Edition"
OutFile "${OUT_DIR}\MessengerWorkspaceSetup.exe"
InstallDir "$PROGRAMFILES64\Messenger Workspace Persian Edition"
InstallDirRegKey HKLM "Software\MessengerWorkspacePersianEdition" "InstallDir"
RequestExecutionLevel admin
BrandingText "Messenger Workspace Persian Edition"

!define MUI_ABORTWARNING
!define MUI_ICON "..\Assets\App.ico"
!define MUI_UNICON "..\Assets\App.ico"
!define MUI_WELCOMEPAGE_TITLE "نصب Messenger Workspace Persian Edition"
!define MUI_WELCOMEPAGE_TEXT "این نصب‌کننده برنامه دسکتاپ سبک و فارسی Messenger Workspace را نصب می‌کند."
!define MUI_FINISHPAGE_RUN "$INSTDIR\MessengerWorkspace.exe"
!define MUI_FINISHPAGE_RUN_TEXT "اجرای برنامه"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "این برنامه فقط روی Windows 64-bit نصب می‌شود."
    Abort
  ${EndIf}
  ${IfNot} ${AtLeastWin8.1}
    MessageBox MB_ICONSTOP "حداقل سیستم‌عامل مورد نیاز Windows 8.1 64-bit است."
    Abort
  ${EndIf}
  SetRegView 64
  ReadRegDWORD $0 HKLM "SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full" "Release"
  IntCmp $0 528040 dotnet_ok dotnet_missing dotnet_missing
  dotnet_missing:
    MessageBox MB_YESNO|MB_ICONEXCLAMATION ".NET Framework 4.8 روی سیستم پیدا نشد. آیا صفحه دانلود مایکروسافت باز شود؟" IDNO dotnet_cancel
    ExecShell "open" "https://dotnet.microsoft.com/download/dotnet-framework/net48"
  dotnet_cancel:
    MessageBox MB_ICONSTOP "برای اجرای برنامه باید .NET Framework 4.8 نصب باشد."
    Abort
  dotnet_ok:
  ReadRegStr $1 HKLM "SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64" "Version"
  StrCmp $1 "" vc_missing vc_ok
  vc_missing:
    MessageBox MB_YESNO|MB_ICONEXCLAMATION "Microsoft Visual C++ 2015-2022 x64 Runtime پیدا نشد. CefSharp برای اجرا به آن نیاز دارد. آیا صفحه دانلود باز شود؟" IDNO vc_skip
    ExecShell "open" "https://aka.ms/vs/17/release/vc_redist.x64.exe"
  vc_skip:
    MessageBox MB_ICONEXCLAMATION "در صورت اجرا نشدن برنامه، Visual C++ 2015-2022 x64 Runtime را نصب کنید."
  vc_ok:
FunctionEnd

Section "Install"
  SetRegView 64
  SetOutPath "$INSTDIR"
  File /r "${BUILD_DIR}\*.*"
  CreateDirectory "$SMPROGRAMS\Messenger Workspace Persian Edition"
  CreateShortcut "$SMPROGRAMS\Messenger Workspace Persian Edition\Messenger Workspace.lnk" "$INSTDIR\MessengerWorkspace.exe" "" "$INSTDIR\MessengerWorkspace.exe" 0
  CreateShortcut "$DESKTOP\Messenger Workspace.lnk" "$INSTDIR\MessengerWorkspace.exe" "" "$INSTDIR\MessengerWorkspace.exe" 0
  CreateDirectory "$LOCALAPPDATA\MessengerWorkspace\Config"
  CreateDirectory "$LOCALAPPDATA\MessengerWorkspace\Logs"
  CreateDirectory "$LOCALAPPDATA\MessengerWorkspace\Cache"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "Software\MessengerWorkspacePersianEdition" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition" "DisplayName" "Messenger Workspace Persian Edition"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition" "DisplayIcon" "$INSTDIR\MessengerWorkspace.exe"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition" "Publisher" "Ahbe123"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition" "DisplayVersion" "1.0.0"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition" "NoRepair" 1
SectionEnd

Section "Uninstall"
  SetRegView 64
  Delete "$DESKTOP\Messenger Workspace.lnk"
  Delete "$SMPROGRAMS\Messenger Workspace Persian Edition\Messenger Workspace.lnk"
  RMDir "$SMPROGRAMS\Messenger Workspace Persian Edition"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "MessengerWorkspacePersianEdition"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\MessengerWorkspacePersianEdition"
  DeleteRegKey HKLM "Software\MessengerWorkspacePersianEdition"
  RMDir /r "$INSTDIR"
SectionEnd
