#include "proxy.hpp"
#include <iostream>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <atomic>

// A simple thread-safe queue for the streaming bridge
class ChunkQueue {
public:
    void push(std::string chunk) {
        std::lock_guard<std::mutex> lock(mtx);
        q.push(std::move(chunk));
        cv.notify_one();
    }

    bool pop(std::string& chunk, bool wait) {
        std::unique_lock<std::mutex> lock(mtx);
        if (wait) {
            cv.wait(lock, [this] { return !q.empty() || done; });
        }
        if (q.empty()) return false;
        chunk = std::move(q.front());
        q.pop();
        return true;
    }

    void set_done() {
        std::lock_guard<std::mutex> lock(mtx);
        done = true;
        cv.notify_all();
    }

    bool is_done() {
        std::lock_guard<std::mutex> lock(mtx);
        return done && q.empty();
    }

private:
    std::queue<std::string> q;
    std::mutex mtx;
    std::condition_variable cv;
    bool done = false;
};

void Proxy::forward_request(const httplib::Request& req, httplib::Response& res, const std::string& host, int port, const std::string& target_path, const std::string& internal_token) {
    std::string path = target_path.empty() ? req.path : target_path;
    
    // Copy headers and remove hop-by-hop
    httplib::Headers headers = req.headers;
    headers.erase("Connection");
    headers.erase("Transfer-Encoding");
    headers.erase("Content-Length");
    headers.erase("Host");

    if (!internal_token.empty()) {
        headers.emplace("X-Internal-Token", internal_token);
    }

    // Detect if we should use streaming (SSE or long-running completion paths)
    bool is_completion = path.find("/completions") != std::string::npos || 
                         path.find("/progress") != std::string::npos ||
                         path.find("/llm/load") != std::string::npos;
    
    if (is_completion) {
        auto queue = std::make_shared<ChunkQueue>();
        auto status = std::make_shared<std::atomic<int>>(0);
        auto content_type = std::make_shared<std::string>("application/json");
        auto response_headers = std::make_shared<httplib::Headers>();

        // Start the client request in a background thread
        std::thread client_thread([host, port, path, headers, req, queue, status, content_type, response_headers, internal_token]() {
            httplib::Client cli(host, port);
            cli.set_connection_timeout(300, 0);
            cli.set_read_timeout(300, 0);
            cli.set_write_timeout(300, 0);

            // Define lambdas with explicit types to help overload resolution
            httplib::ContentReceiver content_receiver = [queue](const char *data, size_t data_length) {
                queue->push(std::string(data, data_length));
                return true;
            };

            if (req.method == "POST") {
                // Optimistic approach for POST: assume success and infer content type
                // because httplib::Client::Post doesn't support ResponseHandler overload.
                *status = 200;
                
                // Heuristic for Content-Type
                bool is_stream_req = (req.body.find("\"stream\": true") != std::string::npos || 
                                      req.body.find("\"stream\":true") != std::string::npos);
                
                if (path.find("/chat/completions") != std::string::npos || path.find("/completions") != std::string::npos) {
                     if (is_stream_req) {
                         *content_type = "text/event-stream";
                     } else {
                         *content_type = "application/json";
                     }
                } else {
                     *content_type = "application/json";
                }

                // POST with ContentReceiver only
                cli.Post(path.c_str(), headers, req.body, req.get_header_value("Content-Type").c_str(), content_receiver);
            } else {
                // GET supports ResponseHandler, so we can capture real headers
                httplib::ResponseHandler res_handler = [status, content_type, response_headers](const httplib::Response& response) {
                    *status = response.status;
                    *content_type = response.get_header_value("Content-Type");
                    for (const auto& h : response.headers) {
                        if (h.first != "Content-Length" && h.first != "Transfer-Encoding") {
                            response_headers->insert(h);
                        }
                    }
                    return true; 
                };
                
                // GET with ResponseHandler AND ContentReceiver
                cli.Get(path.c_str(), headers, res_handler, content_receiver);
            }
            queue->set_done();
        });
        client_thread.detach();

        // Wait a short bit for the status/headers to be populated
        // For POST, this is instant (we set it manually). For GET, we wait for headers.
        int retries = 600; 
        while (*status == 0 && retries-- > 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }

        if (*status == 0) {
            res.status = 504;
            res.set_content("{\"error\":\"Worker timeout during header wait\"}", "application/json");
            return;
        }

        res.status = *status;
        for (const auto& h : *response_headers) {
            res.set_header(h.first, h.second);
        }

        res.set_chunked_content_provider(*content_type, [queue](size_t /*offset*/, httplib::DataSink &sink) {
            std::string chunk;
            if (queue->pop(chunk, true)) {
                if (!sink.write(chunk.c_str(), chunk.size())) return false;
            }
            if (queue->is_done()) {
                sink.done();
                return false;
            }
            return true;
        });

    } else {
        // Standard buffering proxy for non-streaming endpoints
        httplib::Client cli(host, port);
        cli.set_connection_timeout(300, 0);
        cli.set_read_timeout(300, 0);
        cli.set_write_timeout(300, 0);

        httplib::Result result;
        if (req.method == "POST") {
             result = cli.Post(path.c_str(), headers, req.body, req.get_header_value("Content-Type").c_str());
        } else {
             result = cli.Get(path.c_str(), headers);
        }
        
        if (result) {
            res.status = result->status;
            res.set_content(result->body, result->get_header_value("Content-Type"));
            for (const auto& h : result->headers) {
                if (h.first != "Transfer-Encoding" && h.first != "Content-Length" && h.first != "Connection") {
                    res.set_header(h.first, h.second);
                }
            }
        } else {
            res.status = 502;
            res.set_content("{\"error\":\"Proxy failed to connect to worker\"}", "application/json");
        }
    }
}
