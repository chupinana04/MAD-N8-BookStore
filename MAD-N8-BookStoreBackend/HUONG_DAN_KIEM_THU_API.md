# Hướng Dẫn Kiểm Thử (Testing Guide) Đầy Đủ 38 API Hệ Thống Cửa Hàng Sách

Tài liệu này đóng vai trò như một kịch bản kiểm thử (Test Case) chi tiết dành cho QA/QC hoặc Lập trình viên. Vui lòng sử dụng các phần mềm chuyên dụng như **Postman**, **Insomnia** hoặc **Thunder Client** (VSCode) để thực thi.

**Lưu ý chung chuẩn bị trước khi Test:**
- Chạy server local bằng môi trường terminal: `node src/index.js` (Server khởi chạy ở `http://localhost:3000`).
- Khi test các API Yêu cầu Cấp quyền (Có ghi chú **[KÈM TOKEN]**), bạn cần qua tab `Authorization`, chọn `Bearer Token` và dán cái Token dài ngoằng lấy được ở Bước 2 (Đăng nhập) vào đó!

---

## I. Nhóm 1: Xác thực (AUTH)

### 1. Đăng ký tài khoản (Register)
- **Method & Route:** `POST http://localhost:3000/api/auth/register`
- **Tab Body (raw -> JSON):**
  ```json
  { "email": "tester1@gmail.com", "password": "123", "confirmPassword": "123" }
  ```
- **Kịch bản Test (Pass):** Bấm mũi tên `Send`. Bạn sẽ thấy chuỗi `{ "message": "Đăng ký thành công..." }`. Sang Console màn hình dòng lệnh của Terminal Server, bạn LẤY CÁI MÃ OTP 6 SỐ VỪA IN RA.

### 2. Đăng nhập (Login)
- **Method & Route:** `POST http://localhost:3000/api/auth/login`
- **Tab Body (raw -> JSON):**
  ```json
  { "email": "tester1@gmail.com", "password": "123" }
  ```
- **Kịch bản Test (Pass):** Bấm `Send`. Trả về Mã 200 OK kẹp `{ "token": "eyJhb..." }`. COPY CÁI TOKEN NÀY LẠI ĐỂ XÀI CHO 30 MẤY CÁI BÊN DƯỚI NHÉ!

### 3. Quên mật khẩu (Forgot Password)
- **Method & Route:** `POST http://localhost:3000/api/auth/forgot-password`
- **Tab Body (raw -> JSON):**
  ```json
  { "email": "tester1@gmail.com" }
  ```
- **Kịch bản Test (Pass):** Bấm `Send`. Nhìn màn hình Terminal Console để lấy số OTP mới.

### 4. Xác nhận OTP (Verify OTP)
- **Method & Route:** `POST http://localhost:3000/api/auth/verify-otp`
- **Tab Body (raw -> JSON):** Điền OTP lấy ở bước ảo trên.
  ```json
  { "email": "tester1@gmail.com", "otpCode": "123456" }
  ```
- **Kịch bản Test (Pass):** Trả về `resetToken`. (Copy token này).

### 5. Đổi mật khẩu (Change Password)
- **Method & Route:** `POST http://localhost:3000/api/auth/change-password`
- **CÁCH TEST A (Người quên mã):** Ko điền Header. Body điền: `{"newPassword": "456", "confirmPassword": "456", "resetToken": "Mã ở bước 4"}`.
- **CÁCH TEST B (Người nhớ mã đổi app):** Vào tab Authorization gắn Token Đăng Nhập ở Bước 2. Body điền: `{"oldPassword": "123", "newPassword": "456", "confirmPassword": "456"}`.

---

## II. Nhóm 2: Sách & Tương Tác (BOOKS/REVIEWS)

### 6. Lấy toàn bộ Danh mục sách
- **Method & Route:** `GET http://localhost:3000/api/books/categories`
- **Kịch bản Test:** Cứ nã `Send`. Nếu hệ thống bắn ra JSON Cấu trúc mảng `[]` chứa các tên Category (Ví dụ: SGK, Thiếu nhi) là Pass! (Lấy 1 cái ID xài cho Bước 7).

### 7. Lấy Lưới Sách Toàn Cục (Bỏ Phân Trang)
- **Method & Route:** `GET http://localhost:3000/api/books` hoặc `GET http://localhost:3000/api/books?categoryId=1` (ID ở Bước 6).
- **Kịch bản Test:** Cứ nã `Send`. Trả về JSON là một mảng trực tiếp danh sách sách `[{ book1 }, { book2 }]` thay vì bị bọc trong Object `pagination` phức tạp. Code App Android quét một phát ăn ngay!

### 8. Lấy Sách Gợi ý / Dành cho bạn
- **Method & Route:** `GET http://localhost:3000/api/books/for-you`
- **Kịch bản Test:** Chỉ trả thuần ra 1 mảng Mặc định 20 phần tử (hoặc ít hơn tùy DB đang có). Limit chuẩn mảng này là <= 20.

### 9. Chi tiết 1 cuốn sách (Get Book Details)
- **Method & Route:** `GET http://localhost:3000/api/books/2` (Giả sử 2 là ID sách).
- **Kịch bản Test Nâng Cao:** Nếu bấm gửi chay -> Vẫn ra Sách + Reviews bình thường. BẤM THỬ sang tab Authorization nhập Token Bước 2 -> Send -> Ra console Supabase mở bảng `ViewHistory` ra check xem DB có tự lén sinh ra 1 dòng ghi nhận người đó vừa xem sách ID 2 không. (Auto Tracking).

### 10. Chạy Tìm Kiếm Nâng Cao
- **Method & Route:** `GET http://localhost:3000/api/books/search?q=Harry` (Tham số `q` là bắt buộc).
- **Mẹo Gõ Dấu Cách/Ký Tự Đặc Biệt:** Đừng gõ trực tiếp lên thanh URL. Hãy bấm vào tab **Params** ngay dưới thanh URL của Postman. Ở cột *Key* gõ `q`, cột *Value* gõ tẹt ga tiếng Việt có dấu, dấu cách hoặc ký tự đặc biệt (VD: `Sách IT & Code`). Postman sẽ tự động bọc mã hóa (URL Encode) thành `S%C3%A1ch%20IT%20%26%20Code` để ném lên Server không bao giờ lỗi.
- **Kịch bản Test:** Chữ `Harry` viết thường hay hoa đều dò ra. Kết quả trả về rất "ngọt": Chỉ đơn giản là mảng `[{...}, {...}]` danh sách sách. Tuyệt đỉnh, giả sử bạn gõ đúng tên Tác Giả (ví dụ chữ "Nguyễn"), API sẽ lùng sục và trả về `{ "authorMatches": [{Nguyễn Nhật Ánh}, {Nguyễn Du}], "data": [...] }` để App Android có cớ gộp chung tất cả các sách của nhóm tác giả đó hiển thị thành một khối UI sang trọng.

### 11. Xem Tiểu sử Tác giả
- **Method & Route:** `GET http://localhost:3000/api/books/author/1`
- **Kịch bản Test:** Coi chừng nhập ID ko liên quan thì test phải văng ra JSON `[]` hoặc báo null là chuẩn.

### 12. List Sách theo 1 Tác giả 
- **Method & Route:** `GET http://localhost:3000/api/books/author/1/books`
- **Kịch bản Test:** Pass nếu ra `[]` với List sách 1 tác giả đó. 

### 13. List Sách của 1 Nhà Xb
- **Method & Route:** `GET http://localhost:3000/api/books/publisher/1/books`
- **Kịch bản Test:** Nhấn qua lại tự check logic tương tự API 12.

### 14. Đọc Comment Sách
- **Method & Route:** `GET http://localhost:3000/api/review/book/2` (Truyền ID cuốn sách vô cùng).
- **Kịch bản Test:** DB trống sẽ ra `[]`. Nếu ko trống ra list comment.

### 15. Push / Gửi bình luận [KÈM TOKEN TRONG HEADER]
- **Method & Route:** `POST http://localhost:3000/api/review`
- **Kịch bản Test:** Chuyển sang Header điền Bearer Token. Body raw JSON điền:
  ```json
  { "bookId": 2, "rating": 5, "comment": "Giao hàng bọc màn co kĩ cực kỳ!" }
  ```
  => Pass nếu ra status 201 Created. Quay lên gọi API số 14 xem dòng chữ có hiện lên ko.

### 16A. Tìm kiếm bằng Giọng Nói (Voice Search)
- **Method & Route:** `POST http://localhost:3000/api/ai/voice-search`
- **Cách Server Xử lý Nội bộ:** Sử dụng Middleware `diskUpload` để hứng file ghi tạm vào Ổ cứng. Sau đó server đọc nhị phân sang Base64 và **lập tức xóa bỏ File tạm** để chống đầy bộ nhớ rác. Đưa âm thanh đó kèm một câu *Prompt System* gởi lên Gemini 1.5 Flash. Gemini dịch thành chữ Text, Server lập tức lấy chữ đó truy vấn `ilike` tìm cấu trúc Tên Sách trong CSDL.
- **Kịch bản Test (Postman):** Chuyển tab Body sang `form-data`. Ở ô **KEY**, gõ chữ `audio`. Ở góc phải ô Key nhỏ bé có mũi tên, hãy chỉnh nó thành dạng **File** thay vì Text. Click Chọn File ghi âm (.mp3 / .m4a / .wav) từ máy lên. Bấm `Send`. 
- **Pass Dấu Hiệu:** Trả về `{ "recognizedText": "chữ nãy bạn nói", "results": [Sách lấy được] }`.

### 16B. Tìm kiếm bằng Hình Ảnh (Image Search)
- **Method & Route:** `POST http://localhost:3000/api/ai/image-search`
- **Cách Server Xử Lý Nội bộ:** Dùng Middleware `memoryUpload` để hứng File lơ lửng trên thanh RAM (Không lưu ổ cứng rườm rà). Chuyển thành Base64. Gửi ảnh lên cho Gemini bảo đọc Chữ (Tên sách) dính trên đó. Lấy được Tên Sách, Server sẽ cạo bỏ kí tự dơ (dấu ngoặc kép, Enter) rồi đem đi Dò tìm trong Bảng Book.
- **Kịch bản Test (Postman):** Cấu hình `form-data` i chang Voice. Ô **KEY** gõ `image` và chỉnh Type thành **File**. Nạp thẳng tấm ảnh bìa 1 cuốn truyện ở ngoài đời gởi lên nếm thử.

### 16C. Trợ lý Ảo RAG Tư vấn (Chatbot)
- **Method & Route:** `POST http://localhost:3000/api/ai/chatbot`
- **Cách Server Xử Lý (Bơm Kiến Thức DB):** Chatbot này Không chém gió bừa bãi! Khi nhận 1 `userMessage`, Server kích hoạt thao tác vô CSDL rút nóng Thông tin (Tên, Giá, Mô tả) của 5 cuốn sách ngẫu nhiên trong kho. Sau đó trộn (Inject) cục sách này vào bộ Não Prompt bảo AI *Chỉ được tư vấn dựa vào Đống sách này*. Do đó Bot này hiểu rất rành rọt sách nội bộ!
- **Tab Body (raw -> JSON):**
  ```json
  { "userMessage": "Mình đang chán đời thì xem cuốn gì cho bớt buồn tẻ?" }
  ```
- **Kịch bản Test (Pass):** Lấy về JSON có thẻ `{"reply": "Chào bạn! Mình có cuốn X..."}` mang âm điệu tự động tùy biến dựa trên ngữ cảnh cực kỳ khôn.

---

## III. Nhóm 3: Giỏ Hàng (CART)

### 17. Chìa khóa vào Giỏ
- **Method & Route:** `GET http://localhost:3000/api/cart?customerId=1`
- **Kịch bản Test:** Điền đại số 1 vào `customerId`. Hệ thống rớt 1 cái Object `{"cartID": 15, "items": [], "totalAmount": 0}` là Pass, chứng tỏ Backe-end tự đi chợ phát luôn xe đẩy (giỏ hàng null) cho member mới! Lưu ID của customer lại.

### 18. Thảy Sách vào giỏ
- **Method & Route:** `POST http://localhost:3000/api/cart/add`
- **Tab JSON:** `{"customerId": 1, "bookId": 2, "quantity": 1}`
- **Kịch bản Test Gài bẫy:** Bấm nút Send 3 Lần liên tiếp!! Sau đó gọi lại API số 17, nếu API 17 móc ra biến `quantity` của món đồ tăng thành 3 là qua ải Pass Logic! (Cộng dồn tự động).

### 19. Sửa số lượng của Cục Món Đồ
- **Method & Route:** `PUT http://localhost:3000/api/cart/item/99` (Số 99 là `cartItemId` lấy từ hàm 17 trả về!).
- **Tab JSON:** `{"quantity": 10}`
- **Kịch bản Test Gài Bẫy (Tắt 1 phần API 20):** Sửa Body json gửi lên là `{"quantity": 0}`. Nếu Terminal Server bấm xóa dứt điểm Món đồ đó khỏi Giỏ Hàng luôn thì ĐẬU (Logic gom chung giảm xuống 0 là Hủy).

### 20. Trực tiếp Xóa bỏ Cục Món Đồ
- **Method & Route:** `DELETE http://localhost:3000/api/cart/item/99`
- **Kịch bản Test:** Khỏi gửi JSON, nhấp Send mất đồ là OK!

---

## IV. Nhóm 4: Cá Nhân Hóa (PROFILE)

*(Lưu ý: Mấy phần GET này toàn truyền vào URL `?customerId=X` để Test nhé)*

### 21. Vạch trần Profile
- **Method & Route:** `GET http://localhost:3000/api/profile?customerId=1`
- **Kịch bản:** Sẽ ra Object có `fullName`, `email`, `phoneNumber`. 

### 22. Đổi Tên, SĐT
- **Method & Route:** `PUT http://localhost:3000/api/profile`
- **Tab JSON:** `{"customerId": 1, "fullName": "Test Đẹp Trai", "phoneNumber": "0987654321"}`
- **Kịch bản:** Send xong quay lại số 21, data đổi thật thì Passed.

### 23. Móc Túi Lấy Tệp Dữ Liệu Địa Chỉ
- **Method & Route:** `GET http://localhost:3000/api/profile/address?customerId=1`
- **Kịch bản:** Sẽ rơi ra array của nhà số 1, số 2.

### 24. Setup Nhà (Địa Chỉ)
- **Method & Route:** `POST http://localhost:3000/api/profile/address`
- **Tab JSON:** `{"customerId": 1, "receiverName": "Anh B", "addressString": "7A Quận 1"}`. Tạo xong nhớ Lưu cái ID Địa chỉ (`addressID` trả về).

### 25. Thay Đổi Số Nhà
- **Method & Route:** `PUT http://localhost:3000/api/profile/address/1` (Số 1 là ID lấy ở 24).
- **Tab JSON:** `{"receiverName": "Anh C", "addressString": "Dời qua Quận 2"}`

### 26. Giải tỏa Mặt bằng (Xóa Địa Chỉ)
- **Method & Route:** `DELETE http://localhost:3000/api/profile/address/1`
- **Kịch bản:** Quá dễ, mất nhà là Pass!

### 27. Đếm Tiền (Tệp dữ liệu PT Thanh toán)
- **Method & Route:** `GET http://localhost:3000/api/profile/payment?customerId=1`

### 28. Điền Form Lập Thẻ / Ví Momo
- **Method & Route:** `POST http://localhost:3000/api/profile/payment`
- **Tab JSON:** `{"customerId": 1, "paymentMethod": "Ví MoMo"}`

### 29. Khóa Thẻ
- **Method & Route:** `PUT http://localhost:3000/api/profile/payment/1`
- **Tab JSON:** `{"paymentMethod": "Ví MoMo", "status": "Vô Hiệu"}`

### 30. Tiêu Hủy Thẻ
- **Method & Route:** `DELETE http://localhost:3000/api/profile/payment/1`

---

## V. Nhóm 5: Checkout Simulation (VOUCHER/SHIP)

### 31. Tìm Khuyến Mãi Ngon (Vận hành List Voucher)
- **Method & Route:** `GET http://localhost:3000/api/checkout-data/vouchers`
- **Kịch bản Test Phức Tạp:** Mở DataBase của hệ thống (Supabase) kiếm cái Voucher nào trong mảng. Chỉnh `usageLimit` bằng `0`. Nhấn lại API nếu cái dòng Voucher Vừa rồi "Tàng Hình" (Mất tích) tức là DB tự che giấu hàng Hết Lượt Chuẩn! Phải trả về mãng trống hoặc các mã khác.

### 32. Danh Bạ Sứ Giả (List Phương Thức Ship)
- **Method & Route:** `GET http://localhost:3000/api/checkout-data/shipments`
- **Kịch bản:** Lấy list nhà Giao.

### 33. Áp Má Toán Học Xác Nhận Giá Final (TÍNH NHÁP ÁP MÃ)
- **Method & Route:** `POST http://localhost:3000/api/checkout-data/vouchers/validate`
- **Tab JSON:** Gửi giả 1 món hàng tổng trị giá `10 000` VND. Kìm theo 1 cái mả trong DB mà MinOrderValue là `50 000`.
  ```json
  {"voucherCode": "CODETEST_DB", "totalAmount": 10000}
  ```
- **Kịch bản Test Fail Văng Lỗi:** Nút gửi phải phán ngay mã Status 400 `{"isValid": false, "message": "Đơn hàng chưa đạt giá trị..."}` thì API Code này mới an toàn không bị Hack! Chỉnh lên totalAmount: `100 000` lại xem có Pass và xả Trả số tiền sau khi trừ chưa `finalAmount`!

---

## VI. Nhóm 6: Định Đoạt Số Phận Giỏ Hàng (ĐƠN HÀNG/ORDER)

### 34. Hóa Kiếp Thành Đơn (Checkout)
- **Method & Route:** `POST http://localhost:3000/api/order/checkout`
- **Tab JSON NẶNG:** 
  ```json
  {
      "customerId": 1,
      "addressId": 1,
      "paymentId": 1,
      "shipmentId": 1,
      "voucherId": 1 
  }
  ```
- **Kịch bản Chốt Yếu Test Thử:** Đã Gửi Thành Công Nhận Sang Chữ OK chưa. Bước này Phải ra Status 200 Láy cái OrderID! 
- **Lưu ý VNPay:** Nếu bạn chọn `paymentId` của VNPay, kết quả trả về sẽ có thêm trường `paymentUrl`. Hãy Copy link này dán trình duyệt để giả lập thanh toán thẻ.
- **Dọn dẹp:** GỬI TIẾP API 17 (Coi lại giỏ) => NẾU GIỎ HÀNG BỊ RESET Nhẵn THÍNH LÀ 1 QUY TRÌNH HOÀN THÀNH. Nếu trong API Gọi cái `UsageLimit` Voucher Trong DataBase cũng TRỪ 1 LÀ CHỨC NĂNG NGON.

### 35. Mua Nóng Tức Thì (Buy - Thẳng)
- **Method & Route:** `POST http://localhost:3000/api/order/buy-now`
- **Tab JSON NẶNG (Tương Tự + idbook + qty):** 
  ```json
  {
      "customerId": 1, "addressId": 1, "paymentId": 1, "shipmentId": 1,
      "voucherId": null,
      "bookId": 2, "quantity": 1
  }
  ```
- **Lưu ý VNPay:** Tương tự Checkout, nếu là phương thức VNPay sẽ trả về `paymentUrl`.
- **Kịch bản Test:** Ra OrderID mới, Gọi Lại Giỏ (17) xem Giỏ Vẫn Còn Đồ Cũ Chứ k hề Mất thì là đúng (Không dính chạm tới giỏ)!

### 36. Kho Quản Đơn
- **Method & Route:** `GET http://localhost:3000/api/order?customerId=1`
- **Kịch bản Option:** Chèn Thêm `&status=Chờ%20Thanh%20Toán` vào sau ID để test Query Hàng theo Tag lọc! (Chỗ này cần Url Encode dấu Space nha, hoặc xài Param Form của postman cho khỏe).

### 37. Zoom In Hóa Đơn Trọn Bộ
- **Method & Route:** `GET http://localhost:3000/api/order/105` (Số Orderid Hú ở api số 35, 36)
- **Kịch bản:** Màn hình trả về cái JSON khổng lồ của Gói Đơn Hàng từ Table JOIN.

### 38. Từ Bỏ Thế Gian - Hủy Đơn Gấp! (Cancel)
- **Method & Route:** `PUT http://localhost:3000/api/order/105/cancel` (Phải ghi là PUT Route nhé, vì có param Update vào DB)
- **Cơ chế Hoàn tiền (VNPay):** Nếu đơn hàng này từng thanh toán thành công qua VNPay (có mã `vnpTransactionNo`), Server sẽ tự động gọi lệnh **Refund** sang VNPay Sandbox trước khi đổi trạng thái đơn hàng. Bạn có thể check log Terminal để xem dòng chữ `[VNPay Refund] Đã thực hiện hoàn tiền...`.
- **Kịch bản Hack Database Fail:** Nếu Bạn Dùng Status là Đã Giao ở trong Bảng Table Supabase mà bấm Gọi Cái Hủy Đơn Của Đơn Đó, Lập tức Code Chửi Ngay Chỗ `400` không cho hủy. Nếu Đang `Chờ TT` Nhấn 1 Phát Qua Hủy Chơi được! Đồng thời Voucher Hồi Lại `Limit + 1`. Pass Tốt Nghiệp Chức Năng!

---

## VII. Nhóm 7: Thanh Toán & Lưu Thẻ VNPay (VNPAY SPECIAL)

### 39. Tạo Link Đăng Ký Lưu Thẻ (Tokenization)
- **Method & Route:** `POST http://localhost:3000/api/vnpay/create-token-url`
- **Tab Body (raw -> JSON):**
  ```json
  { "customerId": 1, "ipAddr": "127.0.0.1" }
  ```
- **Kịch bản Test:** Trả về `tokenUrl`. Dán vào trình duyệt, nhập thẻ Test của VNPay. Sau khi lưu thành công, hãy check bảng `Payment` trong Supabase xem có dòng mới chứa `vnpToken` và `maskedCardNumber` không.

### 40. Kiểm tra IPN (Mô phỏng VNPay xác nhận ngầm)
- **Method & Route:** `GET http://localhost:3000/api/vnpay/vnpay-ipn`
- **Params (Dán từ Query của VNPay hoặc gõ tay):**
  - `vnp_TxnRef`: ID đơn hàng đang "Chờ thanh toán".
  - `vnp_ResponseCode`: `00`
  - `vnp_Amount`: Số tiền đơn hàng đó x 100.
  - `vnp_TransactionNo`: Mã GD giả lập (vd: 12345).
- **Kịch bản Test:** Sau khi nhấn Send, nếu kết quả là `{"RspCode": "00", ...}`, hãy quay lại bảng `Order` xem status đã tự nhảy sang `Đang xử lý` chưa. Nếu rồi là hệ thống IPN đã thông suốt!


---

### 41. Xóa (Hủy) Đơn Hàng / Hoàn Tiền (Refund)
- **Method & Route:** POST http://localhost:3000/api/order/cancel/:orderId
- **Headers:** 
  - Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Kịch bản Test:** Nếu đơn hàng ở trạng thái "Chờ thanh toán" hoặc "Đang xử lý", đơn sẽ bị hủy. Nếu đơn hàng có mã giao dịch VNPay hoặc Stripe, hệ thống sẽ tự động gọi API hoàn tiền tương ứng và trả lại tiền về thẻ.

### 42. Lấy Danh Sách Thẻ VNPay Đã Liên Kết
- **Method & Route:** GET http://localhost:3000/api/vnpay/payment-methods
- **Headers:** 
  - Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Kịch bản Test:** Sau khi nhấn Send, bạn sẽ nhận được danh sách mảng JSON chứa các thẻ VNPay đã lưu của User.

### 43. Xóa Thẻ (Hủy Liên Kết)
- **Method & Route:** DELETE http://localhost:3000/api/vnpay/remove-card/:id
  - *(Thay :id bằng paymentID của thẻ lấy từ bước 42)*
- **Headers:** 
  - Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Kịch bản Test:** Bấm Send, thẻ sẽ bị chuyển sang trạng thái Đã xóa.

---

## NHÓM XI: THANH TOÁN QUỐC TẾ (STRIPE) - 4 LUỒNG CHÍNH

### Yêu cầu 1: Lưu thẻ (SetupIntent + Save Card)
**Bước 1: Lấy mã hiển thị UI (SetupIntent)**
- **Method & Route:** POST http://localhost:3000/api/stripe/create-setup-intent
- **Headers:** Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Body (JSON):** {"customerId": 99}
- **Kịch bản:** Trả về clientSecret. App Android dùng mã này mở giao diện nhập thẻ Stripe (Không trừ tiền).

**Bước 2: Ghi nhận thẻ vào Database**
- **Method & Route:** POST http://localhost:3000/api/stripe/save-card
- **Headers:** Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Body (JSON):** {"customerId": 99}
- **Kịch bản:** App Android gọi API này sau khi lưu thẻ thành công trên UI. Backend sẽ trích xuất thông tin thẻ và chèn vào bảng Payment.

### Yêu cầu 2: Thanh toán đơn hàng từ Giỏ Hàng
**Bước 1: Tạo Đơn hàng từ Giỏ hàng**
- **Method & Route:** POST http://localhost:3000/api/order/checkout
- **Kịch bản:** Gọi API này để chốt giỏ hàng, trả về orderId.

**Bước 2: Gọi Stripe để thanh toán**
- **Method & Route:** POST http://localhost:3000/api/stripe/create-payment-intent
- **Headers:** Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Body (JSON):** {"orderId": <mã_orderId_vừa_tạo>, "customerId": 99}
- **Kịch bản:** Backend tính toán số tiền và trả về clientSecret. App dùng mã này mở giao diện trừ tiền của Stripe.

### Yêu cầu 3: Thanh toán nhanh (Mua Ngay)
**Bước 1: Tạo Đơn hàng Tốc hành**
- **Method & Route:** POST http://localhost:3000/api/order/buy-now
- **Kịch bản:** Truyền bookId và quantity. API sẽ tạo thẳng một Order và trả về orderId.

**Bước 2: Gọi Stripe để thanh toán**
- **Method & Route:** POST http://localhost:3000/api/stripe/create-payment-intent
- **Body (JSON):** {"orderId": <mã_orderId_vừa_tạo_từ_buy_now>, "customerId": 99}
- **Kịch bản:** Giống hệt bước 2 của Yêu cầu 2. Backend tái sử dụng API này để thanh toán cho Đơn hàng Tốc hành.

### Yêu cầu 4: Hủy đơn hàng (Tự động Hoàn tiền)
- **Method & Route:** POST http://localhost:3000/api/order/cancel/<nhập_mã_orderId>
- **Headers:** Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Kịch bản Test:** Nếu đơn hàng được thanh toán bằng Stripe/VNPay, Backend tự động gửi lệnh Hoàn tiền (Refund) lên máy chủ tương ứng.

---

## NHÓM XII: THANH TOÁN VIETQR (PAYOS) - TỰ ĐỘNG HÓA CHUYỂN KHOẢN

### 46. Tạo Link Thanh Toán QR (PayOS)
- **Method & Route:** POST http://localhost:3000/api/payos/create-payment-link
- **Headers:** 
  - Authorization: Bearer <nhập_token_của_bạn_vào_đây>
- **Body (JSON):** 
  `json
  {
    "orderId": <nhập_mã_orderId_đã_tạo>
  }
  `
- **Kịch bản Test:** Trả về paymentUrl. Bạn dán link này vào trình duyệt để thấy mã QR PayOS.

### 47. Mô phỏng Thanh Toán Thành Công (Webhook)
- **Kịch bản Test:** 
  1. Sau khi mở link thanh toán ở bước 46, bạn sẽ thấy giao diện thanh toán của PayOS (Sandbox).
  2. Ở phía dưới cùng của trang web đó, nhấn vào nút **"Giả lập thanh toán thành công"**.
  3. Quay lại bảng Order trong Supabase kiểm tra xem trạng thái đơn hàng đã tự động chuyển sang Đang xử lý chưa. Nếu rồi là Webhook đã hoạt động tốt!
