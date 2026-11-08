package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractContainerScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
    /**
     * The location of the inventory background texture
     */
    public static final ResourceLocation INVENTORY_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final float SNAPBACK_SPEED = 100.0F;
    private static final int QUICKDROP_DELAY = 500;
    public static final int SLOT_ITEM_BLIT_OFFSET = 100;
    private static final int HOVER_ITEM_BLIT_OFFSET = 200;
    /**
     * The X size of the inventory window in pixels.
     */
    protected int imageWidth = 176;
    /**
     * The Y size of the inventory window in pixels.
     */
    protected int imageHeight = 166;
    protected int titleLabelX;
    protected int titleLabelY;
    protected int inventoryLabelX;
    protected int inventoryLabelY;
    /**
     * A list of the players inventory slots
     */
    protected final T menu;
    protected final Component playerInventoryTitle;
    /**
     * Holds the slot currently hovered
     */
    @Nullable
    protected Slot hoveredSlot;
    /**
     * Used when touchscreen is enabled
     */
    @Nullable
    private Slot clickedSlot;
    @Nullable
    private Slot snapbackEnd;
    @Nullable
    private Slot quickdropSlot;
    @Nullable
    private Slot lastClickSlot;
    /**
     * Starting X position for the Gui. Inconsistent use for Gui backgrounds.
     */
    protected int leftPos;
    /**
     * Starting Y position for the Gui. Inconsistent use for Gui backgrounds.
     */
    protected int topPos;
    /**
     * Used when touchscreen is enabled.
     */
    private boolean isSplittingStack;
    /**
     * Used when touchscreen is enabled
     */
    private ItemStack draggingItem = ItemStack.EMPTY;
    private int snapbackStartX;
    private int snapbackStartY;
    private long snapbackTime;
    /**
     * Used when touchscreen is enabled
     */
    private ItemStack snapbackItem = ItemStack.EMPTY;
    private long quickdropTime;
    protected final Set<Slot> quickCraftSlots = Sets.newHashSet();
    protected boolean isQuickCrafting;
    private int quickCraftingType;
    private int quickCraftingButton;
    private boolean skipNextRelease;
    private int quickCraftingRemainder;
    private long lastClickTime;
    private int lastClickButton;
    private boolean doubleclick;
    private ItemStack lastQuickMoved = ItemStack.EMPTY;

    public AbstractContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(title);
        this.menu = menu;
        this.playerInventoryTitle = playerInventory.getDisplayName();
        this.skipNextRelease = true;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int i = this.leftPos;
        int j = this.topPos;
        // Neo: replicate the super method's implementation to insert the event between background and widgets
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ContainerScreenEvent.Render.Background(this, guiGraphics, mouseX, mouseY));
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        RenderSystem.disableDepthTest();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float)i, (float)j, 0.0F);
        this.hoveredSlot = null;

        for (int k = 0; k < this.menu.slots.size(); k++) {
            Slot slot = this.menu.slots.get(k);
            if (slot.isActive()) {
                this.renderSlot(guiGraphics, slot);
            }

            if (this.isHovering(slot, (double)mouseX, (double)mouseY) && slot.isActive()) {
                this.hoveredSlot = slot;
                this.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
            }
        }

        this.renderLabels(guiGraphics, mouseX, mouseY);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ContainerScreenEvent.Render.Foreground(this, guiGraphics, mouseX, mouseY));
        ItemStack itemstack = this.draggingItem.isEmpty() ? this.menu.getCarried() : this.draggingItem;
        if (!itemstack.isEmpty()) {
            int l1 = 8;
            int i2 = this.draggingItem.isEmpty() ? 8 : 16;
            String s = null;
            if (!this.draggingItem.isEmpty() && this.isSplittingStack) {
                itemstack = itemstack.copyWithCount(Mth.ceil((float)itemstack.getCount() / 2.0F));
            } else if (this.isQuickCrafting && this.quickCraftSlots.size() > 1) {
                itemstack = itemstack.copyWithCount(this.quickCraftingRemainder);
                if (itemstack.isEmpty()) {
                    s = ChatFormatting.YELLOW + "0";
                }
            }

            this.renderFloatingItem(guiGraphics, itemstack, mouseX - i - 8, mouseY - j - i2, s);
        }

        if (!this.snapbackItem.isEmpty()) {
            float f = (float)(Util.getMillis() - this.snapbackTime) / 100.0F;
            if (f >= 1.0F) {
                f = 1.0F;
                this.snapbackItem = ItemStack.EMPTY;
            }

            int j2 = this.snapbackEnd.x - this.snapbackStartX;
            int k2 = this.snapbackEnd.y - this.snapbackStartY;
            int j1 = this.snapbackStartX + (int)((float)j2 * f);
            int k1 = this.snapbackStartY + (int)((float)k2 * f);
            this.renderFloatingItem(guiGraphics, this.snapbackItem, j1, k1, null);
        }

        guiGraphics.pose().popPose();
        RenderSystem.enableDepthTest();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
    }

    public static void renderSlotHighlight(GuiGraphics guiGraphics, int x, int y, int blitOffset) {
        renderSlotHighlight(guiGraphics, x, y, blitOffset, -2130706433);
    }
    public static void renderSlotHighlight(GuiGraphics guiGraphics, int x, int y, int blitOffset, int color) {
        guiGraphics.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, color, color, blitOffset);
    }

    /**
     * Renders a highlight for the given slot to indicate the mouse is currently hovering over it.
     */
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (slot.isHighlightable()) {
            renderSlotHighlight(guiGraphics, slot.x, slot.y, 0, getSlotColor(slot.index));
        }
    }

    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack itemstack = this.hoveredSlot.getItem();
            guiGraphics.renderTooltip(this.font, this.getTooltipFromContainerItem(itemstack), itemstack.getTooltipImage(), itemstack, x, y);
        }
    }

    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        return getTooltipFromItem(this.minecraft, stack);
    }

    private void renderFloatingItem(GuiGraphics guiGraphics, ItemStack stack, int x, int y, String text) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 232.0F);
        guiGraphics.renderItem(stack, x, y);
        var font = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack).getFont(stack, net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext.ITEM_COUNT);
        guiGraphics.renderItemDecorations(font == null ? this.font : font, stack, x, y - (this.draggingItem.isEmpty() ? 0 : 8), text);
        guiGraphics.pose().popPose();
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    protected abstract void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY);

    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        int i = slot.x;
        int j = slot.y;
        ItemStack itemstack = slot.getItem();
        boolean flag = false;
        boolean flag1 = slot == this.clickedSlot && !this.draggingItem.isEmpty() && !this.isSplittingStack;
        ItemStack itemstack1 = this.menu.getCarried();
        String s = null;
        if (slot == this.clickedSlot && !this.draggingItem.isEmpty() && this.isSplittingStack && !itemstack.isEmpty()) {
            itemstack = itemstack.copyWithCount(itemstack.getCount() / 2);
        } else if (this.isQuickCrafting && this.quickCraftSlots.contains(slot) && !itemstack1.isEmpty()) {
            if (this.quickCraftSlots.size() == 1) {
                return;
            }

            if (AbstractContainerMenu.canItemQuickReplace(slot, itemstack1, true) && this.menu.canDragTo(slot)) {
                flag = true;
                int k = Math.min(itemstack1.getMaxStackSize(), slot.getMaxStackSize(itemstack1));
                int l = slot.getItem().isEmpty() ? 0 : slot.getItem().getCount();
                int i1 = AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots, this.quickCraftingType, itemstack1) + l;
                if (i1 > k) {
                    i1 = k;
                    s = ChatFormatting.YELLOW.toString() + k;
                }

                itemstack = itemstack1.copyWithCount(i1);
            } else {
                this.quickCraftSlots.remove(slot);
                this.recalculateQuickCraftRemaining();
            }
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        if (itemstack.isEmpty() && slot.isActive()) {
            Pair<ResourceLocation, ResourceLocation> pair = slot.getNoItemIcon();
            if (pair != null) {
                TextureAtlasSprite textureatlassprite = this.minecraft.getTextureAtlas(pair.getFirst()).apply(pair.getSecond());
                guiGraphics.blit(i, j, 0, 16, 16, textureatlassprite);
                flag1 = true;
            }
        }

        if (!flag1) {
            if (flag) {
                guiGraphics.fill(i, j, i + 16, j + 16, -2130706433);
            }

            renderSlotContents(guiGraphics, itemstack, slot, s);
        }

        guiGraphics.pose().popPose();
    }

    protected void renderSlotContents(GuiGraphics guiGraphics, ItemStack itemstack, Slot slot, @Nullable String countString) {
            GuiGraphics p_281607_ = guiGraphics; Slot p_282613_ = slot; String s = countString; int i = slot.x; int j = slot.y;
            int j1 = p_282613_.x + p_282613_.y * this.imageWidth;
            if (p_282613_.isFake()) {
                p_281607_.renderFakeItem(itemstack, i, j, j1);
            } else {
                p_281607_.renderItem(itemstack, i, j, j1);
            }

            p_281607_.renderItemDecorations(this.font, itemstack, i, j, s);
    }

    private void recalculateQuickCraftRemaining() {
        ItemStack itemstack = this.menu.getCarried();
        if (!itemstack.isEmpty() && this.isQuickCrafting) {
            if (this.quickCraftingType == 2) {
                this.quickCraftingRemainder = itemstack.getMaxStackSize();
            } else {
                this.quickCraftingRemainder = itemstack.getCount();

                for (Slot slot : this.quickCraftSlots) {
                    ItemStack itemstack1 = slot.getItem();
                    int i = itemstack1.isEmpty() ? 0 : itemstack1.getCount();
                    int j = Math.min(itemstack.getMaxStackSize(), slot.getMaxStackSize(itemstack));
                    int k = Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots, this.quickCraftingType, itemstack) + i, j);
                    this.quickCraftingRemainder -= k - i;
                }
            }
        }
    }

    @Nullable
    private Slot findSlot(double mouseX, double mouseY) {
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (this.isHovering(slot, mouseX, mouseY) && slot.isActive()) {
                return slot;
            }
        }

        return null;
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was clicked.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        } else {
            InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
            boolean flag = this.minecraft.options.keyPickItem.isActiveAndMatches(mouseKey) && this.minecraft.player.hasInfiniteMaterials();
            Slot slot = this.findSlot(mouseX, mouseY);
            long i = Util.getMillis();
            this.doubleclick = this.lastClickSlot == slot && i - this.lastClickTime < 250L && this.lastClickButton == button;
            this.skipNextRelease = false;
            if (button != 0 && button != 1 && !flag) {
                this.checkHotbarMouseClicked(button);
            } else {
                int j = this.leftPos;
                int k = this.topPos;
                boolean flag1 = this.hasClickedOutside(mouseX, mouseY, j, k, button);
                if (slot != null) flag1 = false; // Forge, prevent dropping of items through slots outside of GUI boundaries
                int l = -1;
                if (slot != null) {
                    l = slot.index;
                }

                if (flag1) {
                    l = -999;
                }

                if (this.minecraft.options.touchscreen().get() && flag1 && this.menu.getCarried().isEmpty()) {
                    this.onClose();
                    return true;
                }

                if (l != -1) {
                    if (this.minecraft.options.touchscreen().get()) {
                        if (slot != null && slot.hasItem()) {
                            this.clickedSlot = slot;
                            this.draggingItem = ItemStack.EMPTY;
                            this.isSplittingStack = button == 1;
                        } else {
                            this.clickedSlot = null;
                        }
                    } else if (!this.isQuickCrafting) {
                        if (this.menu.getCarried().isEmpty()) {
                            if (this.minecraft.options.keyPickItem.isActiveAndMatches(mouseKey)) {
                                this.slotClicked(slot, l, button, ClickType.CLONE);
                            } else {
                                boolean flag2 = l != -999
                                    && (
                                        InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 340)
                                            || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 344)
                                    );
                                ClickType clicktype = ClickType.PICKUP;
                                if (flag2) {
                                    this.lastQuickMoved = slot != null && slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY;
                                    clicktype = ClickType.QUICK_MOVE;
                                } else if (l == -999) {
                                    clicktype = ClickType.THROW;
                                }

                                this.slotClicked(slot, l, button, clicktype);
                            }

                            this.skipNextRelease = true;
                        } else {
                            this.isQuickCrafting = true;
                            this.quickCraftingButton = button;
                            this.quickCraftSlots.clear();
                            if (button == 0) {
                                this.quickCraftingType = 0;
                            } else if (button == 1) {
                                this.quickCraftingType = 1;
                            } else if (this.minecraft.options.keyPickItem.isActiveAndMatches(mouseKey)) {
                                this.quickCraftingType = 2;
                            }
                        }
                    }
                }
            }

            this.lastClickSlot = slot;
            this.lastClickTime = i;
            this.lastClickButton = button;
            return true;
        }
    }

    private void checkHotbarMouseClicked(int keyCode) {
        if (this.hoveredSlot != null && this.menu.getCarried().isEmpty()) {
            if (this.minecraft.options.keySwapOffhand.matchesMouse(keyCode)) {
                this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 40, ClickType.SWAP);
                return;
            }

            for (int i = 0; i < 9; i++) {
                if (this.minecraft.options.keyHotbarSlots[i].matchesMouse(keyCode)) {
                    this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, i, ClickType.SWAP);
                }
            }
        }
    }

    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return mouseX < (double)guiLeft
            || mouseY < (double)guiTop
            || mouseX >= (double)(guiLeft + this.imageWidth)
            || mouseY >= (double)(guiTop + this.imageHeight);
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that is being dragged.
     * @param dragX  the X distance of the drag.
     * @param dragY  the Y distance of the drag.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        Slot slot = this.findSlot(mouseX, mouseY);
        ItemStack itemstack = this.menu.getCarried();
        if (this.clickedSlot != null && this.minecraft.options.touchscreen().get()) {
            if (button == 0 || button == 1) {
                if (this.draggingItem.isEmpty()) {
                    if (slot != this.clickedSlot && !this.clickedSlot.getItem().isEmpty()) {
                        this.draggingItem = this.clickedSlot.getItem().copy();
                    }
                } else if (this.draggingItem.getCount() > 1 && slot != null && AbstractContainerMenu.canItemQuickReplace(slot, this.draggingItem, false)) {
                    long i = Util.getMillis();
                    if (this.quickdropSlot == slot) {
                        if (i - this.quickdropTime > 500L) {
                            this.slotClicked(this.clickedSlot, this.clickedSlot.index, 0, ClickType.PICKUP);
                            this.slotClicked(slot, slot.index, 1, ClickType.PICKUP);
                            this.slotClicked(this.clickedSlot, this.clickedSlot.index, 0, ClickType.PICKUP);
                            this.quickdropTime = i + 750L;
                            this.draggingItem.shrink(1);
                        }
                    } else {
                        this.quickdropSlot = slot;
                        this.quickdropTime = i;
                    }
                }
            }
        } else if (this.isQuickCrafting
            && slot != null
            && !itemstack.isEmpty()
            && (itemstack.getCount() > this.quickCraftSlots.size() || this.quickCraftingType == 2)
            && AbstractContainerMenu.canItemQuickReplace(slot, itemstack, true)
            && slot.mayPlace(itemstack)
            && this.menu.canDragTo(slot)) {
            this.quickCraftSlots.add(slot);
            this.recalculateQuickCraftRemaining();
        }

        return true;
    }

    /**
     * Called when a mouse button is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was released.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button); //Forge, Call parent to release buttons
        Slot slot = this.findSlot(mouseX, mouseY);
        int i = this.leftPos;
        int j = this.topPos;
        boolean flag = this.hasClickedOutside(mouseX, mouseY, i, j, button);
        if (slot != null) flag = false; // Forge, prevent dropping of items through slots outside of GUI boundaries
        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
        int k = -1;
        if (slot != null) {
            k = slot.index;
        }

        if (flag) {
            k = -999;
        }

        if (this.doubleclick && slot != null && button == 0 && this.menu.canTakeItemForPickAll(ItemStack.EMPTY, slot)) {
            if (hasShiftDown()) {
                if (!this.lastQuickMoved.isEmpty()) {
                    for (Slot slot2 : this.menu.slots) {
                        if (slot2 != null
                            && slot2.mayPickup(this.minecraft.player)
                            && slot2.hasItem()
                            && slot2.isSameInventory(slot)
                            && AbstractContainerMenu.canItemQuickReplace(slot2, this.lastQuickMoved, true)) {
                            this.slotClicked(slot2, slot2.index, button, ClickType.QUICK_MOVE);
                        }
                    }
                }
            } else {
                this.slotClicked(slot, k, button, ClickType.PICKUP_ALL);
            }

            this.doubleclick = false;
            this.lastClickTime = 0L;
        } else {
            if (this.isQuickCrafting && this.quickCraftingButton != button) {
                this.isQuickCrafting = false;
                this.quickCraftSlots.clear();
                this.skipNextRelease = true;
                return true;
            }

            if (this.skipNextRelease) {
                this.skipNextRelease = false;
                return true;
            }

            if (this.clickedSlot != null && this.minecraft.options.touchscreen().get()) {
                if (button == 0 || button == 1) {
                    if (this.draggingItem.isEmpty() && slot != this.clickedSlot) {
                        this.draggingItem = this.clickedSlot.getItem();
                    }

                    boolean flag2 = AbstractContainerMenu.canItemQuickReplace(slot, this.draggingItem, false);
                    if (k != -1 && !this.draggingItem.isEmpty() && flag2) {
                        this.slotClicked(this.clickedSlot, this.clickedSlot.index, button, ClickType.PICKUP);
                        this.slotClicked(slot, k, 0, ClickType.PICKUP);
                        if (this.menu.getCarried().isEmpty()) {
                            this.snapbackItem = ItemStack.EMPTY;
                        } else {
                            this.slotClicked(this.clickedSlot, this.clickedSlot.index, button, ClickType.PICKUP);
                            this.snapbackStartX = Mth.floor(mouseX - (double)i);
                            this.snapbackStartY = Mth.floor(mouseY - (double)j);
                            this.snapbackEnd = this.clickedSlot;
                            this.snapbackItem = this.draggingItem;
                            this.snapbackTime = Util.getMillis();
                        }
                    } else if (!this.draggingItem.isEmpty()) {
                        this.snapbackStartX = Mth.floor(mouseX - (double)i);
                        this.snapbackStartY = Mth.floor(mouseY - (double)j);
                        this.snapbackEnd = this.clickedSlot;
                        this.snapbackItem = this.draggingItem;
                        this.snapbackTime = Util.getMillis();
                    }

                    this.clearDraggingState();
                }
            } else if (this.isQuickCrafting && !this.quickCraftSlots.isEmpty()) {
                this.slotClicked(null, -999, AbstractContainerMenu.getQuickcraftMask(0, this.quickCraftingType), ClickType.QUICK_CRAFT);

                for (Slot slot1 : this.quickCraftSlots) {
                    this.slotClicked(slot1, slot1.index, AbstractContainerMenu.getQuickcraftMask(1, this.quickCraftingType), ClickType.QUICK_CRAFT);
                }

                this.slotClicked(null, -999, AbstractContainerMenu.getQuickcraftMask(2, this.quickCraftingType), ClickType.QUICK_CRAFT);
            } else if (!this.menu.getCarried().isEmpty()) {
                if (this.minecraft.options.keyPickItem.isActiveAndMatches(mouseKey)) {
                    this.slotClicked(slot, k, button, ClickType.CLONE);
                } else {
                    boolean flag1 = k != -999
                        && (
                            InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 340)
                                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 344)
                        );
                    if (flag1) {
                        this.lastQuickMoved = slot != null && slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY;
                    }

                    this.slotClicked(slot, k, button, flag1 ? ClickType.QUICK_MOVE : ClickType.PICKUP);
                }
            }
        }

        if (this.menu.getCarried().isEmpty()) {
            this.lastClickTime = 0L;
        }

        this.isQuickCrafting = false;
        return true;
    }

    public void clearDraggingState() {
        this.draggingItem = ItemStack.EMPTY;
        this.clickedSlot = null;
    }

    private boolean isHovering(Slot slot, double mouseX, double mouseY) {
        return this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
    }

    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        int i = this.leftPos;
        int j = this.topPos;
        mouseX -= (double)i;
        mouseY -= (double)j;
        return mouseX >= (double)(x - 1)
            && mouseX < (double)(x + width + 1)
            && mouseY >= (double)(y - 1)
            && mouseY < (double)(y + height + 1);
    }

    /**
     * Called when the mouse is clicked over a slot or outside the gui.
     */
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot != null) {
            slotId = slot.index;
        }

        this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, slotId, mouseButton, type, this.minecraft.player);
    }

    protected void handleSlotStateChanged(int slotId, int containerId, boolean newState) {
        this.minecraft.gameMode.handleSlotStateChanged(slotId, containerId, newState);
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key mouseKey = InputConstants.getKey(keyCode, scanCode);
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.minecraft.options.keyInventory.isActiveAndMatches(mouseKey)) {
            this.onClose();
            return true;
        } else {
            boolean handled = this.checkHotbarKeyPressed(keyCode, scanCode);// Forge MC-146650: Needs to return true when the key is handled
            if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
                if (this.minecraft.options.keyPickItem.isActiveAndMatches(mouseKey)) {
                    this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ClickType.CLONE);
                    handled = true;
                } else if (this.minecraft.options.keyDrop.isActiveAndMatches(mouseKey)) {
                    this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, hasControlDown() ? 1 : 0, ClickType.THROW);
                    handled = true;
                }
            } else if (this.minecraft.options.keyDrop.isActiveAndMatches(mouseKey)) {
                 handled = true; // Forge MC-146650: Emulate MC bug, so we don't drop from hotbar when pressing drop without hovering over a item.
            }

            return handled;
        }
    }

    protected boolean checkHotbarKeyPressed(int keyCode, int scanCode) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null) {
            if (this.minecraft.options.keySwapOffhand.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
                this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 40, ClickType.SWAP);
                return true;
            }

            for (int i = 0; i < 9; i++) {
                if (this.minecraft.options.keyHotbarSlots[i].isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
                    this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, i, ClickType.SWAP);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void removed() {
        if (this.minecraft.player != null) {
            this.menu.removed(this.minecraft.player);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public final void tick() {
        super.tick();
        if (this.minecraft.player.isAlive() && !this.minecraft.player.isRemoved()) {
            this.containerTick();
        } else {
            this.minecraft.player.closeContainer();
        }
    }

    protected void containerTick() {
    }

    @Override
    public T getMenu() {
        return this.menu;
    }

    @org.jetbrains.annotations.Nullable
    public Slot getSlotUnderMouse() { return this.hoveredSlot; }
    public int getGuiLeft() { return leftPos; }
    public int getGuiTop() { return topPos; }
    public int getXSize() { return imageWidth; }
    public int getYSize() { return imageHeight; }

    protected int slotColor = -2130706433;
    public int getSlotColor(int index) {
        return slotColor;
    }

    @Override
    public void onClose() {
        this.minecraft.player.closeContainer();
        super.onClose();
    }
}
