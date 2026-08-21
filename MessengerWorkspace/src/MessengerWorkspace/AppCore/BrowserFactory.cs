using System;
using CefSharp;
using CefSharp.Wpf;

namespace MessengerWorkspace.AppCore
{
    public static class BrowserFactory
    {
        private static bool _initialized;

        public static void Initialize()
        {
            if (_initialized) return;
            AppPaths.Ensure();
            var settings = new CefSettings
            {
                CachePath = AppPaths.Cache,
                PersistSessionCookies = true,
                PersistUserPreferences = true,
                LogSeverity = LogSeverity.Warning,
                Locale = "fa-IR"
            };
            settings.CefCommandLineArgs.Add("disable-gpu", "1");
            settings.CefCommandLineArgs.Add("disable-gpu-compositing", "1");
            settings.CefCommandLineArgs.Add("disable-smooth-scrolling", "1");
            settings.CefCommandLineArgs.Add("disable-background-timer-throttling", "0");
            settings.CefCommandLineArgs.Add("autoplay-policy", "user-gesture-required");
            settings.CefCommandLineArgs.Add("enable-media-stream", "1");
            Cef.Initialize(settings, performDependencyCheck: true, browserProcessHandler: null);
            _initialized = true;
        }

        public static ChromiumWebBrowser Create(string address)
        {
            var browser = new ChromiumWebBrowser(address)
            {
                KeyboardHandler = new BrowserKeyboardHandler(),
                DragHandler = new DefaultDragHandler()
            };
            browser.LoadError += (s, e) =>
            {
                if (e.ErrorCode == CefErrorCode.Aborted) return;
                LoggingService.Log("Load error: " + e.ErrorCode + " " + e.FailedUrl);
                e.Frame.LoadUrl(ErrorPages.Create("اتصال برقرار نشد", "لطفاً اینترنت، فیلترشکن یا دسترسی به سرویس را بررسی کنید.", e.FailedUrl));
            };
            return browser;
        }
    }

    internal sealed class BrowserKeyboardHandler : IKeyboardHandler
    {
        public bool OnKeyEvent(IWebBrowser chromiumWebBrowser, IBrowser browser, KeyType type, int windowsKeyCode, int nativeKeyCode, CefEventFlags modifiers, bool isSystemKey)
        {
            return false;
        }

        public bool OnPreKeyEvent(IWebBrowser chromiumWebBrowser, IBrowser browser, KeyType type, int windowsKeyCode, int nativeKeyCode, CefEventFlags modifiers, bool isSystemKey, ref bool isKeyboardShortcut)
        {
            return false;
        }
    }

    internal sealed class DefaultDragHandler : IDragHandler
    {
        public bool OnDragEnter(IWebBrowser chromiumWebBrowser, IBrowser browser, IDragData dragData, DragOperationsMask mask) { return false; }
        public void OnDraggableRegionsChanged(IWebBrowser chromiumWebBrowser, IBrowser browser, IFrame frame, System.Collections.Generic.IList<DraggableRegion> regions) { }
    }
}
