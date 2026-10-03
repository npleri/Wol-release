using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace Wol.Agent;

static class SystemInfo
{
    public static IResult Status() => Results.Ok(new
    {
        hostname = Environment.MachineName,
        agentVersion = typeof(SystemInfo).Assembly.GetName().Version?.ToString(3),
        uptimeSeconds = Environment.TickCount64 / 1000,
        loggedInUser = ConsoleUser(),
        mac = EthernetMac(),
        warnings = Warnings(),
    });

    /// <summary>Usuario con sesión en la consola física (el servicio corre en la sesión 0).</summary>
    static string? ConsoleUser()
    {
        var session = WTSGetActiveConsoleSessionId();
        if (session == 0xFFFFFFFF || !WTSQuerySessionInformation(IntPtr.Zero, session, WtsUserName, out var buffer, out _))
            return null;
        try
        {
            var name = Marshal.PtrToStringUni(buffer);
            return string.IsNullOrEmpty(name) ? null : name;
        }
        finally
        {
            WTSFreeMemory(buffer);
        }
    }

    /// <summary>MAC de la placa Ethernet activa con puerta de enlace IPv4 (la que recibe el paquete mágico).</summary>
    static string? EthernetMac() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up
            && n.NetworkInterfaceType == NetworkInterfaceType.Ethernet
            && n.GetIPProperties().GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork))
        .Select(n => string.Join(":", n.GetPhysicalAddress().GetAddressBytes().Select(b => b.ToString("X2"))))
        .FirstOrDefault();

    /// <summary>Misma regla que tools/diagnostico-wol.ps1: Inicio rápido activo impide el WoL desde apagado.</summary>
    static string[] Warnings()
    {
        using var power = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Control\Session Manager\Power");
        using var hibernate = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Control\Power");
        var fastStartup = power?.GetValue("HiberbootEnabled") is 1 && hibernate?.GetValue("HibernateEnabled") is not 0;
        return fastStartup ? ["FAST_STARTUP_ENABLED"] : [];
    }

    const int WtsUserName = 5;

    [DllImport("kernel32.dll")]
    static extern uint WTSGetActiveConsoleSessionId();

    [DllImport("wtsapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    static extern bool WTSQuerySessionInformation(IntPtr server, uint sessionId, int infoClass, out IntPtr buffer, out uint bytes);

    [DllImport("wtsapi32.dll")]
    static extern void WTSFreeMemory(IntPtr memory);
}
