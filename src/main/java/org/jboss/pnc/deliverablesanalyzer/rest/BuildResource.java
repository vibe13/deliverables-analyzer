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

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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
import org.jboss.pnc.deliverablesanalyzer.model.Build;
import org.jboss.pnc.deliverablesanalyzer.model.ProductVersion;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

@Path("builds")
@ApplicationScoped
public class BuildResource {
    @Inject
    @RequestScoped
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/built-from-source/{builtFromSource}")
    @PermitAll
    public static List<Build> findByBuiltFromSource(@NotNull @PathParam Boolean builtFromSource) {
        return Build.findByBuiltFromSource(builtFromSource);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<Build> get() {
        return Build.listAll(Sort.by("identifier"));
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Build getSingle(@NotNull @Positive @PathParam Long id) {
        Build entity = Build.findById(id);

        if (entity == null) {
            throw new NotFoundException();
        }

        return entity;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    @Transactional
    public Response create(@Valid Build param) {
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
    @RolesAllowed("admin")
    @Transactional
    public Build update(@NotNull @Positive @PathParam Long id, @NotNull @Valid Build param) {
        Build entity = getSingle(id);

        entity.identifier = param.identifier;
        entity.kojiId = param.kojiId;
        entity.pncId = param.pncId;
        entity.artifacts = param.artifacts;
        entity.productVersions = param.productVersions;

        return entity;
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@NotNull @Positive @PathParam Long id) {
        Build entity = getSingle(id);

        entity.delete();

        return Response.noContent().build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/artifacts")
    @PermitAll
    public Set<Artifact> getArtifacts(@NotNull @Positive @PathParam Long id) {
        return getSingle(id).artifacts;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/product-versions")
    @PermitAll
    public Set<ProductVersion> getProductVersions(@NotNull @Positive @PathParam Long id) {
        return getSingle(id).productVersions;
    }

    @GET
    @Path("/koji-id/{kojiId}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<Build> findByKojiId(@NotNull @Positive @PathParam Long kojiId) {
        return Build.findByKojiId(kojiId);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/pncId/{pncId}")
    @PermitAll
    public List<Build> findByPncId(@NotNull @Positive @PathParam Long pncId) {
        return Build.findByPncId(pncId);
    }
}
