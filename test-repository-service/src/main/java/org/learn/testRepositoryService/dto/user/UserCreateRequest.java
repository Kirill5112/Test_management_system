package org.learn.testRepositoryService.dto.user;

import jakarta.validation.constraints.*;
import lombok.*;
import org.learn.enums.UserRole;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreateRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
            message = "username may contain only letters, digits, '_', '.', '-'")
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @NotNull
    private UserRole role;

    @Builder.Default
    private Boolean active = true;
}

