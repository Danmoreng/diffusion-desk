#include "proxy.hpp"
#include "utils/common.hpp"
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

std::string generate_boundary() {
    static const char alphanum[] =
        "0123456789"
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "abcdefghijklmnopqrstuvwxyz";
    std::string boundary = "----WebKitFormBoundary";
    for (int i = 0; i < 16; ++i) {
        boundary += alphanum[rand() % (sizeof(alphanum) - 1)];
    }
    return boundary;
}

void reconstruct_multipart_body(const httplib::Request& req, std::string& body, std::string& content_type) {
    std::string boundary = generate_boundary();
    std::stringstream ss;

    DD_LOG_DEBUG("Reconstructing multipart body. Fields: %zu, Files: %zu", req.form.fields.size(), req.form.files.size());

    for (const auto& field : req.form.fields) {
        ss << "--" << boundary << "\r\n";
        ss << "Content-Disposition: form-data; name=\"" << field.first << "\"\r\n\r\n";
        ss << field.second.content << "\r\n";
    }

    for (const auto& file : req.form.files) {
        ss << "--" << boundary << "\r\n";
        ss << "Content-Disposition: form-data; name=\"" << file.first << "\"; filename=\"" << file.second.filename << "\"\r\n";
        ss << "Content-Type: " << file.second.content_type << "\r\n\r\n";
        ss << file.second.content << "\r\n";
    }

    ss << "--" << boundary << "--\r\n";
    body = ss.str();
    content_type = "multipart/form-data; boundary=" + boundary;
}

void Proxy::forward_request(const httplib::Request& req, httplib::Response& res, const std::string& host, int port, const std::string& target_path, const std::string& internal_token) {
    std::string path = target_path.empty() ? req.path : target_path;
    
    // Copy headers and remove hop-by-hop
    httplib::Headers headers = req.headers;
    headers.erase("Connection");
    headers.erase("Transfer-Encoding");
    headers.erase("Content-Length");
    headers.erase("Host");
    headers.erase("Content-Type"); // Remove Content-Type as it will be set by the Post call with correct boundary/type

    if (!internal_token.empty()) {
        headers.emplace("X-Internal-Token", internal_token);
    }

    if (!g_request_id.empty()) {
        headers.emplace("X-Request-ID", g_request_id);
    }

    // Handle Multipart Reconstruction
    std::string request_body = req.body;
    std::string request_content_type = req.get_header_value("Content-Type");

    if (req.is_multipart_form_data()) {
        reconstruct_multipart_body(req, request_body, request_content_type);
        // We must update the header passed to Client::Post
        // Note: headers map will be passed, but Client::Post takes content_type arg which overrides/sets Content-Type header
        // So we just need request_content_type to be correct.
    }

    // Detect if we should use streaming (SSE or long-running completion paths)
    bool is_stream_req = (request_body.find("\"stream\": true") != std::string::npos || 
                          request_body.find("\"stream\":true") != std::string::npos);
    
    bool is_sse_path = path.find("/progress") != std::string::npos;
    bool is_long_running = path.find("/llm/load") != std::string::npos;
    
    // Use streaming proxy ONLY for actual streams or specific long-running paths
    bool use_streaming = is_sse_path || (is_stream_req && path.find("/completions") != std::string::npos) || is_long_running;
    
    if (use_streaming) {
        DD_LOG_DEBUG("Using streaming proxy for %s", path.c_str());
        auto queue = std::make_shared<ChunkQueue>();
        auto status = std::make_shared<std::atomic<int>>(0);
        auto content_type = std::make_shared<std::string>("application/json");
        auto response_headers = std::make_shared<httplib::Headers>();

        // Start the client request in a background thread
        std::thread client_thread([host, port, path, headers, request_body, request_content_type, req, queue, status, content_type, response_headers, internal_token]() {
            httplib::Client cli(host, port);
            cli.set_connection_timeout(300, 0);
            cli.set_read_timeout(300, 0);
            cli.set_write_timeout(300, 0);

            // Define lambdas with explicit types to help overload resolution
            httplib::ContentReceiver content_receiver = [queue, path](const char *data, size_t data_length) {
                queue->push(std::string(data, data_length));
                return true;
            };

            if (req.method == "POST") {
                // Optimistic approach for POST: assume success and infer content type
                *status = 200;
                
                // Heuristic for Content-Type
                bool is_stream_req = (request_body.find("\"stream\": true") != std::string::npos || 
                                      request_body.find("\"stream\":true") != std::string::npos);
                
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
                cli.Post(path.c_str(), headers, request_body, request_content_type.c_str(), content_receiver);
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
            DD_LOG_ERROR("Proxy timeout waiting for headers from %s:%d%s", host.c_str(), port, path.c_str());
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
             result = cli.Post(path.c_str(), headers, request_body, request_content_type.c_str());
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
            DD_LOG_ERROR("Proxy failed to connect to worker at %s:%d", host.c_str(), port);
            res.status = 502;
            res.set_content("{\"error\":\"Proxy failed to connect to worker\"}", "application/json");
        }
    }
}
