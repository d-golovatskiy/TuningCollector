package org.kubsu.tuning.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "measurements")
@NoArgsConstructor
public class Measurements {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "name")
    String name;

    @Column(name="description")
    String description;

    @Column(name = "field_name")
    String fieldName;
}
