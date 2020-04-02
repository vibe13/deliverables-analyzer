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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@JsonIgnoreProperties("persistent")
public class MavenArtifact extends PanacheEntity {
    @OneToOne(mappedBy = "mavenArtifact")
    @NotNull
    @Valid
    public Artifact artifact;

    @NotEmpty
    public String groupId;

    @NotEmpty
    public String artifactId;

    @NotEmpty
    public String type;

    @NotEmpty
    public String version;

    public String classifier;

    @Column(nullable = false)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String username;

    @Column(nullable = false)
    public Date created;

    public static List<MavenArtifact> findByGroupId(String groupId) {
        return list("groupId", groupId);
    }

    public static List<MavenArtifact> findByArtifactId(String artifactId) {
        return list("artifactId", artifactId);
    }

    public static List<MavenArtifact> findByType(String type) {
        return list("type", type);
    }

    public static List<MavenArtifact> findByVersion(String version) {
        return list("version", version);
    }

    public static List<MavenArtifact> findByClassifer(String classifier) {
        return list("classifier", classifier);
    }

    public static List<MavenArtifact> findByGroupIdAndArtifactId(String groupId, String artifactId) {
        Map<String, Object> params = new HashMap<>(2);

        params.put("groupId", groupId);
        params.put("artifactId", artifactId);

        return MavenArtifact.list("groupId = :groupId and artifactId = :artifactId", params);
    }
}
