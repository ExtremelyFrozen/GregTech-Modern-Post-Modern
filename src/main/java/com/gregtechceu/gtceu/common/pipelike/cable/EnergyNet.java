package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.PipeNetData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.Level;

import java.util.*;

public class EnergyNet extends PipeNet<WireProperties> {

    private final Map<BlockPos, List<EnergyRoutePath>> NET_DATA = new HashMap<>();

    private long lastEnergyFluxPerSec;
    private long energyFluxPerSec;
    private long lastTime;

    protected EnergyNet(LevelPipeNet<WireProperties, ? extends EnergyNet> world) {
        super(world);
    }

    public List<EnergyRoutePath> getNetData(BlockPos pipePos) {
        List<EnergyRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = EnergyNetWalker.createNetData(this, pipePos);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(EnergyRoutePath::getDistance));
            NET_DATA.put(pipePos, data);
        }
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        NET_DATA.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        NET_DATA.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<WireProperties>> transferredNodes,
                                    PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((EnergyNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected DataComponentMap writeNodeData(WireProperties nodeData) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PIPE_NET_WIRE.get(), new PipeNetData.Wire(nodeData.getVoltage(),
                        nodeData.getAmperage(), nodeData.getLossPerBlock()))
                .build();
    }

    @Override
    protected WireProperties readNodeData(DataComponentMap components) {
        PipeNetData.Wire data = components.get(GTDataComponents.PIPE_NET_WIRE.get());
        if (data == null) {
            throw new IllegalArgumentException("Missing wire pipe node data component");
        }
        return new WireProperties(data.voltage(), data.amperage(), data.lossPerBlock());
    }

    //////////////////////////////////////
    // ******* Pipe Status *******//
    //////////////////////////////////////

    public long getEnergyFluxPerSec() {
        Level world = getLevel();
        if (world != null && !world.isClientSide && (world.getGameTime() - lastTime) >= 20) {
            lastTime = world.getGameTime();
            clearCache();
        }
        return lastEnergyFluxPerSec;
    }

    public void addEnergyFluxPerSec(long energy) {
        energyFluxPerSec += energy;
    }

    public void clearCache() {
        lastEnergyFluxPerSec = energyFluxPerSec;
        energyFluxPerSec = 0;
    }
}
