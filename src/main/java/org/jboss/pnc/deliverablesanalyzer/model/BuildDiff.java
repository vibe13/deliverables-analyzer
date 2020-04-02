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
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;

import org.apache.commons.collections4.SetUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiNVR;

public class BuildDiff implements Comparable<BuildDiff> {
    @NotEmpty
    public String identifier;

    @NotNull
    @Valid
    public Build build1;

    @NotNull
    @Valid
    public Build build2;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Valid
    public SortedSet<Artifact> artifcatsAdded = new TreeSet<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Valid
    public SortedSet<Artifact> artifactsRemoved = new TreeSet<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Valid
    public SortedSet<ArtifactDiff> artifactsChanged = new TreeSet<>();

    public BuildDiff(Build build1, Build build2) {
        this.build1 = build1;
        this.build2 = build2;

        SetUtils.SetView<Artifact> removed = SetUtils.difference(build1.artifacts, build2.artifacts);
        SetUtils.SetView<Artifact> added = SetUtils.difference(build2.artifacts, build1.artifacts);

        for (Artifact artifact : removed) {
            Set<Artifact> matches = ArtifactDiff.containsDifferentVersion(artifact, added);

            if (matches.isEmpty()) {
                this.artifactsRemoved.add(artifact);
            } else {
                this.artifactsChanged.add(new ArtifactDiff(artifact, matches.iterator().next()));
            }
        }

        for (Artifact artifact : added) {
            Set<Artifact> matches = ArtifactDiff.containsDifferentVersion(artifact, removed);

            if (matches.isEmpty()) {
                this.artifcatsAdded.add(artifact);
            } else {
                this.artifactsChanged.add(new ArtifactDiff(matches.iterator().next(), artifact));
            }
        }
    }

    public static Set<Build> containsDifferentVersion(Build build, SetUtils.SetView<Build> builds) {
        SortedSet<Build> ret = new TreeSet<>();

        try {
            KojiNVR nvr1 = KojiNVR.parseNVR(build.identifier);

            for (Build b : builds) {
                KojiNVR nvr2 = KojiNVR.parseNVR(b.identifier);

                if (nvr2.getName().equals(nvr1.getName())) {
                    ret.add(b);
                }
            }

        } catch (KojiClientException e) {
            throw new BadRequestException(e);
        }

        return ret;
    }

    @Override
    public int compareTo(BuildDiff o) {
        return this.build1.compareTo(o.build1);
    }
}
