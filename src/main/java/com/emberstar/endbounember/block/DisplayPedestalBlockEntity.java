package com.emberstar.endbounember.block;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 展示台方块实体：保存展示中的物品栈，供渲染器读取并做悬浮动画。
 * <p>
 * 同时记录星尘光环仪式的进度，客户端据此播放 15 秒的凝结动画。
 */
public class DisplayPedestalBlockEntity extends BlockEntity {
    private static final String TAG_ITEM = "DisplayItem";
    private static final String TAG_RITUAL_START = "RitualStart";
    private static final String TAG_RITUAL_ACTIVE = "RitualActive";
    private static final String TAG_RITUAL_SWAPPED = "RitualSwapped";
    private static final String TAG_RITUAL_TYPE = "RitualType";
    private static final String TAG_ALTAR_PEDESTALS = "AltarPedestals";

    /** 仪式种类：决定动画配色、时长与产物。 */
    public enum RitualType {
        /** 星尘光环：末地石底座 + 虚无星尘。 */
        HALO(EndboundEmber.HALO_RITUAL_DURATION, EndboundEmber.HALO_RITUAL_SWAP_TICK),
        /** 晨曦护符：任意光源底座 + 拂晓附魔书 / 烈焰粉 / 星尘。 */
        AMULET(EndboundEmber.AMULET_RITUAL_DURATION, EndboundEmber.AMULET_RITUAL_SWAP_TICK),
        /** Herobrine 召唤：下界风格底座 + 中心与四角各一颗下界之星。 */
        HEROBRINE(EndboundEmber.HEROBRINE_RITUAL_DURATION, EndboundEmber.HEROBRINE_RITUAL_SUMMON_TICK);

        private final int duration;
        private final int swapTick;

        RitualType(int duration, int swapTick) {
            this.duration = duration;
            this.swapTick = swapTick;
        }

        public int duration() {
            return this.duration;
        }

        public int swapTick() {
            return this.swapTick;
        }
    }

    private ItemStack storedItem = ItemStack.EMPTY;

    /** 仪式是否进行中 */
    private boolean ritualActive;
    /** 仪式是否已经完成手绳到光环的替换 */
    private boolean ritualSwapped;
    /** 当前仪式的种类 */
    private RitualType ritualType = RitualType.HALO;
    /** 仪式开始的游戏时间（tick） */
    private long ritualStartTime;
    /** 仪式开始时记录下来的环绕展示台位置，仅服务端用于生成汇聚粒子 */
    private List<BlockPos> altarPedestals = List.of();
    /**
     * 客户端动画时间（tick）。只在玩家靠近时推进，玩家远离后停住，回来时从停住的位置继续，
     * 避免远处展示台持续做动画浪费渲染开销。
     */
    private double animationTime;

    public DisplayPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(EndboundEmber.DISPLAY_PEDESTAL_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStack getStoredItem() {
        return storedItem;
    }

    public void setStoredItem(ItemStack stack) {
        storedItem = stack;
        setChanged();
        syncToClient();
    }

    public boolean isRitualActive() {
        return ritualActive;
    }

    public boolean isRitualSwapped() {
        return ritualSwapped;
    }

    public void setRitualSwapped(boolean swapped) {
        ritualSwapped = swapped;
        setChanged();
    }

    public long getRitualStartTime() {
        return ritualStartTime;
    }

    /** 开始仪式：记录起始时间并同步给客户端 */
    public void startRitual(long startTime, List<BlockPos> pedestals, RitualType type) {
        ritualActive = true;
        ritualSwapped = false;
        ritualStartTime = startTime;
        ritualType = type;
        altarPedestals = new ArrayList<>(pedestals);
        setChanged();
        syncToClient();
    }

    /** 当前仪式的种类 */
    public RitualType getRitualType() {
        return ritualType;
    }

    /** 当前仪式的总时长（tick） */
    public int getRitualDuration() {
        return ritualType.duration();
    }

    /** 仪式开始时记录下来的环绕展示台位置 */
    public List<BlockPos> getAltarPedestals() {
        return altarPedestals;
    }

    /** 结束仪式：清空仪式状态并同步给客户端 */
    public void stopRitual() {
        ritualActive = false;
        ritualSwapped = false;
        ritualStartTime = 0L;
        altarPedestals = List.of();
        setChanged();
        syncToClient();
    }

    /** 仪式已经播放的 tick 数；未开始时返回 -1 */
    public int getRitualElapsed(float partialTick) {
        if (!ritualActive || level == null) {
            return -1;
        }
        return (int) (level.getGameTime() - ritualStartTime + partialTick);
    }

    /** 客户端动画时间（tick），供渲染器计算悬浮与自转 */
    public double getAnimationTime() {
        return animationTime;
    }

    /** 推进一步客户端动画；只在玩家靠近时由客户端 tick 调用 */
    public void advanceAnimation() {
        animationTime += 1.0D;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!storedItem.isEmpty()) {
            tag.put(TAG_ITEM, storedItem.save(registries));
        }
        tag.putBoolean(TAG_RITUAL_ACTIVE, ritualActive);
        tag.putBoolean(TAG_RITUAL_SWAPPED, ritualSwapped);
        tag.putLong(TAG_RITUAL_START, ritualStartTime);
        tag.putString(TAG_RITUAL_TYPE, ritualType.name());
        // 区块卸载或服务器重启后仪式仍会继续推进，必须把环绕展示台位置一并存档，
        // 否则重载后 altarPedestals 为空，材料消耗与汇聚粒子会静默失效，仪式材料永久残留。
        tag.putLongArray(TAG_ALTAR_PEDESTALS, altarPedestals.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedItem = tag.contains(TAG_ITEM)
                ? ItemStack.parse(registries, tag.getCompound(TAG_ITEM)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        ritualActive = tag.getBoolean(TAG_RITUAL_ACTIVE);
        ritualSwapped = tag.getBoolean(TAG_RITUAL_SWAPPED);
        ritualStartTime = tag.getLong(TAG_RITUAL_START);
        ritualType = tag.contains(TAG_RITUAL_TYPE)
                ? RitualType.valueOf(tag.getString(TAG_RITUAL_TYPE))
                : RitualType.HALO;
        altarPedestals = tag.contains(TAG_ALTAR_PEDESTALS)
                ? Arrays.stream(tag.getLongArray(TAG_ALTAR_PEDESTALS))
                        .mapToObj(BlockPos::of)
                        .collect(Collectors.toCollection(ArrayList::new))
                : List.of();
    }

    /** 同步给客户端，保证其他玩家也能看到展示中的物品与仪式动画 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
