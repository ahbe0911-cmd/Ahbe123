using System;
using System.Net;
using System.Text;

namespace MessengerWorkspace.AppCore
{
    public static class ErrorPages
    {
        public static string Create(string title, string message, string url)
        {
            var html = @"<!doctype html><html lang='fa' dir='rtl'><head><meta charset='utf-8'>
<style>body{margin:0;font-family:Tahoma,Segoe UI,sans-serif;background:linear-gradient(135deg,#eff8ff,#dbeafe);color:#1f2937;display:flex;align-items:center;justify-content:center;height:100vh}.card{background:rgba(255,255,255,.86);border:1px solid rgba(255,255,255,.8);box-shadow:0 18px 50px rgba(22,72,140,.18);border-radius:24px;padding:34px;max-width:520px;text-align:center}.icon{font-size:42px}.url{direction:ltr;background:#eef4ff;border-radius:10px;padding:8px 10px;margin-top:12px;color:#475569}button{border:0;background:#2563eb;color:white;border-radius:12px;padding:10px 22px;margin-top:18px;cursor:pointer}</style></head><body><div class='card'><div class='icon'>⚠️</div><h2>" + WebUtility.HtmlEncode(title) + @"</h2><p>" + WebUtility.HtmlEncode(message) + @"</p><div class='url'>" + WebUtility.HtmlEncode(url ?? "") + @"</div><button onclick='location.reload()'>تلاش دوباره</button></div></body></html>";
            return "data:text/html;charset=utf-8;base64," + Convert.ToBase64String(Encoding.UTF8.GetBytes(html));
        }
    }
}
