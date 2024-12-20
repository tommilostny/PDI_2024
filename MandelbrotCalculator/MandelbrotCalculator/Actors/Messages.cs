namespace MandelbrotCalculator.Actors;

public record ComputeMandelbrot(int Width, int Height, int MaxIterations, int NumWorkers);
public record MandelbrotResult(int[] Pixels);
public record ComputeChunk(int StartRow, int EndRow, int Width, int Height, int MaxIterations);
public record ChunkResult(int[] Pixels, int StartRow, int EndRow, int Width);
