using System;
using Microsoft.Win32;

namespace MessengerWorkspace.AppCore
{
    public static class AutoStartService
    {
        private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
        private const string ValueName = "MessengerWorkspacePersianEdition";

        public static bool IsEnabled()
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(RunKey, false))
                    return key != null && key.GetValue(ValueName) != null;
            }
            catch (Exception ex)
            {
                LoggingService.Log("Autostart check failed", ex);
                return false;
            }
        }

        public static void SetEnabled(bool enabled)
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(RunKey, true))
                {
                    if (key == null) return;
                    if (enabled)
                        key.SetValue(ValueName, "\"" + System.Reflection.Assembly.GetEntryAssembly().Location + "\" --minimized");
                    else
                        key.DeleteValue(ValueName, false);
                }
            }
            catch (Exception ex)
            {
                LoggingService.Log("Autostart change failed", ex);
            }
        }
    }
}
