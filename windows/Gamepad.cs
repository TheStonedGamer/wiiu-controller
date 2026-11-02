using System.Runtime.InteropServices;

namespace WiiUController.Windows;

internal sealed class Gamepad
{
    // Must match the explicit button order in native/bridge.cpp.
    private enum Button { A, B, X, Y, Back, Start, Guide, LeftThumb, RightThumb,
        LeftShoulder, RightShoulder, Up, Down, Left, Right }
    private IntPtr handle;
    [DllImport("controller_hid", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr wc_create(int profile, int slot);
    [DllImport("controller_hid", CallingConvention = CallingConvention.Cdecl)]
    private static extern int wc_submit(IntPtr pad, uint buttons, float lx, float ly, float rx, float ry, float lt, float rt);
    [DllImport("controller_hid", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr wc_error();
    [DllImport("controller_hid", CallingConvention = CallingConvention.Cdecl)]
    private static extern void wc_close(IntPtr pad);
    private static Exception Error() => new InvalidOperationException(Marshal.PtrToStringUTF8(wc_error()) ?? "Virtual HID operation failed");
    public Gamepad(int profile, int slot)
    {
        handle = wc_create(profile, slot);
        if (handle == IntPtr.Zero) throw Error();
    }
    private void Submit(uint buttons = 0, float lx = 0, float ly = 0, float rx = 0, float ry = 0, float lt = 0, float rt = 0)
    {
        if (wc_submit(handle, buttons, lx, ly, rx, ry, lt, rt) == 0) throw Error();
    }
    public void Neutral() => Submit();
    public void Disconnect()
    {
        if (handle == IntPtr.Zero) return;
        try { Neutral(); }
        finally { var old = handle; handle = IntPtr.Zero; wc_close(old); }
    }
    public void Apply(Sample s, bool byPosition)
    {
        uint buttons = 0;
        void Map(uint mask, Button target) { if ((s.Buttons & mask) != 0) buttons |= 1U << (int)target; }
        if (s.Mode is 1 or 2)
        {
            Map(1, Button.Down); Map(2, Button.Right); Map(0x4000, Button.Left); Map(0x8000, Button.Up);
            Map(0x10, byPosition ? Button.B : Button.A); Map(0x40, byPosition ? Button.A : Button.B);
            Map(8, byPosition ? Button.Y : Button.X); Map(0x20, byPosition ? Button.X : Button.Y);
            Map(0x2000, Button.LeftShoulder); Map(0x200, Button.RightShoulder);
            Map(0x400, Button.Start); Map(0x1000, Button.Back); Map(0x800, Button.Guide);
            if (s.Mode == 1) { Map(0x20000, Button.LeftThumb); Map(0x10000, Button.RightThumb); }
            static float Axis(short value) => Math.Clamp(value / 156f, -1, 1);
            Submit(buttons, Axis(s.LX), Axis(s.LY), Axis(s.RX), Axis(s.RY),
                (s.Buttons & 0x80) != 0 ? 1 : 0, (s.Buttons & 4) != 0 ? 1 : 0);
        }
        else
        {
            Map(1, Button.Left); Map(2, Button.Right); Map(4, Button.Down); Map(8, Button.Up);
            Map(0x800, Button.A); Map(0x400, Button.B); Map(0x200, Button.X); Map(0x100, Button.Y);
            Map(0x10, Button.Start); Map(0x1000, Button.Back); Map(0x8000, Button.Guide);
            Submit(buttons);
        }
    }
}
