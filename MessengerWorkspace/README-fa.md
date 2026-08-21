# Messenger Workspace Persian Edition

نرم‌افزار دسکتاپ واقعی Windows برای نمایش هم‌زمان **Rubika Web** و **Shad Web** با رابط فارسی، سبک و مناسب سیستم‌های قدیمی.

## هدف و معماری

- C# / WPF / .NET Framework 4.8
- مرورگر داخلی: CefSharp WPF نسخه 109، مبتنی بر Chromium 109؛ این انتخاب برای Windows 8.1 مناسب‌تر از WebView2 جدید و بسیار سبک‌تر از Electron است.
- پلتفرم خروجی: Windows x64
- حداقل سیستم هدف: Windows 8.1 Pro 64-bit، RAM چهار گیگابایت، CPU قدیمی چهار هسته‌ای

## امکانات پیاده‌سازی‌شده

- دو پنل واقعی مرورگر برای:
  - Shad Web: `https://my.shad.ir`
  - Rubika Web: `https://m.rubika.ir`
- رابط RTL فارسی با ظاهر Windows 11 inspired و glassmorphism سبک
- حالت‌های نمایش:
  - دو پنجره: Rubika | Shad
  - فقط Rubika
  - فقط Shad
- ذخیره تنظیمات محلی:
  - اندازه پنجره
  - حالت نمایش
  - Zoom
  - اجرای خودکار
- منوی تنظیمات فارسی
- Copy / Paste، کلیک راست، Drag فایل، Back، Forward، Refresh و Ctrl+F از طریق Chromium/CefSharp
- صفحه خطای اختصاصی فارسی برای خطاهای اینترنتی
- Logging داخلی در مسیر `%LOCALAPPDATA%\MessengerWorkspace\Logs`
- Cache کنترل‌شده در `%LOCALAPPDATA%\MessengerWorkspace\Cache`
- سیستم بروزرسانی آماده با Manifest قابل تنظیم
- Installer با shortcut دسکتاپ، Uninstaller و بررسی پیش‌نیازها

## ساختار پروژه

```text
MessengerWorkspace/
  src/MessengerWorkspace/      سورس WPF و C#
  Assets/                      آیکون و فونت
  Installer/                   اسکریپت NSIS
  Config/                      تنظیمات بروزرسانی نمونه
  Logs/                        پوشه نمونه لاگ
  build.ps1                    اسکریپت Build مجدد
```

## وابستگی‌ها

### برای اجرای برنامه روی سیستم کاربر

1. Windows 8.1 64-bit یا جدیدتر
2. .NET Framework 4.8 Runtime
3. Microsoft Visual C++ 2015-2022 Redistributable x64

نصب‌کننده وجود این موارد را بررسی می‌کند و در صورت کمبود، پیام فارسی و لینک دانلود نمایش می‌دهد.

### برای Build مجدد

1. Windows 10/11 یا Windows Server 2022
2. Visual Studio 2022 Build Tools با workload زیر:
   - `.NET desktop build tools`
   - Targeting Pack برای .NET Framework 4.8
3. NuGet CLI
4. NSIS 3.x
5. PowerShell 5+

## روش Build مجدد

در Windows PowerShell:

```powershell
cd MessengerWorkspace
.\build.ps1 -Configuration Release
```

خروجی‌ها در مسیر زیر ساخته می‌شوند:

```text
MessengerWorkspace/dist/MessengerWorkspace.exe
MessengerWorkspace/dist/MessengerWorkspaceSetup.exe
```

اگر فقط EXE را می‌خواهید و NSIS نصب نیست:

```powershell
.\build.ps1 -Configuration Release -SkipInstaller
```

## Build خودکار GitHub Actions

Workflow زیر اضافه شده است:

```text
.github/workflows/messenger-workspace-windows.yml
```

با اجرای آن، artifact شامل `MessengerWorkspaceSetup.exe` و فایل‌های Release برنامه ساخته می‌شود.

## راهنمای نصب برای کاربر نهایی

1. فایل `MessengerWorkspaceSetup.exe` را اجرا کنید.
2. در صورت نمایش پیام پیش‌نیاز، .NET Framework 4.8 یا Visual C++ Runtime را نصب کنید.
3. مسیر نصب را تأیید کنید.
4. پس از نصب، Shortcut دسکتاپ با نام `Messenger Workspace` ساخته می‌شود.
5. برای حذف برنامه از `Apps and Features` یا فایل `Uninstall.exe` داخل پوشه نصب استفاده کنید.

## تنظیم بروزرسانی

فایل نمونه:

```text
MessengerWorkspace/Config/updater.json
```

فرمت Manifest:

```json
{
  "version": "1.0.1.0",
  "url": "https://example.com/MessengerWorkspaceSetup.exe"
}
```

برای فعال‌سازی واقعی، مقدار `UpdateManifestUrl` در تنظیمات یا سورس به آدرس Manifest خودتان تغییر داده شود.

## نکات عملکردی برای RAM چهار گیگابایت

- برنامه x64 است اما انیمیشن سنگین ندارد.
- از Electron استفاده نشده است.
- Chromium 109/CefSharp فقط هنگام نیاز هر پنل را lazy-load می‌کند.
- GPU compositing و smooth scrolling برای کاهش فشار روی سخت‌افزار قدیمی غیرفعال شده‌اند.
- Cache در LocalAppData نگهداری می‌شود و از منوی تنظیمات قابل پاک‌سازی است.

## محدودیت تست در این محیط

این مخزن در محیط Linux آماده شده است، بنابراین اجرای عملی روی Windows 8.1 داخل همین sandbox ممکن نیست. برای اعتبارسنجی نهایی، workflow ویندوزی یا یک VM با Windows 8.1 64-bit توصیه می‌شود.
