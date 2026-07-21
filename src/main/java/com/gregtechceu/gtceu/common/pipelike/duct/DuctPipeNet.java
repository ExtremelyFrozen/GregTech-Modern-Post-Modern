package com.gregtechceu.gtceu.common.pipelike.duct;

import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.PipeNetData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;

import java.util.*;

public class DuctPipeNet extends PipeNet<DuctPipeProperties> {

    private final Map<BlockPos, List<DuctRoutePath>> NET_DATA = new HashMap<>();

    public DuctPipeNet(LevelPipeNet<DuctPipeProperties, ? extends PipeNet<DuctPipeProperties>> world) {
        super(world);
    }

    public List<DuctRoutePath> getNetData(BlockPos pipePos, Direction facing) {
        List<DuctRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = DuctNetWalker.createNetData(this, pipePos, facing);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
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
    protected void transferNodeData(Map<BlockPos, Node<DuctPipeProperties>> transferredNodes,
                                    PipeNet<DuctPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((DuctPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected DataComponentMap writeNodeData(DuctPipeProperties nodeData) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PIPE_NET_DUCT_PIPE.get(),
                        new PipeNetData.DuctPipe(nodeData.getTransferRate()))
                .build();
    }

    @Override
    protected DuctPipeProperties readNodeData(DataComponentMap components) {
        PipeNetData.DuctPipe data = components.get(GTDataComponents.PIPE_NET_DUCT_PIPE.get());
        if (data == null) {
            throw new IllegalArgumentException("Missing duct pipe node data component");
        }
        return new DuctPipeProperties(data.transferRate());
    }
}
