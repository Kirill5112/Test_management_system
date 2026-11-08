package org.learn.testRepositoryService.dto.user;

import jakarta.validation.constraints.*;
import lombok.*;
import org.learn.enums.UserRole;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @NotNull
    private UserRole role;

    @NotNull
    private Boolean active;
}
