#pragma once
#include <string>
namespace AIViz {
    bool CheckServer(std::string& message);
    bool UploadCapture(const std::wstring& filePath, std::string& message);
    void OpenUi();
    void ShowMessage(const wchar_t* title, const std::wstring& message, bool error = false);
}
