package com.powerpredict.plantservice.service;

import com.powerpredict.plantservice.domain.PlantProfile;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InMemoryPlantDomainService implements PlantDomainService {
  private final Map<String, PlantProfile> store = new LinkedHashMap<>();

  public InMemoryPlantDomainService() {
    OffsetDateTime now = OffsetDateTime.now();
    PlantProfile seed = new PlantProfile(
        "plant-demo-001",
        "Demo Plant 001",
        50,
        31.2304,
        121.4737,
        8.5,
        25.0,
        180.0,
        "Asia/Shanghai",
        "active",
        now,
        now);
    store.put(seed.plantId(), seed);
  }

  @Override
  public synchronized PlantPageResult list(PlantListQuery query) {
    String keywordNorm = query.keyword() == null ? "" : query.keyword().trim().toLowerCase(Locale.ROOT);
    String statusNorm = query.status() == null ? "" : query.status().trim().toLowerCase(Locale.ROOT);
    String timezoneNorm = query.timezone() == null ? "" : query.timezone().trim().toLowerCase(Locale.ROOT);

    List<PlantProfile> items = new ArrayList<>();
    for (PlantProfile item : store.values()) {
      boolean matchKeyword = keywordNorm.isEmpty()
          || item.plantId().toLowerCase(Locale.ROOT).contains(keywordNorm)
          || item.plantName().toLowerCase(Locale.ROOT).contains(keywordNorm);
      boolean matchStatus = statusNorm.isEmpty() || item.status().toLowerCase(Locale.ROOT).equals(statusNorm);
      boolean matchTimezone = timezoneNorm.isEmpty() || item.timezone().toLowerCase(Locale.ROOT).contains(timezoneNorm);
      if (matchKeyword && matchStatus && matchTimezone) {
        items.add(item);
      }
    }
    items.sort(Comparator.comparing(PlantProfile::updatedAt).reversed());
    int pageNo = Math.max(1, query.pageNo());
    int pageSize = Math.max(1, query.pageSize());
    int from = Math.max(0, (pageNo - 1) * pageSize);
    int to = Math.min(items.size(), from + pageSize);
    List<PlantProfile> page = from >= items.size() ? List.of() : items.subList(from, to);
    return new PlantPageResult(page, pageNo, pageSize, items.size());
  }

  @Override
  public synchronized PlantProfile getById(String plantId) {
    PlantProfile value = store.get(plantId);
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plant not found: " + plantId);
    }
    return value;
  }

  @Override
  public synchronized PlantProfile create(CreatePlantCommand command) {
    String plantId = nonBlank(command.plantId(), "plant-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    ensurePlantIdUnique(plantId, null);

    OffsetDateTime now = OffsetDateTime.now();
    PlantProfile created = new PlantProfile(
        plantId,
        nonBlank(command.plantName(), "Unnamed Plant"),
      command.capacityMw() == null ? 0.0 : command.capacityMw(),
        command.latitude() == null ? 0.0 : command.latitude(),
        command.longitude() == null ? 0.0 : command.longitude(),
      command.elevationM(),
      command.tiltAngle(),
      command.azimuthAngle(),
        nonBlank(command.timezone(), "Asia/Shanghai"),
        nonBlank(command.status(), "active"),
        now,
        now);
    store.put(plantId, created);
    return created;
  }

  @Override
  public synchronized PlantProfile update(String plantId, UpdatePlantCommand command) {
    PlantProfile old = getById(plantId);
    OffsetDateTime now = OffsetDateTime.now();
    PlantProfile updated = new PlantProfile(
        old.plantId(),
        nonBlank(command.plantName(), old.plantName()),
      command.capacityMw() == null ? old.capacityMw() : command.capacityMw(),
        command.latitude() == null ? old.latitude() : command.latitude(),
        command.longitude() == null ? old.longitude() : command.longitude(),
      command.elevationM() == null ? old.elevationM() : command.elevationM(),
      command.tiltAngle() == null ? old.tiltAngle() : command.tiltAngle(),
      command.azimuthAngle() == null ? old.azimuthAngle() : command.azimuthAngle(),
        nonBlank(command.timezone(), old.timezone()),
        nonBlank(command.status(), old.status()),
        old.createdAt(),
        now);
    store.put(plantId, updated);
    return updated;
  }

  @Override
  public synchronized void delete(String plantId) {
    PlantProfile old = getById(plantId);
    PlantProfile inactive = new PlantProfile(
        old.plantId(),
        old.plantName(),
        old.capacityMw(),
        old.latitude(),
        old.longitude(),
        old.elevationM(),
        old.tiltAngle(),
        old.azimuthAngle(),
        old.timezone(),
        "inactive",
        old.createdAt(),
        OffsetDateTime.now());
    store.put(plantId, inactive);
  }

  private void ensurePlantIdUnique(String code, String selfId) {
    for (PlantProfile value : store.values()) {
      if (value.plantId().equalsIgnoreCase(code) && (selfId == null || !value.plantId().equals(selfId))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Plant id already exists: " + code);
      }
    }
  }

  private String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
