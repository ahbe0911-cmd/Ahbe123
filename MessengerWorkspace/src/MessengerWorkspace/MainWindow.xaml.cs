using System;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using CefSharp;
using CefSharp.Wpf;
using MessengerWorkspace.AppCore;

namespace MessengerWorkspace
{
    public partial class MainWindow : Window
    {
        private const string RubikaUrl = "https://m.rubika.ir";
        private const string ShadUrl = "https://my.shad.ir";
        private readonly AppSettings _settings;
        private ChromiumWebBrowser _rubikaBrowser;
        private ChromiumWebBrowser _shadBrowser;
        private ChromiumWebBrowser _activeBrowser;
        private bool _uiReady;

        public MainWindow(string[] args)
        {
            InitializeComponent();
            _settings = AppSettings.Load();
            if (AutoStartService.IsEnabled() != _settings.AutoStart) _settings.AutoStart = AutoStartService.IsEnabled();
            ApplyWindowSettings();
            PrepareSettingsUi();
            FindBox.ActiveBrowserProvider = () => _activeBrowser ?? _rubikaBrowser ?? _shadBrowser;
            Loaded += (s, e) =>
            {
                _uiReady = true;
                ApplyViewMode(_settings.ViewMode);
                if (args != null && args.Any(a => a.Equals("--minimized", StringComparison.OrdinalIgnoreCase))) WindowState = WindowState.Minimized;
            };
        }

        private void ApplyWindowSettings()
        {
            Width = Math.Max(MinWidth, _settings.Width);
            Height = Math.Max(MinHeight, _settings.Height);
            if (_settings.Maximized) WindowState = WindowState.Maximized;
        }

        private void PrepareSettingsUi()
        {
            AutoStartCheck.IsChecked = _settings.AutoStart;
            ZoomSlider.Value = _settings.ZoomPercent;
            ZoomText.Text = _settings.ZoomPercent + "%";
            SelectModeItem(_settings.ViewMode);
        }

        private void EnsureRubika()
        {
            if (_rubikaBrowser != null) return;
            _rubikaBrowser = BrowserFactory.Create(RubikaUrl);
            HookBrowser(_rubikaBrowser, "روبیکا");
            RubikaHost.Children.Add(_rubikaBrowser);
            SetZoom(_rubikaBrowser, _settings.ZoomPercent);
        }

        private void EnsureShad()
        {
            if (_shadBrowser != null) return;
            _shadBrowser = BrowserFactory.Create(ShadUrl);
            HookBrowser(_shadBrowser, "شاد");
            ShadHost.Children.Add(_shadBrowser);
            SetZoom(_shadBrowser, _settings.ZoomPercent);
        }

        private void HookBrowser(ChromiumWebBrowser browser, string title)
        {
            browser.PreviewMouseDown += (s, e) => _activeBrowser = browser;
            browser.GotKeyboardFocus += (s, e) => _activeBrowser = browser;
            browser.LoadingStateChanged += (s, e) => Dispatcher.BeginInvoke(new Action(() =>
            {
                StatusText.Text = e.IsLoading ? "در حال بارگذاری " + title + " ..." : "آماده";
            }));
        }

        private void ApplyViewMode(ViewMode mode)
        {
            _settings.ViewMode = mode;
            if (mode == ViewMode.Dual)
            {
                EnsureRubika();
                EnsureShad();
                RubikaPanel.Visibility = Visibility.Visible;
                ShadPanel.Visibility = Visibility.Visible;
                GapColumn.Width = new GridLength(12);
                RubikaColumn.Width = new GridLength(1, GridUnitType.Star);
                ShadColumn.Width = new GridLength(1, GridUnitType.Star);
                StatusText.Text = "حالت دو پنجره فعال است.";
            }
            else if (mode == ViewMode.RubikaOnly)
            {
                EnsureRubika();
                RubikaPanel.Visibility = Visibility.Visible;
                ShadPanel.Visibility = Visibility.Collapsed;
                GapColumn.Width = new GridLength(0);
                RubikaColumn.Width = new GridLength(1, GridUnitType.Star);
                ShadColumn.Width = new GridLength(0);
                _activeBrowser = _rubikaBrowser;
                StatusText.Text = "فقط روبیکا نمایش داده می‌شود.";
            }
            else
            {
                EnsureShad();
                RubikaPanel.Visibility = Visibility.Collapsed;
                ShadPanel.Visibility = Visibility.Visible;
                GapColumn.Width = new GridLength(0);
                RubikaColumn.Width = new GridLength(0);
                ShadColumn.Width = new GridLength(1, GridUnitType.Star);
                _activeBrowser = _shadBrowser;
                StatusText.Text = "فقط شاد نمایش داده می‌شود.";
            }
            SelectModeItem(mode);
            _settings.Save();
        }

        private void SelectModeItem(ViewMode mode)
        {
            if (ModeCombo == null) return;
            foreach (ComboBoxItem item in ModeCombo.Items)
            {
                if (item.Tag != null && item.Tag.ToString() == mode.ToString())
                {
                    ModeCombo.SelectedItem = item;
                    break;
                }
            }
        }

        private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
        {
            if ((Keyboard.Modifiers & ModifierKeys.Control) == ModifierKeys.Control && e.Key == Key.F)
            {
                FindBox.Open();
                e.Handled = true;
                return;
            }
            if (e.Key == Key.F5)
            {
                RefreshActive();
                e.Handled = true;
            }
        }

        private void SettingsButton_Click(object sender, RoutedEventArgs e) { SettingsPopup.IsOpen = true; }

        private void ModeCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (!_uiReady || !(ModeCombo.SelectedItem is ComboBoxItem item) || item.Tag == null) return;
            if (Enum.TryParse(item.Tag.ToString(), out ViewMode mode)) ApplyViewMode(mode);
        }

        private void AutoStartCheck_Changed(object sender, RoutedEventArgs e)
        {
            if (!_uiReady) return;
            var enabled = AutoStartCheck.IsChecked == true;
            AutoStartService.SetEnabled(enabled);
            _settings.AutoStart = enabled;
            _settings.Save();
        }

        private void ZoomSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (ZoomText == null) return;
            var zoom = (int)Math.Round(e.NewValue);
            ZoomText.Text = zoom + "%";
            if (!_uiReady) return;
            _settings.ZoomPercent = zoom;
            SetZoom(_rubikaBrowser, zoom);
            SetZoom(_shadBrowser, zoom);
            _settings.Save();
        }

        private static void SetZoom(ChromiumWebBrowser browser, int percent)
        {
            if (browser == null) return;
            var level = Math.Log(percent / 100.0, 1.2);
            if (browser.IsBrowserInitialized) browser.SetZoomLevel(level);
            else browser.IsBrowserInitializedChanged += (s, e) => { if (browser.IsBrowserInitialized) browser.SetZoomLevel(level); };
        }

        private void Back_Click(object sender, RoutedEventArgs e) { if (_activeBrowser != null && _activeBrowser.CanGoBack) _activeBrowser.Back(); }
        private void Forward_Click(object sender, RoutedEventArgs e) { if (_activeBrowser != null && _activeBrowser.CanGoForward) _activeBrowser.Forward(); }
        private void Refresh_Click(object sender, RoutedEventArgs e) { RefreshActive(); }

        private void RefreshActive()
        {
            var browser = _activeBrowser ?? _rubikaBrowser ?? _shadBrowser;
            if (browser != null) browser.Reload(ignoreCache: false);
        }

        private void ClearCache_Click(object sender, RoutedEventArgs e)
        {
            if (MessageBox.Show("Cache و کوکی‌ها پاک شوند؟ برای حذف کامل، برنامه را یک‌بار ببندید و دوباره باز کنید.", "پاک کردن Cache", MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.No, MessageBoxOptions.RightAlign | MessageBoxOptions.RtlReading) == MessageBoxResult.Yes)
            {
                CacheService.ClearCacheFilesBestEffort();
                StatusText.Text = "Cache پاک شد.";
            }
        }

        private async void CheckUpdate_Click(object sender, RoutedEventArgs e)
        {
            StatusText.Text = "در حال بررسی بروزرسانی...";
            var result = await UpdateService.CheckAsync(_settings.UpdateManifestUrl);
            MessageBox.Show(result.Message + (string.IsNullOrEmpty(result.DownloadUrl) ? string.Empty : Environment.NewLine + result.DownloadUrl), "بروزرسانی", MessageBoxButton.OK, result.Available ? MessageBoxImage.Information : MessageBoxImage.None, MessageBoxResult.OK, MessageBoxOptions.RightAlign | MessageBoxOptions.RtlReading);
            StatusText.Text = result.Message;
        }

        private void Window_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            if (WindowState == WindowState.Normal)
            {
                _settings.Width = Width;
                _settings.Height = Height;
            }
            _settings.Maximized = WindowState == WindowState.Maximized;
            _settings.Save();
        }
    }
}
