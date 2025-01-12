package org.kubsu.tuning.domain.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;


@Getter
@Entity
@Table(name="affect_exception")
public class AffectException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    Long id;

    @Column(name = "task_id")
    Long taskId;

    @Column(name = "sys_id")
    Long sysId;

    @JsonIgnore
    @ManyToOne(optional = false, targetEntity = Sys.class)
    @JoinColumn(name = "sys_id", referencedColumnName = "id", insertable=false, updatable=false )
    Sys sys;

    @JsonIgnore
    @ManyToOne(optional = false, targetEntity = TaskToCollect.class)
    @JoinColumn(name = "task_id", referencedColumnName = "id", insertable=false, updatable=false )
    TaskToCollect taskToCollect;

    public AffectException() {
    }


}
