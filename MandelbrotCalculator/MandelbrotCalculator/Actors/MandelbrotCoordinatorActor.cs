using Akka.Actor;

namespace MandelbrotCalculator.Actors;

public class MandelbrotCoordinatorActor : ReceiveActor
{
    private int _pendingChunks;
    private int[]? _pixels;
    private IActorRef? _sender;
    private List<IActorRef> _workers = new();

    public MandelbrotCoordinatorActor()
    {
        Receive<ComputeMandelbrot>(msg =>
        {
            _pixels = new int[msg.Width * msg.Height];
            _pendingChunks = msg.NumWorkers; // Number of workers equals number of chunks
            _sender = Sender; // Store the original sender to send the result back to

            // Create new workers
            for (int i = 0; i < msg.NumWorkers; i++)
            {
                _workers.Add(Context.ActorOf(Props.Create(() => new MandelbrotWorkerActor()), $"worker-{i}"));
            }

            // Calculate the number of rows each worker should process
            int rowsPerWorker = msg.Height / msg.NumWorkers;
            int remainingRows = msg.Height % msg.NumWorkers;

            // Distribute the work to the workers
            int startRow = 0;
            for (int i = 0; i < msg.NumWorkers; i++)
            {
                int endRow = startRow + rowsPerWorker + (i < remainingRows ? 1 : 0);
                var worker = _workers[i];
                worker.Tell(new ComputeChunk(startRow, endRow, msg.Width, msg.Height, msg.MaxIterations, msg.Zoom, msg.OffsetX, msg.OffsetY));
                startRow = endRow;
            }
        });

        Receive<ChunkResult>(result =>
        {
            if (_pixels is not null)
            {
                Array.Copy(result.Pixels, 0, _pixels, result.StartRow * result.Width, result.Pixels.Length);
                if (--_pendingChunks == 0)
                {
                    // Send the result back to the original sender
                    _sender?.Tell(new MandelbrotResult(_pixels));
                    _sender = null;

                    // Stop all workers
                    foreach (var worker in _workers)
                    {
                        Context.Stop(worker);
                    }
                    _workers.Clear();

                    // Stop the coordinator actor itself
                    Context.Stop(Self);
                }
            }
        });
    }
}
