using System.Net;
using System.Net.Sockets;

namespace MandelbrotCalculator.Shared;

public static class ClusterConfig
{
    public const string SystemName = "MandelbrotSystem";
    public const string GatewayPort = "12552";
    public static string GatewayAddress(string? ip) => $"akka.tcp://{SystemName}@{ip ?? GetLocalIPAddress()}:{GatewayPort}";
    public const int ClusterClientPort = 12553;
    public const int WorkerPort = 0;

    public static string GetLocalIPAddress()
    {
        var host = Dns.GetHostEntry(Dns.GetHostName());
        foreach (var ip in host.AddressList)
        {
            if (ip.AddressFamily == AddressFamily.InterNetwork)
            {
                return ip.ToString();
            }
        }
        throw new Exception("No network adapters with an IPv4 address in the system!");
    }
}
