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

## Menu trong game
Go lenh `/totemdoll` trong chat de mo menu:
- Animations: bat/tat.
- Model Selection: Bucket / Flower Bucket / Coffee / Matcha Cup.
- Textures Player Arm Totem Model: Slim [ Alex ] / Wide [ Steve ].
- 3D Skin Layer: bat/tat lop skin ngoai 3D (moi diem anh cua lop ngoai thanh mot khoi nho nho ra,
  giong mod 3D Skin Layers). Mat na lay tu kenh alpha cua `skin.png` trong jar.
- First Person Item Scale.
- Adjust Doll Position...: Rotation XYZ, Offset XYZ, Scale cho tung goc nhin; panel mo di khi chinh.
Cai dat luu o `config/totemdoll.json`.

## Skin: Alex (slim) hoac Steve (normal)
Trong menu chon `Slim [ Alex ]` hoac `Wide [ Steve ]`. Hai file skin:
- `assets/totemdoll/textures/entity/alex.png`  (tay 3px)
- `assets/totemdoll/textures/entity/steve.png` (tay 4px)
Mac dinh ca hai la skin tuy chinh (steve.png la ban chuyen tay rong cua alex.png).
Muon doi: tai skin tren namemc.com (PNG 64x64), doi ten thanh alex.png hoac steve.png roi thay file
(trong jar bang 7-Zip, hoac dung resource pack mau `TotemDoll-SkinPack.zip`). Lop 3D tu dong theo skin moi.

## Animation
Dung file animation cua goi My Totem Doll (`bedrock/animations/doll.animation.json`):
- Cam Totem len tay: nho len khoi xo.
- Nhay: gio hai tay, coc lac.
- Roi: vuon nguoi, hai tay gio chu V.
- Cham dat: nen xuong roi bat lai.
- Dung yen: tho nhe, thinh thoang tu nghieng dau / vay tay / run.

## Credits
- Pack goc: **My Totem Doll** boi **Ancxliy** (TikTok @ancxliy, YouTube @ancraff, https://linktr.ee/ancxliy).
- Ban port 1.21.11: **We1rd ツ** (Discord: itz_sh4rk).
Model, texture va animation thuoc ve Ancxliy - chi dung ca nhan, khong dang lai.

## Lenh
- `/totemdoll` mo menu.
- `/totemdoll scale <0.1-4.0>` chinh nhanh First Person Item Scale.
