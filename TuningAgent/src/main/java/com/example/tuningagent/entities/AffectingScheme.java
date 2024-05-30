package com.example.tuningagent.entities;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name= "affecting_scheme")
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
