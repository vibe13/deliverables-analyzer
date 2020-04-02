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
import javax.persistence.OneToMany;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@JsonIgnoreProperties("persistent")
public class Product extends PanacheEntity {
    @Column(nullable = false, unique = true)
    @NotBlank
    public String name;

    @Column(nullable = false, unique = true)
    @Pattern(regexp = "^[a-z]+$")
    public String shortname;

    @Column(nullable = false)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String username;

    @Column(nullable = false)
    public Date created;

    @OneToMany(mappedBy = "product")
    @JsonIgnoreProperties("product")
    public Set<ProductVersion> productVersions = new HashSet<>();

    public static List<Product> getByShortName(String shortname) {
        return list("shortname", shortname);
    }

    public enum SupportStatus {
        SUPPORTED, SUPERSEDED, UNSUPPORTED, UNKNOWN
    }
}
