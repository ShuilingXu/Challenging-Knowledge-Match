package com.matrixlive.screen;

import static com.matrixlive.screen.ScreenModels.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.realtime.RealtimeEventBus;
import com.matrixlive.security.JwtTokenService;
import com.matrixlive.service.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreenService {
  private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
  private static final TypeReference<List<ScreenComponent>> COMPONENT_LIST = new TypeReference<>() { };
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

  private final ActivityRepository activities;
  private final ScreenTemplateRepository templates;
  private final ScreenDeviceRepository devices;
  private final ScreenActivityStateRepository activityStates;
  private final ObjectMapper objectMapper;
  private final RealtimeEventBus realtime;
  private final JwtTokenService tokenService;

  public ScreenService(ActivityRepository activities, ScreenTemplateRepository templates, ScreenDeviceRepository devices,
      ScreenActivityStateRepository activityStates, ObjectMapper objectMapper, RealtimeEventBus realtime,
      JwtTokenService tokenService) {
    this.activities = activities;
    this.templates = templates;
    this.devices = devices;
    this.activityStates = activityStates;
    this.objectMapper = objectMapper;
    this.realtime = realtime;
    this.tokenService = tokenService;
  }

  @Transactional
  public List<ScreenTemplateResponse> listTemplates(UUID activityId) {
    requireActivity(activityId);
    ensurePresetTemplates(activityId);
    return templates.findByActivityIdOrderByUpdatedAtDesc(activityId).stream().map(this::toTemplate).toList();
  }

  @Transactional
  public ScreenTemplateResponse createTemplate(UUID activityId, UpsertScreenTemplateRequest request) {
    requireActivity(activityId);
    ensurePresetTemplates(activityId);
    validateComponents(request.components());
    ScreenTemplate template = templates.save(new ScreenTemplate(activityId, clean(request.name()), cleanNullable(request.description()), false,
        writeJson(request.components())));
    ScreenTemplateResponse response = toTemplate(template);
    broadcastActivity(activityId, "screen.template.created", response);
    return response;
  }

  @Transactional(readOnly = true)
  public ScreenTemplateResponse getTemplate(UUID activityId, UUID templateId) {
    requireActivity(activityId);
    return toTemplate(requireTemplate(activityId, templateId));
  }

  @Transactional
  public ScreenTemplateResponse updateTemplate(UUID activityId, UUID templateId, UpsertScreenTemplateRequest request) {
    requireActivity(activityId);
    validateComponents(request.components());
    ScreenTemplate template = requireTemplate(activityId, templateId);
    template.update(clean(request.name()), cleanNullable(request.description()), writeJson(request.components()));
    ScreenTemplateResponse response = toTemplate(template);
    broadcastActivity(activityId, "screen.template.updated", response);
    broadcastTemplateConsumers(activityId, templateId, "screen.template.updated");
    return response;
  }

  @Transactional
  public void deleteTemplate(UUID activityId, UUID templateId) {
    requireActivity(activityId);
    ScreenTemplate template = requireTemplate(activityId, templateId);
    List<ScreenDevice> affected = devices.findByActivityIdAndCurrentTemplateId(activityId, templateId);
    for (ScreenDevice device : affected) {
      device.updateDisplay(null, ScreenDisplayMode.LOBBY, writeJson(Map.of()));
      broadcastDevice(activityId, device, "screen.display.updated", toDisplay(device));
    }
    templates.delete(template);
    broadcastActivity(activityId, "screen.template.deleted", Map.of("templateId", templateId));
  }

  @Transactional(readOnly = true)
  public List<ScreenComponentType> componentTypes(UUID activityId) {
    requireActivity(activityId);
    return List.of(ScreenComponentType.values());
  }

  @Transactional(readOnly = true)
  public List<ScreenDeviceResponse> listDevices(UUID activityId) {
    requireActivity(activityId);
    return devices.findByActivityIdOrderByLastSeenAtDesc(activityId).stream().map(this::toDevice).toList();
  }

  @Transactional(readOnly = true)
  public ScreenDeviceResponse getDevice(UUID activityId, UUID deviceId) {
    requireActivity(activityId);
    return toDevice(requireDevice(activityId, deviceId));
  }

  @Transactional
  public ScreenDeviceRegistration registerDevice(UUID activityId, RegisterScreenDeviceRequest request) {
    requireActivity(activityId);
    ensurePresetTemplates(activityId);
    String rawToken = generateToken();
    ScreenDevice device = new ScreenDevice(activityId, clean(request.name()), hashToken(rawToken), request.viewportWidth(), request.viewportHeight());
    ScreenTemplate initialTemplate = templates.findByActivityIdOrderByUpdatedAtDesc(activityId).stream()
        .filter(template -> "信息登记引导".equals(template.getName()))
        .findFirst()
        .orElse(null);
    if (initialTemplate != null) {
      device.updateDisplay(initialTemplate.getId(), ScreenDisplayMode.TEMPLATE,
          writeJson(Map.of("templateId", initialTemplate.getId(), "source", "device-registration")));
    }
    device = devices.save(device);
    ScreenDeviceResponse response = toDevice(device);
    broadcastActivity(activityId, "screen.device.registered", response);
    broadcastDevice(activityId, device, "screen.device.registered", toDisplay(device));
    return new ScreenDeviceRegistration(response, rawToken);
  }

  @Transactional
  public ScreenDeviceSession exchangePairingToken(UUID activityId, UUID deviceId, String pairingToken) {
    requireActivity(activityId);
    if (pairingToken == null || pairingToken.isBlank()) {
      throw new DomainException(HttpStatus.UNAUTHORIZED, "缺少大屏配对令牌");
    }
    ScreenDevice device = devices.findByIdAndDeviceTokenHash(deviceId, hashToken(pairingToken))
        .filter(item -> activityId.equals(item.getActivityId()))
        .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "大屏配对令牌无效"));
    JwtTokenService.IssuedAccessToken token = tokenService.issueScreenDeviceToken(activityId, deviceId);
    // Pairing material cannot be replayed after the device has received its scoped session token.
    device.rotatePairingToken(hashToken(generateToken()));
    return new ScreenDeviceSession(toDevice(device), token.value(), "Bearer", token.expiresAt());
  }

  @Transactional
  public ScreenDeviceRegistration rotatePairingToken(UUID activityId, UUID deviceId) {
    requireActivity(activityId);
    ScreenDevice device = requireDevice(activityId, deviceId);
    String rawToken = generateToken();
    device.rotatePairingToken(hashToken(rawToken));
    ScreenDeviceResponse response = toDevice(device);
    broadcastActivity(activityId, "screen.device.pairing-rotated", response);
    return new ScreenDeviceRegistration(response, rawToken);
  }

  @Transactional
  public ScreenDeviceResponse renameDevice(UUID activityId, UUID deviceId, RenameScreenDeviceRequest request) {
    requireActivity(activityId);
    ScreenDevice device = requireDevice(activityId, deviceId);
    device.rename(clean(request.name()));
    ScreenDeviceResponse response = toDevice(device);
    broadcastActivity(activityId, "screen.device.renamed", response);
    broadcastDevice(activityId, device, "screen.device.renamed", toDisplay(device));
    return response;
  }

  @Transactional
  public List<ScreenDisplayResponse> applyTemplate(UUID activityId, UUID templateId, ApplyScreenTemplateRequest request) {
    requireActivity(activityId);
    ScreenTemplate template = requireTemplate(activityId, templateId);
    List<ScreenDevice> targets = targetDevices(activityId, request.deviceIds());
    if (targets.isEmpty()) throw new DomainException(HttpStatus.BAD_REQUEST, "当前活动没有可下发内容的屏幕设备");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("templateId", template.getId());
    data.put("overrides", request.overrides() == null ? Map.of() : request.overrides());
    String payload = writeJson(data);
    List<ScreenDisplayResponse> responses = new ArrayList<>();
    for (ScreenDevice device : targets) {
      device.updateDisplay(template.getId(), ScreenDisplayMode.TEMPLATE, payload);
      ScreenDisplayResponse response = toDisplay(device);
      responses.add(response);
      broadcastDevice(activityId, device, "screen.display.updated", response);
    }
    broadcastActivity(activityId, "screen.template.applied", Map.of("templateId", templateId,
        "deviceIds", responses.stream().map(ScreenDisplayResponse::deviceId).toList()));
    return responses;
  }

  /**
   * Publishes a staff-controlled runtime view to every display device in an activity.
   * Screen clients are deliberately scoped to device topics, so activity-level events
   * must be fanned out here instead of relying on a broad public subscription.
   */
  @Transactional
  public List<ScreenDisplayResponse> publishActivityDisplay(UUID activityId, ScreenDisplayMode mode,
      Map<String, Object> data) {
    requireActivity(activityId);
    List<ScreenDisplayResponse> responses = new ArrayList<>();
    for (ScreenDevice device : devices.findByActivityIdOrderByLastSeenAtDesc(activityId)) {
      device.updateDisplay(null, mode, writeJson(data == null ? Map.of() : data));
      ScreenDisplayResponse response = toDisplay(device);
      responses.add(response);
      broadcastDevice(activityId, device, "screen.display.updated", response);
    }
    if (!responses.isEmpty()) {
      broadcastActivity(activityId, "screen.activity_display.updated", Map.of(
          "mode", mode.name(), "deviceIds", responses.stream().map(ScreenDisplayResponse::deviceId).toList()));
    }
    return responses;
  }

  @Transactional
  public ScreenDisplayResponse setDisplay(UUID activityId, UUID deviceId, SetScreenDisplayRequest request) {
    requireActivity(activityId);
    ScreenDevice device = requireDevice(activityId, deviceId);
    UUID templateId = request.templateId();
    if (request.mode() == ScreenDisplayMode.TEMPLATE) {
      if (templateId == null) throw new DomainException(HttpStatus.BAD_REQUEST, "模板展示模式必须提供 templateId");
      requireTemplate(activityId, templateId);
    } else if (templateId != null) {
      requireTemplate(activityId, templateId);
    }
    device.updateDisplay(templateId, request.mode(), writeJson(request.data() == null ? Map.of() : request.data()));
    ScreenDisplayResponse response = toDisplay(device);
    broadcastActivity(activityId, "screen.display.updated", response);
    broadcastDevice(activityId, device, "screen.display.updated", response);
    return response;
  }

  @Transactional
  public ScreenDeviceResponse updateSettings(UUID activityId, UUID deviceId, UpdateScreenSettingsRequest request) {
    requireActivity(activityId);
    if (request.fontScale() == null && request.volume() == null && request.scrollPosition() == null && request.autoScroll() == null) {
      throw new DomainException(HttpStatus.BAD_REQUEST, "至少提供一个大屏控制参数");
    }
    ScreenDevice device = requireDevice(activityId, deviceId);
    device.updateSettings(request.fontScale(), request.volume(), request.scrollPosition(), request.autoScroll());
    ScreenDeviceResponse response = toDevice(device);
    broadcastActivity(activityId, "screen.settings.updated", response);
    broadcastDevice(activityId, device, "screen.settings.updated", toDisplay(device));
    return response;
  }

  @Transactional
  public ScreenDeviceResponse markOffline(UUID activityId, UUID deviceId) {
    requireActivity(activityId);
    ScreenDevice device = requireDevice(activityId, deviceId);
    device.markOffline();
    ScreenDeviceResponse response = toDevice(device);
    broadcastActivity(activityId, "screen.device.offline", response);
    broadcastDevice(activityId, device, "screen.device.offline", toDisplay(device));
    return response;
  }

  @Transactional(readOnly = true)
  public ScreenDisplayResponse currentDisplay(UUID activityId, UUID deviceId) {
    requireActivity(activityId);
    return toDisplay(requireDevice(activityId, deviceId));
  }

  @Transactional
  public ScreenDeviceResponse heartbeat(UUID activityId, UUID deviceId, ScreenHeartbeatRequest request) {
    requireActivity(activityId);
    ScreenDevice device = requireDevice(activityId, deviceId);
    device.heartbeat(request.viewportWidth(), request.viewportHeight());
    ScreenDeviceResponse response = toDevice(device);
    broadcastActivity(device.getActivityId(), "screen.device.heartbeat", response);
    broadcastDevice(device.getActivityId(), device, "screen.device.heartbeat", toDisplay(device));
    return response;
  }

  private void ensurePresetTemplates(UUID activityId) {
    ScreenActivityState state = activityStates.findById(activityId).orElseGet(() -> activityStates.save(new ScreenActivityState(activityId)));
    if (state.isPresetsInitialized()) return;
    templates.saveAll(List.of(
        preset(activityId, "信息登记引导", "信息登记二维码与现场引导", List.of(
            component("background", ScreenComponentType.BACKGROUND, Map.of("color", "#091726")),
            component("headline", ScreenComponentType.TEXT, Map.of("text", "扫码登记，加入挑战")),
            component("registration-qr", ScreenComponentType.REGISTRATION_QR, Map.of("label", "扫描二维码登记信息")))),
        preset(activityId, "活动二维码", "展示当前活动入口二维码", List.of(
            component("background", ScreenComponentType.BACKGROUND, Map.of("color", "#123b3d")),
            component("headline", ScreenComponentType.TEXT, Map.of("text", "加入现场挑战")),
            component("activity-qr", ScreenComponentType.ACTIVITY_QR, Map.of("label", "扫描进入活动")))),
        preset(activityId, "题目直播", "适合题目、计时和答题进度展示", List.of(
            component("background", ScreenComponentType.BACKGROUND, Map.of("color", "#183850")),
            component("question-title", ScreenComponentType.TEXT, Map.of("text", "题目将由控场实时下发")))),
        preset(activityId, "积分排行榜", "适合实时积分和最终获奖名单", List.of(
            component("background", ScreenComponentType.BACKGROUND, Map.of("color", "#132439")),
            component("leaderboard-title", ScreenComponentType.TEXT, Map.of("text", "实时积分排行榜")))))
    );
    state.markPresetsInitialized();
  }

  private ScreenTemplate preset(UUID activityId, String name, String description, List<ScreenComponent> components) {
    return new ScreenTemplate(activityId, name, description, true, writeJson(components));
  }

  private ScreenComponent component(String id, ScreenComponentType type, Map<String, Object> config) {
    return new ScreenComponent(id, type, config);
  }

  private List<ScreenDevice> targetDevices(UUID activityId, List<UUID> deviceIds) {
    if (deviceIds == null || deviceIds.isEmpty()) return devices.findByActivityIdOrderByLastSeenAtDesc(activityId);
    Set<UUID> wanted = new HashSet<>(deviceIds);
    if (wanted.size() != deviceIds.size()) throw new DomainException(HttpStatus.BAD_REQUEST, "设备列表不能包含重复设备");
    List<ScreenDevice> found = devices.findAllById(wanted).stream()
        .filter(device -> activityId.equals(device.getActivityId()))
        .sorted(Comparator.comparing(ScreenDevice::getId))
        .toList();
    if (found.size() != wanted.size()) throw new DomainException(HttpStatus.NOT_FOUND, "存在不属于当前活动的大屏设备");
    return found;
  }

  private ScreenTemplate requireTemplate(UUID activityId, UUID templateId) {
    return templates.findById(templateId).filter(template -> activityId.equals(template.getActivityId()))
        .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "大屏模板不存在或不属于当前活动"));
  }

  private ScreenDevice requireDevice(UUID activityId, UUID deviceId) {
    return devices.findByIdAndActivityId(deviceId, activityId)
        .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "大屏设备不存在或不属于当前活动"));
  }

  private Activity requireActivity(UUID activityId) {
    return activities.findById(activityId).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "活动不存在"));
  }

  private void validateComponents(List<ScreenComponent> components) {
    if (components == null || components.isEmpty()) throw new DomainException(HttpStatus.BAD_REQUEST, "模板至少需要一个组件");
    if (components.size() > 30) throw new DomainException(HttpStatus.BAD_REQUEST, "模板最多支持 30 个组件");
    Set<String> ids = new HashSet<>();
    int backgrounds = 0;
    for (ScreenComponent component : components) {
      if (component == null || component.type() == null || component.id() == null || component.id().isBlank()) {
        throw new DomainException(HttpStatus.BAD_REQUEST, "模板组件缺少 id 或类型");
      }
      String id = component.id().trim();
      if (!ids.add(id)) throw new DomainException(HttpStatus.BAD_REQUEST, "模板组件 id 不能重复");
      Map<String, Object> config = component.config() == null ? Map.of() : component.config();
      switch (component.type()) {
        case TEXT -> requireConfigText(config, "text", "文字框需要 text 内容");
        case IMAGE -> requireConfigText(config, "url", "图片框需要 url");
        case FILE -> requireConfigText(config, "url", "文件框需要 url");
        case BACKGROUND -> {
          backgrounds++;
          if (!hasConfigText(config, "color") && !hasConfigText(config, "imageUrl")) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "背景设置需要 color 或 imageUrl");
          }
        }
        case ACTIVITY_QR, REGISTRATION_QR -> { }
      }
    }
    if (backgrounds > 1) throw new DomainException(HttpStatus.BAD_REQUEST, "一个模板只能设置一个背景组件");
  }

  private void requireConfigText(Map<String, Object> config, String key, String message) {
    if (!hasConfigText(config, key)) throw new DomainException(HttpStatus.BAD_REQUEST, message);
  }

  private boolean hasConfigText(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value instanceof String text && !text.isBlank();
  }

  private ScreenTemplateResponse toTemplate(ScreenTemplate template) {
    return new ScreenTemplateResponse(template.getId(), template.getActivityId(), template.getName(), template.getDescription(),
        template.isPreset(), readComponents(template.getComponentsJson()), template.getCreatedAt(), template.getUpdatedAt());
  }

  private ScreenDeviceResponse toDevice(ScreenDevice device) {
    return new ScreenDeviceResponse(device.getId(), device.getActivityId(), device.getName(), device.getViewportWidth(),
        device.getViewportHeight(), device.getCurrentTemplateId(), ScreenDisplayMode.valueOf(device.getDisplayMode()), device.getFontScale(),
        device.getVolume(), device.getScrollPosition(), device.isAutoScroll(), device.getStatus(), device.getLastSeenAt(),
        device.getCreatedAt(), device.getUpdatedAt());
  }

  private ScreenDisplayResponse toDisplay(ScreenDevice device) {
    ScreenTemplateResponse template = device.getCurrentTemplateId() == null ? null : templates.findById(device.getCurrentTemplateId())
        .filter(item -> device.getActivityId().equals(item.getActivityId()))
        .map(this::toTemplate)
        .orElse(null);
    return new ScreenDisplayResponse(device.getId(), device.getActivityId(), device.getName(), device.getCurrentTemplateId(), template,
        ScreenDisplayMode.valueOf(device.getDisplayMode()), readMap(device.getDisplayPayloadJson()), device.getFontScale(), device.getVolume(),
        device.getScrollPosition(), device.isAutoScroll(), device.getUpdatedAt());
  }

  private void broadcastTemplateConsumers(UUID activityId, UUID templateId, String eventType) {
    for (ScreenDevice device : devices.findByActivityIdAndCurrentTemplateId(activityId, templateId)) {
      broadcastDevice(activityId, device, eventType, toDisplay(device));
    }
  }

  private void broadcastActivity(UUID activityId, String type, Object payload) {
    realtime.send("/topic/activities/" + activityId + "/screens", new ScreenEvent(type, "ACTIVITY", payload, Instant.now()));
  }

  private void broadcastDevice(UUID activityId, ScreenDevice device, String type, Object payload) {
    ScreenEvent event = new ScreenEvent(type, "DEVICE", payload, Instant.now());
    realtime.send("/topic/activities/" + activityId + "/screens", event);
    realtime.send("/topic/screens/" + device.getId(), event);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new DomainException(HttpStatus.BAD_REQUEST, "大屏配置无法序列化");
    }
  }

  private List<ScreenComponent> readComponents(String json) {
    try {
      return objectMapper.readValue(json, COMPONENT_LIST);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored screen template is invalid", exception);
    }
  }

  private Map<String, Object> readMap(String json) {
    try {
      return objectMapper.readValue(json, MAP);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored screen display payload is invalid", exception);
    }
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    TOKEN_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashToken(String rawToken) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String clean(String value) { return value.trim(); }
  private String cleanNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
