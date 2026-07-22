package com.powerpredict.systemservice.api;

import com.powerpredict.common.api.ApiResponse;
import com.powerpredict.systemservice.domain.SystemPermission;
import com.powerpredict.systemservice.domain.SystemRole;
import com.powerpredict.systemservice.domain.SystemUser;
import com.powerpredict.systemservice.service.SystemDomainService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemManagementController {
  private final SystemDomainService systemDomainService;

  public SystemManagementController(SystemDomainService systemDomainService) {
    this.systemDomainService = systemDomainService;
  }

  @GetMapping("/users")
  public ApiResponse<Map<String, Object>> users(
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "keyword", required = false) String keyword) {
    List<SystemUser> all = systemDomainService.listUsers(keyword);
    return ApiResponse.ok(page(all, pageNo, pageSize));
  }

  @PostMapping("/users")
  public ApiResponse<SystemUser> createUser(@RequestBody UserUpsertRequest request) {
    SystemUser created = systemDomainService.createUser(new SystemDomainService.CreateUserCommand(
        request.userName,
        request.displayName,
        request.tenantId,
        request.enabled,
        request.roleIds));
    return ApiResponse.ok(created);
  }

  @PutMapping("/users/{userId}")
  public ApiResponse<SystemUser> updateUser(@PathVariable("userId") String userId, @RequestBody UserUpsertRequest request) {
    SystemUser updated = systemDomainService.updateUser(userId, new SystemDomainService.UpdateUserCommand(
        request.displayName,
        request.tenantId,
        request.enabled,
        request.roleIds));
    return ApiResponse.ok(updated);
  }

  @DeleteMapping("/users/{userId}")
  public ApiResponse<Map<String, Object>> deleteUser(@PathVariable("userId") String userId) {
    systemDomainService.deleteUser(userId);
    return ApiResponse.ok(Map.of("deleted", true, "userId", userId));
  }

  @GetMapping("/roles")
  public ApiResponse<Map<String, Object>> roles(
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "keyword", required = false) String keyword) {
    List<SystemRole> all = systemDomainService.listRoles(keyword);
    return ApiResponse.ok(page(all, pageNo, pageSize));
  }

  @PostMapping("/roles")
  public ApiResponse<SystemRole> createRole(@RequestBody RoleUpsertRequest request) {
    SystemRole role = systemDomainService.createRole(new SystemDomainService.CreateRoleCommand(
        request.roleName,
        request.description,
        request.permissionCodes));
    return ApiResponse.ok(role);
  }

  @PutMapping("/roles/{roleId}")
  public ApiResponse<SystemRole> updateRole(@PathVariable("roleId") String roleId, @RequestBody RoleUpsertRequest request) {
    SystemRole role = systemDomainService.updateRole(roleId, new SystemDomainService.UpdateRoleCommand(
        request.roleName,
        request.description,
        request.permissionCodes));
    return ApiResponse.ok(role);
  }

  @DeleteMapping("/roles/{roleId}")
  public ApiResponse<Map<String, Object>> deleteRole(@PathVariable("roleId") String roleId) {
    systemDomainService.deleteRole(roleId);
    return ApiResponse.ok(Map.of("deleted", true, "roleId", roleId));
  }

  @GetMapping("/permissions")
  public ApiResponse<List<SystemPermission>> permissions() {
    return ApiResponse.ok(systemDomainService.listPermissions());
  }

  @PutMapping("/roles/{roleId}/permissions")
  public ApiResponse<SystemRole> bindPermissions(
      @PathVariable("roleId") String roleId,
      @RequestBody BindPermissionsRequest request) {
    return ApiResponse.ok(systemDomainService.bindPermissions(roleId, request.permissionCodes));
  }

  private Map<String, Object> page(List<?> all, int pageNo, int pageSize) {
    int from = Math.max(0, (pageNo - 1) * pageSize);
    int to = Math.min(all.size(), from + pageSize);
    List<?> items = from >= all.size() ? List.of() : all.subList(from, to);
    return Map.of("items", items, "pageNo", pageNo, "pageSize", pageSize, "total", all.size());
  }

  public static class UserUpsertRequest {
    public String userName;
    public String displayName;
    public String tenantId;
    public Boolean enabled;
    public List<String> roleIds;
  }

  public static class RoleUpsertRequest {
    public String roleName;
    public String description;
    public List<String> permissionCodes;
  }

  public static class BindPermissionsRequest {
    public List<String> permissionCodes;
  }
}
