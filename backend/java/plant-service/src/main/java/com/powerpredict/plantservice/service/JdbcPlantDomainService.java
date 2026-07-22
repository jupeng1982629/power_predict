package com.powerpredict.plantservice.service;

import com.powerpredict.plantservice.domain.PlantProfile;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class JdbcPlantDomainService implements PlantDomainService {
  private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
      Map.entry("plantId", "plant_id"),
      Map.entry("plantName", "plant_name"),
      Map.entry("capacityMw", "capacity_mw"),
      Map.entry("latitude", "latitude"),
      Map.entry("longitude", "longitude"),
      Map.entry("elevationM", "elevation_m"),
      Map.entry("tiltAngle", "tilt_angle"),
      Map.entry("azimuthAngle", "azimuth_angle"),
      Map.entry("timezone", "timezone"),
      Map.entry("status", "status"),
      Map.entry("createdAt", "created_at"),
      Map.entry("updatedAt", "updated_at"));

  private final NamedParameterJdbcTemplate jdbcTemplate;

  private final RowMapper<PlantProfile> rowMapper = this::mapPlant;

  public JdbcPlantDomainService(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public PlantPageResult list(PlantListQuery query) {
    int pageNo = Math.max(1, query.pageNo());
    int pageSize = Math.min(100, Math.max(1, query.pageSize()));

    MapSqlParameterSource params = new MapSqlParameterSource();
    String whereClause = buildWhereClause(query, params);
    long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM mdm.plant_info WHERE 1=1" + whereClause,
        params,
        Long.class);

    params.addValue("limit", pageSize);
    params.addValue("offset", (pageNo - 1) * pageSize);

    String orderBy = buildOrderBy(query.sortBy(), query.sortDir());
    List<PlantProfile> items = jdbcTemplate.query(
        "SELECT plant_id, plant_name, capacity_mw, latitude, longitude, elevation_m, tilt_angle, azimuth_angle, timezone, status, created_at, updated_at "
            + "FROM mdm.plant_info WHERE 1=1" + whereClause + orderBy + " LIMIT :limit OFFSET :offset",
        params,
        rowMapper);

    return new PlantPageResult(items, pageNo, pageSize, total);
  }

  @Override
  public PlantProfile getById(String plantId) {
    List<PlantProfile> items = jdbcTemplate.query(
        "SELECT plant_id, plant_name, capacity_mw, latitude, longitude, elevation_m, tilt_angle, azimuth_angle, timezone, status, created_at, updated_at "
            + "FROM mdm.plant_info WHERE plant_id = :plantId",
        new MapSqlParameterSource("plantId", plantId),
        rowMapper);
    if (items.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plant not found: " + plantId);
    }
    return items.get(0);
  }

  @Override
  public PlantProfile create(CreatePlantCommand command) {
    MapSqlParameterSource params = writeParams(
        new MapSqlParameterSource(),
        requireText(command.plantId(), "plantId"),
        requireText(command.plantName(), "plantName"),
        requireNumber(command.capacityMw(), "capacityMw"),
        requireNumber(command.latitude(), "latitude"),
        requireNumber(command.longitude(), "longitude"),
        command.elevationM(),
        command.tiltAngle(),
        command.azimuthAngle(),
        normalizeTimezone(command.timezone()),
        normalizeStatus(command.status()));

    try {
      jdbcTemplate.update(
          "INSERT INTO mdm.plant_info (plant_id, plant_name, capacity_mw, latitude, longitude, elevation_m, tilt_angle, azimuth_angle, timezone, status) "
              + "VALUES (:plantId, :plantName, :capacityMw, :latitude, :longitude, :elevationM, :tiltAngle, :azimuthAngle, :timezone, :status)",
          params);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Plant id already exists: " + command.plantId(), ex);
    }

    return getById(command.plantId());
  }

  @Override
  public PlantProfile update(String plantId, UpdatePlantCommand command) {
    getById(plantId);

    MapSqlParameterSource params = writeParams(
        new MapSqlParameterSource("plantId", plantId),
        plantId,
        requireText(command.plantName(), "plantName"),
        requireNumber(command.capacityMw(), "capacityMw"),
        requireNumber(command.latitude(), "latitude"),
        requireNumber(command.longitude(), "longitude"),
        command.elevationM(),
        command.tiltAngle(),
        command.azimuthAngle(),
        normalizeTimezone(command.timezone()),
        normalizeStatus(command.status()));

    jdbcTemplate.update(
        "UPDATE mdm.plant_info SET plant_name = :plantName, capacity_mw = :capacityMw, latitude = :latitude, longitude = :longitude, "
            + "elevation_m = :elevationM, tilt_angle = :tiltAngle, azimuth_angle = :azimuthAngle, timezone = :timezone, status = :status "
            + "WHERE plant_id = :plantId",
        params);

    return getById(plantId);
  }

  @Override
  public void delete(String plantId) {
    getById(plantId);
    jdbcTemplate.update(
        "UPDATE mdm.plant_info SET status = 'inactive' WHERE plant_id = :plantId",
        new MapSqlParameterSource("plantId", plantId));
  }

  private String buildWhereClause(PlantListQuery query, MapSqlParameterSource params) {
    StringBuilder where = new StringBuilder();
    if (query.keyword() != null && !query.keyword().isBlank()) {
      where.append(" AND (LOWER(plant_id) LIKE :keyword OR LOWER(plant_name) LIKE :keyword OR LOWER(timezone) LIKE :keyword)");
      params.addValue("keyword", "%" + query.keyword().trim().toLowerCase(Locale.ROOT) + "%");
    }
    if (query.status() != null && !query.status().isBlank()) {
      where.append(" AND status = :status");
      params.addValue("status", normalizeStatus(query.status()));
    }
    if (query.timezone() != null && !query.timezone().isBlank()) {
      where.append(" AND timezone = :timezone");
      params.addValue("timezone", query.timezone().trim());
    }
    return where.toString();
  }

  private String buildOrderBy(String sortBy, String sortDir) {
    String column = SORT_COLUMNS.getOrDefault(sortBy, "updated_at");
    String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
    return " ORDER BY " + column + " " + direction;
  }

  private MapSqlParameterSource writeParams(
      MapSqlParameterSource params,
      String plantId,
      String plantName,
      Double capacityMw,
      Double latitude,
      Double longitude,
      Double elevationM,
      Double tiltAngle,
      Double azimuthAngle,
      String timezone,
      String status) {
    return params
        .addValue("plantId", plantId)
        .addValue("plantName", plantName)
        .addValue("capacityMw", capacityMw)
        .addValue("latitude", latitude)
        .addValue("longitude", longitude)
        .addValue("elevationM", elevationM)
        .addValue("tiltAngle", tiltAngle)
        .addValue("azimuthAngle", azimuthAngle)
        .addValue("timezone", timezone)
        .addValue("status", status);
  }

  private PlantProfile mapPlant(ResultSet rs, int rowNum) throws SQLException {
    return new PlantProfile(
        rs.getString("plant_id"),
        rs.getString("plant_name"),
        rs.getBigDecimal("capacity_mw").doubleValue(),
        rs.getBigDecimal("latitude").doubleValue(),
        rs.getBigDecimal("longitude").doubleValue(),
        nullableDouble(rs.getBigDecimal("elevation_m")),
        nullableDouble(rs.getBigDecimal("tilt_angle")),
        nullableDouble(rs.getBigDecimal("azimuth_angle")),
        rs.getString("timezone"),
        rs.getString("status"),
        toOffsetDateTime(rs.getTimestamp("created_at")),
        toOffsetDateTime(rs.getTimestamp("updated_at")));
  }

  private Double nullableDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
  }

  private String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
    }
    return value.trim();
  }

  private Double requireNumber(Double value, String fieldName) {
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
    }
    return value;
  }

  private String normalizeTimezone(String timezone) {
    return timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
  }

  private String normalizeStatus(String status) {
    String normalized = status == null || status.isBlank() ? "active" : status.trim().toLowerCase(Locale.ROOT);
    if (!normalized.equals("active") && !normalized.equals("inactive")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be active or inactive");
    }
    return normalized;
  }
}