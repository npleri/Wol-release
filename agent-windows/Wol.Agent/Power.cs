using System.Diagnostics;
using System.Runtime.InteropServices;

namespace Wol.Agent;

record PowerRequest(int DelaySeconds = 0, bool Force = false);

/// <summary>Lista blanca de acciones de energía. Nunca se ejecuta nada que venga del cliente.</summary>
static class Power
{
    const string Comment = "Apagado solicitado desde la app WoL";

    public static IResult Run(string action, PowerRequest req)
    {
        var delay = Math.Clamp(req.DelaySeconds, 0, 3600).ToString();
        string[] force = req.Force ? ["/f"] : [];
        return action switch
        {
            "shutdown" => ShutdownExe(["/s", "/t", delay, .. force, "/c", Comment]),
            "restart" => ShutdownExe(["/r", "/t", delay, .. force, "/c", Comment]),
            "cancel" => ShutdownExe(["/a"]),
            "sleep" => Suspend(hibernate: false),
            "hibernate" => Suspend(hibernate: true),
            _ => Results.NotFound(new { error = "UNKNOWN_ACTION" }),
        };
    }

    static IResult ShutdownExe(string[] args)
    {
        var psi = new ProcessStartInfo(Path.Combine(Environment.SystemDirectory, "shutdown.exe"))
        {
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        foreach (var arg in args) psi.ArgumentList.Add(arg);
        using var process = Process.Start(psi)!;
        process.WaitForExit();
        return process.ExitCode switch
        {
            0 => Results.Ok(new { ok = true }),
            1116 => Results.Conflict(new { error = "NO_SHUTDOWN_PENDING" }),
            1190 => Results.Conflict(new { error = "SHUTDOWN_ALREADY_PENDING" }),
            var code => Results.Problem($"shutdown.exe terminó con código {code}"),
        };
    }

    static IResult Suspend(bool hibernate)
    {
        // Responder antes de que el equipo se duerma.
        _ = Task.Run(async () =>
        {
            await Task.Delay(1000);
            EnableShutdownPrivilege();
            SetSuspendState(hibernate, false, false);
        });
        return Results.Accepted();
    }

    /// <summary>SetSuspendState exige SeShutdownPrivilege habilitado en el token del proceso.</summary>
    static void EnableShutdownPrivilege()
    {
        const uint TokenAdjustPrivileges = 0x20, TokenQuery = 0x8;
        if (!OpenProcessToken(Process.GetCurrentProcess().Handle, TokenAdjustPrivileges | TokenQuery, out var token)) return;
        try
        {
            var privileges = new TokenPrivileges { Count = 1, Attributes = 2 /* SE_PRIVILEGE_ENABLED */ };
            if (LookupPrivilegeValue(null, "SeShutdownPrivilege", out privileges.Luid))
                AdjustTokenPrivileges(token, false, ref privileges, 0, IntPtr.Zero, IntPtr.Zero);
        }
        finally
        {
            CloseHandle(token);
        }
    }

    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    struct TokenPrivileges
    {
        public int Count;
        public long Luid;
        public int Attributes;
    }

    [DllImport("powrprof.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.I1)]
    static extern bool SetSuspendState(
        [MarshalAs(UnmanagedType.I1)] bool hibernate,
        [MarshalAs(UnmanagedType.I1)] bool forceCritical,
        [MarshalAs(UnmanagedType.I1)] bool disableWakeEvent);

    [DllImport("advapi32.dll", SetLastError = true)]
    static extern bool OpenProcessToken(IntPtr process, uint access, out IntPtr token);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern bool LookupPrivilegeValue(string? system, string name, out long luid);

    [DllImport("advapi32.dll", SetLastError = true)]
    static extern bool AdjustTokenPrivileges(IntPtr token, bool disableAll, ref TokenPrivileges newState, int length, IntPtr previous, IntPtr returnLength);

    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr handle);
}
