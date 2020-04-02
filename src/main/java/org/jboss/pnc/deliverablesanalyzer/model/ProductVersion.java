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

import java.net.URL;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@JsonIgnoreProperties("persistent")
public class ProductVersion extends PanacheEntity {
    @ManyToOne
    @Valid
    @NotNull
    @JsonIgnoreProperties("productVersions")
    public Product product;

    @Column(nullable = false, unique = true)
    @NotBlank
    public String version;

    @Column(nullable = true, unique = true)
    public URL url;

    @Column(nullable = false)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String username;

    @Column(nullable = false)
    public Date created;

    @Column(nullable = true)
    @Pattern(regexp = "^((?!anonymous).).*$")
    public String importedBy;

    @Column(nullable = true)
    public Date imported;

    @ManyToMany
    @JsonIgnoreProperties("artifacts")
    public Set<Build> builds = new HashSet<>();

    public static List<ProductVersion> findByProductId(Long productId) {
        return list("product.id = ?1", productId);
    }

    public static ProductVersion findByProductIdAndVersion(Long productId, String version) {
        return find("product.id = ?1 AND version = ?2", productId, version).firstResult();
    }

    public static ProductVersion findByShortnameAndVersion(String shortname, String version) {
        return find("product.shortname = ?1 AND version = ?2", shortname, version).firstResult();
    }
}
