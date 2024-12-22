using Akka.Actor;
using Akka.Cluster.Hosting;
using Akka.Hosting;
using Akka.Remote.Hosting;
using MandelbrotCalculator.Server.Actors;
using MandelbrotCalculator.Shared;
using SkiaSharp;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();

// Add CORS services
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowBlazorClient", builder =>
    {
        builder.WithOrigins("https://localhost:7236", "http://localhost:5233")
               .AllowAnyHeader()
               .AllowAnyMethod();
    });
});

// Configure Akka.NET
builder.Services.AddAkka(ClusterConfig.SystemName, builder =>
{
    builder
    // Setup remoting
    .WithRemoting(configure: options =>
    {
        options.Port = ClusterConfig.ClusterClientPort;
        options.HostName = ClusterConfig.HostName;
    })

    // Setup `ClusterClient` actor to connect to another actor system
    .WithClusterClient<GatewayClusterClientActor>([$"{ClusterConfig.GatewayAddress}/system/receptionist"])

    // Setup required actors and startup code
    .WithActors((system, registry, resolver) =>
    {
        var requesterActor = system.ActorOf(resolver.Props(typeof(MandelbrotWorkRequesterActor)), "work-requester");
        registry.Register<MandelbrotWorkRequesterActor>(requesterActor);
    });
});

var app = builder.Build();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseHttpsRedirection();

// Use CORS middleware
app.UseCors("AllowBlazorClient");

// Create the endpoint for distributed Mandelbrot computation.
app.MapGet("/mandelbrot", async (IRequiredActor<MandelbrotWorkRequesterActor> requesterActor, int width, int height, int maxIterations, double zoom, double offsetX, double offsetY) =>
{
    var requester = requesterActor.ActorRef;
    var result = await requester.Ask<MandelbrotResult>(new ComputeMandelbrot(width, height, maxIterations, zoom, offsetX, offsetY));

    using var bitmap = new SKBitmap(width, height);
    for (int y = 0; y < height; y++)
    {
        for (int x = 0; x < width; x++)
        {
            int iterations = result.Pixels[y * width + x];
            int colorValue = iterations == maxIterations ? 0 : 255 - (iterations * 255 / maxIterations);
            var color = new SKColor((byte)colorValue, (byte)colorValue, (byte)colorValue);
            bitmap.SetPixel(x, y, color);
        }
    }

    using var image = SKImage.FromBitmap(bitmap);
    using var data = image.Encode(format: SKEncodedImageFormat.Png, quality: 100);
    var memoryStream = new MemoryStream();
    data.SaveTo(memoryStream);
    memoryStream.Seek(0, SeekOrigin.Begin);

    return Results.File(memoryStream, "image/png");
})
.WithName("LaunchMandelbrotDistributedComputation");

app.Run();
