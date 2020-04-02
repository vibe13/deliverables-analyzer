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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.deliverablesanalyzer.model.Artifact;
import org.jboss.pnc.deliverablesanalyzer.model.Build;
import org.jboss.pnc.deliverablesanalyzer.model.ProductVersion;
import org.jboss.pnc.deliverablesanalyzer.model.ProductVersionDiff;
import org.jboss.resteasy.annotations.jaxrs.FormParam;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.jboss.resteasy.annotations.jaxrs.QueryParam;

import io.quarkus.security.Authenticated;

@Path("product-versions")
public interface ProductVersionService {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    List<ProductVersion> get(@QueryParam String artifact, @QueryParam String hash, @QueryParam String build);

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    ProductVersion getSingle(@NotNull @Positive @PathParam Long id);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    @Transactional
    Response create(@NotNull @Valid ProductVersion param);

    @PUT
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @Transactional
    ProductVersion update(@NotNull @Positive @PathParam Long id, @NotNull @Valid ProductVersion param);

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @Transactional
    Response delete(@NotNull @Positive @PathParam Long id);

    @GET
    @Path("{id}/built-from-source")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    Response geBuiltFromSource(@NotNull @Positive @PathParam Long id, @QueryParam @NotNull Boolean builtFromSource);

    @GET
    @Path("{id}/artifacts")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    Set<Artifact> getArtifacts(@NotNull @Positive @PathParam Long id, @PathParam String regex);

    @GET
    @Path("{id}/builds")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    Set<Build> getBuilds(@NotNull @Positive @PathParam Long id);

    @POST
    @Path("{id}/import")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Transactional
    CompletionStage<Map<BuildSystemInteger, KojiBuild>> importFile(
            @NotNull @Positive @PathParam Long id,
            @NotNull @FormParam URL url);

    @GET
    @Path("{id}/diff/{id2}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    ProductVersionDiff getDiff(@NotNull @Positive @PathParam Long id, @NotNull @Positive @PathParam Long id2);

    @GET
    @Path("/products/{productId}/versions/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    ProductVersion getByProductIdAndVersion(
            @NotNull @Positive @PathParam Long productId,
            @NotBlank @PathParam String version);
}
