﻿using Akka.Actor;

namespace MandelbrotCalculator.Actors;

public class MandelbrotWorkerActor : ReceiveActor
{
    public MandelbrotWorkerActor()
    {
        Receive<ComputeChunk>(chunk =>
        {
            var pixels = new int[chunk.Width * (chunk.EndRow - chunk.StartRow)];
            for (int y = chunk.StartRow; y < chunk.EndRow; y++)
            {
                for (int x = 0; x < chunk.Width; x++)
                {
                    int pointValue = ComputeMandelbrotPoint(x, y, chunk.Width, chunk.Height, chunk.MaxIterations, chunk.Zoom, chunk.OffsetX, chunk.OffsetY);
                    pixels[(y - chunk.StartRow) * chunk.Width + x] = pointValue;
                }
            }
            Sender.Tell(new ChunkResult(pixels, chunk.StartRow, chunk.EndRow, chunk.Width));
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
}
