using Akka.Cluster.Hosting;
using Akka.Hosting;
using Akka.Remote.Hosting;
using Microsoft.Extensions.Hosting;
using MandelbrotCalculator.Shared;

// No need to start any actors in the worker nodes, all actors will be deployed using remoting.
var host = new HostBuilder()
    .ConfigureServices((context, services) =>
    {
        services.AddAkka(ClusterConfig.SystemName, builder =>
        {
            builder
                // Setup remoting and clustering.
                .WithRemoting(configure: options =>
                {
                    options.Port = ClusterConfig.WorkerPort;
                    options.HostName = ClusterConfig.HostName;
                })
                .WithClustering(options: new ClusterOptions
                {
                    // Giving this cluster node the role/tag "worker" signals that the gateway node can
                    // deploy worker actors in this node using remoting.
                    Roles = [ "worker" ],
                    SeedNodes = [ ClusterConfig.GatewayAddress ]
                });
        });
    }).Build();

await host.StartAsync();
await host.WaitForShutdownAsync();
