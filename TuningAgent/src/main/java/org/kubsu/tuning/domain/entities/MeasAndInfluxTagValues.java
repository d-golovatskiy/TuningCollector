package org.kubsu.tuning.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;

@Entity
@Data
@Table(name = "meas_and_influx_tag_values")
public class MeasAndInfluxTagValues {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "meas_id", referencedColumnName = "id")
    private Measurements measurements;

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name = "influx_tag_value_id", referencedColumnName = "id")
    private InfluxTagValue influxTagValue;
}
