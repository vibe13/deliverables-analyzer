/*
 * Copyright (C) 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.deliverablesanalyzer.model;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@JsonIgnoreProperties("persistent")
public class Build extends PanacheEntity implements Comparable<Build> {
    @Column(nullable = false)
    @NotBlank
    public String identifier;

    @PositiveOrZero
    public Long kojiId;

    @PositiveOrZero
    public Long pncId;

    @Column(nullable = false)
    @NotNull
    public Boolean builtFromSource = Boolean.FALSE;

    @Column(nullable = true)
    public String source;

    @NotNull
    public BuildSystemType buildSystemType = BuildSystemType.UNKNOWN;

    @Column(nullable = false)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String username;

    @Column(nullable = false)
    public Date created;

    @OneToMany(mappedBy = "build")
    @JsonIgnoreProperties("build")
    public Set<Artifact> artifacts = new HashSet<>();

    @ManyToMany(mappedBy = "builds")
    @JsonIgnoreProperties("builds")
    public Set<ProductVersion> productVersions = new HashSet<>();

    public static Build findByIdentifierAndBuildSystemType(String identifier, BuildSystemType buildSystemType) {
        List<Build> builds = find("identifier = ?1 AND buildSystemType = ?2", identifier, buildSystemType).list();

        return builds.isEmpty() ? null : builds.get(0);
    }

    public static List<Build> findByIdentifier(String identifier) {
        return list("identifier", identifier);
    }

    public static List<Build> findByKojiId(Long kojiId) {
        return list("kojiId", kojiId);
    }

    public static List<Build> findByPncId(Long pncId) {
        return list("pncId", pncId);
    }

    public static List<Build> findByBuiltFromSource(Boolean builtFromSource) {
        return list("builtFromSource", builtFromSource);
    }

    public static List<Build> findBySource(String source) {
        return list("source", source);
    }

    @Override
    public int compareTo(Build o) {
        return id.compareTo(o.id);
    }
}
