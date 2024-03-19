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

import static org.jboss.pnc.build.finder.core.BuildFinderUtils.isBuildIdZero;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.BadRequestException;

import org.jboss.pnc.api.deliverablesanalyzer.dto.Artifact;
import org.jboss.pnc.api.deliverablesanalyzer.dto.Build;
import org.jboss.pnc.api.deliverablesanalyzer.dto.BuildSystemType;
import org.jboss.pnc.api.deliverablesanalyzer.dto.FinderResult;
import org.jboss.pnc.api.deliverablesanalyzer.dto.MavenArtifact;
import org.jboss.pnc.api.deliverablesanalyzer.dto.NPMArtifact;
import org.jboss.pnc.build.finder.core.BuildSystem;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

public final class FinderResultCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinderResultCreator.class);

    private FinderResultCreator() {
    }

    public static FinderResult createFinderResult(String id, URL url, Map<BuildSystemInteger, KojiBuild> builds) {
        FinderResult.Builder builder = FinderResult.builder()
                .id(id)
                .url(url)
                .notFoundArtifacts(getNotFoundArtifacts(builds))
                .builds(getFoundBuilds(builds));

        setUrlChecksums(builder, url, builds);

        return builder.build();
    }

    private static void setCommonArtifactFields(Artifact.ArtifactBuilder builder, KojiLocalArchive archive) {
        KojiArchiveInfo archiveInfo = archive.getArchive();
        long size = archiveInfo.getSize();

        builder.filename(archiveInfo.getFilename()).size(size);

        for (Checksum checksum : archive.getChecksums()) {
            switch (checksum.getType()) {
                case md5:
                    builder.md5(checksum.getValue());
                    break;
                case sha1:
                    builder.sha1(checksum.getValue());
                    break;
                case sha256:
                    builder.sha256(checksum.getValue());
                    break;
                default:
                    break;
            }
        }
    }

    private static MavenArtifact.MavenArtifactBuilder createMavenArtifact(KojiArchiveInfo archiveInfo) {
        return MavenArtifact.builder()
                .groupId(archiveInfo.getGroupId())
                .artifactId(archiveInfo.getArtifactId())
                .type(archiveInfo.getExtension())
                .version(archiveInfo.getVersion())
                .classifier(archiveInfo.getClassifier());
    }

    private static NPMArtifact.NPMArtifactBuilder createNpmArtifact(KojiArchiveInfo archiveInfo) {
        return NPMArtifact.builder().name(archiveInfo.getArtifactId()).version(archiveInfo.getVersion());
    }

    private static Collection<Artifact> createNotFoundArtifacts(KojiLocalArchive localArchive) {
        Collection<Artifact> artifacts = new ArrayList<>();

        if (localArchive.getFilenames() == null || localArchive.getFilenames().isEmpty()) {
            throw new IllegalArgumentException("Filename for not-found artifact is missing. " + localArchive);
        }

        for (String filename : localArchive.getFilenames()) {
            Artifact.ArtifactBuilder<?, ?> builder = Artifact.builder().builtFromSource(false);
            localArchive.getArchive().setFilename(filename);

            setCommonArtifactFields(builder, localArchive);
            builder.archiveFilenames(List.of(filename)).archiveUnmatchedFilenames(localArchive.getUnmatchedFilenames());

            artifacts.add(builder.build());
        }

        return artifacts;
    }

    private static Set<Artifact> getNotFoundArtifacts(Map<BuildSystemInteger, KojiBuild> builds) {
        int buildsSize = builds.size();

        if (buildsSize == 0) {
            return Collections.unmodifiableSet(new LinkedHashSet<>());
        }

        KojiBuild buildZero = builds.get(new BuildSystemInteger(0));
        List<KojiLocalArchive> localArchives = buildZero.getArchives();
        int numArchives = localArchives.size();

        if (numArchives == 0) {
            return Collections.unmodifiableSet(new LinkedHashSet<>());
        }

        Set<Artifact> artifacts = new LinkedHashSet<>(numArchives);
        int archiveCount = 0;

        for (KojiLocalArchive localArchive : localArchives) {
            Collection<Artifact> notFoundArtifacts = createNotFoundArtifacts(localArchive);
            artifacts.addAll(notFoundArtifacts);

            if (LOGGER.isDebugEnabled()) {
                archiveCount += notFoundArtifacts.size();
                LOGGER.debug(
                        "Not found artifact: {} / {} ({})",
                        archiveCount,
                        numArchives,
                        localArchive.getFilenames());
            }
        }

        return Collections.unmodifiableSet(artifacts);
    }

    private static Build createBuild(
            BuildSystemInteger buildSystemInteger,
            KojiBuild kojiBuild,
            Set<Artifact> artifacts) {
        Build.Builder builder = Build.builder();
        if (buildSystemInteger.getBuildSystem() == BuildSystem.pnc) {
            builder.buildSystemType(BuildSystemType.PNC);
            builder.pncId(kojiBuild.getId());
        } else {
            builder.buildSystemType(BuildSystemType.BREW);
            builder.brewId((long) kojiBuild.getBuildInfo().getId());
            builder.brewNVR(kojiBuild.getBuildInfo().getNvr());
        }
        return builder.isImport(kojiBuild.isImport()).artifacts(artifacts).build();
    }

    private static Artifact createArtifact(KojiLocalArchive localArchive, BuildSystem buildSystem, boolean imported) {
        KojiArchiveInfo archiveInfo = localArchive.getArchive();

        Artifact.ArtifactBuilder builder;
        if ("maven".equals(archiveInfo.getBuildType()) || "gradle".equals(archiveInfo.getBuildType())) {
            builder = createMavenArtifact(archiveInfo);
        } else if ("npm".equals(archiveInfo.getBuildType())) {
            builder = createNpmArtifact(archiveInfo);
        } else {
            throw new BadRequestException(
                    "Archive " + archiveInfo.getArtifactId() + " had unhandled artifact type: "
                            + archiveInfo.getBuildType());
        }

        switch (buildSystem) {
            case pnc:
                builder.buildSystemType(BuildSystemType.PNC);
                builder.pncId(archiveInfo.getArchiveId().toString());
                break;
            case koji:
                builder.buildSystemType(BuildSystemType.BREW);
                builder.brewId(archiveInfo.getArchiveId().longValue());
                break;
            default:
                throw new IllegalArgumentException("Unknown build system " + buildSystem);
        }
        builder.builtFromSource(localArchive.isBuiltFromSource() && !imported);

        setCommonArtifactFields(builder, localArchive);
        builder.archiveFilenames(localArchive.getFilenames())
                .archiveUnmatchedFilenames(localArchive.getUnmatchedFilenames());

        return builder.build();
    }

    private static void setUrlChecksums(
            FinderResult.Builder builder,
            URL url,
            Map<BuildSystemInteger, KojiBuild> builds) {

        // Find the url filename
        String filename = Paths.get(url.getPath()).getFileName().toString();
        // Loop in the builds to find the local archive associated with the url
        for (Map.Entry<BuildSystemInteger, KojiBuild> buildEntry : builds.entrySet()) {
            List<KojiLocalArchive> localArchives = buildEntry.getValue().getArchives();
            if (localArchives == null || localArchives.isEmpty()) {
                return;
            }
            for (KojiLocalArchive localArchive : localArchives) {
                if (localArchive.getFilenames().contains(filename)) {
                    for (Checksum checksum : localArchive.getChecksums()) {
                        switch (checksum.getType()) {
                            case md5:
                                builder.md5(checksum.getValue());
                                break;
                            case sha1:
                                builder.sha1(checksum.getValue());
                                break;
                            case sha256:
                                builder.sha256(checksum.getValue());
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        }
    }

    private static Set<Build> getFoundBuilds(Map<BuildSystemInteger, KojiBuild> builds) {
        int buildsSize = builds.size();

        if (buildsSize <= 1) {
            return Collections.unmodifiableSet(new LinkedHashSet<>());
        }

        int numBuilds = buildsSize - 1;
        Set<Build> buildList = new LinkedHashSet<>(numBuilds);
        int buildCount = 0;

        for (Map.Entry<BuildSystemInteger, KojiBuild> entry : builds.entrySet()) {
            BuildSystemInteger buildSystemInteger = entry.getKey();

            if (isBuildIdZero(buildSystemInteger.getValue())) {
                continue;
            }

            KojiBuild kojiBuild = entry.getValue();
            List<KojiLocalArchive> localArchives = kojiBuild.getArchives();

            int numArchives = localArchives.size();
            int archiveCount = 0;

            Set<Artifact> artifacts = new HashSet<>();

            for (KojiLocalArchive localArchive : localArchives) {
                Artifact artifact = createArtifact(
                        localArchive,
                        buildSystemInteger.getBuildSystem(),
                        kojiBuild.isImport());

                artifacts.add(artifact);

                if (LOGGER.isDebugEnabled()) {
                    archiveCount++;
                    String identifier;
                    switch (artifact.getBuildSystemType()) {
                        case BREW:
                            identifier = "Brew#" + artifact.getBrewId();
                            break;
                        case PNC:
                            identifier = "PNC#" + artifact.getPncId();
                            break;
                        default:
                            identifier = "Unknown#-1";
                            break;
                    }

                    LOGGER.debug("Artifact: {} / {} ({})", archiveCount, numArchives, identifier);
                }
            }

            Build build = createBuild(buildSystemInteger, kojiBuild, artifacts);

            if (LOGGER.isDebugEnabled()) {
                buildCount++;

                String identifier;
                switch (build.getBuildSystemType()) {
                    case BREW:
                        identifier = "Brew#" + build.getBrewId();
                        break;
                    case PNC:
                        identifier = "PNC#" + build.getPncId();
                        break;
                    default:
                        identifier = "Unknown#-1";
                        break;
                }

                LOGGER.debug("Build: {} / {} ({})", buildCount, numBuilds, identifier);
            }

            buildList.add(build);
        }

        return Collections.unmodifiableSet(buildList);
    }
}
