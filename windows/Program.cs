using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace WiiUController.Windows;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new ReceiverForm());
    }
}

internal sealed class ReceiverForm : Form
{
    private Receiver? receiver;
    private readonly Button toggle = new() { Text = "Start receiver", AutoSize = true };
    private readonly CheckBox position = new() { Text = "Match face buttons by position (Nintendo B → Xbox A)", Checked = true, AutoSize = true };
    private readonly ComboBox profile = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 360 };
    private readonly Label status = new() { Text = "Stopped", AutoSize = true, MaximumSize = new Size(720, 0) };
    private readonly Label[] slots = Enumerable.Range(1, 4).Select(i => new Label { Text = $"Slot {i}: waiting", AutoSize = true }).ToArray();
    public ReceiverForm()
    {
        Text = "Wii U Controller — PC receiver";
        ClientSize = new Size(820, 680); MinimumSize = new Size(700, 600);
        Font = new Font("Segoe UI", 11);
        var panel = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown,
            WrapContents = false, AutoScroll = true, Padding = new Padding(24) };
        Controls.Add(panel);
        void Line(string text) => panel.Controls.Add(new Label { Text = text, AutoSize = true, MaximumSize = new Size(710, 0), Margin = new Padding(0, 0, 0, 16) });
        Line("Use your phone or 3DS as a PC gamepad");
        Line("Connect both devices to the same network. In your controller app, use Pro or Classic mode and enter one of this PC's addresses:");
        var addresses = NetworkInterface.GetAllNetworkInterfaces().Where(n => n.OperationalStatus == OperationalStatus.Up)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a.Address))
            .Select(a => a.Address.ToString()).Distinct();
        Line(string.Join("   •   ", addresses.DefaultIfEmpty("No active IPv4 network found")) + "   (UDP 26760)");
        profile.Items.AddRange(["Xbox 360 (XInput)", "Xbox Series (HID)", "DualShock 4", "DualSense", "Switch Pro", "Generic HID"]);
        profile.SelectedIndex = 0;
        Line("Install Virtual HID Driver and activate its machine license before connecting.");
        panel.Controls.Add(profile);
        panel.Controls.Add(position); panel.Controls.Add(toggle); panel.Controls.Add(status);
        foreach (var slot in slots) { slot.Margin = new Padding(0, 10, 0, 0); panel.Controls.Add(slot); }
        var driver = new LinkLabel { Text = "Install Virtual HID Driver (official releases)", AutoSize = true, Margin = new Padding(0, 20, 0, 0) };
        driver.LinkClicked += (_, _) => Open("https://github.com/LizardByte/libvirtualhid/releases/tag/v2026.914.1218.10");
        panel.Controls.Add(driver);
        var license = new LinkLabel { Text = "Virtual HID Driver license and activation information", AutoSize = true };
        license.LinkClicked += (_, _) => Open("https://app.lizardbyte.dev/libvirtualhid/virtual-hid-driver/");
        panel.Controls.Add(license);
        var controls = new Button { Text = "Open Windows game controllers", AutoSize = true };
        controls.Click += (_, _) => Open("joy.cpl"); panel.Controls.Add(controls);
        toggle.Click += (_, _) => { if (receiver != null) Stop(); else Start(); };
        FormClosing += (_, _) => Stop();
    }
    private static void Open(string target) => Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
    private void Start()
    {
        try
        {
            var current = new Receiver(position.Checked, profile.SelectedIndex);
            receiver = current;
            current.Status += values => OnUi(() => { if (receiver == current) for (int i = 0; i < 4; i++) slots[i].Text = values[i]; });
            current.Failed += message => OnUi(() => { if (receiver == current) { Stop(); status.Text = "Receiver stopped: " + message; } });
            toggle.Text = "Stop receiver"; position.Enabled = false; profile.Enabled = false;
            status.Text = "Listening. Allow access on your private network if Windows Firewall asks.";
        }
        catch (Exception e)
        {
            status.Text = "Cannot start: " + e.Message + "\nInstall Virtual HID Driver and activate its license, then retry. If UDP 26760 is in use, close the other receiver.";
        }
    }
    private void OnUi(Action action)
    {
        if (IsDisposed || Disposing || !IsHandleCreated) return;
        try { BeginInvoke(action); } catch (InvalidOperationException) { }
    }
    private void Stop()
    {
        var old = receiver; receiver = null; old?.Dispose();
        toggle.Text = "Start receiver"; position.Enabled = true; profile.Enabled = true; status.Text = "Stopped — gamepads removed";
        for (int i = 0; i < 4; i++) slots[i].Text = $"Slot {i + 1}: waiting";
    }
}
