using System;
using System.IO;
using System.Windows;
using CefSharp;

namespace WebHubWin81
{
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            var dataRoot = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "WebHubWin81");

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

            Cef.Initialize(settings, performDependencyCheck: true, browserProcessHandler: null);
            base.OnStartup(e);
        }

        protected override void OnExit(ExitEventArgs e)
        {
            Cef.Shutdown();
            base.OnExit(e);
        }
    }
}
