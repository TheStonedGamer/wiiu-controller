using System.Buffers.Binary;

namespace WiiUController.Windows;

internal sealed record Sample(int Slot, int Mode, bool ThreeDs, ushort Sequence,
    uint Buttons, short LX, short LY, short RX, short RY)
{
    public static Sample? Parse(ReadOnlySpan<byte> p)
    {
        bool ds = p.Length == 20 && p[..4].SequenceEqual("3DWC"u8) && p[4] == 1 && p[5] <= 1;
        bool phone = (p.Length == 32 || p.Length == 64) && p[..4].SequenceEqual("ANWC"u8)
            && p[5] <= 3 && (p[22] & ~1) == 0;
        if (!ds)
        {
            if (!phone || p[24..32].ContainsAnyExcept((byte)0)) return null;
            if (p.Length == 32)
            {
                if (p[4] != 1 || p[23] > 2) return null;
            }
            else
            {
                if (p[4] != 2 || p[23] != 3 || (p[46] & ~1) != 0
                    || p[47..].ContainsAnyExcept((byte)0)) return null;
                if ((p[46] & 1) != 0)
                {
                    double norm = 0;
                    for (int i = 38; i < 46; i += 2)
                    {
                        double q = BinaryPrimitives.ReadInt16BigEndian(p[i..]) / 32767.0;
                        norm += q * q;
                    }
                    if (norm < .8 || norm > 1.2) return null;
                }
            }
        }
        return new(ds ? 1 : p[5], ds ? (p[5] == 0 ? 1 : 2) : p[23], ds,
            BinaryPrimitives.ReadUInt16BigEndian(p[6..]), BinaryPrimitives.ReadUInt32BigEndian(p[8..]),
            BinaryPrimitives.ReadInt16BigEndian(p[12..]), BinaryPrimitives.ReadInt16BigEndian(p[14..]),
            BinaryPrimitives.ReadInt16BigEndian(p[16..]), BinaryPrimitives.ReadInt16BigEndian(p[18..]));
    }
}
