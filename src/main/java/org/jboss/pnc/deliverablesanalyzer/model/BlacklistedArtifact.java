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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@JsonIgnoreProperties("persistent")
public class BlacklistedArtifact extends PanacheEntity {
    @Column(nullable = true)
    @Positive
    public Long artifactId;

    @Column(nullable = false)
    @NotNull
    @Valid
    public Type type;

    @Column(nullable = true)
    public String reason;

    @Column(nullable = true)
    @Positive
    public Long buildId;

    @Column(nullable = true)
    public String regex;

    @Column(nullable = false)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String username;

    @Column(nullable = false)
    public Date created;

    public static BlacklistedArtifact findByArtifactId(Long artifactId) {
        return find("artifactId", artifactId).firstResult();
    }

    public static List<BlacklistedArtifact> findByType(Type type) {
        return list("type", type);
    }

    public static List<BlacklistedArtifact> findByReason(String reason) {
        return list("reason", reason);
    }

    public enum Type {
        NONE, PARTIAL_BUILD, BROKEN_BUILD, CVE
    }
}
