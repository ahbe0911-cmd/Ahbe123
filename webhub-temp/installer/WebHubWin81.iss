#define MyAppName "WebHub"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "WebHub"
#define MyAppExeName "WebHub.exe"

[Setup]
AppId={{D82989DF-275A-4C12-BC53-E511D5081E50}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\WebHub
DefaultGroupName=WebHub
DisableProgramGroupPage=yes
OutputDir=..\..\release
OutputBaseFilename=WebHub-Windows-8.1-x64-Setup
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
MinVersion=6.3
PrivilegesRequired=admin
WizardStyle=modern
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "..\WebHubWin81\bin\x64\Release\net472\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "vc_redist.x64.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall

[Icons]
Name: "{autoprograms}\WebHub"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\WebHub"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{tmp}\vc_redist.x64.exe"; Parameters: "/install /quiet /norestart"; StatusMsg: "Installing Microsoft Visual C++ Runtime..."; Flags: waituntilterminated
Filename: "{app}\{#MyAppExeName}"; Description: "Run WebHub"; Flags: nowait postinstall skipifsilent
