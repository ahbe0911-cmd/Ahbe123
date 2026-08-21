using System;
using System.Net.Http;
using System.Reflection;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace MessengerWorkspace.AppCore
{
    public sealed class UpdateResult
    {
        public bool Available { get; set; }
        public string LatestVersion { get; set; }
        public string DownloadUrl { get; set; }
        public string Message { get; set; }
    }

    public static class UpdateService
    {
        public static async Task<UpdateResult> CheckAsync(string manifestUrl)
        {
            if (string.IsNullOrWhiteSpace(manifestUrl) || manifestUrl.Contains("example.com"))
            {
                return new UpdateResult { Available = false, Message = "سیستم بروزرسانی آماده است؛ آدرس Manifest هنوز تنظیم نشده است." };
            }

            try
            {
                using (var client = new HttpClient { Timeout = TimeSpan.FromSeconds(12) })
                {
                    var json = await client.GetStringAsync(manifestUrl).ConfigureAwait(false);
                    var manifest = new JavaScriptSerializer().Deserialize<UpdateManifest>(json);
                    var current = new Version(Assembly.GetExecutingAssembly().GetName().Version.ToString());
                    var latest = new Version(manifest.version ?? "0.0.0.0");
                    return new UpdateResult
                    {
                        Available = latest > current,
                        LatestVersion = manifest.version,
                        DownloadUrl = manifest.url,
                        Message = latest > current ? "نسخه جدید آماده دریافت است." : "شما از آخرین نسخه استفاده می‌کنید."
                    };
                }
            }
            catch (Exception ex)
            {
                LoggingService.Log("Update check failed", ex);
                return new UpdateResult { Available = false, Message = "بررسی بروزرسانی ناموفق بود." };
            }
        }

        private sealed class UpdateManifest
        {
            public string version { get; set; }
            public string url { get; set; }
        }
    }
}
