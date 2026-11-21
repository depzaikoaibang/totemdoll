# Totem Doll (Fabric, Minecraft 1.21.11)

Totem of Undying thanh bup be 3D trong xo, mac skin cua ban, co animation:
dung yen (lac nhe) / nhay (bat len, gio tay) / roi (troi len, tay vung) / ha canh (nay lo xo).

## Build
1. Tot nhat: vao https://fabricmc.net/develop/template/ tao template cho **1.21.11** (Mojang mappings),
   lay dung `loom_version`, `loader_version`, `fabric_version` roi dien vao `gradle.properties`
   (hoac copy thu muc `src/` nay vao project template).
2. `./gradlew build` -> file jar o `build/libs/totemdoll-1.0.0.jar` (khong lay file `-sources`).
3. Bo vao `mods/` cung Fabric API. Can Java 21.

## Doi skin
Thay file `assets/totemdoll/textures/entity/skin.png` (mac dinh la skin ban vua chinh, kieu tay Steve 4px)
bang resource pack hoac sua truoc khi build. Muon doi coc: sua `CUP` va ten model `bucket` trong
`DollSpecialRenderer.java` (co san flower_bucket, coffee, matcha).

## Chinh animation
Hang so o dau `DollAnim.java` (ARM_SIGN, JUMP_RISE_PX, FALL_ARM_DEG...). Neu tay vung sai chieu, doi ARM_SIGN = -1.
Vi tri/kich thuoc trong tay: `assets/totemdoll/models/item/totem_doll.json` (muc "display").

## Cho de loi khi compile (API 1.21.11 chua kiem tra duoc)
- `net.minecraft.resources.Identifier` (neu phien ban nay van la `ResourceLocation`, doi ten).
- `RenderType.entityCutoutNoCull(Identifier)` (neu da chuyen sang `RenderTypes`, doi theo).
- Chu ky `SpecialModelRenderer#submit` va `#getExtents` trong `DollSpecialRenderer`.
- `SpecialModelRenderers.ID_MAPPER`, `SpecialModelRenderer.BakingContext`.
Gui log loi cho minh de sua.

## Ghi chu
Model xo/coc/bup be va texture lay tu goi My Totem Doll (Ancxliy) -> chi dung ca nhan, khong dang lai.
Code Java la code moi, khong chep tu mod goc.
