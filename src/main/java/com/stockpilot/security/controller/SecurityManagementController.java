package com.stockpilot.security.controller;

import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.service.SecurityManagementApplicationService;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/security")
public class SecurityManagementController {
    private final SecurityManagementApplicationService securityManagementService;

    public SecurityManagementController(
            SecurityManagementApplicationService securityManagementService) {
        this.securityManagementService = securityManagementService;
    }

    @PreAuthorize("hasAuthority('SECURITY_USER_WRITE')")
    @PostMapping("/users")
    public ApiResponse<SecurityVO.User> createUser(
            @Valid @RequestBody SecurityRequests.CreateUser r) {
        return ApiResponse.success(securityManagementService.createUser(r));
    }

    @PreAuthorize("hasAuthority('SECURITY_USER_WRITE')")
    @PutMapping("/users/{id}")
    public ApiResponse<SecurityVO.User> updateUser(
            @PathVariable @Positive long id, @Valid @RequestBody SecurityRequests.UpdateUser r) {
        return ApiResponse.success(securityManagementService.updateUser(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_USER_WRITE')")
    @PatchMapping("/users/{id}/status")
    public ApiResponse<SecurityVO.User> userStatus(
            @PathVariable @Positive long id, @Valid @RequestBody SecurityRequests.Status r) {
        return ApiResponse.success(securityManagementService.userStatus(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_GRANT')")
    @PutMapping("/users/{id}/roles")
    public ApiResponse<SecurityVO.User> userRoles(
            @PathVariable @Positive long id, @Valid @RequestBody SecurityRequests.Ids r) {
        return ApiResponse.success(securityManagementService.setUserRoles(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_USER_READ')")
    @GetMapping("/users/{id}")
    public ApiResponse<SecurityVO.User> user(@PathVariable @Positive long id) {
        return ApiResponse.success(securityManagementService.getUser(id));
    }

    @PreAuthorize("hasAuthority('SECURITY_USER_READ')")
    @GetMapping("/users")
    public ApiResponse<PageResult<SecurityVO.User>> users(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return ApiResponse.success(securityManagementService.users(page, size));
    }

    @PreAuthorize("hasAuthority('SECURITY_ROLE_WRITE')")
    @PostMapping("/roles")
    public ApiResponse<SecurityVO.Role> createRole(
            @Valid @RequestBody SecurityRequests.CreateRole r) {
        return ApiResponse.success(securityManagementService.createRole(r));
    }

    @PreAuthorize("hasAuthority('SECURITY_ROLE_WRITE')")
    @PutMapping("/roles/{id}")
    public ApiResponse<SecurityVO.Role> updateRole(
            @PathVariable @Positive long id, @Valid @RequestBody SecurityRequests.UpdateRole r) {
        return ApiResponse.success(securityManagementService.updateRole(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_ROLE_WRITE')")
    @PatchMapping("/roles/{id}/status")
    public ApiResponse<SecurityVO.Role> roleStatus(
            @PathVariable @Positive long id, @Valid @RequestBody SecurityRequests.Status r) {
        return ApiResponse.success(securityManagementService.roleStatus(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_GRANT')")
    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<SecurityVO.Role> rolePermissions(
            @PathVariable @Positive long id, @Valid @RequestBody SecurityRequests.Ids r) {
        return ApiResponse.success(securityManagementService.setRolePermissions(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_ROLE_READ')")
    @GetMapping("/roles/{id}")
    public ApiResponse<SecurityVO.Role> role(@PathVariable @Positive long id) {
        return ApiResponse.success(securityManagementService.getRole(id));
    }

    @PreAuthorize("hasAuthority('SECURITY_ROLE_READ')")
    @GetMapping("/roles")
    public ApiResponse<List<SecurityVO.Role>> roles() {
        return ApiResponse.success(securityManagementService.roles());
    }

    @PreAuthorize("hasAuthority('SECURITY_PERMISSION_WRITE')")
    @PostMapping("/permissions")
    public ApiResponse<SecurityVO.Permission> createPermission(
            @Valid @RequestBody SecurityRequests.CreatePermission r) {
        return ApiResponse.success(securityManagementService.createPermission(r));
    }

    @PreAuthorize("hasAuthority('SECURITY_PERMISSION_WRITE')")
    @PutMapping("/permissions/{id}")
    public ApiResponse<SecurityVO.Permission> updatePermission(
            @PathVariable @Positive long id,
            @Valid @RequestBody SecurityRequests.UpdatePermission r) {
        return ApiResponse.success(securityManagementService.updatePermission(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_PERMISSION_WRITE')")
    @PatchMapping("/permissions/{id}/status")
    public ApiResponse<SecurityVO.Permission> permissionStatus(
            @PathVariable @Positive long id, @Valid @RequestBody SecurityRequests.Status r) {
        return ApiResponse.success(securityManagementService.permissionStatus(id, r));
    }

    @PreAuthorize("hasAuthority('SECURITY_PERMISSION_READ')")
    @GetMapping("/permissions")
    public ApiResponse<List<SecurityVO.Permission>> permissions() {
        return ApiResponse.success(securityManagementService.permissions());
    }

    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<SecurityVO.Audit>> audits(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return ApiResponse.success(securityManagementService.audits(page, size));
    }
}
