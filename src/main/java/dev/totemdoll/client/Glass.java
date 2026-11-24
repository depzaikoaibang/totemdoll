@Override
protected void init() {
    int boxW = 240;
    int boxH = 160;
    int x = (this.width - boxW) / 2;
    int y = (this.height - boxH) / 2;

    // Nút bấm Liquid Glass chỉ nằm trong màn hình của mod
    this.addRenderableWidget(new Glass.Btn(
        x + 20, y + 120, boxW - 40, 20,
        Component.literal("Done"),
        btn -> this.onClose()
    ));
}

@Override
public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    // Vẽ khung kính mờ cho bảng Option của mod
    int boxW = 240, boxH = 160;
    int x = (this.width - boxW) / 2;
    int y = (this.height - boxH) / 2;
    Glass.panel(g, x, y, x + boxW, y + boxH, 1.0f);

    super.render(g, mouseX, mouseY, partialTick);
}
