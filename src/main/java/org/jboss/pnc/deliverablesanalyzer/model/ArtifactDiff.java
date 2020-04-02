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

public class ArtifactDiff implements Comparable<ArtifactDiff> {
    @NotNull
    @Valid
    public Artifact artifact1;

    @NotNull
    @Valid
    public Artifact artifact2;

    public ArtifactDiff(Artifact artifact1, Artifact artifact2) {
        this.artifact1 = artifact1;
        this.artifact2 = artifact2;
    }

    public static Set<Artifact> containsDifferentVersion(Artifact artifact, SetUtils.SetView<Artifact> artifacts) {
        SortedSet<Artifact> ret = new TreeSet<>();

        for (Artifact a : artifacts) {
            if (equalComponents(a, artifact)) {
                ret.add(a);
            }
        }

        return ret;
    }

    private static boolean equalComponents(Artifact a, Artifact artifact) {
        String[] c1 = a.identifier.split(":");
        String[] c2 = artifact.identifier.split(":");

        if (c1.length != c2.length) {
            return false;
        }

        if (c1.length == 2) {
            return c1[0].equals(c2[0]);
        }

        if (c1.length == 3) {
            return c1[0].equals(c2[0]) && c1[1].equals(c2[1]);
        }

        if (c2.length == 5) {
            return c1[0].equals(c2[0]) && c1[1].equals(c2[1]) && c1[2].equals(c2[2]) && c1[4].equals(c2[4]);
        }

        return false;
    }

    @Override
    public int compareTo(ArtifactDiff o) {
        return this.artifact1.compareTo(o.artifact1);
    }
}
