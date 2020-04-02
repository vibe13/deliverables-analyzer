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
import java.util.List;

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
public class NpmArtifact extends PanacheEntity {
    @OneToOne(mappedBy = "npmArtifact")
    @NotNull
    @Valid
    public Artifact artifact;

    @NotEmpty
    public String name;

    @NotEmpty
    public String version;

    @Column(nullable = false)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String username;

    @Column(nullable = false)
    public Date created;

    public static List<NpmArtifact> findByName(String name) {
        return list("name", name);
    }

    public static List<NpmArtifact> findByVersion(String version) {
        return list("version", version);
    }
}
