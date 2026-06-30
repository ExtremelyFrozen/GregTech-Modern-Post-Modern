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

public class RectPlaceholderRenderer implements IPlaceholderRenderer {

    @Override
    public void render(CentralMonitorMachine machine, MonitorGroup group, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay, DataComponentMap data) {
        PlaceholderRenderData.Rect rect = data.get(GTDataComponents.PLACEHOLDER_RECT_RENDER_DATA.get());
        if (rect == null) {
            throw new IllegalArgumentException("Missing placeholder rect render data");
        }

        poseStack.pushPose();
        VertexConsumer consumer = buffer.getBuffer(GTRenderTypes.getMonitor());
        Matrix4f pose = poseStack.last().pose();
        float minX = 0, maxX = rect.width();
        float minY = 0, maxY = rect.height();
        int color = rect.color();

        consumer.addVertex(pose, minX, maxY, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(pose, maxX, maxY, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(pose, maxX, minY, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(pose, minX, minY, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
