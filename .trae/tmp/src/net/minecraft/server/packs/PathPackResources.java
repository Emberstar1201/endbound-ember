package net.minecraft.server.packs;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult.Error;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;

public class PathPackResources extends AbstractPackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Joiner PATH_JOINER = Joiner.on("/");
    private final Path root;

    public PathPackResources(PackLocationInfo location, Path root) {
        super(location);
        this.root = root;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        FileUtil.validatePath(elements);
        Path path = FileUtil.resolvePath(this.root, List.of(elements));
        return Files.exists(path) ? IoSupplier.create(path) : null;
    }

    public static boolean validatePath(Path path) {
        return true;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        Path path = this.root.resolve(packType.getDirectory()).resolve(location.getNamespace());
        return getResource(location, path);
    }

    @Nullable
    public static IoSupplier<InputStream> getResource(ResourceLocation location, Path p_path) {
        return FileUtil.decomposePath(location.getPath()).mapOrElse(p_251647_ -> {
            Path path = FileUtil.resolvePath(p_path, (List<String>)p_251647_);
            return returnFileIfExists(path);
        }, p_337558_ -> {
            LOGGER.error("Invalid path {}: {}", location, p_337558_.message());
            return null;
        });
    }

    @Nullable
    private static IoSupplier<InputStream> returnFileIfExists(Path path) {
        return Files.exists(path) && validatePath(path) ? IoSupplier.create(path) : null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String p_path, PackResources.ResourceOutput resourceOutput) {
        FileUtil.decomposePath(p_path).ifSuccess(p_250225_ -> {
            Path path = this.root.resolve(packType.getDirectory()).resolve(namespace);
            listPath(namespace, path, (List<String>)p_250225_, resourceOutput);
        }).ifError(p_337560_ -> LOGGER.error("Invalid path {}: {}", p_path, p_337560_.message()));
    }

    public static void listPath(String namespace, Path namespacePath, List<String> decomposedPath, PackResources.ResourceOutput resourceOutput) {
        Path path = FileUtil.resolvePath(namespacePath, decomposedPath);

        try (Stream<Path> stream = Files.find(path, Integer.MAX_VALUE, (p_250060_, p_250796_) -> p_250796_.isRegularFile())) {
            stream.forEach(p_249092_ -> {
                String s = PATH_JOINER.join(namespacePath.relativize(p_249092_));
                ResourceLocation resourcelocation = ResourceLocation.tryBuild(namespace, s);
                if (resourcelocation == null) {
                    Util.logAndPauseIfInIde(String.format(Locale.ROOT, "Invalid path in pack: %s:%s, ignoring", namespace, s));
                } else {
                    resourceOutput.accept(resourcelocation, IoSupplier.create(p_249092_));
                }
            });
        } catch (NotDirectoryException | NoSuchFileException nosuchfileexception) {
        } catch (IOException ioexception) {
            LOGGER.error("Failed to list path {}", path, ioexception);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = Sets.newHashSet();
        Path path = this.root.resolve(type.getDirectory());

        try (DirectoryStream<Path> directorystream = Files.newDirectoryStream(path)) {
            for (Path path1 : directorystream) {
                String s = path1.getFileName().toString();
                if (ResourceLocation.isValidNamespace(s)) {
                    set.add(s);
                } else {
                    LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", s, this.root);
                }
            }
        } catch (NotDirectoryException | NoSuchFileException nosuchfileexception) {
        } catch (IOException ioexception) {
            LOGGER.error("Failed to list path {}", path, ioexception);
        }

        return set;
    }

    @Override
    public void close() {
    }

    public static class PathResourcesSupplier implements Pack.ResourcesSupplier {
        private final Path content;

        public PathResourcesSupplier(Path content) {
            this.content = content;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new PathPackResources(location, this.content);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            PackResources packresources = this.openPrimary(location);
            List<String> list = metadata.overlays();
            if (list.isEmpty()) {
                return packresources;
            } else {
                List<PackResources> list1 = new ArrayList<>(list.size());

                for (String s : list) {
                    Path path = this.content.resolve(s);
                    list1.add(new PathPackResources(location, path));
                }

                return new CompositePackResources(packresources, list1);
            }
        }
    }
}
