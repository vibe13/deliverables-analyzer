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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
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

import org.jboss.pnc.deliverablesanalyzer.model.Artifact;
import org.jboss.pnc.deliverablesanalyzer.model.BlacklistedArtifact;
import org.jboss.pnc.deliverablesanalyzer.model.Build;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

@Path("blacklisted-artifacts")
@ApplicationScoped
public class BlacklistedArtifactResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlacklistedArtifactResource.class);

    @Inject
    @RequestScoped
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<Artifact> get() {
        return BlacklistedArtifact.listAll(Sort.by("created", Sort.Direction.Descending));
    }

    @GET
    @Path("{id}")
    @PermitAll
    public BlacklistedArtifact getSingle(@NotNull @Positive @PathParam Long id) {
        BlacklistedArtifact entity = BlacklistedArtifact.findById(id);

        if (entity == null) {
            throw new NotFoundException();
        }

        return entity;
    }

    @POST
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    public Response create(@Valid BlacklistedArtifact param) {
        List<BlacklistedArtifact> ret = new ArrayList<>();

        if (param.artifactId != null) {
            BlacklistedArtifact found = BlacklistedArtifact.findByArtifactId(param.artifactId);

            if (found != null) {
                throw new BadRequestException("Artifact id " + param.artifactId + " already on blacklist");
            }

            Artifact artifact = Artifact.findById(param.artifactId);

            if (artifact == null) {
                throw new NotFoundException("Artifact with id " + param.artifactId + " not found");
            }

            LOGGER.info("Blacklisting artifact: {}", param.artifactId);

            param.username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
            param.created = new Date();

            param.persist();

            ret.add(param);
        } else if (param.buildId != null) {
            Build build = Build.findById(param.buildId);

            if (build == null) {
                throw new NotFoundException("Build with id " + param.buildId + " not found");
            }

            for (Artifact artifact : build.artifacts) {
                BlacklistedArtifact found = BlacklistedArtifact.findByArtifactId(artifact.id);

                if (found != null) {
                    LOGGER.info("Artifact {} already on blacklist", artifact.identifier);

                    continue;
                }

                LOGGER.info(
                        "Blacklisting artifact {} from build id {}: {}",
                        artifact.identifier,
                        build.id,
                        build.identifier);

                BlacklistedArtifact blacklistedArtifact = new BlacklistedArtifact();

                blacklistedArtifact.username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
                blacklistedArtifact.created = new Date();

                blacklistedArtifact.artifactId = artifact.id;
                blacklistedArtifact.buildId = build.id;
                blacklistedArtifact.regex = param.regex;
                blacklistedArtifact.type = param.type;
                blacklistedArtifact.reason = param.reason;

                blacklistedArtifact.persist();

                ret.add(blacklistedArtifact);
            }
        } else if (param.regex != null) {
            LOGGER.info("Look for artifacts matching {}", param.regex);

            Stream<Artifact> artifactsStream = Artifact.streamAll();
            List<Artifact> artifacts = artifactsStream.filter(a -> a.identifier.matches(param.regex))
                    .collect(Collectors.toList());

            LOGGER.info("Found {} artifacts matching {}", artifacts.size(), param.regex);

            if (artifacts.isEmpty()) {
                throw new BadRequestException("No artifacts matched regex: " + param.regex);
            }

            for (Artifact artifact : artifacts) {
                BlacklistedArtifact found = BlacklistedArtifact.findByArtifactId(artifact.id);

                if (found != null) {
                    LOGGER.info("Artifact {} already on blacklist", artifact.identifier);

                    continue;
                }

                LOGGER.info("Blacklisting artifact {} matching {}", artifact.identifier, param.regex);

                BlacklistedArtifact blacklistedArtifact = new BlacklistedArtifact();

                blacklistedArtifact.username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
                blacklistedArtifact.created = new Date();

                blacklistedArtifact.artifactId = artifact.id;
                blacklistedArtifact.buildId = artifact.build.id;
                blacklistedArtifact.regex = param.regex;
                blacklistedArtifact.type = param.type;
                blacklistedArtifact.reason = param.reason;

                blacklistedArtifact.persist();

                ret.add(blacklistedArtifact);
            }
        } else {
            throw new BadRequestException("Invalid request");
        }

        return Response.ok(ret).status(Response.Status.CREATED).build();
    }

    @PUT
    @Path("{id}")
    @Transactional
    @RolesAllowed("admin")
    public BlacklistedArtifact update(
            @NotNull @Positive @PathParam Long id,
            @NotNull @Valid BlacklistedArtifact param) {
        BlacklistedArtifact entity = getSingle(id);

        entity.artifactId = param.artifactId;
        entity.buildId = param.buildId;
        entity.type = param.type;
        entity.reason = param.reason;

        return entity;
    }

    @DELETE
    @Path("{id}")
    @Transactional
    @RolesAllowed("admin")
    public Response delete(@NotNull @Positive @PathParam Long id) {
        BlacklistedArtifact entity = getSingle(id);

        entity.delete();

        return Response.noContent().build();
    }

    @GET
    @Path("/type/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<BlacklistedArtifact> findByType(@NotNull @Valid @PathParam BlacklistedArtifact.Type type) {
        return BlacklistedArtifact.findByType(type);
    }

    @GET
    @Path("/reason/{reason}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<BlacklistedArtifact> findByType(@NotEmpty @PathParam String reason) {
        return BlacklistedArtifact.findByReason(reason);
    }
}
