using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;

namespace Wol.Agent;

/// <summary>
/// Token de 128 bits (32 caracteres hex) cifrado con DPAPI en %ProgramData%\WolAgent,
/// carpeta accesible solo para SYSTEM y Administradores.
/// </summary>
static class Token
{
    // WOL_AGENT_DATA permite probar el agente sin administrador.
    static readonly string Dir = Environment.GetEnvironmentVariable("WOL_AGENT_DATA")
        ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "WolAgent");
    static readonly string FilePath = Path.Combine(Dir, "token.bin");

    public static string LoadOrCreate()
    {
        if (File.Exists(FilePath))
        {
            var plain = ProtectedData.Unprotect(File.ReadAllBytes(FilePath), null, DataProtectionScope.LocalMachine);
            return Encoding.ASCII.GetString(plain);
        }

        var token = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(16));
        var dir = Directory.CreateDirectory(Dir);
        if (Environment.GetEnvironmentVariable("WOL_AGENT_DATA") is null) RestrictToAdmins(dir);
        File.WriteAllBytes(FilePath, ProtectedData.Protect(Encoding.ASCII.GetBytes(token), null, DataProtectionScope.LocalMachine));
        return token;
    }

    /// <summary>Compara en tiempo constante; ignora guiones, espacios y mayúsculas.</summary>
    public static bool Matches(string token, string candidate)
    {
        var normalized = new string(candidate.Where(Uri.IsHexDigit).ToArray()).ToLowerInvariant();
        return CryptographicOperations.FixedTimeEquals(Encoding.ASCII.GetBytes(token), Encoding.ASCII.GetBytes(normalized));
    }

    /// <summary>"a1b2c3d4…" → "a1b2-c3d4-…", más fácil de copiar a mano.</summary>
    public static string Format(string token) => string.Join("-", token.Chunk(4).Select(c => new string(c)));

    static void RestrictToAdmins(DirectoryInfo dir)
    {
        var acl = new DirectorySecurity();
        acl.SetAccessRuleProtection(isProtected: true, preserveInheritance: false);
        foreach (var sid in new[] { WellKnownSidType.LocalSystemSid, WellKnownSidType.BuiltinAdministratorsSid })
        {
            acl.AddAccessRule(new FileSystemAccessRule(
                new SecurityIdentifier(sid, null),
                FileSystemRights.FullControl,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit,
                PropagationFlags.None,
                AccessControlType.Allow));
        }
        dir.SetAccessControl(acl);
    }
}
