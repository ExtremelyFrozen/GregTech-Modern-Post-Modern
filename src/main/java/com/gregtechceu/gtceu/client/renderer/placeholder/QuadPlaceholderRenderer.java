package com.gregtechceu.gtceu.client.renderer.placeholder;

import com.gregtechceu.gtceu.api.placeholder.IPlaceholderRenderer;
import com.gregtechceu.gtceu.api.placeholder.PlaceholderRenderData;
import com.gregtechceu.gtceu.client.renderer.GTRenderTypes;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponentMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public class QuadPlaceholderRenderer implements IPlaceholderRenderer {

    @Override
    public void render(CentralMonitorMachine machine, MonitorGroup group, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay, DataComponentMap data) {
        PlaceholderRenderData.Quad quad = data.get(GTDataComponents.PLACEHOLDER_QUAD_RENDER_DATA.get());
        if (quad == null) {
            throw new IllegalArgumentException("Missing placeholder quad render data");
        }

        poseStack.pushPose();
        VertexConsumer consumer = buffer.getBuffer(GTRenderTypes.getMonitor());
        Matrix4f pose = poseStack.last().pose();

        consumer.addVertex(pose, quad.x1(), quad.y1(), 0).setColor(quad.color1())
                .setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(pose, quad.x2(), quad.y2(), 0).setColor(quad.color2())
                .setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(pose, quad.x3(), quad.y3(), 0).setColor(quad.color3())
                .setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(pose, quad.x4(), quad.y4(), 0).setColor(quad.color4())
                .setLight(LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
