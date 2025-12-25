#pragma once

#include <ixwebsocket/IXWebSocketServer.h>
#include <ixwebsocket/IXWebSocket.h>
#include <string>
#include <mutex>
#include <set>
#include <memory>
#include "utils/common.hpp"

namespace mysti {

class WsManager {
public:
    WsManager(int port, const std::string& host = "127.0.0.1");
    ~WsManager();

    bool start();
    void stop();

    void broadcast(const json& msg);

private:
    int _port;
    std::string _host;
    std::unique_ptr<ix::WebSocketServer> _server;

    void onMessage(std::shared_ptr<ix::ConnectionState> connectionState, ix::WebSocket& webSocket, const ix::WebSocketMessagePtr& msg);
};

} // namespace mysti
