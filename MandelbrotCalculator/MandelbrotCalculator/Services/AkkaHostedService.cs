using Akka.Actor;
using Akka.Configuration;

namespace MandelbrotCalculator.Services;

// Hosted service to manage the actor system lifecycle
public class AkkaHostedService(ActorSystem actorSystem) : IHostedService
{
    public Task StartAsync(CancellationToken cancellationToken)
    {
        return Task.CompletedTask;
    }

    public async Task StopAsync(CancellationToken cancellationToken)
    {
        await actorSystem.Terminate();
    }
}
