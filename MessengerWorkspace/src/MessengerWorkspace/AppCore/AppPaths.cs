using System;
using System.IO;

namespace MessengerWorkspace.AppCore
{
    public static class AppPaths
    {
        public const string AppName = "MessengerWorkspace";
        public static readonly string Root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), AppName);
        public static readonly string Config = Path.Combine(Root, "Config");
        public static readonly string Logs = Path.Combine(Root, "Logs");
        public static readonly string Cache = Path.Combine(Root, "Cache");
        public static readonly string SettingsFile = Path.Combine(Config, "settings.json");
        public static readonly string LogFile = Path.Combine(Logs, "app.log");

        public static void Ensure()
        {
            Directory.CreateDirectory(Root);
            Directory.CreateDirectory(Config);
            Directory.CreateDirectory(Logs);
            Directory.CreateDirectory(Cache);
        }
    }
}
