package org.star5025.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPatchDTO {
    private String taskName;

    private Boolean status;

    private String description;

    private LocalDateTime startTime;

    private LocalDateTime remindTime;

    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean remindTimeSet;

    @JsonSetter("remindTime")
    public void setRemindTime(LocalDateTime remindTime) {
        this.remindTime = remindTime;
        this.remindTimeSet = true;
    }

    private LocalDateTime dueTime;
    
    private Boolean favourite;
}