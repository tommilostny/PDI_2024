namespace MandelbrotCalculator.Shared;

public sealed record ComputeMandelbrot(int Width, int Height, int MaxIterations, double Zoom, double OffsetX, double OffsetY, int WorkId = 0);
public sealed record MandelbrotResult(int[] Pixels);
public sealed record ComputeChunk(int WorkId, int Row, int Width, int Height, int MaxIterations, double Zoom, double OffsetX, double OffsetY);
public sealed record ChunkResult(int WorkId, int[] Pixels, int Row, int Width, int Height);
