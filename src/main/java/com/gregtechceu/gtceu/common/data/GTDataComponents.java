package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.filter.SimpleFluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SmartItemFilter;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.item.datacomponents.*;
import com.gregtechceu.gtceu.api.placeholder.PlaceholderRenderData;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.datacomponents.AEInputConfigCopyData;
import com.gregtechceu.gtceu.common.data.datacomponents.CoverConfigCopyData;
import com.gregtechceu.gtceu.common.data.datacomponents.FluidProspectionCache;
import com.gregtechceu.gtceu.common.data.datacomponents.MedicalConditionTrackerData;
import com.gregtechceu.gtceu.common.data.datacomponents.OreProspectionCache;
import com.gregtechceu.gtceu.common.data.datacomponents.PipeNetData;
import com.gregtechceu.gtceu.common.data.datacomponents.TransferData;
import com.gregtechceu.gtceu.common.data.datacomponents.VirtualEntryData;
import com.gregtechceu.gtceu.common.item.LampBlockItem;
import com.gregtechceu.gtceu.common.item.behavior.ItemMagnetBehavior;
import com.gregtechceu.gtceu.common.item.datacomponents.*;
import com.gregtechceu.gtceu.common.item.tool.behavior.ToolModeSwitchBehavior;
import com.gregtechceu.gtceu.utils.GlobalPosWithRot;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GTDataComponents {

    private static final StreamCodec<ByteBuf, Unit> UNIT_STREAM_CODEC = StreamCodec.unit(Unit.INSTANCE);
    private static final Codec<Map<String, DataComponentMap>> VIRTUAL_ENTRY_MAP_CODEC = Codec.unboundedMap(Codec.STRING,
            DataComponentMap.CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, DataComponentMap>> VIRTUAL_ENTRY_MAP_STREAM_CODEC = ByteBufCodecs
            .map(HashMap::new, ByteBufCodecs.STRING_UTF8, SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister
            .createDataComponents(Registries.DATA_COMPONENT_TYPE, GTCEu.MOD_ID);

    // Tool-related
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GTTool>> GT_TOOL = DATA_COMPONENTS
            .registerComponentType("gt_tool",
                    builder -> builder.persistent(GTTool.CODEC).networkSynchronized(GTTool.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolBehaviors>> TOOL_BEHAVIORS = DATA_COMPONENTS
            .registerComponentType("tool_behaviors", builder -> builder.persistent(ToolBehaviors.CODEC)
                    .networkSynchronized(ToolBehaviors.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AoESymmetrical>> AOE = DATA_COMPONENTS
            .registerComponentType("aoe", builder -> builder.persistent(AoESymmetrical.CODEC)
                    .networkSynchronized(AoESymmetrical.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> DISALLOW_CONTAINER_ITEM = DATA_COMPONENTS
            .registerComponentType("disallow_container_item", builder -> builder.persistent(Unit.CODEC)
                    .networkSynchronized(UNIT_STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> RELOCATE_MINED_BLOCKS = DATA_COMPONENTS
            .registerComponentType("relocate_mined_blocks", builder -> builder.persistent(Unit.CODEC)
                    .networkSynchronized(UNIT_STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> RELOCATE_MOB_DROPS = DATA_COMPONENTS
            .registerComponentType("relocate_mob_drops", builder -> builder.persistent(Unit.CODEC)
                    .networkSynchronized(UNIT_STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ACTIVE = DATA_COMPONENTS
            .registerComponentType("active", builder -> builder.persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolModeSwitchBehavior.ModeType>> TOOL_MODE = DATA_COMPONENTS
            .registerComponentType("tool_mode", builder -> builder
                    .persistent(ToolModeSwitchBehavior.ModeType.CODEC)
                    .networkSynchronized(ToolModeSwitchBehavior.ModeType.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemEnchantments>> INNATE_ENCHANTMENTS = DATA_COMPONENTS
            .registerComponentType("innate_enchantments", builder -> builder
                    .persistent(ItemEnchantments.CODEC)
                    .networkSynchronized(ItemEnchantments.STREAM_CODEC)
                    .cacheEncoding());

    // Material-related
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Material>> ITEM_MATERIAL = DATA_COMPONENTS
            .registerComponentType("item_material", builder -> builder
                    .persistent(GTRegistries.MATERIALS.byNameCodec())
                    .networkSynchronized(ByteBufCodecs.registry(GTRegistries.MATERIAL_REGISTRY)));

    // Armor-related
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GTArmor>> ARMOR_DATA = DATA_COMPONENTS
            .registerComponentType("armor",
                    builder -> builder.persistent(GTArmor.CODEC).networkSynchronized(GTArmor.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> FLY_MODE = DATA_COMPONENTS
            .registerComponentType("fly_mode", builder -> builder.persistent(Unit.CODEC)
                    .networkSynchronized(UNIT_STREAM_CODEC));

    // component item-related
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResearchManager.ResearchItem>> RESEARCH_ITEM = DATA_COMPONENTS
            .registerComponentType("research_item", builder -> builder.persistent(ResearchManager.ResearchItem.CODEC)
                    .networkSynchronized(ResearchManager.ResearchItem.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DataItem>> DATA_ITEM = DATA_COMPONENTS
            .registerComponentType("data_item", builder -> builder.persistent(DataItem.CODEC)
                    .networkSynchronized(DataItem.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemMagnetBehavior.MagnetComponent>> MAGNET = DATA_COMPONENTS
            .registerComponentType("magnet",
                    builder -> builder.persistent(ItemMagnetBehavior.MagnetComponent.CODEC)
                            .networkSynchronized(ItemMagnetBehavior.MagnetComponent.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Byte>> SCANNER_MODE = DATA_COMPONENTS
            .registerComponentType("scanner_mode",
                    builder -> builder.persistent(Codec.BYTE).networkSynchronized(ByteBufCodecs.BYTE));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidFilter>> SIMPLE_FLUID_FILTER = DATA_COMPONENTS
            .registerComponentType("simple_fluid_filter", builder -> builder.persistent(SimpleFluidFilter.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleItemFilter>> SIMPLE_ITEM_FILTER = DATA_COMPONENTS
            .registerComponentType("simple_item_filter", builder -> builder.persistent(SimpleItemFilter.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TAG_FILTER_EXPRESSION = DATA_COMPONENTS
            .registerComponentType("tag_filter_expression",
                    builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SmartItemFilter.SmartFilteringMode>> SMART_ITEM_FILTER = DATA_COMPONENTS
            .registerComponentType("smart_item_filter",
                    builder -> builder.persistent(SmartItemFilter.SmartFilteringMode.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CIRCUIT_CONFIG = DATA_COMPONENTS
            .registerComponentType("circuit_config", builder -> builder.persistent(Codec.INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENT = DATA_COMPONENTS
            .registerComponentType("fluid_content", builder -> builder.persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleEnergyContent>> ENERGY_CONTENT = DATA_COMPONENTS
            .registerComponentType("energy_content", builder -> builder.persistent(SimpleEnergyContent.CODEC)
                    .networkSynchronized(SimpleEnergyContent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BindingData>> BINDING_DATA = DATA_COMPONENTS
            .registerComponentType("binding_data", builder -> builder.persistent(BindingData.CODEC)
                    .networkSynchronized(BindingData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> DATA_COPY_POS = DATA_COMPONENTS
            .registerComponentType("data_copy_pos", builder -> builder.persistent(BlockPos.CODEC)
                    .networkSynchronized(BlockPos.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MachineConfigCopyData>> DATA_COPY_TAG = DATA_COMPONENTS
            .registerComponentType("data_copy_tag", builder -> builder.persistent(MachineConfigCopyData.CODEC)
                    .networkSynchronized(MachineConfigCopyData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoverConfigCopyData>> COVER_CONFIG_COPY_DATA = DATA_COMPONENTS
            .registerComponentType("cover_config_copy_data", builder -> builder.persistent(CoverConfigCopyData.CODEC)
                    .networkSynchronized(CoverConfigCopyData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AEInputConfigCopyData>> AE_INPUT_CONFIG_COPY_DATA = DATA_COMPONENTS
            .registerComponentType("ae_input_config_copy_data", builder -> builder
                    .persistent(AEInputConfigCopyData.CODEC)
                    .networkSynchronized(AEInputConfigCopyData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TextLineList>> FORMAT_STRING_LIST = DATA_COMPONENTS
            .registerComponentType("format_string_list", builder -> builder.persistent(TextLineList.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FormatStringList>> COMPUTER_MONITOR_DATA = DATA_COMPONENTS
            .registerComponentType("computer_monitor_cover_data", builder -> builder.persistent(FormatStringList.CODEC)
                    .networkSynchronized(FormatStringList.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ComputerMonitorConfig>> COMPUTER_MONITOR_CONFIG = DATA_COMPONENTS
            .registerComponentType("computer_monitor_cover_config",
                    builder -> builder.persistent(ComputerMonitorConfig.CODEC)
                            .networkSynchronized(ComputerMonitorConfig.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COMPUTER_MONITOR_P = DATA_COMPONENTS
            .registerComponentType("computer_monitor_cover_p", builder -> builder.persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TextLineList>> TEXT_LINE_LIST = DATA_COMPONENTS
            .registerComponentType("text_line_list", builder -> builder.persistent(TextLineList.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> IMAGE_MODULE_URL = DATA_COMPONENTS
            .registerComponentType("image_module_url", builder -> builder.persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPosWithRot>> MONITOR_TARGET = DATA_COMPONENTS
            .registerComponentType("monitor_target", builder -> builder.persistent(GlobalPosWithRot.CODEC)
                    .networkSynchronized(GlobalPosWithRot.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> ENDER_REDSTONE_LINK_TRANSMITTER_UUID = DATA_COMPONENTS
            .registerComponentType("ender_redstone_link_transmitter_uuid",
                    builder -> builder.persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> PLACEHOLDER_UUID = DATA_COMPONENTS
            .registerComponentType("placeholder_uuid",
                    builder -> builder.persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlaceholderRenderData.Rect>> PLACEHOLDER_RECT_RENDER_DATA = DATA_COMPONENTS
            .registerComponentType("placeholder_rect_render_data",
                    builder -> builder.persistent(PlaceholderRenderData.Rect.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlaceholderRenderData.Quad>> PLACEHOLDER_QUAD_RENDER_DATA = DATA_COMPONENTS
            .registerComponentType("placeholder_quad_render_data",
                    builder -> builder.persistent(PlaceholderRenderData.Quad.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>> PLACEHOLDER_ITEM_STACK = DATA_COMPONENTS
            .registerComponentType("placeholder_item_stack", builder -> builder.persistent(ItemStack.OPTIONAL_CODEC)
                    .networkSynchronized(ItemStack.STREAM_CODEC));

    // machine info
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LargeItemContent>> LARGE_ITEM_CONTENT = DATA_COMPONENTS
            .registerComponentType("large_item_content", builder -> builder
                    .persistent(LargeItemContent.CODEC)
                    .networkSynchronized(LargeItemContent.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LargeFluidContent>> LARGE_FLUID_CONTENT = DATA_COMPONENTS
            .registerComponentType("large_fluid_content", builder -> builder
                    .persistent(LargeFluidContent.CODEC)
                    .networkSynchronized(LargeFluidContent.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CreativeMachineInfo>> CREATIVE_MACHINE_INFO = DATA_COMPONENTS
            .registerComponentType("creative_machine_info", builder -> builder
                    .persistent(CreativeMachineInfo.CODEC)
                    .networkSynchronized(CreativeMachineInfo.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> TAPED = DATA_COMPONENTS
            .registerComponentType("taped",
                    builder -> builder.persistent(Unit.CODEC).networkSynchronized(UNIT_STREAM_CODEC));

    // misc
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FacadeWrapper>> FACADE = DATA_COMPONENTS
            .registerComponentType("facade", builder -> builder.persistent(FacadeWrapper.CODEC)
                    .networkSynchronized(FacadeWrapper.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LampBlockItem.LampData>> LAMP_DATA = DATA_COMPONENTS
            .registerComponentType("lamp", builder -> builder.persistent(LampBlockItem.LampData.CODEC)
                    .networkSynchronized(LampBlockItem.LampData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LIGHTER_OPEN = DATA_COMPONENTS
            .registerComponentType("lighter_open",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SyncFieldData>> SYNC_FIELD_DATA = DATA_COMPONENTS
            .registerComponentType("sync_field_data", builder -> builder.persistent(SyncFieldData.CODEC)
                    .networkSynchronized(SyncFieldData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DataComponentMap>> BLOCK_ITEM_DATA = DATA_COMPONENTS
            .registerComponentType("block_item_data", builder -> builder.persistent(DataComponentMap.CODEC)
                    .networkSynchronized(SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FluidProspectionCache>> FLUID_PROSPECTION_CACHE = DATA_COMPONENTS
            .registerComponentType("fluid_prospection_cache", builder -> builder
                    .persistent(FluidProspectionCache.CODEC)
                    .networkSynchronized(FluidProspectionCache.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<OreProspectionCache>> ORE_PROSPECTION_CACHE = DATA_COMPONENTS
            .registerComponentType("ore_prospection_cache", builder -> builder
                    .persistent(OreProspectionCache.CODEC)
                    .networkSynchronized(OreProspectionCache.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MedicalConditionTrackerData>> MEDICAL_CONDITION_TRACKER = DATA_COMPONENTS
            .registerComponentType("medical_condition_tracker", builder -> builder
                    .persistent(MedicalConditionTrackerData.CODEC)
                    .networkSynchronized(MedicalConditionTrackerData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeNetData.Nodes>> PIPE_NET_NODES = DATA_COMPONENTS
            .registerComponentType("pipe_net_nodes", builder -> builder
                    .persistent(PipeNetData.Nodes.CODEC)
                    .networkSynchronized(PipeNetData.Nodes.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeNetData.Wire>> PIPE_NET_WIRE = DATA_COMPONENTS
            .registerComponentType("pipe_net_wire", builder -> builder
                    .persistent(PipeNetData.Wire.CODEC)
                    .networkSynchronized(PipeNetData.Wire.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeNetData.FluidPipe>> PIPE_NET_FLUID_PIPE = DATA_COMPONENTS
            .registerComponentType("pipe_net_fluid_pipe", builder -> builder
                    .persistent(PipeNetData.FluidPipe.CODEC)
                    .networkSynchronized(PipeNetData.FluidPipe.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeNetData.ItemPipe>> PIPE_NET_ITEM_PIPE = DATA_COMPONENTS
            .registerComponentType("pipe_net_item_pipe", builder -> builder
                    .persistent(PipeNetData.ItemPipe.CODEC)
                    .networkSynchronized(PipeNetData.ItemPipe.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PipeNetData.DuctPipe>> PIPE_NET_DUCT_PIPE = DATA_COMPONENTS
            .registerComponentType("pipe_net_duct_pipe", builder -> builder
                    .persistent(PipeNetData.DuctPipe.CODEC)
                    .networkSynchronized(PipeNetData.DuctPipe.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TransferData.FluidTank>> TRANSFER_FLUID_TANK = DATA_COMPONENTS
            .registerComponentType("transfer_fluid_tank", builder -> builder
                    .persistent(TransferData.FluidTank.CODEC)
                    .networkSynchronized(TransferData.FluidTank.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TransferData.FluidHandlers>> TRANSFER_FLUID_HANDLERS = DATA_COMPONENTS
            .registerComponentType("transfer_fluid_handlers", builder -> builder
                    .persistent(TransferData.FluidHandlers.CODEC)
                    .networkSynchronized(TransferData.FluidHandlers.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TransferData.ItemHandler>> TRANSFER_ITEM_HANDLER = DATA_COMPONENTS
            .registerComponentType("transfer_item_handler", builder -> builder
                    .persistent(TransferData.ItemHandler.CODEC)
                    .networkSynchronized(TransferData.ItemHandler.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VirtualEntryData.Base>> VIRTUAL_ENTRY_BASE = DATA_COMPONENTS
            .registerComponentType("virtual_entry_base", builder -> builder
                    .persistent(VirtualEntryData.Base.CODEC)
                    .networkSynchronized(VirtualEntryData.Base.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VirtualEntryData.Tank>> VIRTUAL_TANK = DATA_COMPONENTS
            .registerComponentType("virtual_tank", builder -> builder
                    .persistent(VirtualEntryData.Tank.CODEC)
                    .networkSynchronized(VirtualEntryData.Tank.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VirtualEntryData.Items>> VIRTUAL_ITEM_STORAGE = DATA_COMPONENTS
            .registerComponentType("virtual_item_storage", builder -> builder
                    .persistent(VirtualEntryData.Items.CODEC)
                    .networkSynchronized(VirtualEntryData.Items.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VirtualEntryData.Redstone>> VIRTUAL_REDSTONE = DATA_COMPONENTS
            .registerComponentType("virtual_redstone", builder -> builder
                    .persistent(VirtualEntryData.Redstone.CODEC)
                    .networkSynchronized(VirtualEntryData.Redstone.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, DataComponentMap>>> VIRTUAL_FLUID_ENTRIES = DATA_COMPONENTS
            .registerComponentType("virtual_fluid_entries", builder -> builder
                    .persistent(VIRTUAL_ENTRY_MAP_CODEC)
                    .networkSynchronized(VIRTUAL_ENTRY_MAP_STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, DataComponentMap>>> VIRTUAL_ITEM_ENTRIES = DATA_COMPONENTS
            .registerComponentType("virtual_item_entries", builder -> builder
                    .persistent(VIRTUAL_ENTRY_MAP_CODEC)
                    .networkSynchronized(VIRTUAL_ENTRY_MAP_STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, DataComponentMap>>> VIRTUAL_REDSTONE_ENTRIES = DATA_COMPONENTS
            .registerComponentType("virtual_redstone_entries", builder -> builder
                    .persistent(VIRTUAL_ENTRY_MAP_CODEC)
                    .networkSynchronized(VIRTUAL_ENTRY_MAP_STREAM_CODEC));
}
