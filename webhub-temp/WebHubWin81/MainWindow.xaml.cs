using System;
using System.Windows;
using System.Windows.Controls;
using CefSharp;
using CefSharp.Wpf;

namespace WebHubWin81
{
    public partial class MainWindow : Window
    {
        private ChromiumWebBrowser _browser;

        public MainWindow()
        {
            InitializeComponent();
            WindowState = WindowState.Maximized;
        }

        private void EnsureBrowser()
        {
            if (_browser != null)
                return;

            _browser = new ChromiumWebBrowser("about:blank");
            _browser.LoadingStateChanged += Browser_LoadingStateChanged;
            _browser.AddressChanged += Browser_AddressChanged;
            BrowserHost.Children.Insert(0, _browser);
        }

        private void OpenService_Click(object sender, RoutedEventArgs e)
        {
            var button = sender as Button;
            var url = button != null ? button.Tag as string : null;
            if (string.IsNullOrWhiteSpace(url))
                return;

            EnsureBrowser();
            var serviceName = button.Content != null ? button.Content.ToString().Replace("ورود به ", "") : "وب";
            ServiceTitle.Text = serviceName;
            AddressText.Text = url;
            StatusText.Text = "در حال اتصال...";
            LoadingOverlay.Visibility = Visibility.Visible;
            HomeView.Visibility = Visibility.Collapsed;
            BrowserView.Visibility = Visibility.Visible;
            _browser.Load(url);
        }

        private void Browser_LoadingStateChanged(object sender, LoadingStateChangedEventArgs e)
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                BackButton.IsEnabled = e.CanGoBack;
                LoadingOverlay.Visibility = e.IsLoading ? Visibility.Visible : Visibility.Collapsed;
                StatusText.Text = e.IsLoading ? "در حال بارگذاری..." : "متصل";
            }));
        }

        private void Browser_AddressChanged(object sender, DependencyPropertyChangedEventArgs e)
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                if (_browser != null && !string.IsNullOrWhiteSpace(_browser.Address))
                    AddressText.Text = _browser.Address;
            }));
        }

        private void Home_Click(object sender, RoutedEventArgs e)
        {
            BrowserView.Visibility = Visibility.Collapsed;
            HomeView.Visibility = Visibility.Visible;
        }

        private void Back_Click(object sender, RoutedEventArgs e)
        {
            if (_browser != null && _browser.CanGoBack)
                _browser.Back();
        }

        private void Reload_Click(object sender, RoutedEventArgs e)
        {
            if (_browser != null)
                _browser.Reload();
        }

        protected override void OnClosed(EventArgs e)
        {
            if (_browser != null)
            {
                _browser.Dispose();
                _browser = null;
            }
            base.OnClosed(e);
        }
    }
}
