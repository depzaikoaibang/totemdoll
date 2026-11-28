# 3D My Totem Doll - Fabric 1.21.11 port

Totem of Undying thanh bup be 3D: model, texture va animation tu goi Bedrock goc **My Totem Doll** cua **Ancxliy**.
Ban port Fabric 1.21.11 boi **We1rd ツ** (Discord: itz_sh4rk). Chi dung ca nhan, khong dang lai.

## Build
Day len GitHub, tab Actions se tu build (xem `.github/workflows/build.yml`), hoac `gradle build`.
Can Java 21 va Fabric API cho 1.21.11. Mod Menu la tuy chon.

## Mo cai dat
- **Mod Menu**: vao Mods -> 3D My Totem Doll -> nut Configure (bieu tuong cai dat).
- Phim **M** mo cai dat (giong ban 26.3). Phim bat/tat mod nhanh co trong Options > Controls (mac dinh chua gan phim).
- Hoac go lenh `/totemdoll` trong game.
- Bat/tat mod nhanh luc dang choi (sinh ton dung doll, PvP tat de ve totem vanilla):
  `/totemdoll toggle`, `/totemdoll on`, `/totemdoll off`.
- `/totemdoll scale <0.1-4.0>` chinh nhanh do to khi cam.

## Menu cai dat
Giao dien kieu ban 26.3 (nut vanilla, panel toi trong suot).
Ben phai: cac nut cai dat. Ben trai: **Totem Doll View** - bup be 3D xoay (turntable), co animation that,
cap nhat NGAY khi doi bat ky cai dat nao. Ve o do phan giai that cua man hinh (1 o = 1 pixel man hinh) co khu rang cua,
khong dung atlas item nen khong bi pixel/dung hinh.
- Enable Mod: tat = quay ve totem vanilla.
- 3D Totem: tat = totem 2D phang (model2d cua goi goc).
- Model Selection: Bucket / Flower Bucket / Coffee / Matcha / Foot Wide / Foot Slim.
- Textures Player Arm Totem Model: Slim [ Alex ] / Wide [ Steve ].
- 3D Skin Layer: bat/tat lop skin ngoai 3D (moi diem anh thanh mot khoi nho).
- Blink: chop mat theo dung moc thoi gian cua goi goc.
- Animations: bat/tat animation.
- First Person Item Scale: 0.75 - 1.8.
- Adjust Doll Position: Rotation / Offset / Scale cho tung goc nhin va tung model.
  First / Third Person co **Edit hand: Both / Left / Right** (Both = sua ca hai tay, tay trai tu lat guong).
  Ben phai co khung Preview xem ngay ket qua; cam Totem de thay trong game.

## Skin: Alex (slim) hoac Steve (normal)
- `assets/totemdoll/textures/entity/alex.png`  (tay 3px)
- `assets/totemdoll/textures/entity/steve.png` (tay 4px)
Mac dinh ca hai la skin tuy chinh. Muon doi: tai skin tren namemc.com (PNG 64x64), doi ten thanh
alex.png hoac steve.png roi thay file (trong jar bang 7-Zip, hoac dung resource pack mau `TotemDoll-SkinPack.zip`).

## Animation (port tu animation controller cua goi Bedrock)
- Cam Totem len tay: nho len khoi xo (drssdv), sau ~1.4s vay tay.
- Nhay nhe / xuong cau thang / roi duoi 3 block: chi nhun dau len roi dap xuong nhu lo xo, tay ha xuong mot it
  (khong thut vao, khong gio len), KHONG co nuoc bat len.
- Roi tu 3 block tro len (xuong dat hoac xuong nuoc): gio hai tay chu V khi dang roi; cham dat/cham nuoc: nen xuong +
  nuoc bat len trong coc + vay tay chao (hello).
- Totem no (totem pop): doll mac dinh duoc xoay 180 do de khong bi nguoc dau (View: Totem Pop / Frame,
  co nut Flip upside down).
- Tay luon dat 24/7: o xo/coc chong hai tay len mep, o Foot chong tay len dui. CHI khi roi (gio chu V) va vay tay
  (hello) thi tay roi vi tri nghi, roi quay lai muot (khong tuot ra sau, khong thut vao). Cac animation khac
  (cham dat, cam len, bi thuong, nghieng dau...) khong dong vao tay.
- Bi thuong: gio tay hoang so (hurt).
- Dung yen: tho nhe (bob), thinh thoang tu nghieng dau / vay tay / lac coc, chop mat.

## Credits
- Pack goc: **Ancxliy** - TikTok @ancxliy, YouTube @ancraff, https://linktr.ee/ancxliy
- Port 1.21.11: **We1rd ツ** - Discord: itz_sh4rk

## Tuong thich Punchy (first-person animations)
Punchy ve vat cam o goc nhin thu nhat bang display context **THIRD_PERSON_*** cua Minecraft. Mod nhan biet theo camera:
khi camera dang o goc nhin thu nhat thi context THIRD_PERSON duoc coi la tay cua ban va dung cai dat **First Person**
(Adjust Doll Position > View: First Person). Khi bam F5 (camera third person) thi dung cai dat **Third Person**.
