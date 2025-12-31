#include "ws_manager.hpp"
#include <iostream>

namespace mysti {

WsManager::WsManager(int port, const std::string& host)
    : _port(port), _host(host) {
    // Pass explicit values to avoid LNK2001 on static constants in some builds
    _server = std::make_unique<ix::WebSocketServer>(
        _port, 
        _host,
        64,    // backlog
        100,   // maxConnections
        10,    // handshakeTimeoutSecs
        2,     // addressFamily (AF_INET)
        30     // pingIntervalSeconds
    );

    _server->setOnClientMessageCallback(
        [this](std::shared_ptr<ix::ConnectionState> connectionState,
               ix::WebSocket& webSocket,
               const ix::WebSocketMessagePtr& msg) {
            onMessage(connectionState, webSocket, msg);
        }
    );
}

WsManager::~WsManager() {
    stop();
}

bool WsManager::start() {
    auto res = _server->listen();
    if (!res.first) {
        LOG_ERROR("WebSocket server failed to listen on port %d: %s", _port, res.second.c_str());
        return false;
    }
    _server->start();
    LOG_INFO("WebSocket server started on %s:%d", _host.c_str(), _port);
    return true;
}

void WsManager::stop() {
    _server->stop();
}

void WsManager::broadcast(const json& msg) {
    std::string text = msg.dump();
    for (auto client : _server->getClients()) {
        client->send(text);
    }
}

void WsManager::onMessage(std::shared_ptr<ix::ConnectionState> connectionState, ix::WebSocket& webSocket, const ix::WebSocketMessagePtr& msg) {
    if (msg->type == ix::WebSocketMessageType::Open) {
        LOG_DEBUG("New WebSocket client connected.");
    } else if (msg->type == ix::WebSocketMessageType::Close) {
        LOG_DEBUG("WebSocket client disconnected.");
    } else if (msg->type == ix::WebSocketMessageType::Message) {
        // Handle incoming messages from clients if needed
        LOG_DEBUG("Received message from WS client: %s", msg->str.c_str());
    }
}

} // namespace mysti
