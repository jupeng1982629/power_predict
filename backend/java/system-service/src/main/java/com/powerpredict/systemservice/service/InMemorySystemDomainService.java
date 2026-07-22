package com.powerpredict.systemservice.service;

import com.powerpredict.systemservice.domain.SystemPermission;
import com.powerpredict.systemservice.domain.SystemRole;
import com.powerpredict.systemservice.domain.SystemUser;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InMemorySystemDomainService implements SystemDomainService {
  private final Map<String, SystemUser> users = new LinkedHashMap<>();
  private final Map<String, SystemRole> roles = new LinkedHashMap<>();
  private final Map<String, SystemPermission> permissions = new LinkedHashMap<>();

  public InMemorySystemDomainService() {
    seedPermissions();
    seedData();
  }

  @Override
  public synchronized List<SystemUser> listUsers(String keyword) {
    String norm = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    List<SystemUser> result = new ArrayList<>();
    for (SystemUser user : users.values()) {
      boolean ok = norm.isEmpty()
          || user.userName().toLowerCase(Locale.ROOT).contains(norm)
          || user.displayName().toLowerCase(Locale.ROOT).contains(norm);
      if (ok) {
        result.add(user);
      }
    }
    return result;
  }

  @Override
  public synchronized SystemUser getUser(String userId) {
    SystemUser user = users.get(userId);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
    }
    return user;
  }

  @Override
  public synchronized SystemUser createUser(CreateUserCommand command) {
    String userId = "user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    OffsetDateTime now = OffsetDateTime.now();
    SystemUser created = new SystemUser(
        userId,
        nonBlank(command.userName(), userId),
        nonBlank(command.displayName(), command.userName()),
        nonBlank(command.tenantId(), "tenant-default"),
        command.enabled() == null || command.enabled(),
        command.roleIds() == null ? List.of() : command.roleIds(),
        now,
        now);
    users.put(userId, created);
    return created;
  }

  @Override
  public synchronized SystemUser updateUser(String userId, UpdateUserCommand command) {
    SystemUser old = getUser(userId);
    SystemUser updated = new SystemUser(
        old.userId(),
        old.userName(),
        nonBlank(command.displayName(), old.displayName()),
        nonBlank(command.tenantId(), old.tenantId()),
        command.enabled() == null ? old.enabled() : command.enabled(),
        command.roleIds() == null ? old.roleIds() : command.roleIds(),
        old.createdAt(),
        OffsetDateTime.now());
    users.put(userId, updated);
    return updated;
  }

  @Override
  public synchronized void deleteUser(String userId) {
    getUser(userId);
    users.remove(userId);
  }

  @Override
  public synchronized List<SystemRole> listRoles(String keyword) {
    String norm = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    List<SystemRole> result = new ArrayList<>();
    for (SystemRole role : roles.values()) {
      boolean ok = norm.isEmpty() || role.roleName().toLowerCase(Locale.ROOT).contains(norm);
      if (ok) {
        result.add(role);
      }
    }
    return result;
  }

  @Override
  public synchronized SystemRole createRole(CreateRoleCommand command) {
    String roleId = "role-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    OffsetDateTime now = OffsetDateTime.now();
    SystemRole role = new SystemRole(
        roleId,
        nonBlank(command.roleName(), roleId),
        nonBlank(command.description(), ""),
        validPermissionCodes(command.permissionCodes()),
        now,
        now);
    roles.put(roleId, role);
    return role;
  }

  @Override
  public synchronized SystemRole updateRole(String roleId, UpdateRoleCommand command) {
    SystemRole old = getRole(roleId);
    SystemRole updated = new SystemRole(
        old.roleId(),
        nonBlank(command.roleName(), old.roleName()),
        nonBlank(command.description(), old.description()),
        command.permissionCodes() == null ? old.permissionCodes() : validPermissionCodes(command.permissionCodes()),
        old.createdAt(),
        OffsetDateTime.now());
    roles.put(roleId, updated);
    return updated;
  }

  @Override
  public synchronized void deleteRole(String roleId) {
    getRole(roleId);
    roles.remove(roleId);
  }

  @Override
  public synchronized List<SystemPermission> listPermissions() {
    return new ArrayList<>(permissions.values());
  }

  @Override
  public synchronized SystemRole bindPermissions(String roleId, List<String> permissionCodes) {
    SystemRole old = getRole(roleId);
    SystemRole updated = new SystemRole(
        old.roleId(),
        old.roleName(),
        old.description(),
        validPermissionCodes(permissionCodes),
        old.createdAt(),
        OffsetDateTime.now());
    roles.put(roleId, updated);
    return updated;
  }

  private SystemRole getRole(String roleId) {
    SystemRole role = roles.get(roleId);
    if (role == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + roleId);
    }
    return role;
  }

  private List<String> validPermissionCodes(List<String> input) {
    if (input == null) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (String code : input) {
      if (code == null || code.isBlank()) {
        continue;
      }
      if (!permissions.containsKey(code)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission: " + code);
      }
      values.add(code);
    }
    return values;
  }

  private String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private void seedPermissions() {
    addPermission("plant:read", "Read plants", "plant");
    addPermission("plant:write", "Write plants", "plant");
    addPermission("monitor:read", "Read monitor data", "monitor");
    addPermission("monitor:write", "Write monitor data", "monitor");
    addPermission("model:train", "Train model", "model");
    addPermission("model:publish", "Publish model", "model");
    addPermission("forecast:read", "Read forecast", "forecast");
    addPermission("forecast:run", "Run forecast", "forecast");
    addPermission("system:admin", "System admin", "system");
  }

  private void seedData() {
    OffsetDateTime now = OffsetDateTime.now();
    SystemRole admin = new SystemRole(
        "role-system-admin",
        "SYSTEM_ADMIN",
        "Built-in admin role",
        List.of("system:admin", "plant:write", "monitor:write", "forecast:run", "model:publish"),
        now,
        now);
    roles.put(admin.roleId(), admin);

    SystemUser demo = new SystemUser(
        "user-demo-admin",
        "demo.admin",
        "Demo Admin",
        "tenant-default",
        true,
        List.of(admin.roleId()),
        now,
        now);
    users.put(demo.userId(), demo);
  }

  private void addPermission(String code, String name, String domain) {
    permissions.put(code, new SystemPermission(code, name, domain));
  }
}
