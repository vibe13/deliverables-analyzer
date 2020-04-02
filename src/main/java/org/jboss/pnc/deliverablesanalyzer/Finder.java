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
package org.jboss.pnc.deliverablesanalyzer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.io.FileUtils;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.jboss.marshalling.commons.GenericJBossMarshaller;
import org.infinispan.manager.DefaultCacheManager;
import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.ConfigDefaults;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.core.Utils;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.jboss.pnc.build.finder.pnc.client.PncClient14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.KojiClientException;

public class Finder {
    private static final Logger LOGGER = LoggerFactory.getLogger(Finder.class);

    private final File configFile = new File(ConfigDefaults.CONFIG);

    private DefaultCacheManager cacheManager;

    private BuildConfig config;

    private String krbService;

    private String krbPrincipal;

    private String krbPassword;

    private File krbCCache;

    private File krbKeytab;

    private BuildConfig setupBuildConfig() throws IOException {
        BuildConfig defaults = BuildConfig.load(Finder.class.getClassLoader());

        if (configFile.exists()) {
            if (defaults == null) {
                config = BuildConfig.load(configFile);
            } else {
                config = BuildConfig.merge(defaults, configFile);
            }
        } else {
            if (defaults == null) {
                config = new BuildConfig();
            } else {
                config = defaults;
            }
        }

        // XXX: Force output directory since it defaults to "." which usually isn't the best
        Path tmpDir = Files.createTempDirectory("deliverables-analyzer-");

        config.setOutputDirectory(tmpDir.toString());

        LOGGER.info("Output directory set to: {}", config.getOutputDirectory());

        return config;
    }

    @SuppressWarnings("deprecation")
    private void initCaches(BuildConfig config) {
        KojiBuild.KojiBuildExternalizer externalizer = new KojiBuild.KojiBuildExternalizer();
        GlobalConfigurationBuilder globalConfigurationBuilder = new GlobalConfigurationBuilder();
        globalConfigurationBuilder.serialization()
                .marshaller(new GenericJBossMarshaller())
                .addAdvancedExternalizer(externalizer.getId(), externalizer)
                .whiteList()
                .addRegexp(".*")
                .create();
        GlobalConfiguration globalConfiguration = globalConfigurationBuilder.build();
        cacheManager = new DefaultCacheManager(globalConfiguration);

        String location = configFile.getParent();
        Configuration configuration = new ConfigurationBuilder().expiration()
                .lifespan(config.getCacheLifespan())
                .maxIdle(config.getCacheMaxIdle())
                .wakeUpInterval(-1L)
                .persistence()
                .passivation(false)
                .addSingleFileStore()
                .shared(false)
                .preload(true)
                .fetchPersistentState(true)
                .purgeOnStartup(false)
                .location(location)
                .build();
        Set<ChecksumType> checksumTypes = config.getChecksumTypes();

        for (ChecksumType checksumType : checksumTypes) {
            cacheManager.defineConfiguration("files-" + checksumType, configuration);
            cacheManager.defineConfiguration("checksums-" + checksumType, configuration);
            cacheManager.defineConfiguration("checksums-pnc-" + checksumType, configuration);
            cacheManager.defineConfiguration("rpms-" + checksumType, configuration);
        }

        cacheManager.defineConfiguration("builds", configuration);
        cacheManager.defineConfiguration("builds-pnc", configuration);
    }

    public Map<BuildSystemInteger, KojiBuild> find(URL url) {
        LOGGER.info("Find: {}", url);

        Map<BuildSystemInteger, KojiBuild> builds = Collections.emptyMap();
        ExecutorService pool = null;

        try {
            config = setupBuildConfig();
            Path filename = Paths.get(url.getPath()).getFileName();
            File file = Paths.get(config.getOutputDirectory()).resolve(filename).toFile();

            LOGGER.info("Copying {} to {}", url, file);

            FileUtils.copyURLToFile(url, file);

            List<File> inputs = Collections.singletonList(file);

            if (cacheManager == null && !config.getDisableCache()) {
                initCaches(config);
            }

            pool = Executors.newFixedThreadPool(1 + config.getChecksumTypes().size());
            Map<ChecksumType, MultiValuedMap<String, String>> checksums;
            DistributionAnalyzer analyzer = new DistributionAnalyzer(inputs, config, cacheManager);
            Future<Map<ChecksumType, MultiValuedMap<String, String>>> futureChecksum = pool.submit(analyzer);
            boolean isKerberos = krbService != null && krbPrincipal != null && krbPassword != null || krbCCache != null
                    || krbKeytab != null;

            try (KojiClientSession session = isKerberos
                    ? new KojiClientSession(
                            config.getKojiHubURL(),
                            krbService,
                            krbPrincipal,
                            krbPassword,
                            krbCCache,
                            krbKeytab)
                    : new KojiClientSession(config.getKojiHubURL())) {
                BuildFinder finder;

                if (config.getPncURL() != null) {
                    PncClient14 pncclient = new PncClient14(config);
                    finder = new BuildFinder(session, config, analyzer, cacheManager, pncclient);
                } else {
                    finder = new BuildFinder(session, config, analyzer, cacheManager);
                }

                Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);

                try {
                    checksums = futureChecksum.get();
                    builds = futureBuilds.get();

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Got {} checksums and {} builds", checksums.size(), checksums.size());
                    }
                } catch (ExecutionException e) {
                    // throw new KojiClientException("", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (KojiClientException e) {
                // ignore
            }
        } catch (IOException e) {
            // throw new KojiClientException("Error getting url " + url + " to file " + file, e);
        } finally {
            Path outputDirectory = Paths.get(config.getOutputDirectory());

            try {
                LOGGER.info("Cleanup after finding {}", url);

                Files.walk(outputDirectory).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        LOGGER.info("Delete: {}", path);

                        Files.delete(path);
                    } catch (IOException e) {
                        // ignore
                    }
                });
            } catch (IOException e) {
                // ignore
            }

            if (cacheManager != null) {
                try {
                    cacheManager.close();
                } catch (IOException e) {
                    // ignore
                }
            }

            if (pool != null) {
                Utils.shutdownAndAwaitTermination(pool);
            }
        }

        return builds;
    }

    public BuildConfig getBuildConfig() {
        return config;
    }
}
