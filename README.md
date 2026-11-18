# Redmi TV Launcher & Firebase Remote Receiver

## 1. Tính năng nổi bật
- **Siêu nhẹ**: Không dùng Jetpack Compose hay thư viện nặng, dùng `RecyclerView` + `ViewBinding` thuần giúp khởi động tức thì trên TV Redmi RAM thấp.
- **Hiển thị 1 dòng ngang**: Tự động quét toàn bộ ứng dụng TV (`LAUNCHER` + `LEANBACK_LAUNCHER`).
- **Quản lý ứng dụng trực tiếp**: Bấm nút `Sắp xếp / Ẩn App` để dùng phím **Trái / Phải** đổi vị trí ứng dụng, nhấn **OK** (hoặc giữ chặt phím OK) để **Ẩn/Hiện** hoặc **Gỡ cài đặt** app.
- **Widget Giá Xăng & Giá Vàng**: Card hiển thị thông tin giá xăng RON 95, dầu DO và giá vàng SJC hàng ngày.
- **Điều khiển từ xa qua Firebase**:
  - Nhận phím điều hướng D-Pad, Tăng/Giảm âm lượng, Back, Home.
  - Chụp ảnh màn hình TV (`SCREENSHOT`) nén JPEG Base64 đẩy ngược lên Firebase.
  - Khóa / Mở khóa TV từ xa (`locked: true/false`) chặn toàn bộ thao tác remote vật lý.
  - Nhận tin nhắn từ điện thoại (`message`) hiển thị popup ngay trên TV.
  - Tắt màn hình / Sleep TV từ xa (`POWER_OFF`) qua lệnh shell/ADB.

## 2. Hướng dẫn Build bằng GitHub Actions
1. Tạo file `google-services.json` từ Firebase Console (với package name `com.custom.redmitvlauncher`) và ghi đè vào thư mục `app/google-services.json`.
2. Đẩy toàn bộ mã nguồn này lên GitHub repository của bạn.
3. Vào tab **Actions** trên GitHub -> chọn workflow **Build Android TV Launcher APK** -> tải file `RedmiTvLauncher-debug.apk` về cài lên TV.

## 3. Cài đặt làm Launcher mặc định trên TV Redmi (qua ADB)
```bash
adb connect <IP_CUA_TV>:5555
adb install -r app-debug.apk
# Tắt launcher mặc định của Xiaomi/Redmi để tránh xung đột:
adb shell pm disable-user --user 0 com.mitv.tvhome
```
