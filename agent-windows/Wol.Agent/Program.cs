using System.Threading.RateLimiting;
using Wol.Agent;

const int Port = 47800;

// "Wol.Agent.exe token" muestra el token para cargarlo en la app (requiere administrador).
if (args is ["token"])
{
    try
    {
        Console.WriteLine(Token.Format(Token.LoadOrCreate()));
    }
    catch (UnauthorizedAccessException)
    {
        Console.Error.WriteLine("Sin permiso para leer el token: ejecutar como administrador.");
        Environment.Exit(1);
    }
    return;
}

var token = Token.LoadOrCreate();
var builder = WebApplication.CreateBuilder(args);
builder.Host.UseWindowsService(o => o.ServiceName = "WolAgent");
builder.WebHost.UseUrls(builder.Configuration["urls"] ?? $"http://*:{Port}");
builder.Services.AddRateLimiter(o =>
{
    o.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    o.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(ctx =>
        RateLimitPartition.GetFixedWindowLimiter(
            ctx.Connection.RemoteIpAddress?.ToString() ?? "",
            _ => new FixedWindowRateLimiterOptions { PermitLimit = 60, Window = TimeSpan.FromMinutes(1) }));
});

var app = builder.Build();
app.UseRateLimiter();
app.Use(async (ctx, next) =>
{
    var header = ctx.Request.Headers.Authorization.ToString();
    if (!header.StartsWith("Bearer ") || !Token.Matches(token, header["Bearer ".Length..]))
    {
        ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
        return;
    }
    await next();
});

var api = app.MapGroup("/api/v1");
api.MapGet("/status", SystemInfo.Status);
api.MapPost("/power/{action}", (string action, PowerRequest? body, HttpContext ctx, ILogger<Program> log) =>
{
    log.LogInformation("Acción {Action} pedida desde {Ip}", action, ctx.Connection.RemoteIpAddress);
    return Power.Run(action, body ?? new PowerRequest());
});

app.Run();
