using System.Net;
using System.Net.Sockets;

namespace WiiUController.Windows;

internal sealed class Receiver : IDisposable
{
    private sealed class Slot
    {
        public Gamepad? Pad;
        public IPAddress? Owner;
        public Sample? Last;
        public long Received;
        public bool Active;
    }
    private readonly Slot[] slots = Enumerable.Range(0, 4).Select(_ => new Slot()).ToArray();
    private readonly UdpClient udp;
    private readonly CancellationTokenSource stop = new();
    private readonly Task worker;
    private readonly bool byPosition;
    private readonly int profile;
    public event Action<string[]>? Status;
    public event Action<string>? Failed;

    public Receiver(bool byPosition, int profile)
    {
        this.byPosition = byPosition;
        this.profile = profile;
        try
        {
            udp = new UdpClient(AddressFamily.InterNetwork);
            udp.Client.ExclusiveAddressUse = true;
            udp.Client.Bind(new IPEndPoint(IPAddress.Any, 26760));
        }
        catch { udp?.Dispose(); throw; }
        worker = Task.Run(Run);
    }
    private async Task Run()
    {
        try
        {
            Task<UdpReceiveResult> pending = udp.ReceiveAsync(stop.Token).AsTask();
            using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(8));
            long nextStatus = 0;
            while (await timer.WaitForNextTickAsync(stop.Token))
            {
                long now = Environment.TickCount64;
                foreach (var slot in slots)
                {
                    if (slot.Active && now - slot.Received >= 250)
                    {
                        slot.Pad!.Neutral(); slot.Active = false;
                    }
                }
                // Bound each batch so a busy sender cannot starve timeout handling.
                for (int i = 0; i < 128 && pending.IsCompleted; i++)
                {
                    var packet = await pending;
                    Handle(packet, Environment.TickCount64);
                    pending = udp.ReceiveAsync(stop.Token).AsTask();
                }
                if (now < nextStatus) continue;
                nextStatus = now + 250;
                Status?.Invoke(slots.Select((s, i) => s.Pad == null ? $"Slot {i + 1}: waiting"
                    : s.Active ? $"Slot {i + 1}: {s.Owner} · {(s.Last!.ThreeDs ? "3DS" : "Phone")} · {ModeName(s.Last.Mode)} · Virtual HID"
                    : $"Slot {i + 1}: disconnected · inputs released · Virtual HID").ToArray());
            }
        }
        catch (OperationCanceledException) when (stop.IsCancellationRequested) { }
        catch (Exception e) { Failed?.Invoke(e.Message); }
        finally
        {
            foreach (var slot in slots)
            {
                try { slot.Pad?.Disconnect(); } catch { /* Continue releasing other pads. */ }
            }
        }
    }
    private static string ModeName(int mode) => mode switch { 1 => "Pro", 2 => "Classic", 3 => "MotionPlus", _ => "Remote" };
    private void Reply(string message, IPEndPoint peer) => udp.Send(System.Text.Encoding.ASCII.GetBytes(message), peer);
    private void Handle(UdpReceiveResult datagram, long now)
    {
        if (datagram.Buffer.AsSpan().SequenceEqual("WUD?"u8)) { Reply("WUD!", datagram.RemoteEndPoint); return; }
        var sample = Sample.Parse(datagram.Buffer);
        if (sample == null) return;
        var slot = slots[sample.Slot];
        bool owned = slot.Last != null && now - slot.Received < 250;
        if (owned && (!Equals(slot.Owner, datagram.RemoteEndPoint.Address) || slot.Last!.ThreeDs != sample.ThreeDs))
        {
            Reply(sample.ThreeDs ? "3DWB" : "ANWB", datagram.RemoteEndPoint); return;
        }
        if (owned && unchecked((short)(sample.Sequence - slot.Last!.Sequence)) <= 0) return;
        slot.Pad ??= new Gamepad(profile, sample.Slot);
        slot.Pad.Apply(sample, byPosition);
        slot.Owner = datagram.RemoteEndPoint.Address; slot.Last = sample; slot.Received = now; slot.Active = true;
        Reply(sample.ThreeDs ? "3DWA" : "ANWA", datagram.RemoteEndPoint);
    }
    public void Dispose()
    {
        stop.Cancel();
        worker.GetAwaiter().GetResult();
        udp.Dispose(); stop.Dispose();
    }
}
