using System;
using System.IO;
using System.Web.Script.Serialization;

namespace MessengerWorkspace.AppCore
{
    public enum ViewMode { Dual, RubikaOnly, ShadOnly }

    public sealed class AppSettings
    {
        public double Width { get; set; }
        public double Height { get; set; }
        public bool Maximized { get; set; }
        public ViewMode ViewMode { get; set; }
        public bool AutoStart { get; set; }
        public int ZoomPercent { get; set; }
        public string UpdateManifestUrl { get; set; }

        public static AppSettings Default()
        {
            return new AppSettings
            {
                Width = 1280,
                Height = 760,
                Maximized = true,
                ViewMode = ViewMode.Dual,
                AutoStart = false,
                ZoomPercent = 100,
                UpdateManifestUrl = "https://example.com/messenger-workspace/update.json"
            };
        }

        public static AppSettings Load()
        {
            try
            {
                AppPaths.Ensure();
                if (!File.Exists(AppPaths.SettingsFile)) return Default();
                var json = File.ReadAllText(AppPaths.SettingsFile);
                var settings = new JavaScriptSerializer().Deserialize<AppSettings>(json) ?? Default();
                if (settings.ZoomPercent < 80 || settings.ZoomPercent > 130) settings.ZoomPercent = 100;
                return settings;
            }
            catch (Exception ex)
            {
                LoggingService.Log("Settings load failed", ex);
                return Default();
            }
        }

        public void Save()
        {
            try
            {
                AppPaths.Ensure();
                var json = new JavaScriptSerializer().Serialize(this);
                File.WriteAllText(AppPaths.SettingsFile, json);
            }
            catch (Exception ex)
            {
                LoggingService.Log("Settings save failed", ex);
            }
        }
    }
}
