namespace MandelbrotCalculator.Shared;

public static class ClusterConfig
{
    public const string SystemName = "MandelbrotSystem";
    public const string HostName = "localhost";
    public const string GatewayPort = "12552";
    public const string GatewayAddress = $"akka.tcp://{SystemName}@{HostName}:{GatewayPort}";
    public const int ClusterClientPort = 12553;
    public const int WorkerPort = 0;
}
