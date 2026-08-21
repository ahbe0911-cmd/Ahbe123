using System;
using System.Windows;
using System.Windows.Controls;
using CefSharp.Wpf;

namespace MessengerWorkspace.Controls
{
    public partial class FindOverlay : UserControl
    {
        public Func<ChromiumWebBrowser> ActiveBrowserProvider { get; set; }

        public FindOverlay()
        {
            InitializeComponent();
        }

        public void Open()
        {
            Visibility = Visibility.Visible;
            QueryBox.Focus();
            QueryBox.SelectAll();
        }

        private void Next_Click(object sender, RoutedEventArgs e) { Find(false); }
        private void Previous_Click(object sender, RoutedEventArgs e) { Find(true); }
        private void Close_Click(object sender, RoutedEventArgs e)
        {
            var browser = ActiveBrowserProvider == null ? null : ActiveBrowserProvider();
            if (browser != null && browser.GetBrowser() != null) browser.GetBrowserHost().StopFinding(true);
            Visibility = Visibility.Collapsed;
        }

        private void Find(bool previous)
        {
            var browser = ActiveBrowserProvider == null ? null : ActiveBrowserProvider();
            if (browser == null || browser.GetBrowser() == null || string.IsNullOrWhiteSpace(QueryBox.Text)) return;
            browser.GetBrowserHost().Find(0, QueryBox.Text, !previous, false, false);
        }
    }
}
