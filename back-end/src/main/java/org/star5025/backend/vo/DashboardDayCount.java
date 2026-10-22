package org.star5025.backend.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 未来 7 天每天到期待办数量。
 */
@Data
public class DashboardDayCount {
    private LocalDate date;
    private Long count;
}
