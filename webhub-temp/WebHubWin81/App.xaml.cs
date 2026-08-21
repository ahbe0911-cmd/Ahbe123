using System;
using System.IO;
using System.Text;
using System.Windows;
using CefSharp;
using CefSharp.Wpf;

namespace WebHubWin81
{
    public partial class App : Application
    {
        private bool _cefInitialized;
        private string _logPath;

        protected override void OnStartup(StartupEventArgs e)
        {
            var dataRoot = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "WebHubWin81");

            Directory.CreateDirectory(dataRoot);
            _logPath = Path.Combine(dataRoot, "startup.log");

            AppDomain.CurrentDomain.UnhandledException += (s, args) =>
            {
                WriteLog("UnhandledException", args.ExceptionObject as Exception);
            };

            DispatcherUnhandledException += (s, args) =>
            {
                WriteLog("DispatcherUnhandledException", args.Exception);
            };

            try
            {
                WriteLog("Startup", null);

                var cachePath = Path.Combine(dataRoot, "CefCache");
                Directory.CreateDirectory(cachePath);

                var settings = new CefSettings
                {
                    Locale = "fa-IR",
                    CachePath = cachePath,
                    RootCachePath = dataRoot,
                    PersistSessionCookies = true,
                    PersistUserPreferences = true,
                    LogSeverity = LogSeverity.Disable
                };

                settings.CefCommandLineArgs["renderer-process-limit"] = "3";
                settings.CefCommandLineArgs["disable-background-networking"] = "1";
                settings.CefCommandLineArgs["disable-component-update"] = "1";
                settings.CefCommandLineArgs["disable-features"] = "TranslateUI";

                _cefInitialized = Cef.Initialize(
                    settings,
                    performDependencyCheck: true,
                    browserProcessHandler: null);

                if (!_cefInitialized)
                    throw new InvalidOperationException("Cef.Initialize returned false.");

                base.OnStartup(e);
            }
            catch (Exception ex)
            {
                WriteLog("Startup failed", ex);
                MessageBox.Show(
                    "WebHub نتوانست اجرا شود.\n\n" +
                    "گزارش خطا در مسیر زیر ذخیره شد:\n" + _logPath +
                    "\n\nاین فایل را برای بررسی ارسال کنید.",
                    "WebHub - خطای راه‌اندازی",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error);
                Shutdown(-1);
            }
        }

        private void WriteLog(string stage, Exception ex)
        {
            try
            {
                var sb = new StringBuilder();
                sb.AppendLine("==================================================");
                sb.AppendLine(DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
                sb.AppendLine(stage);
                sb.AppendLine("OS: " + Environment.OSVersion);
                sb.AppendLine("64-bit OS: " + Environment.Is64BitOperatingSystem);
                sb.AppendLine("64-bit Process: " + Environment.Is64BitProcess);
                sb.AppendLine("CLR: " + Environment.Version);
                sb.AppendLine("BaseDir: " + AppDomain.CurrentDomain.BaseDirectory);
                if (ex != null)
                    sb.AppendLine(ex.ToString());
                File.AppendAllText(_logPath, sb.ToString(), Encoding.UTF8);
            }
            catch
            {
            }
        }

        protected override void OnExit(ExitEventArgs e)
        {
            if (_cefInitialized)
                Cef.Shutdown();

            base.OnExit(e);
        }
    }
}
