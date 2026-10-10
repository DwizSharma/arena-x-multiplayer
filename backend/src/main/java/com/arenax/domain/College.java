package com.arenax.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "colleges")
public class College {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false, unique = true) private String name;
    private String domain;
    @Column(name = "total_points", nullable = false) private long totalPoints;
    protected College() {}
    public College(String name, String domain) { this.name = name; this.domain = domain; }
    public String getId() { return id; } public String getName() { return name; }
    public String getDomain() { return domain; } public long getTotalPoints() { return totalPoints; }
    public void addPoints(long points) { this.totalPoints += points; }
}
