# THIẾT KẾ UML: ĐÁNH GIÁ SẢN PHẨM, THANH TOÁN VÀ QUẢN LÝ ĐƠN HÀNG

Tài liệu này chứa thiết kế chi tiết về **Sơ đồ Use Case**, **Sơ đồ lớp (Class Diagram)** và **Sơ đồ tuần tự (Sequence Diagram)** cho 3 nhóm tính năng nâng cao của ứng dụng di động BookStore:
1. **Đánh giá sản phẩm (Product Review)**
2. **Thanh toán (Checkout & Payment)** - *Chỉ sử dụng COD, Stripe, PayOS (đã loại bỏ cổng VNPay)*
3. **Quản lý đơn hàng (Order Management)**

Các sơ đồ được vẽ bằng công cụ Mermaid Diagram trực quan và tuân thủ các quy tắc thiết kế hệ thống di động (MVVM Client, đối xứng stack kích hoạt, mối quan hệ UML chuẩn hóa).

---

## I. SƠ ĐỒ USE CASE CHI TIẾT (USE CASE DIAGRAM)

Sơ đồ này mô tả sự tương tác giữa Khách hàng, Cổng thanh toán ngoại và các chức năng của ứng dụng.

```mermaid
flowchart LR
    %% Actors
    User((Khách hàng))
    Stripe((Cổng Stripe))
    PayOS((Cổng PayOS))
    
    subgraph App["Hệ thống BookStore App"]
        %% Review Group
        UC_Review(["Xem & Viết Đánh giá"])
        UC_ViewReviews(["Xem danh sách đánh giá"])
        UC_PostReview(["Gửi đánh giá mới"])
        
        %% Payment Group
        UC_Checkout(["Thanh toán (Checkout)"])
        UC_PayCOD(["Thanh toán khi nhận hàng (COD)"])
        UC_PayStripe(["Thanh toán thẻ (Stripe)"])
        UC_PayPayOS(["Thanh toán chuyển khoản QR (PayOS)"])
        
        %% Order Group
        UC_OrderManage(["Quản lý đơn hàng"])
        UC_ViewOrders(["Xem danh sách đơn hàng"])
        UC_ViewOrderDetail(["Xem chi tiết đơn hàng"])
        UC_CancelOrder(["Hủy đơn hàng"])
    end

    %% Review Connections
    User --> UC_Review
    UC_Review -.->|&lt;&lt;include&gt;&gt;| UC_ViewReviews
    UC_Review -.->|&lt;&lt;extend&gt;&gt;| UC_PostReview

    %% Checkout Connections
    User --> UC_Checkout
    UC_PayCOD --> UC_Checkout
    UC_PayStripe --> UC_Checkout
    UC_PayPayOS --> UC_Checkout
    UC_PayStripe --> Stripe
    UC_PayPayOS --> PayOS

    %% Order Connections
    User --> UC_OrderManage
    UC_OrderManage -.->|&lt;&lt;include&gt;&gt;| UC_ViewOrders
    UC_OrderManage -.->|&lt;&lt;extend&gt;&gt;| UC_ViewOrderDetail
    UC_OrderManage -.->|&lt;&lt;extend&gt;&gt;| UC_CancelOrder
```

---

## II. SƠ ĐỒ LỚP CHI TIẾT (CLASS DIAGRAM)

Sơ đồ này biểu diễn cấu trúc các lớp di động theo mô hình MVVM (View - ViewModel - Model/API) và các mối quan hệ UML (`-->` Association, `..>` Dependency, `o--` Aggregation).

### 1. Nhóm Đánh giá sản phẩm (Product Review)
```mermaid
classDiagram
    class BookDetailScreen {
        -viewModel: BookDetailViewModel
        -navController: NavController
        +Content()
    }
    class WriteReviewScreen {
        -viewModel: BookDetailViewModel
        -navController: NavController
        +Content()
    }
    class BookDetailViewModel {
        -apiService: BookStoreApiService
        +reviewsList: StateFlow~List~ReviewItem~~
        +fetchReviews(bookId: Int)
        +submitReview(bookId: Int, rating: Int, comment: String)
    }
    class BookStoreApiService {
        <<interface>>
        +getReviews(bookId: Int) Response~List~ReviewItem~~
        +postReview(request: PostReviewRequest) Response~ReviewSubmissionResponse~
    }
    class ReviewItem {
        +reviewID: Int
        +customerName: String
        +rating: Int
        +comment: String
        +createdAt: String
    }
    class PostReviewRequest {
        +bookId: Int
        +rating: Int
        +comment: String
    }
    class ReviewSubmissionResponse {
        +success: Boolean
        +message: String
    }

    BookDetailScreen --> BookDetailViewModel : Association
    WriteReviewScreen --> BookDetailViewModel : Association
    BookDetailViewModel --> BookStoreApiService : Association
    BookDetailViewModel o-- ReviewItem : Aggregation (holds list)
    BookDetailViewModel ..> PostReviewRequest : Dependency
    BookDetailViewModel ..> ReviewSubmissionResponse : Dependency
```

### 2. Nhóm Thanh toán (Checkout & Payment)
```mermaid
classDiagram
    class CheckoutScreen {
        -viewModel: CheckoutViewModel
        -navController: NavController
        +Content()
    }
    class CheckoutViewModel {
        -apiService: BookStoreApiService
        +checkoutState: StateFlow~CheckoutState~
        +processCheckout(request: CheckoutRequest)
        +createStripePayment(amount: Double)
        +createPayOSLink(orderId: Int)
    }
    class BookStoreApiService {
        <<interface>>
        +checkout(request: CheckoutRequest) Response~OrderResponse~
        +createStripePaymentIntent(request: StripePaymentIntentRequest) Response~StripePaymentIntentResponse~
        +confirmStripePayment(request: ConfirmStripePaymentRequest) Response~ApiMessage~
        +createPayOSPaymentLink(request: PayOSPaymentRequest) Response~PayOSPaymentResponse~
    }
    class CheckoutRequest {
        +customerId: Int
        +addressId: Int
        +paymentMethodId: Int
        +voucherId: Int?
    }
    class OrderResponse {
        +orderId: Int
        +totalPrice: Double
        +status: String
    }
    class StripePaymentIntentRequest {
        +amount: Double
        +currency: String
    }
    class StripePaymentIntentResponse {
        +clientSecret: String
        +publishableKey: String
    }
    class ConfirmStripePaymentRequest {
        +paymentIntentId: String
        +orderId: Int
    }
    class PayOSPaymentRequest {
        +orderId: Int
        +amount: Double
        +description: String
    }
    class PayOSPaymentResponse {
        +checkoutUrl: String
        +paymentLinkId: String
    }
    class ApiMessage {
        +message: String
    }

    CheckoutScreen --> CheckoutViewModel : Association
    CheckoutViewModel --> BookStoreApiService : Association
    CheckoutViewModel ..> CheckoutRequest : Dependency
    CheckoutViewModel ..> OrderResponse : Dependency
    CheckoutViewModel ..> StripePaymentIntentRequest : Dependency
    CheckoutViewModel ..> StripePaymentIntentResponse : Dependency
    CheckoutViewModel ..> ConfirmStripePaymentRequest : Dependency
    CheckoutViewModel ..> PayOSPaymentRequest : Dependency
    CheckoutViewModel ..> PayOSPaymentResponse : Dependency
    BookStoreApiService ..> ApiMessage : Dependency
```

### 3. Nhóm Quản lý đơn hàng (Order Management)
```mermaid
classDiagram
    class OrderListScreen {
        -viewModel: OrderViewModel
        -navController: NavController
        +Content()
    }
    class OrderDetailScreen {
        -viewModel: OrderViewModel
        -navController: NavController
        +Content()
    }
    class OrderViewModel {
        -apiService: BookStoreApiService
        +ordersList: StateFlow~List~OrderItem~~
        +orderDetail: StateFlow~OrderDetailResponse~
        +fetchOrders(customerId: Int, status: String?)
        +fetchOrderDetail(orderId: Int)
        +cancelOrder(orderId: Int)
    }
    class BookStoreApiService {
        <<interface>>
        +getOrders(customerId: Int, status: String?) Response~List~OrderItem~~
        +getOrderDetail(orderId: Int) Response~OrderDetailResponse~
        +cancelOrder(orderId: Int) Response~ApiMessage~
    }
    class OrderItem {
        +orderId: Int
        +orderDate: String
        +totalAmount: Double
        +status: String
    }
    class OrderDetailResponse {
        +orderId: Int
        +receiverName: String
        +addressString: String
        +books: List~BookOrderItem~
        +shippingFee: Double
        +totalAmount: Double
        +status: String
    }
    class BookOrderItem {
        +bookId: Int
        +title: String
        +quantity: Int
        +price: Double
    }
    class ApiMessage {
        +message: String
    }

    OrderListScreen --> OrderViewModel : Association
    OrderDetailScreen --> OrderViewModel : Association
    OrderViewModel --> BookStoreApiService : Association
    OrderViewModel o-- OrderItem : Aggregation
    OrderViewModel ..> OrderDetailResponse : Dependency
    OrderDetailResponse o-- BookOrderItem : Aggregation
    BookStoreApiService ..> ApiMessage : Dependency
```

---

## III. SƠ ĐỒ TUẦN TỰ CHI TIẾT (SEQUENCE DIAGRAM)
*Vẽ theo mô hình tuyến tính (Happy Path) của Client-side để đảm bảo import mượt mà vào Visual Paradigm.*

### 1. Đánh giá sản phẩm (Xem reviews & gửi đánh giá mới thành công)
```mermaid
sequenceDiagram
    actor User as Khách hàng
    participant ViewD as BookDetailScreen
    participant ViewW as WriteReviewScreen
    participant VM as BookDetailViewModel
    participant Model as BookStoreApiService

    %% Luồng xem danh sách đánh giá
    User->>ViewD: Mở trang chi tiết sách
    activate ViewD
    ViewD->>VM: fetchReviews(bookId)
    activate VM
    VM->>Model: getReviews(bookId)
    activate Model
    Model-->>VM: Response(List(ReviewItem))
    deactivate Model
    VM-->>ViewD: Cập nhật reviewsList local
    ViewD-->User: Hiển thị các đánh giá và số sao trung bình
    deactivate ViewD

    %% Luồng viết đánh giá mới
    User->>ViewD: Bấm nút "Viết đánh giá"
    activate ViewD
    ViewD->>ViewW: Mở màn hình viết đánh giá
    deactivate ViewD
    
    activate ViewW
    User->>ViewW: Chọn 5 sao, nhập nội dung bình luận & Bấm "Gửi"
    ViewW->>VM: submitReview(bookId, 5, "Sách rất hay!")
    Note over VM: Khởi tạo PostReviewRequest(bookId, 5, comment)
    VM->>Model: postReview(PostReviewRequest)
    activate Model
    Model-->>VM: Response(ReviewSubmissionResponse) (success=true)
    deactivate Model
    VM->>VM: fetchReviews(bookId) (tải lại danh sách ngầm)
    VM-->>ViewW: Trả trạng thái thành công
    ViewW-->User: Hiển thị Toast thành công & quay về trang chi tiết
    deactivate ViewW
    deactivate VM
```

### 2. Thanh toán - Cổng thẻ tín dụng Stripe (Đặt đơn & Thanh toán thành công)
```mermaid
sequenceDiagram
    actor User as Khách hàng
    participant ViewC as CheckoutScreen
    participant VM as CheckoutViewModel
    participant Model as BookStoreApiService

    User->>ViewC: Điền thông tin giao hàng, Chọn Stripe & Click "Đặt hàng"
    activate ViewC
    ViewC->>VM: processCheckout(CheckoutRequest)
    activate VM
    VM->>Model: checkout(CheckoutRequest)
    activate Model
    Model-->>VM: Response(OrderResponse) (orderId, totalPrice, status="Chờ thanh toán")
    deactivate Model
    
    %% Luồng cổng thanh toán Stripe
    VM->>Model: createStripePaymentIntent(StripePaymentIntentRequest)
    activate Model
    Model-->>VM: Response(StripePaymentIntentResponse) (clientSecret)
    deactivate Model
    VM-->>ViewC: Cung cấp clientSecret cho Stripe SDK
    Note over ViewC: Stripe SDK hiển thị Card Input Form
    User->>ViewC: Nhập số thẻ Visa/Mastercard & Xác nhận thanh toán
    Note over ViewC: Stripe SDK thực hiện thanh toán trực tiếp
    
    ViewC->>VM: confirmStripePayment(paymentIntentId, orderId)
    VM->>Model: confirmStripePayment(ConfirmStripePaymentRequest)
    activate Model
    Model-->>VM: Response(ApiMessage) (message="Đã thanh toán")
    deactivate Model
    VM-->>ViewC: Cập nhật checkoutState = Success
    ViewC-->User: Chuyển hướng sang màn hình Đặt hàng thành công
    deactivate ViewC
    deactivate VM
```

### 3. Thanh toán - Cổng chuyển khoản QR PayOS (Đặt đơn & Lấy link QR thành công)
```mermaid
sequenceDiagram
    actor User as Khách hàng
    participant ViewC as CheckoutScreen
    participant VM as CheckoutViewModel
    participant Model as BookStoreApiService

    User->>ViewC: Chọn PayOS & Click "Thanh toán"
    activate ViewC
    ViewC->>VM: processCheckout(CheckoutRequest)
    activate VM
    VM->>Model: checkout(CheckoutRequest)
    activate Model
    Model-->>VM: Response(OrderResponse) (orderId, totalPrice, status="Chờ thanh toán")
    deactivate Model
    
    %% Luồng PayOS
    VM->>Model: createPayOSPaymentLink(PayOSPaymentRequest)
    activate Model
    Model-->>VM: Response(PayOSPaymentResponse) (checkoutUrl)
    deactivate Model
    VM-->>ViewC: Trả về checkoutUrl
    ViewC-->User: Mở trình duyệt/WebView hiển thị mã QR PayOS để quét chuyển khoản
    deactivate ViewC
    deactivate VM
```

### 4. Quản lý đơn hàng (Xem danh sách, xem chi tiết và hủy đơn thành công)
```mermaid
sequenceDiagram
    actor User as Khách hàng
    participant ViewL as OrderListScreen
    participant ViewD as OrderDetailScreen
    participant VM as OrderViewModel
    participant Model as BookStoreApiService

    %% Xem danh sách
    User->>ViewL: Chọn tab "Đơn hàng"
    activate ViewL
    ViewL->>VM: fetchOrders(customerId, status=null)
    activate VM
    VM->>Model: getOrders(customerId, null)
    activate Model
    Model-->>VM: Response(List(OrderItem))
    deactivate Model
    VM-->>ViewL: Cập nhật list local
    ViewL-->User: Hiển thị danh sách các đơn hàng
    deactivate ViewL

    %% Xem chi tiết
    User->>ViewL: Nhấp chọn một đơn hàng cụ thể
    activate ViewL
    ViewL->>ViewD: Mở màn hình chi tiết đơn hàng
    deactivate ViewL
    
    activate ViewD
    ViewD->>VM: fetchOrderDetail(orderId)
    VM->>Model: getOrderDetail(orderId)
    activate Model
    Model-->>VM: Response(OrderDetailResponse)
    deactivate Model
    VM-->>ViewD: Cập nhật orderDetail State
    ViewD-->User: Hiển thị thông tin vận chuyển, sách mua, tổng tiền

    %% Hủy đơn hàng (Trạng thái chờ xử lý)
    User->>ViewD: Click nút "Hủy đơn hàng" & Xác nhận
    ViewD->>VM: cancelOrder(orderId)
    VM->>Model: cancelOrder(orderId)
    activate Model
    Model-->>VM: Response(ApiMessage)
    deactivate Model
    Note over VM: Cập nhật trạng thái đơn hàng cục bộ thành "Đã hủy"
    VM-->>ViewD: Cập nhật State thành công
    ViewD-->User: Cập nhật trạng thái hiển thị thành "Đã hủy" & ẩn nút Hủy
    deactivate ViewD
    deactivate VM
```
