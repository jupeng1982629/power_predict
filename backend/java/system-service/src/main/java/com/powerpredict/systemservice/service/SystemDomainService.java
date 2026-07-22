package com.powerpredict.systemservice.service;

import com.powerpredict.systemservice.domain.SystemPermission;
import com.powerpredict.systemservice.domain.SystemRole;
import com.powerpredict.systemservice.domain.SystemUser;
import java.util.List;

public interface SystemDomainService {
  List<SystemUser> listUsers(String keyword);

  SystemUser getUser(String userId);

  SystemUser createUser(CreateUserCommand command);

  SystemUser updateUser(String userId, UpdateUserCommand command);

  void deleteUser(String userId);

  List<SystemRole> listRoles(String keyword);

  SystemRole createRole(CreateRoleCommand command);

  SystemRole updateRole(String roleId, UpdateRoleCommand command);

  void deleteRole(String roleId);

  List<SystemPermission> listPermissions();

  SystemRole bindPermissions(String roleId, List<String> permissionCodes);

  record CreateUserCommand(
      String userName,
      String displayName,
      String tenantId,
      Boolean enabled,
      List<String> roleIds
  ) {
  }

  record UpdateUserCommand(
      String displayName,
      String tenantId,
      Boolean enabled,
      List<String> roleIds
  ) {
  }

  record CreateRoleCommand(String roleName, String description, List<String> permissionCodes) {
  }

  record UpdateRoleCommand(String roleName, String description, List<String> permissionCodes) {
  }
}
