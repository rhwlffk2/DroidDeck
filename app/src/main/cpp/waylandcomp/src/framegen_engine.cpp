#include "framegen_engine.h"

#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include <android/log.h>

#include "VulkanRendererContext.h"
#include "fg_probe.h"
#include "ddfg/ddfg_engine.h"

#define ELOG(...) __android_log_print(ANDROID_LOG_INFO, "DroidDeckWayland", "framegen: " __VA_ARGS__)
#define ELOGE(...) __android_log_print(ANDROID_LOG_ERROR, "DroidDeckWayland", "framegen: " __VA_ARGS__)

namespace {

VkTable g_table{};
fgcaps::Caps g_caps{};
VkPhysicalDeviceVulkan12Features g_v12{};
VkPhysicalDeviceFeatures2 g_f2{};
VkPhysicalDevice g_pd = VK_NULL_HANDLE;
VkDevice g_dev = VK_NULL_HANDLE;
VkQueue g_queue = VK_NULL_HANDLE;
uint32_t g_qfam = 0;
bool g_probed = false, g_ready = false;

std::unique_ptr<ddfg::Engine> g_ddfg;
char g_build[96] = "";

#define FGE_INSTANCE_FUNCS(X)                                                              \
    X(GetPhysicalDeviceProperties) X(GetPhysicalDeviceMemoryProperties)                    \
    X(GetPhysicalDeviceFeatures2) X(GetPhysicalDeviceFormatProperties)                     \
    X(GetPhysicalDeviceQueueFamilyProperties) X(GetDeviceProcAddr)
#define FGE_DEVICE_FUNCS(X)                                                                \
    X(AllocateDescriptorSets) X(AllocateMemory) X(BindBufferMemory) X(BindImageMemory)     \
    X(CmdBindDescriptorSets) X(CmdBindPipeline) X(CmdCopyImage) X(CmdDispatch)             \
    X(CmdPipelineBarrier) X(CreateBuffer) X(CreateComputePipelines) X(CreateDescriptorPool)  \
    X(CreateDescriptorSetLayout) X(CreateImage) X(CreateImageView) X(CreatePipelineLayout)  \
    X(CreateSampler) X(CreateShaderModule) X(DestroyBuffer) X(DestroyDescriptorPool)        \
    X(DestroyDescriptorSetLayout) X(DestroyImage) X(DestroyImageView) X(DestroyPipeline)    \
    X(DestroyPipelineLayout) X(DestroySampler) X(DestroyShaderModule) X(FreeMemory)         \
    X(GetBufferMemoryRequirements) X(GetImageMemoryRequirements) X(MapMemory) X(UnmapMemory) \
    X(UpdateDescriptorSets) X(CmdClearColorImage) X(DeviceWaitIdle) X(ResetDescriptorPool)  \
    X(QueueSubmit) X(CreateFence) X(DestroyFence) X(WaitForFences)        \
    X(ResetFences) X(GetFenceStatus) X(CreateSemaphore) X(DestroySemaphore)                 \
    X(CreateCommandPool) X(DestroyCommandPool) X(AllocateCommandBuffers)                    \
    X(FreeCommandBuffers) X(BeginCommandBuffer) X(EndCommandBuffer) X(ResetCommandBuffer)   \
    X(CmdBlitImage) X(CmdCopyBufferToImage) X(FreeDescriptorSets) X(FlushMappedMemoryRanges) \
    X(GetDeviceQueue) X(CreateQueryPool) X(DestroyQueryPool) X(CmdResetQueryPool)           \
    X(CmdWriteTimestamp) X(GetQueryPoolResults)

void fill_instance(PFN_vkGetInstanceProcAddr gipa, VkInstance inst) {
#define X(n) g_table.n = reinterpret_cast<PFN_vk##n>(gipa(inst, "vk" #n));
    FGE_INSTANCE_FUNCS(X)
#undef X
}

void fill_device(VkDevice dev) {
    if (!g_table.GetDeviceProcAddr) return;
#define X(n) g_table.n = reinterpret_cast<PFN_vk##n>(g_table.GetDeviceProcAddr(dev, "vk" #n));
    FGE_DEVICE_FUNCS(X)
#undef X
}

}

extern "C" {

const void *fge_probe(PFN_vkGetInstanceProcAddr gipa, VkInstance inst, VkPhysicalDevice pd,
                      VkFormat ring_fmt) {
    g_pd = pd;
    fill_instance(gipa, inst);
    std::vector<VkExtensionProperties> exts;
    auto enumExts = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
        gipa(inst, "vkEnumerateDeviceExtensionProperties"));
    if (enumExts) {
        uint32_t n = 0;
        enumExts(pd, nullptr, &n, nullptr);
        exts.resize(n);
        if (n) enumExts(pd, nullptr, &n, exts.data());
    }
    g_caps = fgcaps::Caps{};
    g_caps.features = fgcaps::queryFeatures(g_table, pd, exts, false);
    g_caps.probedFormat = ring_fmt;
    g_caps.storageOnSwapchainFormat = g_table.GetPhysicalDeviceFormatProperties
        ? fgcaps::probeStorageFormat(g_table, pd, ring_fmt) : false;
    g_caps.linearBlitOnSwapchainFormat = g_table.GetPhysicalDeviceFormatProperties
        ? fgcaps::probeLinearBlit(g_table, pd, ring_fmt) : false;
    g_probed = true;
    if (!g_caps.features.deviceGatesPass()) {
        fgcaps::explain(g_caps);
        ELOG("device gates for frame generation fail: %s", g_caps.reason);
        return nullptr;
    }
    g_v12 = VkPhysicalDeviceVulkan12Features{};
    g_v12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
    g_v12.vulkanMemoryModel = VK_TRUE;
    g_v12.vulkanMemoryModelDeviceScope =
        g_caps.features.vulkanMemoryModelDeviceScope ? VK_TRUE : VK_FALSE;
    g_f2 = VkPhysicalDeviceFeatures2{};
    g_f2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
    g_f2.pNext = &g_v12;
    g_f2.features.shaderStorageImageWriteWithoutFormat = VK_TRUE;
    g_f2.features.shaderStorageImageExtendedFormats = VK_TRUE;
    return &g_f2;
}

void fge_device_ready(PFN_vkGetInstanceProcAddr gipa, VkInstance inst, VkPhysicalDevice pd,
                      VkDevice dev, VkQueue queue, uint32_t qfam, int features_enabled) {
    if (!g_probed) fill_instance(gipa, inst);
    g_pd = pd; g_dev = dev; g_queue = queue; g_qfam = qfam;
    fill_device(dev);
    g_caps.featuresEnabled = features_enabled != 0;
    fgcaps::explain(g_caps);
    g_ready = true;
    ELOG("device ready: caps '%s', storage on ring format %d", g_caps.reason,
         (int)g_caps.storageOnSwapchainFormat);
}

int fge_caps_ok(void) {
    if (!g_ready) return 0;
    return g_caps.storageOnSwapchainFormat ? 1 : 0;
}

const char *fge_caps_reason(void) { return g_caps.reason; }

int fge_format_ok(VkFormat fmt) {
    if (!g_ready || !g_table.GetPhysicalDeviceFormatProperties || fmt == VK_FORMAT_UNDEFINED) return 0;
    return fgcaps::probeStorageFormat(g_table, g_pd, fmt) && fgcaps::probeLinearBlit(g_table, g_pd, fmt) ? 1 : 0;
}

int fge_start(void) {
    fge_stop();
    if (!g_ready) return -1;
    auto e = std::make_unique<ddfg::Engine>();
    if (!e->init(g_table, g_pd, g_dev, g_qfam, g_queue)) {
        ELOGE("ddfg-native: engine init failed");
        return -1;
    }
    g_ddfg = std::move(e);
#if defined(DDFG_UPSTREAM) && defined(DDFG_CHAIN_HASH)
    std::snprintf(g_build, sizeof(g_build), "chain %s, src %s", DDFG_UPSTREAM, DDFG_CHAIN_HASH);
#else
    std::snprintf(g_build, sizeof(g_build), "embedded chain");
#endif
    return 0;
}

void fge_stop(void) {
    if (g_ddfg && g_table.DeviceWaitIdle && g_dev) g_table.DeviceWaitIdle(g_dev);
    g_ddfg.reset();
}

int fge_unavailable(void) {
    return g_ddfg && g_ddfg->unavailable() ? 1 : 0;
}

void fge_configure(uint32_t multiplier, float flow_scale, int model, int perf_preset) {
    if (multiplier < 2) multiplier = 2;
    if (g_ddfg) g_ddfg->configure(multiplier, model, perf_preset, flow_scale);
}

int fge_prepare(uint32_t w, uint32_t h, VkFormat fmt) {
    return g_ddfg && g_ddfg->prepare(w, h, fmt) ? 1 : 0;
}

uint32_t fge_plan(uint32_t capacity) {
    return g_ddfg ? g_ddfg->plan(capacity) : 0;
}

void fge_process(VkCommandBuffer cmd, VkImage source, uint32_t w, uint32_t h, uint32_t gens) {
    if (g_ddfg) g_ddfg->process(cmd, source, w, h, gens);
}

void fge_generate(VkCommandBuffer cmd, uint32_t g, uint32_t count, VkImage img, VkImageView view,
                  uint32_t w, uint32_t h) {
    if (g_ddfg) g_ddfg->generateInto(cmd, g, count, img, view, w, h);
}

const char *fge_engine_name(void) { return "DD-FG Native"; }
const char *fge_build_info(void) { return g_build; }

}
