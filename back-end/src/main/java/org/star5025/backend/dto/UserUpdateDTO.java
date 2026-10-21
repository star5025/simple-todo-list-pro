package org.star5025.backend.dto;

import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class UserUpdateDTO {
    private Long userId;
    private String userName;
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String oldPassword;
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String userPassword;
}