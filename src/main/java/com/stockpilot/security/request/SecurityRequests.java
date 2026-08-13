package com.stockpilot.security.request;
import com.stockpilot.security.domain.SecurityStatus; import jakarta.validation.constraints.*; import java.util.List;
public final class SecurityRequests { private SecurityRequests(){}
 public record Login(@NotBlank @Size(max=64) String username,@NotBlank @Size(max=100) String password){}
 public record CreateUser(@NotBlank @Pattern(regexp="^[A-Za-z][A-Za-z0-9_.-]{2,63}$") String username,@NotBlank @Size(min=8,max=100) String password,@NotBlank @Size(max=100) String displayName){}
 public record UpdateUser(@NotBlank @Size(max=100) String displayName,@Size(min=8,max=100) String newPassword,@NotNull Integer version){}
 public record Status(@NotNull SecurityStatus status,@NotNull Integer version){}
 public record CreateRole(@NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") String code,@NotBlank @Size(max=100) String name){}
 public record UpdateRole(@NotBlank @Size(max=100) String name,@NotNull Integer version){}
 public record CreatePermission(@NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{1,99}$") String code,@NotBlank @Size(max=100) String name,@Size(max=255) String description){}
 public record UpdatePermission(@NotBlank @Size(max=100) String name,@Size(max=255) String description,@NotNull Integer version){}
 public record Ids(@NotNull List<@Positive Long> ids){}
}
