package org.kubsu.tuning.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "sys_measurements")
public class SysMeas {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;


    @Column(name = "meas_id")
    Long measId;

    @Column(name = "sys_id")
    Long sysId;

    @Column(name = "external_api_uri")
    String externalApiUri;

    @ManyToOne(optional = false, targetEntity = Measurements.class)
    @JoinColumn(name = "meas_id", referencedColumnName = "id", insertable=false, updatable=false )
    Measurements measurements;

    @ManyToOne(optional = false, targetEntity = Sys.class)
    @JoinColumn(name = "sys_id", referencedColumnName = "id", insertable=false, updatable=false )
    Sys sys;


}
