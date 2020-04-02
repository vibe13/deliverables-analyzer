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
package org.jboss.pnc.deliverablesanalyzer.rest;

import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.jboss.pnc.build.finder.core.BuildSystem;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.jboss.pnc.deliverablesanalyzer.Finder;
import org.jboss.pnc.deliverablesanalyzer.model.Artifact;
import org.jboss.pnc.deliverablesanalyzer.model.Build;
import org.jboss.pnc.deliverablesanalyzer.model.BuildSystemType;
import org.jboss.pnc.deliverablesanalyzer.model.MavenArtifact;
import org.jboss.pnc.deliverablesanalyzer.model.NpmArtifact;
import org.jboss.pnc.deliverablesanalyzer.model.Product;
import org.jboss.pnc.deliverablesanalyzer.model.ProductVersion;
import org.jboss.pnc.deliverablesanalyzer.model.ProductVersionDiff;
import org.jboss.resteasy.annotations.jaxrs.FormParam;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.jboss.resteasy.annotations.jaxrs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

@Path("/product-versions")
@ApplicationScoped
public class ProductVersionResource implements ProductVersionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductVersionResource.class);

    @Inject
    @RequestScoped
    SecurityIdentity identity;

    @Inject
    ManagedExecutor pool;

    @Override
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<ProductVersion> get(@QueryParam String artifact, @QueryParam String hash, @QueryParam String build) {
        if (artifact != null) {
            return Artifact.findByIdentifier(artifact)
                    .stream()
                    .map(a -> a.build)
                    .filter(b -> b.identifier.matches(build))
                    .flatMap(b -> b.productVersions.stream())
                    .distinct()
                    .collect(Collectors.toList());
        }

        if (hash != null) {
            return Artifact.findByHash(hash)
                    .stream()
                    .map(a -> a.build)
                    .filter(b -> b.identifier.matches(build))
                    .flatMap(b -> b.productVersions.stream())
                    .distinct()
                    .collect(Collectors.toList());
        }

        if (build != null) {
            return Build.<Build> streamAll()
                    .filter(b -> b.identifier.matches(build))
                    .flatMap(b -> b.productVersions.stream())
                    .distinct()
                    .collect(Collectors.toList());
        }

        return ProductVersion.listAll(Sort.by("version"));
    }

    @Override
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public ProductVersion getSingle(@NotNull @Positive @PathParam Long id) {
        ProductVersion entity = ProductVersion.findById(id);

        if (entity == null) {
            throw new NotFoundException();
        }

        return entity;
    }

    @Override
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    @Transactional
    public Response create(@NotNull @Valid ProductVersion param) {
        param.username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
        param.created = new Date();
        param.product = Product.findById(param.product.id);

        param.persist();

        param.product.productVersions.add(param);

        return Response.ok(param).status(Response.Status.CREATED).build();
    }

    @Override
    @PUT
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @Transactional
    public ProductVersion update(@NotNull @Positive @PathParam Long id, @NotNull @Valid ProductVersion param) {
        ProductVersion entity = getSingle(id);

        entity.builds = param.builds;
        entity.imported = param.imported;
        entity.importedBy = param.importedBy;
        entity.product = param.product;
        entity.version = param.version;

        return entity;
    }

    @Override
    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@NotNull @Positive @PathParam Long id) {
        ProductVersion entity = getSingle(id);

        entity.delete();

        return Response.noContent().build();
    }

    @Override
    @GET
    @Path("{id}/built-from-source")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response geBuiltFromSource(
            @NotNull @Positive @PathParam Long id,
            @QueryParam @NotNull Boolean builtFromSource) {
        Boolean isBuiltFromSource = getBuilds(id).stream()
                .flatMap(build -> build.artifacts.stream())
                .filter(a -> a.builtFromSource.equals(builtFromSource))
                .count() == 0L;
        ObjectNode objectNode = new ObjectMapper().createObjectNode()
                .put("builtFromSource", isBuiltFromSource.toString());

        return Response.ok(objectNode.toString()).build();
    }

    @Override
    @GET
    @Path("{id}/artifacts")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Set<Artifact> getArtifacts(@NotNull @Positive @PathParam Long id, @PathParam String regex) {
        Stream<Artifact> stream = getBuilds(id).stream().flatMap(build -> build.artifacts.stream());

        if (regex == null) {
            return stream.collect(Collectors.toSet());
        }

        return stream.filter(artifact -> artifact.identifier.matches(regex)).collect(Collectors.toSet());
    }

    @Override
    @GET
    @Path("{id}/builds")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Set<Build> getBuilds(@NotNull @Positive @PathParam Long id) {
        ProductVersion entity = getSingle(id);

        return entity.builds;
    }

    @Override
    @POST
    @Path("{id}/import")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Authenticated
    @Transactional
    public CompletionStage<Map<BuildSystemInteger, KojiBuild>> importFile(
            @NotNull @Positive @PathParam Long id,
            @NotNull @FormParam URL url) {
        ProductVersion productVersion = ProductVersion.findById(id);
        String username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();

        LOGGER.info("Hello {}!", username);

        LOGGER.info("Checking if {} {} was already imported", productVersion.product.shortname, productVersion.version);

        if (LOGGER.isInfoEnabled()) {
            if (productVersion.imported == null) {
                LOGGER.info("Creating new product {} {}", productVersion.product.name, productVersion.version);
            } else {
                LOGGER.info(
                        "Product (" + productVersion.product.name + " " + productVersion.version
                                + ") has already been imported by " + productVersion.importedBy + " on "
                                + productVersion.imported);
            }
        }

        CompletionStage<Map<BuildSystemInteger, KojiBuild>> cs = pool.supplyAsync(() -> {
            Map<BuildSystemInteger, KojiBuild> builds = new Finder().find(url);

            if (productVersion.imported != null) {
                importBuilds(productVersion, username, builds);
            } else {
                LOGGER.info(
                        "Product ({}) has already been imported by {} on {}",
                        productVersion.product.name,
                        productVersion.version,
                        productVersion.importedBy,
                        productVersion.imported);
            }

            return builds;
        });

        productVersion.url = url;
        productVersion.imported = new Date();
        productVersion.importedBy = username;

        return cs;
    }

    @Timed(
            name = "importBuildsTimer",
            description = "A measure of how long it takes to perform the product insertion.",
            unit = MetricUnits.MILLISECONDS)
    @Transactional
    private void importBuilds(
            ProductVersion productVersion,
            String username,
            Map<BuildSystemInteger, KojiBuild> builds) {
        builds.remove(new BuildSystemInteger(0, BuildSystem.none));

        LOGGER.info(
                "Importing {} builds for productVersion: {} {}, username: {}",
                builds.size(),
                productVersion.product.shortname,
                productVersion.version,
                username);

        productVersion.imported = new Date();
        productVersion.importedBy = username;

        productVersion.product.productVersions.add(productVersion);

        LOGGER.info("Initial setup of product version: {}", productVersion);

        int numBuilds = builds.size();
        int buildCount = 0;

        Set<Entry<BuildSystemInteger, KojiBuild>> entrySet = builds.entrySet();

        for (Entry<BuildSystemInteger, KojiBuild> entry : entrySet) {
            buildCount++;

            BuildSystemInteger buildSystemInteger = entry.getKey();
            BuildSystemType buildSystemType;

            if (buildSystemInteger.getBuildSystem().equals(BuildSystem.pnc)) {
                buildSystemType = BuildSystemType.PNC;
            } else {
                buildSystemType = BuildSystemType.KOJI;
            }

            KojiBuild kojiBuild = entry.getValue();
            String identifier = kojiBuild.getBuildInfo().getNvr();

            LOGGER.info("Build: {} / {} ({}.{})", buildCount, numBuilds, identifier, buildSystemType);

            Build existingBuild = Build.findByIdentifierAndBuildSystemType(identifier, buildSystemType);
            Build build;

            if (existingBuild == null) {
                build = new Build();

                build.identifier = identifier;
                build.buildSystemType = buildSystemType;

                if (build.buildSystemType.equals(BuildSystemType.PNC)) {
                    build.pncId = (long) kojiBuild.getBuildInfo().getId();
                } else {
                    build.kojiId = (long) kojiBuild.getBuildInfo().getId();
                }

                build.source = kojiBuild.getSource();
                build.builtFromSource = !kojiBuild.isImport();

                // FIXME
                build.username = username;
                build.created = new Date();

                // buildResource.create(build);

                build.persist();

                build.productVersions.add(productVersion);

                LOGGER.info("Build has {} product versions", build.productVersions.size());
            } else {
                build = existingBuild;
                build.productVersions.add(productVersion);
                LOGGER.info("Existing build has {} product versions", build.productVersions.size());
            }

            List<KojiLocalArchive> localArchives = kojiBuild.getArchives();
            int numArchives = localArchives.size();
            int archiveCount = 0;

            for (KojiLocalArchive localArchive : localArchives) {
                archiveCount++;

                KojiArchiveInfo archiveInfo = localArchive.getArchive();
                String artifactIdentifier;
                Artifact artifact;
                MavenArtifact mavenArtifact = null;
                NpmArtifact npmArtifact = null;

                if (archiveInfo.getBuildType().equals("maven")) {
                    String groupId = archiveInfo.getGroupId();
                    String artifactId = archiveInfo.getArtifactId();
                    String type = archiveInfo.getExtension() != null ? archiveInfo.getExtension() : "";
                    String version = archiveInfo.getVersion();
                    String classifier = archiveInfo.getClassifier() != null ? archiveInfo.getClassifier() : "";
                    artifactIdentifier = String.join(":", groupId, artifactId, type, version, classifier);
                    mavenArtifact = new MavenArtifact();
                    mavenArtifact.groupId = groupId;
                    mavenArtifact.artifactId = artifactId;
                    mavenArtifact.type = type;
                    mavenArtifact.version = version;
                    mavenArtifact.classifier = classifier;
                } else if (archiveInfo.getBuildType().equals("npm")) { // TODO: NPM support doesn't exist yet
                    String name = archiveInfo.getArtifactId();
                    String version = archiveInfo.getVersion();
                    artifactIdentifier = String.join(":", name, version);
                    npmArtifact = new NpmArtifact();
                    npmArtifact.name = name;
                    npmArtifact.version = version;
                } else {
                    throw new BadRequestException(
                            "Archive " + archiveInfo.getArtifactId() + " had unhandled artifact type: "
                                    + archiveInfo.getBuildType());
                }

                LOGGER.info("Artifact: {} / {} ({})", archiveCount, numArchives, artifactIdentifier);

                Artifact existingArtifact = Artifact
                        .findByIdentifierAndBuildSystemType(artifactIdentifier, buildSystemType);

                if (existingArtifact == null) {
                    artifact = new Artifact();

                    artifact.identifier = artifactIdentifier;
                    artifact.buildSystemType = buildSystemType;
                    artifact.builtFromSource = localArchive.isBuiltFromSource();
                    artifact.filesNotBuiltFromSource.addAll(localArchive.getUnmatchedFilenames());

                    Collection<Checksum> checksums = localArchive.getChecksums();

                    for (Checksum checksum : checksums) {
                        switch (checksum.getType()) {
                            case md5:
                                artifact.md5 = checksum.getValue();
                                break;
                            case sha1:
                                artifact.sha1 = checksum.getValue();
                                break;
                            case sha256:
                                artifact.sha256 = checksum.getValue();
                                break;
                            default:
                                break;
                        }
                    }

                    if (kojiBuild.isPnc()) {
                        artifact.pncId = Long.valueOf(archiveInfo.getArchiveId());
                    } else {
                        artifact.kojiId = Long.valueOf(archiveInfo.getArchiveId());
                    }

                    artifact.build = build;

                    // FIXME
                    artifact.username = username;
                    artifact.created = new Date();

                    // artifactResource.create(artifact);

                    artifact.persist();

                    build.artifacts.add(artifact);

                    if (mavenArtifact != null) {
                        mavenArtifact.artifact = artifact;
                        mavenArtifact.username = username;
                        mavenArtifact.created = new Date();
                        mavenArtifact.persist();
                        artifact.mavenArtifact = mavenArtifact;
                        artifact.type = Artifact.Type.MAVEN;
                    } else if (npmArtifact != null) {
                        npmArtifact.artifact = artifact;
                        npmArtifact.username = username;
                        npmArtifact.created = new Date();
                        npmArtifact.persist();
                        artifact.npmArtifact = npmArtifact;
                        artifact.type = Artifact.Type.NPM;
                    }

                } else {
                    build.artifacts.add(existingArtifact);
                }
            }

            productVersion.builds.add(build);
        }
    }

    @Override
    @GET
    @Path("{id}/diff/{id2}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    public ProductVersionDiff getDiff(@NotNull @Positive @PathParam Long id, @NotNull @Positive @PathParam Long id2) {
        return new ProductVersionDiff(getSingle(id), getSingle(id2));
    }

    @Override
    @GET
    @Path("/products/{productId}/versions/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public ProductVersion getByProductIdAndVersion(
            @NotNull @Positive @PathParam Long productId,
            @NotBlank @PathParam String version) {
        return ProductVersion.findByProductIdAndVersion(productId, version);
    }
}
