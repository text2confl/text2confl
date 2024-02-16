package io.github.text2confl.client.quarkus;

import com.github.zeldigas.confclient.ConfluenceAuth;
import com.github.zeldigas.confclient.ConfluenceClient;
import com.github.zeldigas.confclient.ConfluenceClientConfig;
import com.github.zeldigas.confclient.TokenAuth;
import com.github.zeldigas.text2confl.convert.Converter;
import com.github.zeldigas.text2confl.convert.EditorVersion;
import com.github.zeldigas.text2confl.convert.Page;
import com.github.zeldigas.text2confl.core.ContentValidationFailedException;
import com.github.zeldigas.text2confl.core.ContentValidator;
import com.github.zeldigas.text2confl.core.ServiceProvider;
import com.github.zeldigas.text2confl.core.ServiceProviderImpl;
import com.github.zeldigas.text2confl.core.config.Cleanup;
import com.github.zeldigas.text2confl.core.config.ConverterConfig;
import com.github.zeldigas.text2confl.core.config.DirectoryConfig;
import com.github.zeldigas.text2confl.core.config.UploadConfig;
import com.github.zeldigas.text2confl.core.upload.ChangeDetector;
import com.github.zeldigas.text2confl.core.upload.ContentUploader;
import com.github.zeldigas.text2confl.core.upload.LoggingUploadOperationsTracker;
import io.ktor.client.plugins.logging.LogLevel;
import io.ktor.http.URLUtilsKt;
import io.quarkus.bootstrap.forkjoin.QuarkusForkJoinWorkerThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static com.github.zeldigas.text2confl.core.config.ConverterConfigKt.createConversionConfig;
import static com.github.zeldigas.text2confl.core.config.IOKt.readDirectoryConfig;

@Dependent
public class Text2ConflQuarkus {

    @Inject
    Text2ConflConfig config;

    @Inject
    Logger log;

    public Text2ConflConfig config() {
        return config;
    }

    private final ServiceProvider provider = new ServiceProviderImpl();

    @PostConstruct
    void displayConfig() {
        log.info("###############################");
        log.info("Text2ConflConfiguration : ");
        log.info("parentPageId=" + config.confluence().parentPageId());
        log.info("spaceKey=" + config.confluence().spaceKey());
        log.info("dryRun=" + config.confluence().dryRun());
        log.info("token=" + config.confluence().token());
        log.info("url=" + config.confluence().url());
        log.info("requestTimeout=" + config.confluence().requestTimeout());
        log.info("connectTimeout=" + config.confluence().connectTimeout());
        log.info("socketTimeout=" + config.confluence().socketTimeout());
        log.info("directory=" + config.directory());
        log.info("saveConverted=" + config.saveConverted());
        log.info("Validate Pages=" + config.validate());
        log.info("AutoFix Pages=" + config.autofix());
        log.info("###############################");

        if (config.directory().toFile().exists()) {
            try {
                emptyDirectory(config.directory());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    Path local() {
        return Paths.get(".");
    }

    Converter converter() {
        return provider.createConverter(config.confluence().spaceKey(), converterConfig(local()));
    }

    private ContentValidator validator() {
        return provider.createContentValidator();
    }

    LoggingUploadOperationsTracker tracker() {
        io.ktor.http.Url server = URLUtilsKt.Url(config.confluence().url());
        return new LoggingUploadOperationsTracker(server);
    }

    ConverterConfig converterConfig(Path confFolder) {
        DirectoryConfig directoryConfig = readDirectoryConfig(confFolder);
        ConverterConfig converterConfig = createConversionConfig(directoryConfig, EditorVersion.V1, null);
        log.info("Kroki Url : " + converterConfig.getMarkdownConfig().getDiagrams().getKroki().getServer());
        return converterConfig;
    }

    ConfluenceClient confluenceClient() {
        return provider.createConfluenceClient(getConfluenceClientConfig(), config.confluence().dryRun());
    }

    ContentUploader contentUploader() {
        return provider.createUploader(
                confluenceClient(),
                uploadConfig(),
                converterConfig(local()),
                tracker());
    }

    public Unit uploadPages(List<Page> pages) throws InterruptedException {
        return BuildersKt.runBlocking(
                EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> contentUploader().uploadPages(pages, config.confluence().spaceKey(),
                        config.confluence().parentPageId(), continuation));
    }

    public void uploadPageBlocking(List<Page> pages) {
        contentUploader().runBlocking(pages, config.confluence().spaceKey(), config.confluence().parentPageId());
    }

    public List<Page> convert(Path path) throws InterruptedException {
        List<Page> pages = convertDir(path);

        if (config.saveConverted()) {
            parallelSave(pages);
        }

        if (config.autofix()) {
            validator().fixHtml(pages);
        }

        if (config.validate()) {
            try {
                validator().validate(pages);
            } catch (ContentValidationFailedException e) {
                log.error(e.getErrors());
            }
        }
        return pages;
    }

    private List<Page> convertDir(Path path) {
        return converter().convertDir(path);
    }

    @NotNull
    private UploadConfig uploadConfig() {
        UploadConfig uploadConfig = new UploadConfig(config.confluence().spaceKey(), Cleanup.Managed, "", true,
                ChangeDetector.CONTENT, "default-tenant");
        return uploadConfig;
    }

    @NotNull
    private ConfluenceClientConfig getConfluenceClientConfig() {

        ConfluenceAuth confluenceAuth = new TokenAuth(config.confluence().token());
        Boolean skipSsl = Boolean.TRUE;
        io.ktor.http.Url server = URLUtilsKt.Url(config.confluence().url());
        LogLevel clientLogLevel = LogLevel.NONE;
        ConfluenceClientConfig confluenceClientConfig = new ConfluenceClientConfig(
                server,
                skipSsl,
                confluenceAuth,
                clientLogLevel,
                config.confluence().requestTimeout(),
                config.confluence().connectTimeout(),
                config.confluence().socketTimeout());
        return confluenceClientConfig;
    }

    private void save(Page page) {
        try {
            Path converted = config.directory().resolve(page.getTitle() + ".html");
            createParent(converted);
            Files.writeString(converted, page.getContent().getBody());
            parallelSave(page.getChildren());
        } catch (IOException e) {
            log.error(e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public static void createParent(Path targetPath) throws IOException {
        if (Files.notExists(targetPath.getParent())) {
            Files.createDirectories(targetPath.getParent());
        }
    }

    public void parallelSave(List<Page> pages) throws InterruptedException {
        ForkJoinPool forkJoinPool = createPool();
        forkJoinPool.submit(() -> pages.parallelStream().forEach(this::save));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    //TODO move me to dedicated lib
    @NotNull
    private static ForkJoinPool createPool() {
        int processors = Runtime.getRuntime().availableProcessors() * 2;
        ForkJoinPool forkJoinPool = new ForkJoinPool(processors, new QuarkusForkJoinWorkerThreadFactory(), null, false);
        return forkJoinPool;
    }

    //TODO move me to dedicated lib
    private void emptyDirectory(Path pathToBeDeleted) throws IOException {
        Files.walkFileTree(pathToBeDeleted,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
