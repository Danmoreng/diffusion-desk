#pragma once

#include "httplib.h"
#include <string>
#include <functional>

class Proxy {
public:
    static void forward_request(const httplib::Request& req, httplib::Response& res, const std::string& host, int port, const std::string& target_path = "");
};
