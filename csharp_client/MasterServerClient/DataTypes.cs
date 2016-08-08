using System;
using System.Collections.Generic;
using System.Net;

namespace RamjetAnvil.Padrone.Client {

    public class PeerInfo {
        private readonly IPEndPoint _externalEndpoint;
        private readonly IPEndPoint _internalEndpoint;

        public PeerInfo(IPEndPoint externalEndpoint, IPEndPoint internalEndpoint) {
            _externalEndpoint = externalEndpoint;
            _internalEndpoint = internalEndpoint;
        }

        public IPEndPoint ExternalEndpoint {
            get { return _externalEndpoint; }
        }

        public IPEndPoint InternalEndpoint {
            get { return _internalEndpoint; }
        }

        public override string ToString() {
            return string.Format("(ext:{0},int:{1})", _externalEndpoint, _internalEndpoint);
        }
    }

    public class RemoteHost {
        private readonly string _name;
        private readonly string _hostedBy;
        private readonly PeerInfo _peerInfo;
        private readonly DateTime _onlineSince;
        private readonly double _distanceInKm;
        private readonly string _country;
        private readonly string _version;
        private readonly int _playerCount;
        private readonly int _maxPlayers;

        public RemoteHost(string name, string hostedBy, PeerInfo peerInfo, DateTime onlineSince, 
            double distanceInKm, string country, string version, int playerCount, int maxPlayers) {
            _name = name;
            _peerInfo = peerInfo;
            _hostedBy = hostedBy;
            _onlineSince = onlineSince;
            _distanceInKm = distanceInKm;
            _country = country;
            _version = version;
            _playerCount = playerCount;
            _maxPlayers = maxPlayers;
        }

        public string Name {
            get { return _name; }
        }

        public string HostedBy {
            get { return _hostedBy; }
        }

        public PeerInfo PeerInfo {
            get { return _peerInfo; }
        }

        public DateTime OnlineSince {
            get { return _onlineSince; }
        }

        public double DistanceInKm {
            get { return _distanceInKm; }
        }

        public string Country {
            get { return _country; }
        }

        public string Version {
            get { return _version; }
        }

        public int PlayerCount {
            get { return _playerCount; }
        }

        public int MaxPlayers {
            get { return _maxPlayers; }
        }

        public override string ToString() {
            return string.Format("Name: {0}, PeerInfo: {1}, OnlineSince: {2}, DistanceInKm: {3}, Country: {4}, Version: {5}", _name, _peerInfo, _onlineSince, _distanceInKm, _country, _version);
        }
    }

    public class HostRegistrationRequest {
        private readonly string _hostName;
        private readonly PeerInfo _peerInfo;
        private readonly bool _shouldAdvertise;
        private readonly string _version;
        private readonly int _maxPlayers;

        public HostRegistrationRequest(string hostName, PeerInfo peerInfo, bool shouldAdvertise, string version, int maxPlayers) {
            _hostName = hostName;
            _peerInfo = peerInfo;
            _shouldAdvertise = shouldAdvertise;
            _version = version;
            _maxPlayers = maxPlayers;
        }

        public string HostName {
            get { return _hostName; }
        }

        public PeerInfo PeerInfo {
            get { return _peerInfo; }
        }

        public bool ShouldAdvertise {
            get { return _shouldAdvertise; }
        }

        public string Version {
            get { return _version; }
        }

        public int MaxPlayers {
            get { return _maxPlayers; }
        }
    }

    public class PingRequest {
        private readonly IPEndPoint _hostEndpoint;
        private readonly IList<ClientSessionId> _connectedClients; 

        public PingRequest(IPEndPoint hostEndpoint, IList<ClientSessionId> connectedClients) {
            _hostEndpoint = hostEndpoint;
            _connectedClients = connectedClients;
        }

        public IPEndPoint HostEndpoint {
            get { return _hostEndpoint; }
        }

        public IList<ClientSessionId> ConnectedClients {
            get { return _connectedClients; }
        }
    }

    public class JoinRequest {
        private readonly IPEndPoint _hostEndpoint;

        public JoinRequest(IPEndPoint hostEndpoint) {
            _hostEndpoint = hostEndpoint;
        }

        public IPEndPoint HostEndpoint {
            get { return _hostEndpoint; }
        }
    }

    public class JoinResponse {
        private readonly string _sessionId;
        private readonly string _secret;

        public JoinResponse(string sessionId, string secret) {
            _sessionId = sessionId;
            _secret = secret;
        }

        public string SessionId {
            get { return _sessionId; }
        }

        public string Secret {
            get { return _secret; }
        }
    }

    public class ReportLeaveRequest {
        private readonly IPEndPoint _hostEndpoint;
        private readonly ClientSessionId _sessionId;

        public ReportLeaveRequest(IPEndPoint hostEndpoint, ClientSessionId sessionId) {
            _hostEndpoint = hostEndpoint;
            _sessionId = sessionId;
        }

        public IPEndPoint HostEndpoint {
            get { return _hostEndpoint; }
        }

        public ClientSessionId SessionId {
            get { return _sessionId; }
        }
    }

    public struct ClientSessionId : IEquatable<ClientSessionId> {
        private readonly string _value;

        public ClientSessionId(string value) {
            _value = value;
        }

        public string Value {
            get { return _value; }
        }

        public bool Equals(ClientSessionId other) {
            return string.Equals(_value, other._value);
        }

        public override bool Equals(object obj) {
            if (ReferenceEquals(null, obj)) return false;
            return obj is ClientSessionId && Equals((ClientSessionId) obj);
        }

        public override int GetHashCode() {
            return (_value != null ? _value.GetHashCode() : 0);
        }

        public static bool operator ==(ClientSessionId left, ClientSessionId right) {
            return left.Equals(right);
        }

        public static bool operator !=(ClientSessionId left, ClientSessionId right) {
            return !left.Equals(right);
        }

        public override string ToString() {
            return string.Format("ClientSessionId({0})", _value);
        }
    }

    public struct ClientSecret : IEquatable<ClientSecret> {
        private readonly string _value;

        public ClientSecret(string value) {
            _value = value;
        }

        public string Value {
            get { return _value; }
        }

        public bool Equals(ClientSecret other) {
            return string.Equals(_value, other._value);
        }

        public override bool Equals(object obj) {
            if (ReferenceEquals(null, obj)) return false;
            return obj is ClientSecret && Equals((ClientSecret) obj);
        }

        public override int GetHashCode() {
            return (_value != null ? _value.GetHashCode() : 0);
        }

        public static bool operator ==(ClientSecret left, ClientSecret right) {
            return left.Equals(right);
        }

        public static bool operator !=(ClientSecret left, ClientSecret right) {
            return !left.Equals(right);
        }

        public override string ToString() {
            return string.Format("ClientSecret({0})", _value);
        }
    }

    public class PlayerInfo {
        private readonly string _name;
        private readonly string _avatarUrl;
        private readonly bool _isAdmin;
        private readonly bool _isDeveloper;

        public PlayerInfo(string name, string avatarUrl, bool isAdmin, bool isDeveloper) {
            _name = name;
            _avatarUrl = avatarUrl;
            _isAdmin = isAdmin;
            _isDeveloper = isDeveloper;
        }

        public string Name {
            get { return _name; }
        }

        public string AvatarUrl {
            get { return _avatarUrl; }
        }

        public bool IsAdmin {
            get { return _isAdmin; }
        }

        public bool IsDeveloper {
            get { return _isDeveloper; }
        }
    }

    public class PlayerSessionInfo {
        private readonly ClientSessionId _sessionId;
        private readonly ClientSecret _secret;
        private readonly PlayerInfo _playerInfo;

        public PlayerSessionInfo(ClientSessionId sessionId, ClientSecret secret, PlayerInfo playerInfo) {
            _sessionId = sessionId;
            _secret = secret;
            _playerInfo = playerInfo;
        }

        public ClientSessionId SessionId {
            get { return _sessionId; }
        }

        public ClientSecret Secret {
            get { return _secret; }
        }

        public PlayerInfo PlayerInfo {
            get { return _playerInfo; }
        }
    }

}
