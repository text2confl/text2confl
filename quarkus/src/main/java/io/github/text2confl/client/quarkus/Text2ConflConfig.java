package io.github.text2confl.client.quarkus;

import io.smallrye.config.ConfigMapping;

import java.nio.file.Path;

@ConfigMapping(prefix = "text2confl")
public interface Text2ConflConfig {

    Confluence confluence();

    Path directory();

    Boolean saveConverted();

    Boolean validate();

    boolean autofix();

    interface Confluence {

        String parentPageId();

        String spaceKey();

        Boolean dryRun();

        String token();

        String url();

        Long requestTimeout();

        Long socketTimeout();

        Long connectTimeout();
    }

}
