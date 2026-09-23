import time
from pathlib import Path


def _require_windows():
    import os
    if os.name != "nt":
        raise RuntimeError("Захват окна Archicad поддерживается только в Windows")


def _find_archicad_window():
    _require_windows()
    import win32gui

    candidates = []

    def enum(hwnd, _):
        if not win32gui.IsWindowVisible(hwnd):
            return
        title = win32gui.GetWindowText(hwnd) or ""
        upper = title.upper()
        if "ARCHICAD" not in upper:
            return
        try:
            l, t, r, b = win32gui.GetWindowRect(hwnd)
            area = max(0, r - l) * max(0, b - t)
            if area > 200_000:
                candidates.append((area, hwnd, title))
        except Exception:
            pass

    win32gui.EnumWindows(enum, None)
    if not candidates:
        raise RuntimeError("Не найдено открытое окно Archicad")
    candidates.sort(reverse=True)
    return candidates[0][1], candidates[0][2]


def _largest_viewport_rect(main_hwnd):
    import win32gui

    ml, mt, mr, mb = win32gui.GetClientRect(main_hwnd)
    p1 = win32gui.ClientToScreen(main_hwnd, (ml, mt))
    p2 = win32gui.ClientToScreen(main_hwnd, (mr, mb))
    main_rect = (p1[0], p1[1], p2[0], p2[1])
    main_area = max(1, (p2[0] - p1[0]) * (p2[1] - p1[1]))

    children = []

    def enum_child(hwnd, _):
        if not win32gui.IsWindowVisible(hwnd):
            return
        try:
            l, t, r, b = win32gui.GetWindowRect(hwnd)
            w, h = r - l, b - t
            area = max(0, w) * max(0, h)
            if area > main_area * 0.25 and w > 500 and h > 300:
                children.append((area, (l, t, r, b), hwnd, win32gui.GetClassName(hwnd)))
        except Exception:
            pass

    win32gui.EnumChildWindows(main_hwnd, enum_child, None)
    if children:
        children.sort(reverse=True, key=lambda x: x[0])
        for area, rect, hwnd, cls in children:
            if area < main_area * 0.98:
                return rect
        return children[0][1]
    return main_rect


def capture_archicad(output_path, viewport_only=True, restore_focus=True):
    _require_windows()
    import win32gui
    import win32con
    import mss
    from PIL import Image

    hwnd, title = _find_archicad_window()
    previous = win32gui.GetForegroundWindow()
    try:
        win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
        try:
            win32gui.SetForegroundWindow(hwnd)
        except Exception:
            pass
        time.sleep(0.6)

        rect = _largest_viewport_rect(hwnd) if viewport_only else win32gui.GetWindowRect(hwnd)
        l, t, r, b = rect
        if r <= l or b <= t:
            raise RuntimeError("Не удалось определить область окна Archicad")
        monitor = {"left": l, "top": t, "width": r - l, "height": b - t}
        with mss.mss() as sct:
            shot = sct.grab(monitor)
            img = Image.frombytes("RGB", shot.size, shot.rgb)
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        img.save(output_path, "PNG")
        return {"path": str(output_path), "title": title, "size": img.size, "rect": rect}
    finally:
        if restore_focus and previous and previous != hwnd:
            try:
                win32gui.SetForegroundWindow(previous)
            except Exception:
                pass
