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

import java.util.Date;
import java.util.List;

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

import org.jboss.pnc.deliverablesanalyzer.model.Product;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.jboss.resteasy.annotations.jaxrs.QueryParam;

import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

@Path("products")
@ApplicationScoped
public class ProductResource implements ProductService {
    @Inject
    @RequestScoped
    SecurityIdentity identity;

    @Override
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<Product> get(@QueryParam String shortname) {
        if (shortname != null) {
            return Product.getByShortName(shortname);
        }

        return Product.listAll(Sort.by("name"));
    }

    @Override
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Product getSingle(@NotNull @Positive @PathParam Long id) {
        Product entity = Product.findById(id);

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
    public Response create(@NotNull @Valid Product param) {
        param.username = identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
        param.created = new Date();

        param.persist();

        return Response.ok(param).status(Response.Status.CREATED).build();
    }

    @Override
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @Transactional
    @RolesAllowed("admin")
    public Product update(@NotNull @Positive @PathParam("id") Long id, @NotNull @Valid Product param) {
        Product entity = getSingle(id);

        entity.name = param.name;
        entity.productVersions = param.productVersions;
        entity.shortname = param.shortname;

        return entity;
    }

    @Override
    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@NotNull @Positive @PathParam("id") Long id) {
        Product entity = getSingle(id);

        entity.delete();

        return Response.noContent().build();
    }
}
