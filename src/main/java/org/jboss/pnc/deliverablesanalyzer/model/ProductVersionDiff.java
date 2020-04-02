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

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.SetUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ProductVersionDiff {
    @NotNull
    @Valid
    public ProductVersion productVersion1;

    @NotNull
    @Valid
    public ProductVersion productVersion2;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Valid
    public SortedSet<Build> buildsAdded = new TreeSet<>();

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Valid
    public SortedSet<Build> buildsRemoved = new TreeSet<>();

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Valid
    public SortedSet<BuildDiff> buildsChanged = new TreeSet<>();

    public ProductVersionDiff(ProductVersion pv1, ProductVersion pv2) {
        this.productVersion1 = pv1;
        this.productVersion2 = pv2;

        SetUtils.SetView<Build> removed = SetUtils.difference(pv1.builds, pv2.builds);
        SetUtils.SetView<Build> added = SetUtils.difference(pv2.builds, pv1.builds);

        for (Build build : removed) {
            Set<Build> matches = BuildDiff.containsDifferentVersion(build, added);

            if (matches.isEmpty()) {
                this.buildsRemoved.add(build);
            } else {
                this.buildsChanged.add(new BuildDiff(build, matches.iterator().next()));
            }
        }

        for (Build build : added) {
            Set<Build> matches = BuildDiff.containsDifferentVersion(build, removed);

            if (matches.isEmpty()) {
                this.buildsAdded.add(build);
            } else {
                this.buildsChanged.add(new BuildDiff(matches.iterator().next(), build));
            }
        }
    }
}
