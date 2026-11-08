package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;

public class Pack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final Pack.ResourcesSupplier resources;
    private final Pack.Metadata metadata;
    private final PackSelectionConfig selectionConfig;
    private final boolean hidden; // Neo: Allow packs to be hidden from the UI entirely
    private final List<Pack> children; // Neo: Allows packs to specify packs which will always be placed beneath them; must be hidden
    private static final PackSource CHILD_SOURCE = PackSource.create(
            name -> Component.translatable(
                    "pack.nameAndSource",
                    name,
                    Component.translatable("pack.neoforge.source.child")
            ).withStyle(net.minecraft.ChatFormatting.GRAY),
            false
    ); // Neo: Pack source for child packs; should not be otherwise used

    @Nullable
    public static Pack readMetaAndCreate(PackLocationInfo location, Pack.ResourcesSupplier resources, PackType packType, PackSelectionConfig selectionConfig) {
        int i = SharedConstants.getCurrentVersion().getPackVersion(packType);
        Pack.Metadata pack$metadata = readPackMetadata(location, resources, i);
        return pack$metadata != null ? new Pack(location, resources, pack$metadata, selectionConfig) : null;
    }

    public Pack(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata, PackSelectionConfig selectionConfig) {
        this(location, resources, metadata, selectionConfig, List.of());
    }

    private Pack(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata, PackSelectionConfig selectionConfig, List<Pack> children) {
        List<Pack> flattenedChildren = new java.util.ArrayList<>();
        List<Pack> remainingChildren = children;
        // recursively flatten children
        while (!remainingChildren.isEmpty()) {
            List<Pack> oldChildren = remainingChildren;
            remainingChildren = new java.util.ArrayList<>();
            for (Pack child : oldChildren) {
                // Adapts the child pack with the following changes:
                // - Must be hidden
                // - Must have no children
                // - Has a pack source of CHILD_SOURCE, which is not automatically added
                Pack adaptedChild = new Pack(
                        new PackLocationInfo(child.location.id(), child.location.title(), CHILD_SOURCE, child.location.knownPackInfo()),
                        child.resources,
                        new Metadata(child.metadata.description, child.metadata.compatibility, child.metadata.requestedFeatures, child.metadata.overlays, true),
                        new PackSelectionConfig(false, child.selectionConfig.defaultPosition(), child.selectionConfig.fixedPosition()),
                        List.of()
                );
                flattenedChildren.add(adaptedChild);
                remainingChildren.addAll(child.getChildren());
            }
        }
        this.children = List.copyOf(flattenedChildren);
        this.hidden = metadata.isHidden();
        this.location = location;
        this.resources = resources;
        this.metadata = metadata;
        this.selectionConfig = selectionConfig;
    }

    @Nullable
    public static Pack.Metadata readPackMetadata(PackLocationInfo location, Pack.ResourcesSupplier resources, int version) {
        try {
            Pack.Metadata pack$metadata;
            try (PackResources packresources = resources.openPrimary(location)) {
                PackMetadataSection packmetadatasection = packresources.getMetadataSection(PackMetadataSection.TYPE);
                if (packmetadatasection == null) {
                    LOGGER.warn("Missing metadata in pack {}", location.id());
                    return null;
                }

                FeatureFlagsMetadataSection featureflagsmetadatasection = packresources.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
                FeatureFlagSet featureflagset = featureflagsmetadatasection != null ? featureflagsmetadatasection.flags() : FeatureFlagSet.of();
                InclusiveRange<Integer> inclusiverange = getDeclaredPackVersions(location.id(), packmetadatasection);
                PackCompatibility packcompatibility = PackCompatibility.forVersion(inclusiverange, version);
                OverlayMetadataSection overlaymetadatasection = packresources.getMetadataSection(OverlayMetadataSection.TYPE);
                List<String> list = overlaymetadatasection != null ? overlaymetadatasection.overlaysForVersion(version) : List.of();
                // Neo: add `neoforge:overlays` overlays as well
                var neoOverlays = packresources.getMetadataSection(OverlayMetadataSection.NEOFORGE_TYPE);
                if (neoOverlays != null) {
                    list = new java.util.ArrayList<>(list);
                    list.addAll(neoOverlays.overlaysForVersion(version));
                    list = List.copyOf(list);
                }
                pack$metadata = new Pack.Metadata(packmetadatasection.description(), packcompatibility, featureflagset, list, packresources.isHidden());
            }

            return pack$metadata;
        } catch (Exception exception) {
            LOGGER.warn("Failed to read pack {} metadata", location.id(), exception);
            return null;
        }
    }

    public static InclusiveRange<Integer> getDeclaredPackVersions(String id, PackMetadataSection metadata) {
        int i = metadata.packFormat();
        if (metadata.supportedFormats().isEmpty()) {
            return new InclusiveRange<>(i);
        } else {
            InclusiveRange<Integer> inclusiverange = metadata.supportedFormats().get();
            if (!inclusiverange.isValueInRange(i)) {
                LOGGER.warn("Pack {} declared support for versions {} but declared main format is {}, defaulting to {}", id, inclusiverange, i, i);
                return new InclusiveRange<>(i);
            } else {
                return inclusiverange;
            }
        }
    }

    public PackLocationInfo location() {
        return this.location;
    }

    public Component getTitle() {
        return this.location.title();
    }

    public Component getDescription() {
        return this.metadata.description();
    }

    /**
     * @param green used to indicate either a successful operation or datapack enabled
     *              status
     */
    public Component getChatLink(boolean green) {
        return this.location.createChatLink(green, this.metadata.description);
    }

    public PackCompatibility getCompatibility() {
        return this.metadata.compatibility();
    }

    public FeatureFlagSet getRequestedFeatures() {
        return this.metadata.requestedFeatures();
    }

    public PackResources open() {
        return this.resources.openFull(this.location, this.metadata);
    }

    public String getId() {
        return this.location.id();
    }

    public PackSelectionConfig selectionConfig() {
        return this.selectionConfig;
    }

    public boolean isRequired() {
        return this.selectionConfig.required();
    }

    public boolean isFixedPosition() {
        return this.selectionConfig.fixedPosition();
    }

    public Pack.Position getDefaultPosition() {
        return this.selectionConfig.defaultPosition();
    }

    public PackSource getPackSource() {
        return this.location.source();
    }

    public boolean isHidden() {
        return hidden;
    }

    public List<Pack> getChildren() {
        return children;
    }

    public java.util.stream.Stream<Pack> streamSelfAndChildren() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(this), children.stream());
    }

    /**
     * {@return a copy of the pack with the provided children in place of any children this pack currently has}
     */
    public Pack withChildren(List<Pack> children) {
        return new Pack(this.location, this.resources, this.metadata, this.selectionConfig, children);
    }

    /**
     * {@return a copy of the pack that is hidden}
     */
    public Pack hidden() {
        return new Pack(
                new PackLocationInfo(this.location.id(), this.location.title(), this.location.source(), this.location.knownPackInfo()),
                this.resources,
                new Metadata(this.metadata.description, this.metadata.compatibility, this.metadata.requestedFeatures, this.metadata.overlays, true),
                new PackSelectionConfig(this.selectionConfig.required(), this.selectionConfig.defaultPosition(), this.selectionConfig.fixedPosition()),
                this.children
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof Pack pack) ? false : this.location.equals(pack.location);
        }
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }

    public static record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays, boolean isHidden) {
        /** @deprecated Neo: use {@link #Metadata(Component,PackCompatibility,FeatureFlagSet,List,boolean)} instead */
        @Deprecated
        public Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {
            this(description, compatibility, requestedFeatures, overlays, false);
        }
    }

    public static enum Position {
        TOP,
        BOTTOM;

        public <T> int insert(List<T> list, T element, Function<T, PackSelectionConfig> packFactory, boolean flipPosition) {
            Pack.Position pack$position = flipPosition ? this.opposite() : this;
            if (pack$position == BOTTOM) {
                int j;
                for (j = 0; j < list.size(); j++) {
                    PackSelectionConfig packselectionconfig1 = packFactory.apply(list.get(j));
                    if (!packselectionconfig1.fixedPosition() || packselectionconfig1.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(j, element);
                return j;
            } else {
                int i;
                for (i = list.size() - 1; i >= 0; i--) {
                    PackSelectionConfig packselectionconfig = packFactory.apply(list.get(i));
                    if (!packselectionconfig.fixedPosition() || packselectionconfig.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(i + 1, element);
                return i + 1;
            }
        }

        public Pack.Position opposite() {
            return this == TOP ? BOTTOM : TOP;
        }
    }

    public interface ResourcesSupplier {
        PackResources openPrimary(PackLocationInfo location);

        PackResources openFull(PackLocationInfo location, Pack.Metadata metadata);
    }
}
