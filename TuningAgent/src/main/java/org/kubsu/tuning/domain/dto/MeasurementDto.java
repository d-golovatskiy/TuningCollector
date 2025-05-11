package org.kubsu.tuning.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.kubsu.tuning.domain.entities.Measurements;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class MeasurementDto {
    private Long id;
    private String name;
    private String fieldName;
    private List<InfluxTagValueDto> influxTagValues = new ArrayList<>();
    private String influxBucketName;
}
