using Akka.Actor;
using Akka.Configuration;
using MandelbrotCalculator.Components;
using MandelbrotCalculator.Services;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

// Configure Akka.NET
var config = ConfigurationFactory.ParseString(@"
    akka {
        actor {
            provider = remote
        }
        remote {
            dot-netty.tcp {
                hostname = ""localhost""
                port = 8081
            }
        }
    }");

var actorSystem = ActorSystem.Create("MandelbrotSystem", config);
builder.Services.AddSingleton(actorSystem);

// Ensure the actor system is properly configured and shut down
// using a hosted service.
builder.Services.AddHostedService<AkkaHostedService>();

var app = builder.Build();

// Configure the HTTP request pipeline.
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error", createScopeForErrors: true);
    // The default HSTS value is 30 days. You may want to change this for production scenarios, see https://aka.ms/aspnetcore-hsts.
    app.UseHsts();
}

app.UseHttpsRedirection();

app.UseAntiforgery();

app.MapStaticAssets();
app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();

app.Run();
