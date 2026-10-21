package org.star5025.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long userId;

    private String userName;

    @JsonIgnore
    @ToString.Exclude
    private String userPassword;
    
    private LocalDateTime createTime;
    
    private Integer taskCount;
}