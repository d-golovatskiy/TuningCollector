package org.kubsu.tuning.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

import java.util.List;

@Entity
@Table(name="systems", schema = "public")
@Getter
@NoArgsConstructor
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
}
