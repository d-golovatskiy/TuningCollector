package org.kubsu.tuning.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Getter
@Table(name = "alarms")
@NoArgsConstructor
public class Alarm {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column
    String description;

    @Column(name="sys_Id")
    Long sysId;

    @Column(name = "date_start")
    Timestamp dateStart;

    @Column(name = "date_end")
    Timestamp dateEnd;

    @JsonIgnore
    @ManyToOne(optional = false, targetEntity = Sys.class)
    @JoinColumn(name = "sys_id", referencedColumnName = "id", insertable=false, updatable=false )
    Sys sys;

    public void setId(Long id) {
        this.id = id;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSysId(Long sysId) {
        this.sysId = sysId;
    }

    public void setDateStart(Timestamp dateStart) {
        this.dateStart = dateStart;
    }

    public void setDateEnd(Timestamp dateEnd) {
        this.dateEnd = dateEnd;
    }

    public void setSys(Sys sys) {
        this.sys = sys;
    }
}
