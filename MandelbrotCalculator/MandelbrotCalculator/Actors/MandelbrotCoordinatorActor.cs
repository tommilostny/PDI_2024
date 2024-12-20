using Akka.Actor;

namespace MandelbrotCalculator.Actors;

public class MandelbrotCoordinatorActor : ReceiveActor
{
    private int _pendingChunks;
    private int[]? _pixels;
    private IActorRef? _sender;
    private List<IActorRef> _workers = [];

    public MandelbrotCoordinatorActor()
    {
        Receive<ComputeMandelbrot>(msg =>
        {
            _pixels = new int[msg.Width * msg.Height];
            _pendingChunks = msg.Height / 10; // Divide the work into chunks of 10 rows each
            _sender = Sender; // Store the original sender to send the result back to

            // Create new workers
            for (int i = 0; i < msg.NumWorkers; i++)
            {
                _workers.Add(Context.ActorOf(Props.Create(() => new MandelbrotWorkerActor()), $"worker-{i}"));
            }

            // Distribute the work to the workers
            for (int i = 0; i < msg.Height; i += 10)
            {
                var worker = _workers[i % msg.NumWorkers];
                worker.Tell(new ComputeChunk(i, Math.Min(i + 10, msg.Height), msg.Width, msg.Height, msg.MaxIterations));
            }
        });

        Receive<ChunkResult>(result =>
        {
            if (_pixels is not null)
            {
                Array.Copy(result.Pixels, 0, _pixels, result.StartRow * result.Width, result.Pixels.Length);
                if (--_pendingChunks == 0)
                {
                    _sender?.Tell(new MandelbrotResult(_pixels));
                    _sender = null;
                    foreach (var worker in _workers)
                    {
                        Context.Stop(worker);
                    }
                    _workers.Clear();
                }
            }
        });
    }
}
