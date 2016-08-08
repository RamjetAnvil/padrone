using System;
using System.Collections.Generic;
using System.Net;
using Newtonsoft.Json;

namespace RamjetAnvil.Padrone.Client {
    public static class JsonProtocol {

        public class IPEndpointConverter : JsonConverter {
            public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer) {
                var ipEndpoint = (IPEndPoint)value;
                serializer.Serialize(writer, new Dictionary<string, object> {
                    {"address", ipEndpoint.Address.ToString()},
                    {"port", ipEndpoint.Port}
                });
            }

            public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer) {
                var ipEndpoint = serializer.Deserialize<IDictionary<string, object>>(reader);
                return new IPEndPoint(IPAddress.Parse(Convert.ToString(ipEndpoint["address"])), Convert.ToInt32(ipEndpoint["port"]));
            }

            public override bool CanConvert(Type objectType) {
                return objectType == typeof (IPEndPoint);
            }
        }

        public class ClientSessionIdConverter : JsonConverter {
            public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer) {
                var sessionId = (ClientSessionId)value;
                serializer.Serialize(writer, sessionId.Value);
            }

            public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer) {
                var sessionId = serializer.Deserialize<string>(reader);
                return new ClientSessionId(sessionId);
            }

            public override bool CanConvert(Type objectType) {
                return objectType == typeof (ClientSessionId);
            }
        }

        public class ClientSecretConverter : JsonConverter {
            public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer) {
                var sessionId = (ClientSecret)value;
                serializer.Serialize(writer, sessionId.Value);
            }

            public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer) {
                var sessionId = serializer.Deserialize<string>(reader);
                return new ClientSecret(sessionId);
            }

            public override bool CanConvert(Type objectType) {
                return objectType == typeof (ClientSecret);
            }
        }
    }
}
