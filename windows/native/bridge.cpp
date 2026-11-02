#include <libvirtualhid/libvirtualhid.hpp>
#include <algorithm>
#include <cstdint>
#include <exception>
#include <memory>
#include <string>

namespace {
thread_local std::string error;
struct Pad {
    // Destruction order matters: device must close before runtime.
    std::unique_ptr<lvh::Runtime> runtime;
    std::unique_ptr<lvh::Gamepad> device;
};
lvh::DeviceProfile profile_for(int profile) {
    switch (profile) {
    case 0: return lvh::profiles::xbox_360();
    case 1: return lvh::profiles::xbox_series();
    case 2: return lvh::profiles::dualshock4();
    case 3: return lvh::profiles::dualsense();
    case 4: return lvh::profiles::switch_pro();
    default: return lvh::profiles::generic_gamepad();
    }
}
}
#define API extern "C" __declspec(dllexport)
// Errors are UTF-8 and remain valid until the next failing call on this thread.
API const char* wc_error() noexcept { return error.c_str(); }
API void* wc_create(int profile, int slot) noexcept {
    try {
        if (profile < 0 || profile > 5 || slot < 0 || slot > 3) {
            error = "Invalid controller profile or slot"; return nullptr;
        }
        auto pad = std::make_unique<Pad>();
        lvh::RuntimeOptions runtime_options;
        runtime_options.backend = lvh::BackendKind::platform_default;
        pad->runtime = lvh::Runtime::create(runtime_options);
        lvh::CreateGamepadOptions options;
        options.profile = profile_for(profile);
        options.metadata.global_index = slot;
        options.metadata.client_relative_index = slot;
        options.metadata.client_type = lvh::ClientControllerType::unknown;
        options.metadata.stable_id = "wiiu-controller-slot-" + std::to_string(slot);
        auto result = pad->runtime->create_gamepad(options);
        if (!result) { error = result.status.message(); return nullptr; }
        pad->device = std::move(result.gamepad);
        return pad.release();
    } catch (const std::exception& e) { error = e.what(); }
    catch (...) { error = "Unknown Virtual HID device creation error"; }
    return nullptr;
}
API int wc_submit(void* handle, std::uint32_t buttons, float lx, float ly,
                  float rx, float ry, float lt, float rt) noexcept {
    try {
        if (!handle) { error = "Controller is closed"; return 0; }
        // Bridge mask is explicitly ordered and independent of SDK enum values.
        constexpr lvh::GamepadButton mapping[] = {
            lvh::GamepadButton::a, lvh::GamepadButton::b, lvh::GamepadButton::x,
            lvh::GamepadButton::y, lvh::GamepadButton::back, lvh::GamepadButton::start,
            lvh::GamepadButton::guide, lvh::GamepadButton::left_stick,
            lvh::GamepadButton::right_stick, lvh::GamepadButton::left_shoulder,
            lvh::GamepadButton::right_shoulder, lvh::GamepadButton::dpad_up,
            lvh::GamepadButton::dpad_down, lvh::GamepadButton::dpad_left,
            lvh::GamepadButton::dpad_right
        };
        lvh::GamepadState state;
        for (unsigned i = 0; i < 15; ++i) state.buttons.set(mapping[i], (buttons & (1U << i)) != 0);
        state.left_stick = {std::clamp(lx, -1.0F, 1.0F), std::clamp(ly, -1.0F, 1.0F)};
        state.right_stick = {std::clamp(rx, -1.0F, 1.0F), std::clamp(ry, -1.0F, 1.0F)};
        state.left_trigger = std::clamp(lt, 0.0F, 1.0F);
        state.right_trigger = std::clamp(rt, 0.0F, 1.0F);
        const auto result = static_cast<Pad*>(handle)->device->submit(state);
        if (result.ok()) return 1;
        error = result.message();
    } catch (const std::exception& e) { error = e.what(); }
    catch (...) { error = "Unknown Virtual HID submit error"; }
    return 0;
}
API void wc_close(void* handle) noexcept {
    try { delete static_cast<Pad*>(handle); } catch (...) { }
}
