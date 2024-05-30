package com.example.tuningagent.entities;

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

    @ManyToOne(optional = false, targetEntity = Measurements.class)
    @JoinColumn(name = "meas_id", referencedColumnName = "id", insertable=false, updatable=false )
    Measurements measurements;

    @ManyToOne(optional = false, targetEntity = Sys.class)
    @JoinColumn(name = "sys_id", referencedColumnName = "id", insertable=false, updatable=false )
    Sys sys;


}
