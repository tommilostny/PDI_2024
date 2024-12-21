using Akka.Actor;
using Akka.Event;

namespace MandelbrotCalculator.Shared.Actors;

public class MandelbrotWorkerActor : ReceiveActor
{
    private readonly ILoggingAdapter? _log;

    public MandelbrotWorkerActor()
    {
        _log = Context.GetLogger();

        Receive<ComputeChunk>(chunk =>
        {
            _log.Info("Received ComputeChunk message, calculating Mandelbrot set for chunk with work ID #{0}, row {1}.", chunk.WorkId, chunk.Row);

            var pixels = new int[chunk.Width];
            int y = chunk.Row;
            for (int x = 0; x < chunk.Width; x++)
            {
                int pointValue = ComputeMandelbrotPoint(x, y, chunk.Width, chunk.Height, chunk.MaxIterations, chunk.Zoom, chunk.OffsetX, chunk.OffsetY);
                pixels[(y - chunk.Row) * chunk.Width + x] = pointValue;
            }
            Sender.Tell(new ChunkResult(chunk.WorkId, pixels, chunk.Row, chunk.Width, chunk.Height));
        });
    }

    private static int ComputeMandelbrotPoint(int x, int y, int width, int height, int maxIterations, double zoom, double offsetX, double offsetY)
    {
        double zx = 0, zy = 0;
        double cx = (x - width / 2.0) * 4.0 / (width * zoom) + offsetX;
        double cy = (y - height / 2.0) * 4.0 / (height * zoom) + offsetY;
        int iteration = 0;
        while (zx * zx + zy * zy < 4 && iteration < maxIterations)
        {
            double temp = zx * zx - zy * zy + cx;
            zy = 2.0 * zx * zy + cy;
            zx = temp;
            iteration++;
        }
        return iteration;
    }

    protected override void PreStart()
    {
        base.PreStart();
        _log?.Info("Worker actor started at {0}", Self.Path);
    }
}
