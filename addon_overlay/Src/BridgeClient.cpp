#include "BridgeClient.hpp"
#ifdef WINDOWS
#include <windows.h>
#include <winhttp.h>
#include <shellapi.h>
#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "shell32.lib")
#endif
#include <fstream>
#include <vector>

namespace AIViz {
#ifdef WINDOWS
static bool Request(const wchar_t* method, const wchar_t* path, const std::vector<unsigned char>* body, const wchar_t* contentType, DWORD& status)
{
    status = 0;
    HINTERNET session = WinHttpOpen(L"Archicad25AIVisualizer/1.0", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                                    WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!session) return false;
    WinHttpSetTimeouts(session, 3000, 3000, 3000, 600000);
    HINTERNET connect = WinHttpConnect(session, L"127.0.0.1", 8099, 0);
    if (!connect) { WinHttpCloseHandle(session); return false; }
    HINTERNET request = WinHttpOpenRequest(connect, method, path, nullptr, WINHTTP_NO_REFERER,
                                           WINHTTP_DEFAULT_ACCEPT_TYPES, 0);
    if (!request) { WinHttpCloseHandle(connect); WinHttpCloseHandle(session); return false; }
    std::wstring headers;
    if (contentType != nullptr) {
        headers = L"Content-Type: "; headers += contentType; headers += L"\r\n";
    }
    LPVOID ptr = WINHTTP_NO_REQUEST_DATA; DWORD len = 0;
    if (body != nullptr && !body->empty()) { ptr = const_cast<unsigned char*>(body->data()); len = static_cast<DWORD>(body->size()); }
    BOOL ok = WinHttpSendRequest(request,
                                 headers.empty() ? WINHTTP_NO_ADDITIONAL_HEADERS : headers.c_str(),
                                 headers.empty() ? 0 : static_cast<DWORD>(headers.size()),
                                 ptr, len, len, 0);
    if (ok) ok = WinHttpReceiveResponse(request, nullptr);
    if (ok) {
        DWORD size = sizeof(status);
        WinHttpQueryHeaders(request, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                            WINHTTP_HEADER_NAME_BY_INDEX, &status, &size, WINHTTP_NO_HEADER_INDEX);
    }
    WinHttpCloseHandle(request); WinHttpCloseHandle(connect); WinHttpCloseHandle(session);
    return ok == TRUE;
}
#endif

bool CheckServer(std::string& message)
{
#ifdef WINDOWS
    DWORD status = 0; bool ok = Request(L"GET", L"/health", nullptr, nullptr, status);
    if (!ok) { message = "AI server is not reachable on 127.0.0.1:8099"; return false; }
    message = "HTTP " + std::to_string(status); return status == 200;
#else
    message = "Windows only"; return false;
#endif
}

bool UploadCapture(const std::wstring& filePath, std::string& message)
{
#ifdef WINDOWS
    std::ifstream f(filePath, std::ios::binary);
    if (!f) { message = "Cannot read exported PNG"; return false; }
    std::vector<unsigned char> data((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
    if (data.empty()) { message = "Exported PNG is empty"; return false; }
    DWORD status = 0; bool ok = Request(L"POST", L"/capture", &data, L"image/png", status);
    message = "HTTP " + std::to_string(status); return ok && status == 200;
#else
    message = "Windows only"; return false;
#endif
}

void OpenUi()
{
#ifdef WINDOWS
    ShellExecuteW(nullptr, L"open", L"http://127.0.0.1:8099/ui", nullptr, nullptr, SW_SHOWNORMAL);
#endif
}

void ShowMessage(const wchar_t* title, const std::wstring& message, bool error)
{
#ifdef WINDOWS
    MessageBoxW(nullptr, message.c_str(), title, MB_OK | (error ? MB_ICONERROR : MB_ICONINFORMATION));
#endif
}
}
