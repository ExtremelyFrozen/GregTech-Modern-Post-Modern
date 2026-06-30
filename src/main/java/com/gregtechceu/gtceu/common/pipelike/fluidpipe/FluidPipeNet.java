package com.gregtechceu.gtceu.common.pipelike.fluidpipe;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.PipeNetData;

import net.minecraft.core.component.DataComponentMap;

public class FluidPipeNet extends PipeNet<FluidPipeProperties> {

    public FluidPipeNet(LevelPipeNet<FluidPipeProperties, FluidPipeNet> world) {
        super(world);
    }

    /////////////////////////////////////
    // *********** Persistent data ***********//
    /////////////////////////////////////

    @Override
    protected DataComponentMap writeNodeData(FluidPipeProperties nodeData) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PIPE_NET_FLUID_PIPE.get(), new PipeNetData.FluidPipe(
                        nodeData.getMaxFluidTemperature(), nodeData.getThroughput(), nodeData.isGasProof(),
                        nodeData.isAcidProof(), nodeData.isCryoProof(), nodeData.isPlasmaProof(),
                        nodeData.getChannels()))
                .build();
    }

    @Override
    protected FluidPipeProperties readNodeData(DataComponentMap components) {
        PipeNetData.FluidPipe data = components.get(GTDataComponents.PIPE_NET_FLUID_PIPE.get());
        if (data == null) {
            throw new IllegalArgumentException("Missing fluid pipe node data component");
        }
        return new FluidPipeProperties(data.maxTemperature(), data.throughput(), data.gasProof(), data.acidProof(),
                data.cryoProof(), data.plasmaProof(), data.channels());
    }
}
