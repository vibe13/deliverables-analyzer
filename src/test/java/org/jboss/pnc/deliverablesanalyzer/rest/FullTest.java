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

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.pnc.build.finder.core.BuildFinderObjectMapper;
import org.jboss.pnc.deliverablesanalyzer.Version;
import org.jboss.pnc.deliverablesanalyzer.model.Artifact;
import org.jboss.pnc.deliverablesanalyzer.model.BlacklistedArtifact;
import org.jboss.pnc.deliverablesanalyzer.model.Build;
import org.jboss.pnc.deliverablesanalyzer.model.Product;
import org.jboss.pnc.deliverablesanalyzer.model.ProductVersion;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;

@QuarkusTest
public class FullTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FullTest.class);

    @Inject
    SecurityIdentity identity;

    @Test
    public void testEverything() throws IOException {
        final String URL_OLD = System.getProperty("old.url");
        final String URL_NEW = System.getProperty("new.url");
        final AuthenticationScheme AUTH = RestAssured.preemptive().basic("admin", "admin");

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.config = RestAssuredConfig.config()
                .objectMapperConfig(
                        new ObjectMapperConfig()
                                .jackson2ObjectMapperFactory((cls, charset) -> new BuildFinderObjectMapper()));
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        given().log()
                .all()
                .accept(MediaType.TEXT_PLAIN)
                .when()
                .get("/api/version")
                .then()
                .log()
                .all()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(not(containsString("unknown")), containsString(Version.getVersion()));

        given().log()
                .all()
                .accept(MediaType.TEXT_PLAIN)
                .when()
                .get("/api/whoami")
                .then()
                .log()
                .all()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(is("anonymous"));

        given().log()
                .all()
                .when()
                .get("/api/products")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("$.size()", is(0));

        Product product = new Product();

        product.name = "Red Hat AMQ";
        product.shortname = "jbossamq";

        ObjectMapper mapper = new BuildFinderObjectMapper();
        String str = mapper.writeValueAsString(product);

        LOGGER.info("Creating product: {}", product);

        // Create a product
        RestAssured.authentication = AUTH;
        long id = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(str)
                .when()
                .post("/api/products")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract()
                .jsonPath()
                .getLong("id");
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        // List it
        int size = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .when()
                .get("/api/products")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getInt("$.size()");

        LOGGER.info("Got {} products", size);

        LOGGER.info("Search for product by name {} and shortname: {}", product.name, product.shortname);

        // Check that it exists again
        given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("name", product.name)
                .queryParam("shortname", product.shortname)
                .when()
                .get("/api/products")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        // Check that it doesn't exist
        given().when()
                .get("/api/product-versions")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("$.size()", is(0));

        // Create a product version
        ProductVersion productVersion = new ProductVersion();
        product.id = id;
        productVersion.product = product;
        productVersion.version = "7.0.2";
        str = mapper.writeValueAsString(productVersion);

        LOGGER.info("Creating product version: {}", productVersion);

        RestAssured.authentication = AUTH;
        long productVersionId = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(str)
                .when()
                .post("/api/product-versions")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getLong("id");
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        // Check that it exists
        given().log()
                .all()
                .when()
                .get("/api/product-versions")
                .then()
                .log()
                .all()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("$.size()", is(1), "[0].version", is("7.0.2"));

        // Create a product version
        ProductVersion productVersion2 = new ProductVersion();
        product.id = id;
        productVersion2.product = product;
        productVersion2.version = "7.0.3";
        str = mapper.writeValueAsString(productVersion2);

        LOGGER.info("Creating product version: {}", productVersion2);

        RestAssured.authentication = AUTH;
        long productVersionId2 = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(str)
                .when()
                .post("/api/product-versions")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getLong("id");
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        // Check that it exists
        LOGGER.info("Should list second product version");

        given().log()
                .all()
                .when()
                .get("/api/product-versions")
                .then()
                .log()
                .all()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("$.size()", is(2));

        LOGGER.info("Analyze for: {}", productVersion);

        given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("url", URL_OLD)
                .when()
                .get("/api/analyze")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        LOGGER.info("Import for: {}", productVersion);

        RestAssured.authentication = AUTH;
        given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", productVersionId)
                .formParam("url", URL_OLD)
                .when()
                .post("/api/product-versions/{id}/import")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        LOGGER.info("Import (again) for: {}", productVersion);

        RestAssured.authentication = AUTH;
        given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", productVersionId)
                .formParam("url", URL_OLD)
                .when()
                .post("/api/product-versions/{id}/import")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        LOGGER.info("Import for: {}", productVersion2);

        RestAssured.authentication = AUTH;
        given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", productVersionId2)
                .formParam("url", URL_NEW)
                .when()
                .post("/api/product-versions/{id}/import")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        LOGGER.info("Get list of artifacts distributed in: {} {}", productVersion.product.name, productVersion.version);

        List<Artifact> productVersionArtifacts = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", productVersionId)
                .when()
                .get("/api/product-versions/{id}/artifacts")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Artifact.class);

        LOGGER.info(
                "Product {} {} has {} artifacts",
                productVersion.product.name,
                productVersion.version,
                productVersionArtifacts.size());

        assertThat(productVersionArtifacts.size(), greaterThan(0));

        String md5 = productVersionArtifacts.get(0).md5;
        String sha1 = productVersionArtifacts.get(0).sha1;
        String sha256 = productVersionArtifacts.get(0).sha256;

        LOGGER.info("Get artifacts by checksum");

        Artifact artifactByMd5 = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("md5", md5)
                .when()
                .get("/api/artifacts/md5/{md5}")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Artifact.class)
                .get(0);
        Artifact artifactBySha1 = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("sha1", sha1)
                .when()
                .get("/api/artifacts/sha1/{sha1}")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Artifact.class)
                .get(0);
        Artifact artifactBySha256 = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("sha256", sha256)
                .when()
                .get("/api/artifacts/sha256/{sha256}")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Artifact.class)
                .get(0);

        assertThat(artifactByMd5.md5.length(), is(32));
        assertThat(artifactBySha1.sha1.length(), is(40));
        assertThat(artifactBySha256.sha256.length(), is(64));

        LOGGER.info("Get artifacts by identifier regex");

        List<Artifact> productVersionArtifactsMatching = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", productVersionId)
                .queryParam("identifier", "org.*")
                .when()
                .get("/api/product-versions/{id}/artifacts")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Artifact.class);

        LOGGER.info(
                "Product {} {} has {} artifacts",
                productVersion.product.name,
                productVersion.version,
                productVersionArtifactsMatching.size());

        assertThat(productVersionArtifacts.size(), greaterThan(0));

        LOGGER.info("Diff for: {} and {}", productVersionId, productVersionId2);

        given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", productVersionId)
                .pathParam("id2", productVersionId2)
                .when()
                .get("/api/product-versions/{id}/diff/{id2}")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        LOGGER.info("List builds");

        List<Build> builds = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .when()
                .get("/api/builds")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Build.class);
        int buildsSize = builds.size();

        LOGGER.info("Got {} builds", buildsSize);

        assertThat(buildsSize, greaterThan(0));

        Build build = builds.get(0);

        LOGGER.info("Get artifacts for build: {}", build.identifier);

        List<Artifact> artifacts = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", build.id)
                .when()
                .get("/api/builds/{id}/artifacts")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Artifact.class);

        LOGGER.info("Get products that consume build: {}", build.identifier);

        List<ProductVersion> productVersions = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", build.id)
                .when()
                .get("/api/builds/{id}/product-versions")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", ProductVersion.class);

        LOGGER.info("Build {} is consumed by: {}", build.identifier, productVersions);

        assertThat(productVersions.size(), greaterThan(0));

        LOGGER.info("List artifacts");

        List<Artifact> allArtifacts = given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .when()
                .get("/api/artifacts")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList(".", Artifact.class);
        int artifactsSize = allArtifacts.size();

        assertThat(artifactsSize, greaterThan(0));

        LOGGER.info("List blacklisted artifacts");

        given().log()
                .all()
                .accept(MediaType.APPLICATION_JSON)
                .when()
                .get("/api/blacklisted-artifacts")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("$.size()", is(0));

        Artifact artifactToBlacklist = artifacts.get(0);

        assertNotNull(artifactToBlacklist);

        BlacklistedArtifact blacklistedArtifact = new BlacklistedArtifact();

        LOGGER.info("Creating blacklist artifact from: {}", artifactToBlacklist.identifier);

        blacklistedArtifact.type = BlacklistedArtifact.Type.CVE;
        blacklistedArtifact.reason = "No reason";
        blacklistedArtifact.artifactId = artifactToBlacklist.id;
        str = mapper.writeValueAsString(blacklistedArtifact);

        RestAssured.authentication = AUTH;
        long blacklistedArtifactId = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(str)
                .when()
                .post("/api/blacklisted-artifacts")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getLong("[0].id");
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", blacklistedArtifactId)
                .when()
                .get("/api/blacklisted-artifacts/{id}")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        Build buildToBlacklist = builds.get(1);

        LOGGER.info(
                "Creating blacklist artifact from build id {}: {}",
                buildToBlacklist.id,
                buildToBlacklist.identifier);

        blacklistedArtifact.artifactId = null;
        blacklistedArtifact.buildId = buildToBlacklist.id;
        blacklistedArtifact.regex = null;
        str = mapper.writeValueAsString(blacklistedArtifact);

        RestAssured.authentication = AUTH;
        long blacklistedArtifactbyBuildId = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(str)
                .when()
                .post("/api/blacklisted-artifacts")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getLong("[0].id");
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", blacklistedArtifactbyBuildId)
                .when()
                .get("/api/blacklisted-artifacts/{id}")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());

        blacklistedArtifact.artifactId = null;
        blacklistedArtifact.buildId = null;
        blacklistedArtifact.regex = "^" + artifactToBlacklist.identifier.split(":")[0] + ":.*$";
        str = mapper.writeValueAsString(blacklistedArtifact);

        LOGGER.info("Creating blacklist artifact from regex: {}", blacklistedArtifact.regex);

        RestAssured.authentication = AUTH;
        long blacklistedArtifactbyPatternId = given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(str)
                .when()
                .post("/api/blacklisted-artifacts")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getLong("[0].id");
        RestAssured.authentication = RestAssured.DEFAULT_AUTH;

        given().log()
                .all()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .pathParam("id", blacklistedArtifactbyPatternId)
                .when()
                .get("/api/blacklisted-artifacts/{id}")
                .then()
                .log()
                .ifError()
                .assertThat()
                .statusCode(Response.Status.OK.getStatusCode());
    }
}
