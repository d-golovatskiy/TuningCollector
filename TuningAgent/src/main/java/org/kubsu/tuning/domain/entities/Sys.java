package org.kubsu.tuning.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.relational.core.mapping.Table;

import java.util.List;

@Entity
@Table(name="systems", schema = "public")
@Getter

public class Sys {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "name")
    String name;

    @Column(name = "description")
    String description;

    @OneToMany(mappedBy = "sys")
    private List<Alarm> alarms;

    public Sys() {
    }


    public Sys(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    /*@OneToMany(mappedBy = "sys")
    private List<Alarm> alarms;

    @OneToMany(mappedBy = "sys")
    private List<TaskToCollect> tasksToCollect;

    @OneToMany(mappedBy = "sys")
    private List<SysMeas> sysMeas;

    @OneToMany(mappedBy = "sys")
    private List<AffectException> affectExceptions;*/

   /* public void setAlarm(List<Alarm> alarms) {
        this.alarms = alarms;
    }

    public void setTasks(List<TaskToCollect> tasksToCollect) {
        this.tasksToCollect = tasksToCollect;
    }

    public void setSysMeas(List<SysMeas> sysMeas) {
        this.sysMeas = sysMeas;
    }

    public void setAffectExceptions(List<AffectException> affectExceptions) {
        this.affectExceptions = affectExceptions;
    }*/
}
