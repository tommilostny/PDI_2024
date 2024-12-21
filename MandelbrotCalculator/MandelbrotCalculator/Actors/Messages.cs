namespace MandelbrotCalculator.Actors;

public record ComputeMandelbrot(int Width, int Height, int MaxIterations, int NumWorkers, double Zoom, double OffsetX, double OffsetY);
public record MandelbrotResult(int[] Pixels);
public record ComputeChunk(int StartRow, int EndRow, int Width, int Height, int MaxIterations, double Zoom, double OffsetX, double OffsetY);
public record ChunkResult(int[] Pixels, int StartRow, int EndRow, int Width);
