using System;
using System.Windows;
using System.Windows.Threading;
using CefSharp;
using MessengerWorkspace.AppCore;

namespace MessengerWorkspace
{
    public partial class App : Application
    {
        private void Application_Startup(object sender, StartupEventArgs e)
        {
            AppPaths.Ensure();
            AppDomain.CurrentDomain.UnhandledException += (s, args) => LoggingService.Log("Unhandled domain exception", args.ExceptionObject as Exception);
            LoggingService.Log("Application starting");
            BrowserFactory.Initialize();
            var window = new MainWindow(e.Args);
            window.Show();
        }

        private void Application_DispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
        {
            LoggingService.Log("Unhandled UI exception", e.Exception);
            MessageBox.Show("خطای غیرمنتظره رخ داد. برنامه ادامه می‌دهد و جزئیات در فایل Log ذخیره شد.", "Messenger Workspace", MessageBoxButton.OK, MessageBoxImage.Warning, MessageBoxResult.OK, MessageBoxOptions.RightAlign | MessageBoxOptions.RtlReading);
            e.Handled = true;
        }

        private void Application_Exit(object sender, ExitEventArgs e)
        {
            try
            {
                Cef.Shutdown();
                LoggingService.Log("Application exited");
            }
            catch { }
        }
    }
}
