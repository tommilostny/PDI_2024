using Akka.Actor;
using Akka.Cluster.Tools.Client;
using Akka.Event;
using Akka.Hosting;
using MandelbrotCalculator.Shared;

namespace MandelbrotCalculator.Server.Actors;

public class MandelbrotWorkRequesterActor : ReceiveActor
{
    // Key: Work ID, Value: Array of pixels for the work
    // After the work is done, the result will be stored in this dictionary
    // which will be sent back to the original sender and then cleared from here.
    private readonly Dictionary<int, (int pendingChunks, int[] pixels)> _results = [];

    // Store the original sender to send the result back to.
    private readonly Dictionary<int, IActorRef> _senders = [];

    // Keep track of the next work ID to assign to the next work request.
    private int _nextWorkId = 0;

    public MandelbrotWorkRequesterActor(IRequiredActor<GatewayClusterClientActor> clusterClientActor)
    {
        var log = Context.GetLogger();
        var clusterClient = clusterClientActor.ActorRef;

        Receive<ComputeMandelbrot>(msg =>
        {
            var work = msg with { WorkId = _nextWorkId++ };
            log.Info("Received ComputeMandelbrot message, requesting work from coordinator with id #{0}", work.WorkId);

            // We can access the coordinator actor through the path set in the ClusterClientReceptionist
            // which is on the gateway node and accessible through the ClusterClient.
            clusterClient.Tell(new ClusterClient.Send("/user/coordinator", work, true));
            _senders[work.WorkId] = Sender;
            _results[work.WorkId] = (msg.Height, new int[msg.Width * msg.Height]);
        });

        Receive<ChunkResult>(msg =>
        {
            if (_results.TryGetValue(msg.WorkId, out var result))
            {
                var (pendingChunks, pixels) = result;
                log.Info("Received ChunkResult ({0}/{1}, work ID: #{2}, row: {3})", msg.Height - pendingChunks + 1, msg.Height, msg.WorkId, msg.Row);

                if (pixels is not null)
                {
                    Array.Copy(msg.Pixels, 0, pixels, msg.Row * msg.Width, msg.Pixels.Length);
                    if (--pendingChunks == 0)
                    {
                        log.Info("All chunks for work with id #{0} have been received, sending result back to original sender", msg.WorkId);
                        // Send the result back to the original sender
                        if (_senders.TryGetValue(msg.WorkId, out var originalSender))
                        {
                            originalSender.Tell(new MandelbrotResult(pixels));
                            log.Info("Result sent back to original sender for work with id #{0}", msg.WorkId);
                        }
                        else
                        {
                            // Should not happen, but log a warning just in case.
                            log.Warning("Original sender not found for work with id #{0}", msg.WorkId);
                        }
                        _senders.Remove(msg.WorkId);
                        _results.Remove(msg.WorkId);
                    }
                    else
                    {
                        // Not all chunks have been received yet, update the pending chunks count.
                        _results[msg.WorkId] = (pendingChunks, pixels);
                    }
                }
            }
            else
            {
                // Should not happen, but log a warning just in case.
                log.Warning("Work ID #{0} not found in results dictionary", msg.WorkId);
            }
        });
    }
}
