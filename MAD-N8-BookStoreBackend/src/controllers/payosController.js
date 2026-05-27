/**
 * =====================================================================
 * PAYOS CONTROLLER - Chức năng: Thanh toán chuyển khoản VietQR
 * =====================================================================
 * 
 * Mô tả: Tích hợp cổng PayOS để tạo mã QR động cho phép chuyển khoản
 * nhanh chóng tới tài khoản ngân hàng của cửa hàng.
 * 
 * Luồng hoạt động:
 *   1. Tạo link thanh toán bằng PayOS SDK. Truyền `orderCode` đặc biệt
 *      để tránh trùng lặp khi một đơn được tạo link nhiều lần.
 *   2. Trả về `checkoutUrl` để Frontend Android hiển thị qua WebView.
 *   3. Khi user quét mã và thanh toán, PayOS gọi Webhook về server.
 *   4. Server cập nhật đơn hàng thành "Đang xử lý".
 * 
 * API gọi ngoài:
 *   - `@payos/node` (Thư viện PayOS cho Node.js)
 * =====================================================================
 */

const { PayOS } = require('@payos/node');
const supabase = require('../config/supabase');

// Khởi tạo đối tượng PayOS với thông tin từ biến môi trường
const payOS = new PayOS({
  clientId: process.env.PAYOS_CLIENT_ID,
  apiKey: process.env.PAYOS_API_KEY,
  checksumKey: process.env.PAYOS_CHECKSUM_KEY
});

const payosController = {
  /**
   * 1. Tạo Link thanh toán (QR Code)
   * Tạo Payment Link chứa mã VietQR tương ứng với đơn hàng.
   * 
   * @route POST /api/payos/create-payment-link
   */
  createPaymentLink: async (req, res) => {
    const { orderId } = req.body;

    try {
      // 1. Lấy thông tin số tiền và trạng thái của đơn hàng từ CSDL
      const { data: order, error: orderError } = await supabase
        .from('Order')
        .select('orderID, finalAmount, status')
        .eq('orderID', orderId)
        .single();

      if (orderError || !order) {
        return res.status(404).json({ message: 'Không tìm thấy đơn hàng' });
      }

      if (order.status !== 'Chờ thanh toán') {
        return res.status(400).json({ message: 'Đơn hàng không ở trạng thái Chờ thanh toán' });
      }

      // MẸO: Tránh lỗi "231 - Payment request already exists" của PayOS
      // PayOS không cho tạo nhiều link cho cùng 1 orderCode.
      // Giải pháp: Thêm "salt" (3 chữ số cuối của timestamp) vào orderId
      // VD: Đơn #38 thanh toán lần 1 là 38001, lần 2 là 38055...
      const salt = Math.floor(Date.now() / 1000) % 1000;
      const orderCode = Number(order.orderID) * 1000 + salt;
      
      const amount = Math.round((order.finalAmount || 0) * 100000); 
      
      console.log(`[PayOS] Khởi tạo thanh toán đơn #${orderId} (Code: ${orderCode}), Số tiền: ${amount} VND`);

      if (amount <= 0) {
        return res.status(400).json({ message: 'Số tiền thanh toán không hợp lệ (bằng 0)' });
      }
      
      // Payload cấu hình tạo payment link
      const paymentData = {
        orderCode: orderCode,
        amount: amount,
        description: `Thanh toan don hang ${orderId}`,
        returnUrl: `http://localhost:3000/api/payos/success`, // Trả về nếu thanh toán ok
        cancelUrl: `http://localhost:3000/api/payos/cancel`,  // Trả về nếu user hủy giao dịch
      };

      try {
        // Gọi PayOS SDK để tạo link
        const paymentLinkData = await payOS.paymentRequests.create(paymentData);
        res.status(200).json({
          message: 'Tạo link thanh toán PayOS thành công',
          paymentUrl: paymentLinkData.checkoutUrl,
          orderId: order.orderID,
        });
      } catch (error) {
        // Xử lý Lỗi 231 (nếu salt bị trùng ngẫu nhiên hoặc logic lỗi)
        if (error.code === '231' || (error.message && error.message.includes('already exists'))) {
          console.log(`[PayOS] Link cho đơn #${orderId} đã tồn tại, đang lấy lại thông tin...`);
          
          try {
            const existingLink = await payOS.paymentRequests.get(orderCode);
            
            // Tái sử dụng link cũ nếu nó vẫn trong trạng thái chờ
            if (existingLink.status === 'PENDING') {
              return res.status(200).json({
                message: 'Lấy lại link thanh toán đang chờ',
                paymentUrl: existingLink.checkoutUrl,
                orderId: order.orderID,
              });
            }
            
            return res.status(400).json({
              message: `Đơn hàng này đã có giao dịch trên PayOS với trạng thái: ${existingLink.status}. Không thể tạo link mới cho cùng một mã đơn hàng.`,
              status: existingLink.status
            });
          } catch (getError) {
            throw getError;
          }
        }
        throw error;
      }
    } catch (error) {
      console.error('PayOS Create Link Error:', error);
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * 2. Webhook nhận thông báo từ PayOS
   * Khi khách hàng quét VietQR và ngân hàng xác nhận có tiền vào tài khoản, 
   * hệ thống PayOS sẽ gọi vào API này (POST).
   * 
   * @route POST /api/payos/webhook
   */
  payosWebhook: async (req, res) => {
    const body = req.body;
    console.log('[PayOS Webhook] Received:', body);

    try {
      // SDK tự động xác minh tính hợp lệ (chữ ký) của dữ liệu Webhook
      const webhookData = await payOS.webhooks.verify(body);
      
      // Nếu có mô tả 'success' -> khách hàng đã thanh toán thành công
      if (webhookData.description === 'success' || body.success === true) {
        // Tách lại orderId từ orderCode (đã ghép salt trước đó bằng orderId * 1000)
        const orderId = Math.floor(webhookData.orderCode / 1000);
        console.log(`[PayOS Webhook] Xác nhận thanh toán cho đơn hàng gốc: ${orderId}`);

        // Cập nhật CSDL: chuyển sang "Đang xử lý" và lưu mã giao dịch
        const { error } = await supabase
          .from('Order')
          .update({ 
            status: 'Đang xử lý',
            vnpTransactionNo: webhookData.paymentLinkId,
            vnpResponseCode: 'PAYOS_SUCCESS'
          })
          .eq('orderID', orderId);

        if (error) throw error;
        console.log(`[PayOS] Đơn hàng ${orderId} đã thanh toán thành công tự động.`);
      }

      return res.status(200).json({ message: 'Webhook processed' });
    } catch (error) {
      console.error('PayOS Webhook Error:', error);
      // Vẫn phải trả về 200 OK để PayOS ngừng spam gửi lại
      return res.status(200).send('Webhook Error');
    }
  },

  /**
   * 3. Callback thành công (Redirect)
   * Endpoint trung gian để chuyển hướng từ PayOS về. WebView Android
   * sẽ lắng nghe URL này để bắt sự kiện thành công và tự đóng.
   */
  successRedirect: (req, res) => {
    res.send('<h1>Thanh toán thành công!</h1><p>Bạn có thể quay lại ứng dụng.</p>');
  },

  /**
   * 4. Callback hủy (Redirect)
   * Endpoint trung gian nếu user bấm Hủy trên trang PayOS.
   */
  cancelRedirect: (req, res) => {
    res.send('<h1>Thanh toán đã bị hủy.</h1>');
  }
};

module.exports = payosController;
