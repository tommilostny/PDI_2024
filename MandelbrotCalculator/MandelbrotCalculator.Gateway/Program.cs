using Akka.Cluster.Hosting;
using Akka.Cluster.Tools.Client;
using Akka.Hosting;
using Akka.Remote.Hosting;
using MandelbrotCalculator.Gateway.Actors;
using MandelbrotCalculator.Shared;
using Microsoft.Extensions.Hosting;

// This node acts as a known contact point for all external actor systems to connect via `ClusterClient` by setting up
// the `ClusterClientReceptionist`

var host = new HostBuilder()
    .ConfigureServices((context, services) =>
    {
        services.AddAkka(ClusterConfig.SystemName, builder =>
        {
            builder
            // Setup remoting and clustering
            .WithRemoting(configure: options =>
            {
                options.Port = int.Parse(ClusterConfig.GatewayPort);
                options.HostName = ClusterConfig.HostName;
            })
            .WithClustering(options: new ClusterOptions
            {
                Roles = ["gateway"],
                SeedNodes = [ClusterConfig.GatewayAddress]
            })

            // Setup `ClusterClientReceptionist` to only deploy on nodes with "gateway" role
            .WithClusterClientReceptionist(role: "gateway")

            // Setup required actors and startup code
            .WithActors((system, registry, resolver) =>
            {
                // The name of this actor ("coordinator") is required, because its absolute path
                // ("/user/coordinator") will be used as a service path by ClusterClientReceptionist.
                var coordinatorActor = system.ActorOf(resolver.Props(typeof(MandelbrotCoordinatorActor)), "coordinator");
                registry.Register<MandelbrotCoordinatorActor>(coordinatorActor);
            })
            .AddStartup((system, registry) =>
            {
                var receptionist = ClusterClientReceptionist.Get(system);

                // Register the coordinator actor as a service,
                // this can be accessed through "user/coordinator"
                var coordinatorActor = registry.Get<MandelbrotCoordinatorActor>();
                receptionist.RegisterService(coordinatorActor);
            });
        });
    }).Build();

await host.StartAsync();
await host.WaitForShutdownAsync();
