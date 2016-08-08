using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Text;
using RamjetAnvil.Padrone.Client;

namespace Test {
    class Program {
        static void Main(string[] args) {
            Console.WriteLine(Uri.EscapeUriString("http://127.0.0.1:15492"));
            Console.ReadLine();
//            var localUrl = "http://127.0.0.1:15492";
//            var remoteUrl = "https://mercurial.ramjetanvil.com/volo-master-server";
//            var client = new MasterServerClient(localUrl, requestTimeout: TimeSpan.FromSeconds(3));
////            client.ListHosts(hosts => {
////                Console.WriteLine("received host list, entries: ");
////                foreach (var host in hosts) {
////                    Console.WriteLine(host);
////                }
////            });
//            var defaultIpEndpoint = new IPEndPoint(IPAddress.Parse("127.0.0.1"), 5000);
//            var dummyAuthToken = new AuthToken("dummy", "dummy");
//            client.RegisterHost(dummyAuthToken, new HostRegistrationRequest("aap", 
//                new PeerInfo(defaultIpEndpoint, defaultIpEndpoint), 
//                shouldAdvertise: true,
//                version: "1.0"),
//                statusCode => {
//                    Console.WriteLine("Registration response " + statusCode);
////                    client.UnregisterHost(dummyAuthToken, defaultIpEndpoint, code => {
////                        Console.WriteLine("Unregistration response " + statusCode);
////                    });
//                });
//
//            client.Ping(dummyAuthToken, defaultIpEndpoint, statusCode => {
//                Console.WriteLine("ping " + statusCode);
//            });
//            Console.ReadLine();
        }
    }
}
