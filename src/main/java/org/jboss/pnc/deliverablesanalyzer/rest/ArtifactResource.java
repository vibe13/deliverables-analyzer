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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
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
import org.jboss.pnc.deliverablesanalyzer.model.ProductVersion;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.jboss.resteasy.annotations.jaxrs.QueryParam;

import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

@Path("artifacts")
@ApplicationScoped
public class ArtifactResource {
    @Inject
    @RequestScoped
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/built-from-source")
    @PermitAll
    public static List<Artifact> findByBuiltFromSource(@NotNull @QueryParam Boolean builtFromSource) {
        return Artifact.findByBuiltFromSource(builtFromSource);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<Artifact> get(@QueryParam String regex, @QueryParam Artifact.Type type) {
        if (regex != null) {
            Stream<Artifact> artifactStream = Artifact.streamAll(Sort.by("identifier"));

            return artifactStream.filter(a -> a.identifier.matches(regex)).collect(Collectors.toList());
        }

        if (type != null) {
            return Artifact.findByType(type);
        }

        return Artifact.listAll(Sort.by("identifier"));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @PermitAll
    public Artifact getSingle(@NotNull @Positive @PathParam Long id) {
        Artifact entity = Artifact.findById(id);

        if (entity == null) {
            throw new NotFoundException();
        }

        return entity;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Authenticated
    public Response create(@NotNull @Valid Artifact param) {
        // FIXME: When this is called from another service, the identity doesn't seem to propagate
        // param.username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
        // param.created = new Date();

        param.persist();

        return Response.ok(param).status(Response.Status.CREATED).build();
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @Transactional
    @RolesAllowed("admin")
    public Artifact update(@NotNull @Positive @PathParam Long id, @NotNull @Valid Artifact param) {
        Artifact entity = Artifact.findById(id);

        if (entity == null) {
            throw new NotFoundException();
        }

        entity.identifier = param.identifier;
        entity.md5 = param.md5;
        entity.sha1 = param.sha1;
        entity.sha256 = param.sha256;
        entity.kojiId = param.kojiId;
        entity.pncId = param.pncId;
        entity.build = param.build;

        return entity;
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @Transactional
    @RolesAllowed("admin")
    public Response delete(@NotNull @Positive @PathParam Long id) {
        Artifact entity = getSingle(id);

        entity.delete();

        return Response.noContent().build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/md5/{md5}")
    @PermitAll
    public List<Artifact> findByMd5(@Pattern(regexp = "^[a-f0-9]{32}$") @PathParam String md5) {
        return Artifact.findByMd5(md5);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/sha1/{sha1}")
    @PermitAll
    public List<Artifact> findBySha1(@Pattern(regexp = "^[a-f0-9]{40}$") @PathParam String sha1) {
        return Artifact.findBySha1(sha1);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/sha256/{sha256}")
    @PermitAll
    public List<Artifact> findBySha256(@Pattern(regexp = "^[a-f0-9]{64}$") @PathParam String sha256) {
        return Artifact.findBySha256(sha256);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/koji-id/{kojiId}")
    @PermitAll
    public List<Artifact> findByKojiId(@NotNull @Positive @PathParam Long kojiId) {
        return Artifact.findByKojiId(kojiId);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pnc-id/{pncId}")
    @PermitAll
    public List<Artifact> findByPncId(@NotNull @Positive @PathParam Long pncId) {
        return Artifact.findByPncId(pncId);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/product-versions")
    @PermitAll
    public Set<ProductVersion> getProductVersions(@NotNull @Positive @PathParam Long id) {
        Artifact entity = getSingle(id);

        return ProductVersion.<ProductVersion> streamAll(Sort.by("shortname"))
                .filter(pv -> pv.builds.contains(entity.build))
                .collect(Collectors.toSet());
    }
}
