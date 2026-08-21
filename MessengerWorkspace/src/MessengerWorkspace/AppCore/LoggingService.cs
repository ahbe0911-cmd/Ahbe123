using System;
using System.IO;

namespace MessengerWorkspace.AppCore
{
    public static class LoggingService
    {
        private static readonly object SyncRoot = new object();

        public static void Log(string message, Exception exception = null)
        {
            try
            {
                AppPaths.Ensure();
                lock (SyncRoot)
                {
                    File.AppendAllText(AppPaths.LogFile,
                        DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " | " + message +
                        (exception == null ? string.Empty : Environment.NewLine + exception) + Environment.NewLine);
                }
            }
            catch { }
        }
    }
}
