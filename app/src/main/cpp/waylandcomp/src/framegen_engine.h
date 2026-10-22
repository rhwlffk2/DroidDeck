#ifndef FRAMEGEN_ENGINE_H
#define FRAMEGEN_ENGINE_H
#include <stdint.h>
#ifndef VK_USE_PLATFORM_ANDROID_KHR
#define VK_USE_PLATFORM_ANDROID_KHR
#endif
#include <vulkan/vulkan.h>

#ifdef __cplusplus
extern "C" {
#endif

const void *fge_probe(PFN_vkGetInstanceProcAddr gipa, VkInstance inst, VkPhysicalDevice pd,
                      VkFormat ring_fmt);
void fge_device_ready(PFN_vkGetInstanceProcAddr gipa, VkInstance inst, VkPhysicalDevice pd,
                      VkDevice dev, VkQueue queue, uint32_t qfam, int features_enabled);
int fge_caps_ok(void);
const char *fge_caps_reason(void);
int fge_format_ok(VkFormat fmt);

int fge_start(void);
void fge_stop(void);
int fge_unavailable(void);
void fge_configure(uint32_t multiplier, float flow_scale, int model, int perf_preset);
int fge_prepare(uint32_t w, uint32_t h, VkFormat fmt);
uint32_t fge_plan(uint32_t capacity);
void fge_process(VkCommandBuffer cmd, VkImage source, uint32_t w, uint32_t h, uint32_t gens);
void fge_generate(VkCommandBuffer cmd, uint32_t g, uint32_t count, VkImage img, VkImageView view,
                  uint32_t w, uint32_t h);
const char *fge_engine_name(void);
const char *fge_build_info(void);

#ifdef __cplusplus
}
#endif
#endif
