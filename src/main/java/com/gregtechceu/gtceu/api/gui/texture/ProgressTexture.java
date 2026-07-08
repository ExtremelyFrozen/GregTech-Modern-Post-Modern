package com.gregtechceu.gtceu.api.gui.texture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Progress bar texture with the LDLib1 fill-direction contract preserved for GTM widgets.
 */
public class ProgressTexture extends TransformTexture {

    protected FillDirection fillDirection = FillDirection.LEFT_TO_RIGHT;
    protected IGuiTexture emptyBarArea;
    protected IGuiTexture filledBarArea;
    protected double progress;
    private boolean demo;

    public ProgressTexture() {
        this(new ResourceTexture("gtpm:textures/gui/progress_bar/progress_bar_fuel.png").getSubTexture(0, 0, 1, 0.5),
                new ResourceTexture("gtpm:textures/gui/progress_bar/progress_bar_fuel.png").getSubTexture(0, 0.5, 1,
                        0.5));
        fillDirection = FillDirection.DOWN_TO_UP;
        demo = true;
    }

    public ProgressTexture(IGuiTexture emptyBarArea, IGuiTexture filledBarArea) {
        this.emptyBarArea = emptyBarArea;
        this.filledBarArea = filledBarArea;
    }

    public ProgressTexture setTexture(IGuiTexture emptyBarArea, IGuiTexture filledBarArea) {
        this.emptyBarArea = emptyBarArea;
        this.filledBarArea = filledBarArea;
        return this;
    }

    public FillDirection getFillDirection() {
        return fillDirection;
    }

    public IGuiTexture getEmptyBarArea() {
        return emptyBarArea;
    }

    public IGuiTexture getFilledBarArea() {
        return filledBarArea;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = Mth.clamp(progress, 0.0, 1.0);
    }

    public ProgressTexture setFillDirection(FillDirection fillDirection) {
        this.fillDirection = fillDirection;
        return this;
    }

    @Override
    public ProgressTexture copy() {
        var copy = new ProgressTexture(emptyBarArea.copy(), filledBarArea.copy());
        copy.fillDirection = fillDirection;
        copy.progress = progress;
        copy.demo = demo;
        copy.copyTransform(this);
        return copy;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateTick() {
        if (emptyBarArea != null) {
            emptyBarArea.updateTick();
        }
        if (filledBarArea != null) {
            filledBarArea.updateTick();
        }
        if (demo) {
            progress = Math.abs(System.currentTimeMillis() % 2000) / 2000.0;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                float height, float partialTicks) {
        if (emptyBarArea != null) {
            emptyBarArea.draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
        }
        if (filledBarArea == null) {
            return;
        }
        float drawnU = (float) fillDirection.getDrawnU(progress);
        float drawnV = (float) fillDirection.getDrawnV(progress);
        float drawnWidth = (float) fillDirection.getDrawnWidth(progress);
        float drawnHeight = (float) fillDirection.getDrawnHeight(progress);
        filledBarArea.drawSubArea(graphics, x + drawnU * width, y + drawnV * height, width * drawnWidth,
                height * drawnHeight, drawnU, drawnV, drawnWidth, drawnHeight);
    }

    public static class Auto extends ProgressTexture {

        public Auto(IGuiTexture emptyBarArea, IGuiTexture filledBarArea) {
            super(emptyBarArea, filledBarArea);
        }

        @Override
        public void updateTick() {
            progress = Math.abs(System.currentTimeMillis() % 2000) / 2000.0;
        }
    }

    public enum FillDirection {

        LEFT_TO_RIGHT {

            @Override
            public double getDrawnHeight(double progress) {
                return 1.0;
            }
        },
        RIGHT_TO_LEFT {

            @Override
            public double getDrawnU(double progress) {
                return 1.0 - progress;
            }

            @Override
            public double getDrawnHeight(double progress) {
                return 1.0;
            }
        },
        UP_TO_DOWN {

            @Override
            public double getDrawnWidth(double progress) {
                return 1.0;
            }
        },
        DOWN_TO_UP {

            @Override
            public double getDrawnV(double progress) {
                return 1.0 - progress;
            }

            @Override
            public double getDrawnWidth(double progress) {
                return 1.0;
            }
        },
        ALWAYS_FULL {

            @Override
            public double getDrawnHeight(double progress) {
                return 1.0;
            }

            @Override
            public double getDrawnWidth(double progress) {
                return 1.0;
            }
        };

        public double getDrawnU(double progress) {
            return 0.0;
        }

        public double getDrawnV(double progress) {
            return 0.0;
        }

        public double getDrawnWidth(double progress) {
            return progress;
        }

        public double getDrawnHeight(double progress) {
            return progress;
        }
    }
}
