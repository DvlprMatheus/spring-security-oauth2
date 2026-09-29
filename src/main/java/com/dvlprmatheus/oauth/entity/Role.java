package com.dvlprmatheus.oauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Role extends AbstractEntity {

  @Column(nullable = false, unique = true, length = 50)
  private String name;

  @Column(length = 255)
  private String description;

  @ManyToMany(mappedBy = "roles")
  private Set<User> users = new HashSet<>();
}
