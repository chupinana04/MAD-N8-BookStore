/**
 * =====================================================================
 * ORDER CONTROLLER - Chức năng: Thanh toán & Quản lý đơn hàng
 * =====================================================================
 * 
 * Mô tả: Xử lý toàn bộ logic nghiệp vụ liên quan đến đơn hàng:
 *   - Checkout từ giỏ hàng (chọn nhiều sản phẩm)
 *   - Mua ngay (Buy Now - 1 sản phẩm)
 *   - Xem lịch sử đơn hàng + Chi tiết đơn
 *   - Hủy đơn hàng (kèm hoàn tiền tự động qua Stripe/VNPay)
 *   - Thanh toán lại đơn chờ (Repay)
 * 
 * Quy trình trạng thái đơn hàng (Order Status Workflow):
 *   "Chờ thanh toán" → "Đang xử lý" → "Hoàn tất"
 *                    → "Đã hủy" (kèm hoàn tiền nếu đã thanh toán online)
 * 
 * Phương thức thanh toán được hỗ trợ:
 *   1. COD (Thanh toán khi nhận hàng) → Đơn vào "Đang xử lý" ngay
 *   2. Chuyển khoản ngân hàng (VietQR/PayOS) → Đơn "Chờ thanh toán"
 *   3. Thẻ quốc tế (Stripe) → Đơn "Chờ thanh toán"
 *   4. Thẻ nội địa (VNPay Token) → Đơn "Chờ thanh toán"
 * 
 * Bảng CSDL liên quan:
 *   - Order:     Đơn hàng chính (status, totalAmount, finalAmount, ...)
 *   - OrderItem: Chi tiết sản phẩm trong đơn (idBook, quantity)
 *   - Cart/CartItem: Giỏ hàng (bị xóa sau checkout)
 *   - Payment:   Phương thức thanh toán đã liên kết
 *   - Voucher:   Mã giảm giá (PERCENT hoặc FIXED)
 *   - Address:   Địa chỉ giao hàng
 *   - Shipment:  Phương thức vận chuyển
 *   - Book:      Thông tin sách (giá, ảnh)
 * 
 * API Endpoints (định nghĩa trong orderRoutes.js):
 *   POST /api/order/checkout       → checkout()
 *   POST /api/order/buy-now        → buyNow()
 *   GET  /api/order                → getOrderHistory()
 *   GET  /api/order/:orderId       → getOrderDetail()
 *   PUT  /api/order/:orderId/cancel→ cancelOrder()
 *   POST /api/order/repay          → repayOrder()
 * =====================================================================
 */

const supabase = require('../config/supabase');

// ===================== HẰNG SỐ TRẠNG THÁI ĐƠN HÀNG =====================
const PAYMENT_PENDING_STATUS = 'Chờ thanh toán';     // Đang chờ thanh toán online
const PAYMENT_PROCESSING_STATUS = 'Đang xử lý';      // Đã thanh toán, đang chuẩn bị hàng
const PAYMENT_COMPLETED_STATUS = 'Hoàn tất';           // Đã giao hàng thành công
const PAYMENT_CANCELLED_STATUS = 'Đã hủy';             // Đơn bị hủy

// ===================== HÀM TIỆN ÍCH: CHUẨN HÓA PHƯƠNG THỨC THANH TOÁN =====================

/**
 * Chuẩn hóa chuỗi Unicode Tiếng Việt: Bỏ dấu, uppercase, trim.
 * Dùng để so sánh phương thức thanh toán bất kể người dùng nhập hoa/thường/có dấu.
 * VD: "Thanh toán khi nhận hàng" → "THANH TOAN KHI NHAN HANG"
 */
const normalizeText = (value = '') =>
  value
    .toString()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')  // Loại bỏ dấu tiếng Việt
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .trim()
    .toUpperCase();

const normalizePaymentMethod = (paymentMethod = '') => normalizeText(paymentMethod);

/** Kiểm tra phương thức = COD (Cash on Delivery) */
const isCashOnDelivery = (payment) => normalizePaymentMethod(payment?.paymentMethod).includes('COD');

/** Kiểm tra phương thức = Chuyển khoản ngân hàng */
const isBankTransfer = (payment) => {
  const normalized = normalizePaymentMethod(payment?.paymentMethod);
  return normalized.includes('CHUYEN KHOAN') || normalized.includes('BANK TRANSFER');
};

/** Kiểm tra phương thức có được hỗ trợ hay không (COD, Bank, Stripe, VNPay) */
const isSupportedPaymentMethod = (payment) => {
  const normalized = normalizePaymentMethod(payment?.paymentMethod);
  return (
    isCashOnDelivery(payment) ||
    isBankTransfer(payment) ||
    normalized.includes('STRIPE') ||
    normalized.includes('VNPAY') ||
    !!payment?.vnpToken   // Có token thẻ đã lưu → hỗ trợ
  );
};

/**
 * Xác định trạng thái ban đầu của đơn hàng khi checkout:
 *   - COD → "Đang xử lý" (không cần chờ thanh toán, giao hàng thu tiền)
 *   - Các phương thức khác → "Chờ thanh toán" (đợi xác nhận từ cổng thanh toán)
 */
const resolveCheckoutStatus = (payment) => (
  isCashOnDelivery(payment) ? PAYMENT_PROCESSING_STATUS : PAYMENT_PENDING_STATUS
);

const resolveConfirmedPaymentStatus = () => PAYMENT_PROCESSING_STATUS;

/** Tạo thông báo phù hợp cho từng phương thức thanh toán */
const buildCheckoutMessage = (payment) => {
  if (isCashOnDelivery(payment)) {
    return 'Đặt hàng COD thành công';
  }

  if (isBankTransfer(payment)) {
    return 'Đơn hàng đã được tạo. Vui lòng quét mã VietQR để thanh toán';
  }

  return 'Đặt hàng thành công';
};

// ===================== HÀM TIỆN ÍCH: XỬ LÝ DỮ LIỆU ĐƠN HÀNG =====================

/**
 * Chuyển đổi danh sách OrderItem thành format gọn cho client.
 * Join thông tin sách (title, price, ảnh đầu tiên) từ quan hệ Book → BookImages.
 */
const mapOrderItems = (orderItems = []) =>
  orderItems.map((item) => {
    const images = item.Book?.BookImages || [];
    const primaryImage = images[0]?.imageURL || null;

    return {
      bookId: item.Book?.bookID || item.idBook || null,
      bookTitle: item.Book?.title || 'Sách',
      bookPrice: item.Book?.price || 0,
      quantity: item.quantity || 0,
      bookImage: primaryImage,
    };
  });

/** Tạo object tóm tắt đơn hàng (dùng cho danh sách lịch sử) */
const buildOrderSummary = (order) => ({
  orderID: order.orderID,
  orderDate: order.orderDate,
  totalAmount: order.totalAmount,
  finalAmount: order.finalAmount,
  status: order.status,
  items: mapOrderItems(order.OrderItem || []),
});

/** Tạo object chi tiết đơn hàng đầy đủ (join Address, Payment, Shipment, Voucher) */
const buildOrderDetail = (order) => ({
  orderID: order.orderID,
  orderDate: order.orderDate,
  totalAmount: order.totalAmount,
  finalAmount: order.finalAmount,
  status: order.status,
  address: order.Address || null,
  payment: order.Payment || null,
  shipment: order.Shipment || null,
  voucher: order.Voucher || null,
  items: mapOrderItems(order.OrderItem || []),
});

// ===================== HÀM NGHIỆP VỤ: VOUCHER =====================

/**
 * Áp dụng voucher giảm giá vào đơn hàng.
 * 
 * Hỗ trợ 2 loại voucher:
 *   - PERCENT: Giảm theo % (VD: 10% → discount = totalAmount * 10 / 100)
 *   - FIXED:   Giảm số tiền cố định (VD: 50000 VND)
 * 
 * Sau khi áp dụng, tự động giảm usageLimit của voucher đi 1.
 * finalAmount = max(0, totalAmount - discount)
 * 
 * @param {number|null} voucherId - ID voucher (null nếu không dùng)
 * @param {number} totalAmount - Tổng tiền trước giảm giá
 * @returns {{ finalAmount, usedVoucher }}
 */
const applyVoucher = async (voucherId, totalAmount) => {
  if (!voucherId) {
    return { finalAmount: totalAmount, usedVoucher: null };
  }

  const { data: voucher, error } = await supabase
    .from('Voucher')
    .select('*')
    .eq('voucherID', voucherId)
    .single();

  if (error) throw error;
  if (!voucher) return { finalAmount: totalAmount, usedVoucher: null };

  // Tính discount dựa trên loại voucher
  const discount = voucher.type === 'PERCENT'
    ? (totalAmount * Number(voucher.discountValue || 0)) / 100
    : Number(voucher.discountValue || 0);

  const finalAmount = Math.max(0, totalAmount - discount);

  // Giảm số lượt sử dụng còn lại của voucher
  if (voucher.usageLimit > 0) {
    await supabase
      .from('Voucher')
      .update({ usageLimit: voucher.usageLimit - 1 })
      .eq('voucherID', voucherId);
  }

  return { finalAmount, usedVoucher: voucher };
};

// ===================== HÀM NGHIỆP VỤ: GIỎ HÀNG =====================

/**
 * Lấy các sản phẩm đã chọn trong giỏ hàng để checkout.
 * 
 * Hỗ trợ 2 mode:
 *   - selectedCartItemIds = [] hoặc null → Lấy TẤT CẢ sản phẩm trong giỏ
 *   - selectedCartItemIds = [1,2,3]      → Chỉ lấy các sản phẩm được chọn
 * 
 * @param {number} customerId - ID khách hàng
 * @param {Array|null} selectedCartItemIds - Danh sách cartItemID được chọn
 * @returns {{ cart, cartItems }}
 */
const checkoutSelectedCartItems = async ({ customerId, selectedCartItemIds }) => {
  // Bước 1: Tìm giỏ hàng của khách hàng
  const { data: cart, error: cartError } = await supabase
    .from('Cart')
    .select('cartID')
    .eq('idCustomer', customerId)
    .maybeSingle();

  if (cartError) throw cartError;
  if (!cart) {
    return { cart: null, cartItems: [] };
  }

  // Bước 2: Lấy các CartItem (join Book để lấy giá)
  let query = supabase
    .from('CartItem')
    .select('cartItemID, idBook, quantity, Book(price)')
    .eq('idCart', cart.cartID);

  // Nếu có chỉ định cụ thể → chỉ lấy những item được chọn
  if (Array.isArray(selectedCartItemIds) && selectedCartItemIds.length > 0) {
    query = query.in('cartItemID', selectedCartItemIds);
  }

  const { data: cartItems, error: cartItemsError } = await query;
  if (cartItemsError) throw cartItemsError;

  return { cart, cartItems: cartItems || [] };
};

/** Tạo object payload để insert vào bảng Order */
const buildOrderPayload = ({
  customerId,
  addressId,
  paymentId,
  shipmentId,
  voucherId,
  totalAmount,
  finalAmount,
  status,
}) => ({
  orderDate: new Date(),
  totalAmount,
  finalAmount,
  status,
  idCustomer: customerId,
  idAddress: addressId,
  idPayment: paymentId,
  idShipment: shipmentId,
  idVoucher: voucherId || null,
});

/** Kiểm tra phương thức thanh toán có được hỗ trợ không. Trả false + response 400 nếu không. */
const ensureSupportedPayment = (payment, res) => {
  if (!isSupportedPaymentMethod(payment)) {
    res.status(400).json({
      message: 'Phương thức thanh toán này chưa được hỗ trợ hoàn tất trong ứng dụng',
    });
    return false;
  }

  return true;
};

// ===================== CONTROLLER CHÍNH =====================

const orderController = {
  /**
   * Checkout đơn hàng từ giỏ hàng.
   * 
   * Luồng xử lý chi tiết:
   *   1. Kiểm tra phương thức thanh toán có hợp lệ không
   *   2. Lấy các sản phẩm đã chọn trong giỏ hàng
   *   3. Tính tổng tiền (sum of quantity × price)
   *   4. Áp dụng voucher giảm giá (nếu có)
   *   5. Xác định trạng thái ban đầu (COD → "Đang xử lý", khác → "Chờ thanh toán")
   *   6. Insert đơn hàng mới vào bảng Order
   *   7. Insert chi tiết sản phẩm vào bảng OrderItem
   *   8. Xóa các CartItem đã checkout khỏi giỏ hàng
   *   9. Trả kết quả cho client (orderId, status, message)
   * 
   * @route POST /api/order/checkout
   * @body {number} customerId, addressId, paymentId, shipmentId
   * @body {number|null} voucherId
   * @body {Array|null} selectedCartItemIds
   */
  checkout: async (req, res) => {
    const {
      customerId,
      addressId,
      paymentId,
      shipmentId,
      voucherId,
      selectedCartItemIds,
    } = req.body;

    try {
      // Bước 1: Kiểm tra phương thức thanh toán
      const { data: payment, error: paymentError } = await supabase
        .from('Payment')
        .select('*')
        .eq('paymentID', paymentId)
        .single();

      if (paymentError) throw paymentError;
      if (!payment) {
        return res.status(404).json({ message: 'Không tìm thấy phương thức thanh toán' });
      }
      if (!ensureSupportedPayment(payment, res)) return;

      // Bước 2: Lấy sản phẩm từ giỏ hàng
      const { cart, cartItems } = await checkoutSelectedCartItems({ customerId, selectedCartItemIds });
      if (!cart || cartItems.length === 0) {
        return res.status(400).json({ message: 'Giỏ hàng trống hoặc chưa chọn sản phẩm để thanh toán' });
      }

      // Bước 3: Tính tổng tiền đơn hàng
      const totalAmount = cartItems.reduce(
        (sum, item) => sum + Number(item.quantity || 0) * Number(item.Book?.price || 0),
        0
      );

      // Bước 4: Áp dụng voucher giảm giá
      const { finalAmount } = await applyVoucher(voucherId, totalAmount);

      // Bước 5: Xác định trạng thái ban đầu
      const status = resolveCheckoutStatus(payment);

      // Bước 6: Insert đơn hàng mới
      const { data: newOrder, error: orderError } = await supabase
        .from('Order')
        .insert([
          buildOrderPayload({
            customerId,
            addressId,
            paymentId,
            shipmentId,
            voucherId,
            totalAmount,
            finalAmount,
            status,
          }),
        ])
        .select('orderID, status')
        .single();

      if (orderError) throw orderError;

      // Bước 7: Insert chi tiết sản phẩm (OrderItem)
      const { error: orderItemError } = await supabase.from('OrderItem').insert(
        cartItems.map((item) => ({
          idOrder: newOrder.orderID,
          idBook: item.idBook,
          quantity: item.quantity,
        }))
      );

      if (orderItemError) throw orderItemError;

      // Bước 8: Xóa các CartItem đã checkout khỏi giỏ hàng
      const { error: cartDeleteError } = await supabase
        .from('CartItem')
        .delete()
        .in('cartItemID', cartItems.map((item) => item.cartItemID));

      if (cartDeleteError) throw cartDeleteError;

      // Bước 9: Trả kết quả
      res.status(200).json({
        message: buildCheckoutMessage(payment),
        orderId: newOrder.orderID,
        orderID: newOrder.orderID,
        status,
        paymentUrl: null,
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Mua ngay 1 sản phẩm (không qua giỏ hàng).
   * 
   * Luồng tương tự checkout() nhưng đơn giản hơn:
   *   - Không cần lấy từ giỏ hàng
   *   - Chỉ 1 sản phẩm với quantity được chỉ định
   *   - Không xóa CartItem
   * 
   * @route POST /api/order/buy-now
   * @body {number} customerId, bookId, quantity, addressId, paymentId, shipmentId
   * @body {number|null} voucherId
   */
  buyNow: async (req, res) => {
    const { customerId, bookId, quantity, addressId, paymentId, shipmentId, voucherId } = req.body;

    try {
      // Lấy giá sách + kiểm tra phương thức thanh toán song song (Promise.all tối ưu tốc độ)
      const [bookResult, paymentResult] = await Promise.all([
        supabase.from('Book').select('price').eq('bookID', bookId).single(),
        supabase.from('Payment').select('*').eq('paymentID', paymentId).single(),
      ]);

      if (bookResult.error) throw bookResult.error;
      if (paymentResult.error) throw paymentResult.error;
      if (!bookResult.data) return res.status(404).json({ message: 'Không tìm thấy sách' });
      if (!paymentResult.data) return res.status(404).json({ message: 'Không tìm thấy phương thức thanh toán' });
      if (!ensureSupportedPayment(paymentResult.data, res)) return;

      // Tính tổng tiền và áp voucher
      const totalAmount = Number(bookResult.data.price || 0) * Number(quantity || 0);
      const { finalAmount } = await applyVoucher(voucherId, totalAmount);
      const status = resolveCheckoutStatus(paymentResult.data);

      // Insert đơn hàng
      const { data: newOrder, error: orderError } = await supabase
        .from('Order')
        .insert([
          buildOrderPayload({
            customerId,
            addressId,
            paymentId,
            shipmentId,
            voucherId,
            totalAmount,
            finalAmount,
            status,
          }),
        ])
        .select('orderID, status')
        .single();

      if (orderError) throw orderError;

      // Insert chi tiết đơn hàng (1 sản phẩm)
      const { error: orderItemError } = await supabase
        .from('OrderItem')
        .insert([{ idOrder: newOrder.orderID, idBook: bookId, quantity }]);

      if (orderItemError) throw orderItemError;

      res.status(200).json({
        message: buildCheckoutMessage(paymentResult.data),
        orderId: newOrder.orderID,
        orderID: newOrder.orderID,
        status,
        paymentUrl: null,
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Lấy lịch sử đơn hàng của khách hàng.
   * 
   * Hỗ trợ lọc theo trạng thái (status query param).
   * Join: OrderItem → Book → BookImages để hiển thị ảnh sản phẩm.
   * Sắp xếp theo ngày đặt hàng mới nhất lên đầu.
   * 
   * @route GET /api/order?customerId=X&status=Y
   */
  getOrderHistory: async (req, res) => {
    const { customerId, status } = req.query;

    try {
      let query = supabase
        .from('Order')
        .select('orderID, orderDate, totalAmount, finalAmount, status, OrderItem(quantity, idBook, Book(bookID, title, price, BookImages(imageURL)))')
        .eq('idCustomer', customerId)
        .order('orderDate', { ascending: false });

      // Lọc theo trạng thái nếu có
      if (status) query = query.eq('status', status);

      const { data: orders, error } = await query;
      if (error) throw error;

      res.status(200).json((orders || []).map(buildOrderSummary));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Lấy chi tiết đầy đủ 1 đơn hàng.
   * 
   * Join tất cả bảng liên quan: Address, Payment, Shipment, Voucher, OrderItem → Book → BookImages
   * 
   * @route GET /api/order/:orderId
   */
  getOrderDetail: async (req, res) => {
    const { orderId } = req.params;

    try {
      const { data: order, error } = await supabase
        .from('Order')
        .select(`
          orderID, orderDate, totalAmount, finalAmount, status,
          Address(receiverName, addressString),
          Payment(paymentID, paymentMethod, status, vnpToken),
          Shipment(shipmentID, shipmentMethod, estimatedDate),
          Voucher(voucherID, code, description, discountValue, type),
          OrderItem(quantity, idBook, Book(bookID, title, price, BookImages(imageURL)))
        `)
        .eq('orderID', orderId)
        .single();

      if (error || !order) {
        return res.status(404).json({ message: 'Không tìm thấy đơn hàng' });
      }

      res.status(200).json(buildOrderDetail(order));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Hủy đơn hàng.
   * 
   * Quy tắc nghiệp vụ:
   *   - Chỉ cho phép hủy khi status = "Chờ thanh toán" hoặc "Đang xử lý"
   *   - Tự động hoàn tiền nếu đã thanh toán online:
   *     + vnpResponseCode === 'STRIPE_00' → Hoàn qua Stripe Refunds API
   *     + Các trường hợp khác → Hoàn qua VNPay Refund API
   *   - Hoàn lại số lượt sử dụng voucher (usageLimit + 1) nếu đơn có dùng voucher
   * 
   * @route PUT /api/order/:orderId/cancel
   */
  cancelOrder: async (req, res) => {
    const { orderId } = req.params;

    try {
      // Lấy thông tin đơn hàng
      const { data: order, error: orderFetchError } = await supabase
        .from('Order')
        .select('*')
        .eq('orderID', orderId)
        .single();

      if (orderFetchError) throw orderFetchError;
      if (!order) return res.status(404).json({ message: 'Đơn hàng không tồn tại' });

      // Kiểm tra trạng thái: chỉ cho hủy khi đang chờ hoặc đang xử lý
      if (order.status !== PAYMENT_PENDING_STATUS && order.status !== PAYMENT_PROCESSING_STATUS) {
        return res.status(400).json({
          message: "Chỉ có thể hủy đơn hàng ở trạng thái 'Chờ thanh toán' hoặc 'Đang xử lý'",
        });
      }

      // Hoàn tiền tự động nếu đã thanh toán online
      if (order.vnpTransactionNo) {
        if (order.vnpResponseCode === 'STRIPE_00') {
          // Đơn thanh toán qua Stripe → Hoàn tiền qua Stripe Refunds API
          const stripeController = require('./stripeController');
          await stripeController.refundOrder(order.vnpTransactionNo);
        } else {
          // Đơn thanh toán qua VNPay → Hoàn tiền qua VNPay Refund API
          const vnpayController = require('./vnpayController');
          await vnpayController.refundOrder(order.orderID, order.finalAmount, order.vnpTransactionNo);
        }
      }

      // Cập nhật trạng thái đơn hàng thành "Đã hủy"
      const { data, error } = await supabase
        .from('Order')
        .update({ status: PAYMENT_CANCELLED_STATUS })
        .eq('orderID', orderId)
        .select()
        .single();

      if (error) throw error;

      // Hoàn lại số lượt sử dụng voucher (nếu đơn có dùng voucher)
      if (order.idVoucher) {
        const { data: voucher, error: voucherError } = await supabase
          .from('Voucher')
          .select('usageLimit')
          .eq('voucherID', order.idVoucher)
          .single();

        if (voucherError) throw voucherError;
        if (voucher) {
          await supabase
            .from('Voucher')
            .update({ usageLimit: Number(voucher.usageLimit || 0) + 1 })
            .eq('voucherID', order.idVoucher);
        }
      }

      res.status(200).json({ message: 'Đã hủy đơn hàng thành công', data });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Thanh toán lại cho đơn hàng đang chờ thanh toán.
   * 
   * Xử lý theo phương thức:
   *   - COD → Không cần thanh toán lại (báo lỗi)
   *   - Chuyển khoản → Trả flag requiresPayOS: true để App tạo link PayOS mới
   *   - Stripe/VNPay → Xử lý tương ứng (hoặc báo chưa hỗ trợ)
   * 
   * @route POST /api/order/repay
   * @body {number} orderId
   */
  repayOrder: async (req, res) => {
    const { orderId } = req.body;

    try {
      // Lấy đơn hàng kèm thông tin Payment
      const { data: order, error } = await supabase
        .from('Order')
        .select('*, Payment(*)')
        .eq('orderID', orderId)
        .single();

      if (error) throw error;
      if (!order) return res.status(404).json({ message: 'Không tìm thấy đơn hàng' });

      // COD không cần xác nhận thanh toán
      if (isCashOnDelivery(order.Payment)) {
        return res.status(400).json({ message: 'Đơn COD không cần xác nhận thanh toán' });
      }

      // Chuyển khoản → Trả flag để App Android gọi API tạo link PayOS
      if (isBankTransfer(order.Payment)) {
        return res.status(200).json({
          message: 'Vui lòng sử dụng mã VietQR để hoàn tất thanh toán',
          orderId: order.orderID,
          orderID: order.orderID,
          status: order.status,
          requiresPayOS: true, // Flag để App Android biết cần gọi API tạo link PayOS
          paymentUrl: null,
        });
      }

      // Chỉ cho phép thanh toán lại khi đơn đang "Chờ thanh toán"
      if (order.status !== PAYMENT_PENDING_STATUS) {
        return res.status(400).json({
          message: 'Chỉ đơn hàng ở trạng thái Chờ thanh toán mới có thể thực hiện thanh toán lại',
        });
      }

      // Các phương thức khác chưa hỗ trợ thanh toán lại tự động
      res.status(400).json({
        message: 'Phương thức thanh toán của đơn hàng này chưa được hỗ trợ thanh toán lại tự động',
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },
};

module.exports = orderController;
