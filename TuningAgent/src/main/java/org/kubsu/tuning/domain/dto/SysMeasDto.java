package org.kubsu.tuning.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.kubsu.tuning.domain.entities.SysMeas;

@Data
@NoArgsConstructor
public class SysMeasDto {
    private Long sysId;
    private MeasurementDto measurement;
    private String externalApiUrl;
    private boolean isWorkload;
    private String dataSource;
}

