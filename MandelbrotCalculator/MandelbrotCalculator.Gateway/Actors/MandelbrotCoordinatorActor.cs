using Akka.Actor;
using Akka.Cluster.Routing;
using Akka.Event;
using Akka.Routing;
using MandelbrotCalculator.Shared;
using MandelbrotCalculator.Shared.Actors;

namespace MandelbrotCalculator.Gateway.Actors;

public class MandelbrotCoordinatorActor : ReceiveActor
{
    public MandelbrotCoordinatorActor()
    {
        // Creates a cluster router pool of 20 workers for each cluster node with the role "worker"
        // that joins the cluster.
        // The router will use a round-robin strategy to distribute messages amongst the worker actors.
        var props = new ClusterRouterPool(new RoundRobinPool(20), new ClusterRouterPoolSettings(100, 20, true, "worker"))
            .Props(Props.Create(() => new MandelbrotWorkerActor()));

        var workerRouter = Context.ActorOf(props);
        var log = Context.GetLogger();

        Receive<ComputeMandelbrot>(msg =>
        {
            log.Info("Received ComputeMandelbrot message, distributing work to workers");

            // Distribute the work (computation of each row) to the workers.
            for (int y = 0; y < msg.Height; y++)
            {
                // Forward the work request as if it was sent by the original sender so that the work result can be
                // sent back to the original sender by the worker.
                workerRouter.Forward(new ComputeChunk(msg.WorkId, y, msg.Width, msg.Height, msg.MaxIterations, msg.Zoom, msg.OffsetX, msg.OffsetY));
            }
        });
    }
}
