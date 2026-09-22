#include "APIEnvir.h"
#include "ACAPinc.h"
#include "ResourceIds.hpp"
#include "BridgeClient.hpp"
#include "RS.hpp"
#ifdef WINDOWS
#include <windows.h>
#endif

static const GSResID AddOnInfoID = ID_ADDON_INFO;
static const Int32 AddOnNameID = 1;
static const Int32 AddOnDescriptionID = 2;
static const short AddOnMenuID = ID_ADDON_MENU;

static std::wstring GetCapturePath()
{
#ifdef WINDOWS
    wchar_t tempPath[MAX_PATH] = {};
    GetTempPathW(MAX_PATH, tempPath);
    std::wstring folder(tempPath); folder += L"ArchicadAIVisualizer\\";
    CreateDirectoryW(folder.c_str(), nullptr);
    return folder + L"archicad_current_view.png";
#else
    return L"archicad_current_view.png";
#endif
}

static GSErrCode ExportCurrentView(const std::wstring& path)
{
    API_FileSavePars fsp = {};
    API_SavePars_Picture picture = {};
    fsp.fileTypeID = APIFType_PNGFile;
    IO::Location location(GS::UniString(path.c_str()));
    fsp.file = &location;
    picture.colorDepth = APIColorDepth_256C;
    picture.dithered = false;
    picture.view2D = false;
    picture.crop = true;
    GSErrCode err = ACAPI_Automate(APIDo_SaveID, &fsp, &picture);
    if (err != NoError) {
        picture.view2D = true;
        err = ACAPI_Automate(APIDo_SaveID, &fsp, &picture);
    }
    return err;
}

static void StartVisualizer()
{
    std::string status;
    if (!AIViz::CheckServer(status)) {
        AIViz::ShowMessage(L"AI Visualizer", L"Локальный AI-сервер не запущен.\n\nСначала запустите START_AI_SERVER.bat из папки server.", true);
        return;
    }
    const std::wstring capturePath = GetCapturePath();
    GSErrCode err = ExportCurrentView(capturePath);
    if (err != NoError) {
        AIViz::ShowMessage(L"AI Visualizer", L"Не удалось экспортировать текущий вид Archicad. Откройте 3D-окно или план и повторите.", true);
        return;
    }
    if (!AIViz::UploadCapture(capturePath, status)) {
        AIViz::ShowMessage(L"AI Visualizer", L"PNG создан, но не удалось передать его AI-серверу.", true);
        return;
    }
    AIViz::OpenUi();
}

static GSErrCode MenuCommandHandler(const API_MenuParams* menuParams)
{
    if (menuParams->menuItemRef.menuResID != AddOnMenuID) return NoError;
    switch (menuParams->menuItemRef.itemIndex) {
        case 1: StartVisualizer(); break;
        case 2: {
            std::string status; bool ok = AIViz::CheckServer(status);
            AIViz::ShowMessage(L"AI Visualizer", ok ? L"AI-сервер доступен." : L"AI-сервер недоступен.", !ok);
            break;
        }
        case 3: AIViz::OpenUi(); break;
        default: break;
    }
    return NoError;
}

API_AddonType CheckEnvironment(API_EnvirParams* envir)
{
    RSGetIndString(&envir->addOnInfo.name, AddOnInfoID, AddOnNameID, ACAPI_GetOwnResModule());
    RSGetIndString(&envir->addOnInfo.description, AddOnInfoID, AddOnDescriptionID, ACAPI_GetOwnResModule());
    return APIAddon_Normal;
}
GSErrCode RegisterInterface(void) { return ACAPI_Register_Menu(AddOnMenuID, 0, MenuCode_Tools, MenuFlag_Default); }
GSErrCode Initialize(void) { return ACAPI_Install_MenuHandler(AddOnMenuID, MenuCommandHandler); }
GSErrCode FreeData(void) { return NoError; }
