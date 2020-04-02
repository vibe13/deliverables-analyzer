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

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@JsonIgnoreProperties("persistent")
public class Artifact extends PanacheEntity implements Comparable<Artifact> {
    @Column(nullable = false)
    @NotBlank
    public String identifier;

    @Column(nullable = false)
    @NotNull
    public Type type = Type.NONE;

    @Column(nullable = false, length = 128)
    @Pattern(regexp = "^[a-f0-9]{32}$")
    public String md5;

    @Column(nullable = false, length = 160)
    @Pattern(regexp = "^[a-f0-9]{40}$")
    public String sha1;

    @Column(nullable = false, length = 256)
    @Pattern(regexp = "^[a-f0-9]{64}$")
    public String sha256;

    @Positive
    public Long kojiId;

    @Positive
    public Long pncId;

    @NotNull
    public BuildSystemType buildSystemType = BuildSystemType.UNKNOWN;

    @Column(nullable = false)
    @NotNull
    public Boolean builtFromSource = Boolean.FALSE;

    @ElementCollection
    @OrderBy
    public SortedSet<String> filesNotBuiltFromSource = new TreeSet<>();

    @ManyToOne
    @NotNull
    @Valid
    @JsonIgnoreProperties("artifacts")
    public Build build;

    @OneToOne
    @JsonIgnoreProperties("artifact")
    public MavenArtifact mavenArtifact;

    @OneToOne
    @JsonIgnoreProperties("artifact")
    public NpmArtifact npmArtifact;

    @Column(nullable = false)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String username;

    @Column(nullable = false)
    public Date created;

    public static Artifact findByIdentifierAndBuildSystemType(String identifier, BuildSystemType buildSystemType) {
        return find("identifier = ?1 AND buildSystemType = ?2", identifier, buildSystemType).firstResult();
    }

    public static List<Artifact> findByIdentifier(String identifier) {
        return list("identifier", identifier);
    }

    public static List<Artifact> findByHash(String hash) {
        if (hash.length() == 128) {
            return findByMd5(hash);
        }

        if (hash.length() == 160) {
            return findBySha1(hash);
        }

        if (hash.length() == 256) {
            return findBySha256(hash);
        }

        return Collections.emptyList();
    }

    public static List<Artifact> findByMd5(String md5) {
        return list("md5", md5);
    }

    public static List<Artifact> findBySha1(String sha1) {
        return list("sha1", sha1);
    }

    public static List<Artifact> findBySha256(String sha256) {
        return list("sha256", sha256);
    }

    public static List<Artifact> findByKojiId(Long kojiId) {
        return list("kojiId", kojiId);
    }

    public static List<Artifact> findByPncId(Long pncId) {
        return list("pncId", pncId);
    }

    public static List<Artifact> findByBuiltFromSource(Boolean builtFromSource) {
        return list("builtFromSource", builtFromSource);
    }

    public static List<Artifact> findByType(Type type) {
        return list("type", type);
    }

    @Override
    public int compareTo(Artifact o) {
        return id.compareTo(o.id);
    }

    public enum Type {
        NONE, MAVEN, NPM
    }
}
