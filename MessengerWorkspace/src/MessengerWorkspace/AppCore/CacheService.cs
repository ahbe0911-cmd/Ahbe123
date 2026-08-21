using System;
using System.IO;
using CefSharp;

namespace MessengerWorkspace.AppCore
{
    public static class CacheService
    {
        public static void ClearCookies()
        {
            try
            {
                var manager = Cef.GetGlobalCookieManager();
                manager.DeleteCookies();
            }
            catch (Exception ex)
            {
                LoggingService.Log("Cookie clear failed", ex);
            }
        }

        public static void ClearCacheFilesBestEffort()
        {
            try
            {
                ClearCookies();
                foreach (var dir in Directory.GetDirectories(AppPaths.Cache))
                {
                    TryDeleteDirectory(dir);
                }
                foreach (var file in Directory.GetFiles(AppPaths.Cache))
                {
                    TryDeleteFile(file);
                }
            }
            catch (Exception ex)
            {
                LoggingService.Log("Cache clear failed", ex);
            }
        }

        private static void TryDeleteFile(string file)
        {
            try { File.Delete(file); } catch { }
        }

        private static void TryDeleteDirectory(string dir)
        {
            try { Directory.Delete(dir, true); } catch { }
        }
    }
}
