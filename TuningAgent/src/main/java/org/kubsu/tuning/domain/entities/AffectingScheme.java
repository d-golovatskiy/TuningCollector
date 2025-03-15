package org.kubsu.tuning.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name= "affecting_scheme")
@NoArgsConstructor
public class AffectingScheme {
    @Id
    @Column(name="id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name="affected_sys_id")
    Long affectedId;

    @Column(name="affecting_sys_id")
    Long affectingId;
}
