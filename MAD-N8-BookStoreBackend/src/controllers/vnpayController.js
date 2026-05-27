/**
 * =====================================================================
 * VNPAY CONTROLLER - Chức năng: Thanh toán thẻ nội địa (ATM/VNPay)
 * =====================================================================
 * 
 * Mô tả: Xử lý thanh toán qua cổng VNPay, bao gồm:
 *   - Thanh toán đơn hàng thông thường (redirect sang trang VNPay).
 *   - Lưu thẻ (Tokenization) để thanh toán 1-click (Frictionless / 3DS).
 *   - Xử lý Webhook (IPN - Server-to-Server) và Return URL (Client-to-Server).
 *   - Hoàn tiền (Refund) khi hủy đơn.
 * 
 * API gọi ngoài:
 *   - VNPay API (Tạo URL vpcpay.html, token_ui/create-token.html)
 *   - Thư viện `vnpay` (Node.js) hỗ trợ refund.
 *   - Thuật toán mã hóa HMAC-SHA512 để ký tham số (bảo mật dữ liệu).
 * 
 * =====================================================================
 */

const crypto = require('crypto');
const supabase = require('../config/supabase');
const qs = require('qs');

const vnpayController = {
  // ===================== HÀM HỖ TRỢ KÝ CHỮ KÝ (HMAC-SHA512) =====================
  /**
   * Sắp xếp các tham số theo thứ tự từ điển (A-Z) để đảm bảo chuỗi ký (hash)
   * luôn đồng nhất giữa Client và Server VNPay.
   */
  sortObject: (obj) => {
    let sorted = {};
    let str = [];
    let key;
    for (key in obj){
      if (obj.hasOwnProperty(key)) {
        str.push(encodeURIComponent(key));
      }
    }
    str.sort();
    for (key = 0; key < str.length; key++) {
        sorted[str[key]] = encodeURIComponent(obj[str[key]]).replace(/%20/g, "+");
    }
    return sorted;
  },

  /**
   * Tạo chữ ký bảo mật HMAC-SHA512 từ chuỗi tham số đã sắp xếp.
   * Chữ ký này giúp VNPay xác thực request là từ hệ thống của mình.
   */
  signParams: (sortedParams) => {
    const secret = (process.env.VNP_HASHSECRET || '').trim();
    const signData = qs.stringify(sortedParams, { encode: false });
    const hmac = crypto.createHmac('sha512', secret);
    return hmac.update(Buffer.from(signData, 'utf-8')).digest('hex');
  },

  // ===================== CHỨC NĂNG THANH TOÁN =====================
  /**
   * 1. Tạo URL thanh toán (Dùng cho Checkout/BuyNow)
   * Tạo đường dẫn chứa các tham số đơn hàng (chuẩn CamelCase) để redirect 
   * khách hàng sang cổng thanh toán của VNPay (vpcpay.html).
   */
  createUrl: async (orderId, amount, ipAddr, orderInfo = 'Thanh toan don hang Bookstore') => {
    const tmnCode = (process.env.VNP_TMNCODE || '').trim();
    const returnUrl = (process.env.VNP_RETURNURL || '').trim();
    const date = new Date();
    // Format thời gian: YYYYMMDDHHmmss
    const createDate = date.getFullYear().toString() +
                       (date.getMonth() + 1).toString().padStart(2, '0') +
                       date.getDate().toString().padStart(2, '0') +
                       date.getHours().toString().padStart(2, '0') +
                       date.getMinutes().toString().padStart(2, '0') +
                       date.getSeconds().toString().padStart(2, '0');

    // Các tham số bắt buộc theo tài liệu VNPay
    const params = {
        vnp_Version: '2.1.0',
        vnp_Command: 'pay',
        vnp_TmnCode: tmnCode,
        vnp_Amount: amount * 100, // Đơn vị tiền tệ VNPay là Hào (nhân 100)
        vnp_CreateDate: createDate,
        vnp_CurrCode: 'VND',
        vnp_IpAddr: ipAddr || '127.0.0.1',
        vnp_Locale: 'vn',
        vnp_OrderInfo: orderInfo.replace(/\s/g, ''),
        vnp_OrderType: 'billpayment',
        vnp_ReturnUrl: returnUrl,
        vnp_TxnRef: orderId.toString(),
    };

    let sortedParams = vnpayController.sortObject(params);
    const secureHash = vnpayController.signParams(sortedParams);
    sortedParams['vnp_SecureHash'] = secureHash; // Đính kèm chữ ký

    const baseUrl = process.env.VNP_URL;
    return baseUrl + '?' + qs.stringify(sortedParams, { encode: false });
  },

  /**
   * 2. Xử lý Return URL
   * Giao diện WebView (App) hoặc Browser sẽ điều hướng về URL này sau khi thanh toán.
   * Xử lý 2 trường hợp: 
   *  - Lưu thẻ (Tokenization)
   *  - Thanh toán đơn hàng thông thường.
   * 
   * @route GET /api/vnpay/vnpay-return
   */
  vnpayReturn: async (req, res) => {
    try {
      let vnp_Params = req.query;
      let secureHash = vnp_Params['vnp_SecureHash'];

      delete vnp_Params['vnp_SecureHash'];
      delete vnp_Params['vnp_SecureHashType'];

      vnp_Params = vnpayController.sortObject(vnp_Params);
      const checkHash = vnpayController.signParams(vnp_Params);

      // Xác minh tính toàn vẹn của dữ liệu (checksum)
      if (secureHash && checkHash && secureHash.toLowerCase() === checkHash.toLowerCase()) {
        const responseCode = vnp_Params['vnp_ResponseCode'];
        const token = vnp_Params['vnp_Token'];

        if (responseCode === '00') {
           
           // --- TRƯỜNG HỢP 1: TRẢ VỀ TỪ LUỒNG LƯU THẺ (TOKENIZATION) ---
           if (token) {
              const appUserId = vnp_Params['vnp_AppUserId'] || vnp_Params['vnp_app_user_id'];
              const cardType = vnp_Params['vnp_card_type'] || vnp_Params['vnp_CardType'] || 'ATM';
              const maskedCard = vnp_Params['vnp_CardNumber'] || vnp_Params['vnp_card_number'] || '****';

              // Kiểm tra xem thẻ đã tồn tại chưa
              const { data: existing } = await supabase.from('Payment').select('id').eq('vnpToken', token).single();
              
              if (!existing && appUserId) {
                // Lưu token thẻ vào CSDL Supabase
                await supabase.from('Payment').insert([{
                    idCustomer: appUserId,
                    paymentMethod: `Thẻ VNPay (${cardType})`,
                    vnpToken: token,
                    maskedCardNumber: maskedCard,
                    vnpCardType: cardType,
                    status: 'Hoạt động'
                }]);
                console.log(`[VNPay] Đã lưu thẻ thành công từ Return URL cho Khách hàng #${appUserId}`);
              }

              // Trả về HTML giao diện thành công cho WebView
              return res.status(200).send(`
               <html>
                 <head>
                   <title>Thành công</title>
                   <meta name="viewport" content="width=device-width, initial-scale=1">
                 </head>
                 <body style="text-align:center; padding-top:100px; font-family:sans-serif; background:#f4f7f6;">
                   <div style="background:white; max-width:400px; margin:0 auto; padding:30px; border-radius:15px; box-shadow:0 10px 25px rgba(0,0,0,0.1);">
                    <h1 style="color:#2ecc71; font-size:60px; margin:0;">💳</h1>
                    <h2 style="color:#2c3e50;">Liên kết thẻ thành công!</h2>
                    <p style="color:#7f8c8d;">Thẻ của bạn đã được lưu an toàn vào hệ thống Bookstore.</p>
                    <hr style="border:0; border-top:1px solid #eee; margin:20px 0;">
                    <p style="font-weight:bold; color:#34495e;">Vui lòng quay lại ứng dụng để tiếp tục.</p>
                   </div>
                 </body>
               </html>
             `);
           }

           // --- TRƯỜNG HỢP 2: THANH TOÁN ĐƠN HÀNG ---
           const orderId = vnp_Params['vnp_TxnRef'] || vnp_Params['vnp_txn_ref'];
           if (orderId) {
             const { data: order } = await supabase
               .from('Order')
               .select('orderID, status')
               .eq('orderID', orderId)
               .single();

             if (order && order.status === 'Chờ thanh toán') {
               // Cập nhật trạng thái đơn hàng thành "Hoàn tất"
               await supabase
                 .from('Order')
                 .update({
                   status: 'Hoàn tất',
                   vnpTransactionNo: vnp_Params['vnp_TransactionNo'] || vnp_Params['vnp_transaction_no'],
                   vnpResponseCode: vnp_Params['vnp_ResponseCode'] || vnp_Params['vnp_response_code'],
                 })
                 .eq('orderID', orderId);
             }
           }

           return res.status(200).send(`
             <html>
               <body style="text-align:center; padding-top:100px; font-family:sans-serif;">
                 <h1 style="color:#2ecc71;">✅ Giao dịch thành công!</h1>
                 <p>Mã đơn hàng: ${vnp_Params['vnp_TxnRef'] || vnp_Params['vnp_txn_ref']}</p>
                 <p>Bạn có thể quay lại ứng dụng.</p>
               </body>
             </html>
           `);
        }
      }
      res.status(200).send(`<html><body style="text-align:center; padding-top:50px;"><h1>❌ Giao dịch thất bại</h1><p>Mã lỗi: ${vnp_Params['vnp_ResponseCode'] || vnp_Params['vnp_response_code']}</p></body></html>`);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * 3. Xử lý IPN (Instant Payment Notification)
   * Hệ thống VNPay gọi ngầm API này (Server-to-Server) để báo kết quả thanh toán.
   * (Quan trọng: IPN đảm bảo tính chính xác cao hơn Return URL).
   */
  vnpayIpn: async (req, res) => {
    try {
      const vnp_Params = req.query;
      const secureHash = vnp_Params['vnp_SecureHash'] || vnp_Params['vnp_secure_hash'];
      const checkHash = vnpayController.signParams(vnp_Params);

      if (!secureHash || !checkHash || secureHash.toLowerCase() !== checkHash.toLowerCase()) {
        return res.status(200).json({ RspCode: '97', Message: 'Checksum failed' });
      }

      const responseCode = vnp_Params['vnp_ResponseCode'] || vnp_Params['vnp_response_code'];
      const token = vnp_Params['vnp_Token'] || vnp_Params['vnp_token'];
      const appUserId = vnp_Params['vnp_AppUserId'] || vnp_Params['vnp_app_user_id'];

      // Lưu thẻ từ IPN
      if (token && appUserId) {
        if (responseCode === '00') {
           await supabase.from('Payment').insert([{
              idCustomer: appUserId,
              paymentMethod: `Thẻ VNPay (${vnp_Params['vnp_card_type'] || vnp_Params['vnp_CardType'] || 'ATM'})`,
              vnpToken: token,
              maskedCardNumber: vnp_Params['vnp_CardNumber'] || vnp_Params['vnp_card_number'] || '****',
              vnpCardType: vnp_Params['vnp_CardType'] || vnp_Params['vnp_card_type'] || 'ATM',
              status: 'Hoạt động'
           }]);
        }
        return res.status(200).json({ RspCode: '00', Message: 'Token confirmed' });
      }

      // Xử lý đơn hàng từ IPN
      const orderId = vnp_Params['vnp_TxnRef'] || vnp_Params['vnp_txn_ref'];
      const { data: order } = await supabase.from('Order').select('*').eq('orderID', orderId).single();
      
      if (!order) return res.status(200).json({ RspCode: '01', Message: 'Order not found' });
      if (order.status !== 'Chờ thanh toán') return res.status(200).json({ RspCode: '02', Message: 'Order already confirmed' });

      await supabase.from('Order').update({ 
        status: responseCode === '00' ? 'Hoàn tất' : 'Đã hủy',
        vnpTransactionNo: vnp_Params['vnp_TransactionNo'] || vnp_Params['vnp_transaction_no'],
        vnpResponseCode: responseCode
      }).eq('orderID', orderId);

      res.status(200).json({ RspCode: '00', Message: 'Confirm success' });
    } catch (error) {
      res.status(200).json({ RspCode: '99', Message: 'Unknown error' });
    }
  },

  /**
   * 4. Hoàn tiền (Refund)
   * Sử dụng thư viện `vnpay` Node.js để gửi yêu cầu hoàn tiền.
   */
  refundOrder: async (orderId, amount, transactionNo, createBy = 'System') => {
     const { VNPay } = require('vnpay');
     const vnpayLib = new VNPay({
        tmnCode: (process.env.VNP_TMNCODE || '').trim(),
        secureSecret: (process.env.VNP_HASHSECRET || '').trim(),
        vnpayHost: (process.env.VNP_URL || '').trim(),
        testMode: true,
     });
     return vnpayLib.refund({
        vnp_TxnRef: orderId.toString(),
        vnp_Amount: amount,
        vnp_TransactionNo: transactionNo,
        vnp_TransactionDate: new Date().toISOString(),
        vnp_CreateBy: createBy,
        vnp_TransactionType: '02', // 02: Hoàn trả toàn phần
     });
  },

  // ===================== CHỨC NĂNG TOKENIZATION (LƯU THẺ) =====================
  /**
   * 5. Tạo URL liên kết thẻ
   * Chú ý: Các API liên quan đến thẻ của VNPay sử dụng `snake_case` (vnp_version)
   * thay vì CamelCase (vnp_Version) như thanh toán thông thường.
   */
  createTokenUrl: async (customerId, returnUrl, ipAddr, cardType = '01') => {
    const tmnCode = (process.env.VNP_TMNCODE || '').trim();
    const date = new Date();
    const createDate = date.getFullYear().toString() +
                       (date.getMonth() + 1).toString().padStart(2, '0') +
                       date.getDate().toString().padStart(2, '0') +
                       date.getHours().toString().padStart(2, '0') +
                       date.getMinutes().toString().padStart(2, '0') +
                       date.getSeconds().toString().padStart(2, '0');

    const params = {
        vnp_version: '2.0.1',
        vnp_command: 'token_create',
        vnp_tmn_code: tmnCode,
        vnp_app_user_id: customerId.toString(),
        vnp_txn_ref: `TK${customerId}X${Math.floor(Date.now() / 1000).toString().slice(-6)}`, 
        vnp_txn_desc: `LuuTheBK${customerId}`, 
        vnp_card_type: cardType, 
        vnp_return_url: returnUrl,
        vnp_ip_addr: ipAddr || '127.0.0.1',
        vnp_create_date: createDate,
        vnp_locale: 'vn',
    };

    let sortedParams = vnpayController.sortObject(params);
    const secureHash = vnpayController.signParams(sortedParams);
    sortedParams['vnp_secure_hash'] = secureHash;

    const baseUrl = 'https://sandbox.vnpayment.vn/token_ui/create-token.html';
    return baseUrl + '?' + qs.stringify(sortedParams, { encode: false });
  },

  /**
   * 6. Tạo URL thanh toán bằng Token đã lưu (Frictionless / 3DS)
   */
  createTokenPayUrl: async (orderId, amount, customerId, token, ipAddr) => {
    const tmnCode = (process.env.VNP_TMNCODE || '').trim();
    const returnUrl = (process.env.VNP_RETURNURL || '').trim();
    const date = new Date();
    const createDate = date.getFullYear().toString() +
                       (date.getMonth() + 1).toString().padStart(2, '0') +
                       date.getDate().toString().padStart(2, '0') +
                       date.getHours().toString().padStart(2, '0') +
                       date.getMinutes().toString().padStart(2, '0') +
                       date.getSeconds().toString().padStart(2, '0');

    const params = {
        vnp_version: '2.0.1',
        vnp_command: 'token_pay',
        vnp_tmn_code: tmnCode,
        vnp_amount: amount * 100,
        vnp_app_user_id: customerId.toString(), 
        vnp_create_date: createDate,
        vnp_curr_code: 'VND',
        vnp_ip_addr: ipAddr || '127.0.0.1',
        vnp_locale: 'vn',
        vnp_txn_desc: `ThanhToanDonHang${orderId}`, 
        vnp_return_url: returnUrl,
        vnp_token: token, 
        vnp_txn_ref: orderId.toString(),
    };

    let sortedParams = vnpayController.sortObject(params);
    const secureHash = vnpayController.signParams(sortedParams);
    sortedParams['vnp_secure_hash'] = secureHash;

    const baseUrl = 'https://sandbox.vnpayment.vn/token_ui/payment-token.html'; 
    return baseUrl + '?' + qs.stringify(sortedParams, { encode: false });
  },

  handleCreateTokenUrl: async (req, res) => {
    const { customerId, ipAddr, cardType = '01' } = req.body;
    try {
      const returnUrl = (process.env.VNP_RETURNURL || '').trim();
      const url = await vnpayController.createTokenUrl(customerId, returnUrl, ipAddr, cardType);
      res.status(200).json({ tokenUrl: url });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  // ===================== API TEST & QUẢN LÝ =====================
  /**
   * 8. API Kiểm thử: Giả lập quá trình thanh toán thành công.
   * Dùng trên localhost (Postman) khi không muốn sử dụng thẻ thật.
   */
  mockDevSuccess: async (req, res) => {
    const { orderId } = req.body;
    if (!orderId) {
       return res.status(400).json({ message: "Vui lòng truyền orderId. Ví dụ: { \"orderId\": 999 }" });
    }

    try {
      // Giả lập tham số trả về từ VNPay
      const params = {
         vnp_Amount: '1000000',
         vnp_OrderInfo: 'Kiem_thu_Postman',
         vnp_ResponseCode: '00',
         vnp_TmnCode: process.env.VNP_TMNCODE || 'TEST',
         vnp_TransactionNo: Math.floor(Math.random() * 100000).toString(),
         vnp_TxnRef: orderId.toString()
      };

      const secureHash = vnpayController.signParams(params);
      
      const qs = require('qs');
      const sorted = {};
      Object.keys(params).sort().forEach(k => sorted[k] = params[k]);
      const queryString = qs.stringify(sorted, { encode: true });
      
      const fullUrl = `http://localhost:${process.env.PORT || 3000}/api/vnpay/vnpay-return?${queryString}&vnp_SecureHash=${secureHash}`;

      // Gọi ngầm vào Return URL
      const fetchResponse = await fetch(fullUrl);
      const htmlResult = await fetchResponse.text();

      const { data: updatedOrder } = await supabase.from('Order').select('orderID, status, vnpTransactionNo').eq('orderID', orderId).single();

      res.status(200).json({
          message: "Đã tự động tạo URL và thực thi truy vấn thành công!",
          generatedUrl: fullUrl,
          orderInfo: {
              id: orderId,
              newStatus: updatedOrder ? updatedOrder.status : 'Không tìm thấy Order',
              vnpTransactionNo: updatedOrder ? updatedOrder.vnpTransactionNo : 'N/A'
          },
      });
    } catch (error) {
       res.status(500).json({ error: error.message });
    }
  },

  /**
   * 9. Lấy danh sách thẻ VNPay đã lưu của khách hàng (status = Hoạt động)
   */
  getSavedCards: async (req, res) => {
    const customerId = req.user?.customerID;
    if (!customerId) return res.status(401).json({ message: "Vui lòng đăng nhập" });

    try {
      const { data, error } = await supabase
        .from('Payment')
        .select('paymentID, paymentMethod, maskedCardNumber, status')
        .eq('idCustomer', customerId)
        .eq('status', 'Hoạt động')
        .not('vnpToken', 'is', null);
        
      if (error) throw error;
      res.status(200).json(data || []);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * 10. Xóa (Soft delete) thẻ VNPay đã lưu
   */
  removeCard: async (req, res) => {
    const { id } = req.params;
    const customerId = req.user?.customerID;
    if (!customerId) return res.status(401).json({ message: "Vui lòng đăng nhập" });

    try {
      const { data: card, error: findError } = await supabase
        .from('Payment')
        .select('*')
        .eq('paymentID', id)
        .eq('idCustomer', customerId)
        .single();

      if (findError || !card) {
         return res.status(404).json({ message: "Không tìm thấy thẻ này" });
      }

      // Đánh dấu 'Đã xóa' thay vì xóa vật lý
      const { error } = await supabase
        .from('Payment')
        .update({ status: 'Đã xóa' })
        .eq('paymentID', id);

      if (error) throw error;
      
      res.status(200).json({ message: "Đã xóa thẻ thành công" });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }
};

module.exports = vnpayController;
