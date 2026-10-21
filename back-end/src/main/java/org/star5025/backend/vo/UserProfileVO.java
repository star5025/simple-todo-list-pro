package org.star5025.backend.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class UserProfileVO {
    Long userId;
    String userName;
    LocalDateTime createTime;
    Integer taskCount;
}
