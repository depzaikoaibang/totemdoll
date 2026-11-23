# 3D My Totem Doll - Fabric 1.21.11 port

Totem of Undying thanh bup be 3D: model, texture va animation tu goi Bedrock goc **My Totem Doll** cua **Ancxliy**.
Ban port Fabric 1.21.11 boi **We1rd ツ** (Discord: itz_sh4rk). Chi dung ca nhan, khong dang lai.

## Build
Day len GitHub, tab Actions se tu build (xem `.github/workflows/build.yml`), hoac `gradle build`.
Can Java 21 va Fabric API cho 1.21.11. Mod Menu la tuy chon.

## Mo cai dat
- **Mod Menu**: vao Mods -> 3D My Totem Doll -> nut Configure (bieu tuong cai dat).
- Hoac go lenh `/totemdoll` trong game.
- Bat/tat mod nhanh luc dang choi (sinh ton dung doll, PvP tat de ve totem vanilla):
  `/totemdoll toggle`, `/totemdoll on`, `/totemdoll off`.
- `/totemdoll scale <0.1-4.0>` chinh nhanh do to khi cam.

## Menu cai dat
- Enable Mod: tat = quay ve totem vanilla.
- 3D Totem: tat = totem 2D phang (model2d cua goi goc).
- Model Selection: Bucket / Flower Bucket / Coffee / Matcha / Foot Wide / Foot Slim.
- Textures Player Arm Totem Model: Slim [ Alex ] / Wide [ Steve ].
- 3D Skin Layer: bat/tat lop skin ngoai 3D (moi diem anh thanh mot khoi nho).
- Blink: chop mat theo dung moc thoi gian cua goi goc.
- Animations: bat/tat animation.
- First Person Item Scale: 0.75 - 1.8.
- Adjust Doll Position: Rotation / Offset / Scale cho tung goc nhin va tung model.

## Skin: Alex (slim) hoac Steve (normal)
- `assets/totemdoll/textures/entity/alex.png`  (tay 3px)
- `assets/totemdoll/textures/entity/steve.png` (tay 4px)
Mac dinh ca hai la skin tuy chinh. Muon doi: tai skin tren namemc.com (PNG 64x64), doi ten thanh
alex.png hoac steve.png roi thay file (trong jar bang 7-Zip, hoac dung resource pack mau `TotemDoll-SkinPack.zip`).

## Animation (port tu animation controller cua goi Bedrock)
- Cam Totem len tay: nho len khoi xo (drssdv), sau ~1.4s vay tay.
- Roi: vuon nguoi (fall), cham dat: nen xuong roi bat lai (yytrhz) + song nuoc trong coc (llaman).
- Bi thuong: gio tay hoang so (hurt).
- Dung yen: tho nhe (bob), thinh thoang tu nghieng dau / vay tay / lac coc, chop mat.

## Credits
- Pack goc: **Ancxliy** - TikTok @ancxliy, YouTube @ancraff, https://linktr.ee/ancxliy
- Port 1.21.11: **We1rd ツ** - Discord: itz_sh4rk
