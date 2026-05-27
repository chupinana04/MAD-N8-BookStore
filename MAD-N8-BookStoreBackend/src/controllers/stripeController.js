/**
 * =====================================================================
 * STRIPE CONTROLLER - Chức năng: Thanh toán thẻ quốc tế
 * =====================================================================
 * 
 * Mô tả: Tích hợp cổng thanh toán Stripe để xử lý thẻ Visa/Mastercard.
 * 
 * Luồng hoạt động (Tokenization & Thanh toán):
 *   1. Tạo SetupIntent để lưu thẻ an toàn trên Stripe (trả về clientSecret).
 *   2. App sử dụng clientSecret để hiển thị form nhập thẻ (PaymentSheet).
 *   3. Stripe xác thực và lưu thẻ → gọi API savePaymentMethod() để lưu ID vào DB.
 *   4. Khi checkout, tạo PaymentIntent để thanh toán đơn hàng (sử dụng customer ID).
 *   5. Stripe Webhook nhận kết quả thanh toán từ server Stripe và cập nhật trạng thái đơn hàng.
 *      (Hoặc gọi API confirmPayment() làm fallback nếu webhook không hoạt động ở localhost).
 * 
 * API gọi ngoài:
 *   - Stripe SDK (stripe.customers, stripe.setupIntents, stripe.paymentIntents, stripe.refunds)
 * 
 * =====================================================================
 */

const stripeKey = process.env.STRIPE_SECRET_KEY || 'sk_test_placeholder';
const stripe = require('stripe')(stripeKey);
const supabase = require('../config/supabase');

const stripeController = {
  /**
   * Helper: Tìm hoặc tạo khách hàng trên Stripe dựa theo email từ Supabase.
   * Để thanh toán và lưu thẻ trên Stripe, bắt buộc phải có đối tượng Customer trên hệ thống của họ.
   * 
   * @param {number} customerId - ID của khách hàng trong DB Supabase
   * @returns {Object} Đối tượng Customer của Stripe
   */
  getOrCreateStripeCustomer: async (customerId) => {
    // 1. Lấy thông tin khách hàng từ CSDL Supabase
    const { data: customerData, error } = await supabase
      .from('Customer')
      .select('email, fullName')
      .eq('customerID', customerId)
      .single();

    if (error || !customerData) {
      throw new Error('Không tìm thấy khách hàng trong cơ sở dữ liệu.');
    }

    const { email, fullName } = customerData;

    // 2. Tìm khách hàng trên Stripe qua email
    const existingCustomers = await stripe.customers.list({
      email: email,
      limit: 1
    });

    if (existingCustomers.data.length > 0) {
      return existingCustomers.data[0];
    }

    // 3. Nếu chưa tồn tại, tạo mới trên Stripe
    const newCustomer = await stripe.customers.create({
      email: email,
      name: fullName || `Customer ${customerId}`,
      metadata: {
        supabase_customer_id: customerId
      }
    });

    return newCustomer;
  },

  /**
   * Tạo SetupIntent để liên kết/lưu thẻ tín dụng mới.
   * 
   * Trả về clientSecret cho Frontend (Android App) để mở PaymentSheet
   * ở chế độ "Setup" (Lưu thẻ, chưa trừ tiền).
   * 
   * @route POST /api/stripe/create-setup-intent
   */
  createSetupIntent: async (req, res) => {
    const { customerId } = req.body;
    
    if (!customerId) {
      return res.status(400).json({ message: 'Thiếu customerId' });
    }

    try {
      const stripeCustomer = await stripeController.getOrCreateStripeCustomer(customerId);

      // Tạo SetupIntent trên Stripe cho thẻ (card)
      const setupIntent = await stripe.setupIntents.create({
        customer: stripeCustomer.id,
        payment_method_types: ['card'],
      });

      // ephemeralKey được dùng để Frontend Android có quyền truy cập thông tin thẻ của Customer
      res.status(200).json({
        clientSecret: setupIntent.client_secret,
        stripeCustomerId: stripeCustomer.id,
        ephemeralKey: await stripe.ephemeralKeys.create(
          { customer: stripeCustomer.id },
          { apiVersion: '2023-10-16' }
        ).then(key => key.secret).catch(() => null)
      });
    } catch (error) {
      console.error('Error in createSetupIntent:', error);
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Lưu thông tin thẻ vào DB nội bộ (Supabase) sau khi người dùng
   * hoàn tất SetupIntent thành công trên Frontend.
   * 
   * @route POST /api/stripe/save-payment-method
   */
  savePaymentMethod: async (req, res) => {
    const { customerId } = req.body;

    if (!customerId) {
      return res.status(400).json({ message: 'Thiếu thông tin customerId' });
    }

    try {
      // Tìm Stripe Customer
      const stripeCustomer = await stripeController.getOrCreateStripeCustomer(customerId);

      // Lấy danh sách thẻ đã lưu của khách hàng này trên Stripe (mới nhất)
      const paymentMethods = await stripe.paymentMethods.list({
        customer: stripeCustomer.id,
        type: 'card',
        limit: 1,
      });

      if (paymentMethods.data.length === 0) {
        return res.status(400).json({ message: 'Không tìm thấy thẻ nào trên hệ thống Stripe' });
      }

      const paymentMethod = paymentMethods.data[0];
      const paymentMethodId = paymentMethod.id;
      const cardBrand = paymentMethod.card.brand; // VD: "visa", "mastercard"
      const last4 = paymentMethod.card.last4;     // VD: "4242"

      // Lưu thông tin vắn tắt của thẻ vào bảng Payment
      // vnpToken lưu trữ paymentMethodId để sau này gọi PaymentIntent không cần nhập lại thẻ
      const maskedCard = `**** ${last4}`;
      const { data, error } = await supabase.from('Payment').insert([{
        idCustomer: customerId,
        paymentMethod: maskedCard,
        vnpToken: paymentMethodId,
        maskedCardNumber: maskedCard,
        vnpCardType: cardBrand,
        status: 'Hoạt động'
      }]).select().single();

      if (error) throw error;

      res.status(200).json({ message: 'Lưu thẻ thành công', payment: data });
    } catch (error) {
      console.error('Error saving payment method:', error);
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Tạo PaymentIntent để thực hiện thanh toán cho một đơn hàng cụ thể.
   * Trả về clientSecret để Frontend gọi hàm thanh toán hiển thị UI.
   * 
   * @route POST /api/stripe/create-payment-intent
   */
  createPaymentIntent: async (req, res) => {
    const { orderId, customerId } = req.body;

    if (!orderId || !customerId) {
      return res.status(400).json({ message: 'Thiếu orderId hoặc customerId' });
    }

    try {
      // Lấy tổng tiền (finalAmount) của đơn hàng từ CSDL
      const { data: order, error: orderError } = await supabase
        .from('Order')
        .select('finalAmount, status')
        .eq('orderID', orderId)
        .single();

      if (orderError || !order) {
        return res.status(404).json({ message: 'Không tìm thấy đơn hàng' });
      }

      if (order.status !== 'Chờ thanh toán') {
        return res.status(400).json({ message: 'Đơn hàng không ở trạng thái Chờ thanh toán' });
      }

      const stripeCustomer = await stripeController.getOrCreateStripeCustomer(customerId);

      // Lưu ý: Stripe sử dụng đơn vị tiền tệ nhỏ nhất.
      // Tuy nhiên đối với VND (zero-decimal currency), 1 VND = 1 đơn vị.
      // Trong mô hình DB của ứng dụng này, giá trị đang được chia cho 100,000, nên ta nhân lại.
      const amount = Math.round(order.finalAmount * 100000);
      
      // Khởi tạo giao dịch (PaymentIntent) trên Stripe
      const paymentIntent = await stripe.paymentIntents.create({
        amount: amount,
        currency: 'vnd',
        customer: stripeCustomer.id,
        metadata: {
          orderId: orderId.toString(),
          customerId: customerId.toString()
        }
      });

      res.status(200).json({
        paymentIntentId: paymentIntent.id,
        clientSecret: paymentIntent.client_secret,
        stripeCustomerId: stripeCustomer.id,
        ephemeralKey: await stripe.ephemeralKeys.create(
          { customer: stripeCustomer.id },
          { apiVersion: '2023-10-16' }
        ).then(key => key.secret).catch(() => null)
      });
    } catch (error) {
      console.error('Error creating payment intent:', error);
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Webhook nhận phản hồi bất đồng bộ từ máy chủ Stripe.
   * Được gọi tự động bởi Stripe khi khách hàng thanh toán thành công hoặc thất bại.
   * 
   * @route POST /api/stripe/webhook
   */
  stripeWebhook: async (req, res) => {
    const sig = req.headers['stripe-signature'];
    let event;

    try {
      // req.body phải là raw string (đã cấu hình trong index.js) để verify signature
      event = stripe.webhooks.constructEvent(req.body, sig, process.env.STRIPE_WEBHOOK_SECRET);
    } catch (err) {
      console.error('Webhook Error:', err.message);
      return res.status(400).send(`Webhook Error: ${err.message}`);
    }

    // Xử lý các sự kiện trả về từ Stripe
    switch (event.type) {
      case 'payment_intent.succeeded':
        // Thanh toán thành công
        const paymentIntent = event.data.object;
        const orderId = paymentIntent.metadata.orderId;
        
        if (orderId) {
          // Cập nhật trạng thái đơn hàng trong DB thành "Đang xử lý"
          await supabase.from('Order').update({ 
            status: 'Đang xử lý',
            vnpTransactionNo: paymentIntent.id, // Lưu ID giao dịch của Stripe
            vnpResponseCode: 'STRIPE_00'        // Mã code biểu thị thành công
          }).eq('orderID', orderId);
          console.log(`Đã cập nhật đơn hàng ${orderId} thành công qua Stripe webhook!`);
        }
        break;
        
      case 'payment_intent.payment_failed':
        // Thanh toán thất bại
        const failedIntent = event.data.object;
        const failedOrderId = failedIntent.metadata.orderId;
        if (failedOrderId) {
           await supabase.from('Order').update({ 
            status: 'Đã hủy',
            vnpTransactionNo: failedIntent.id,
            vnpResponseCode: 'STRIPE_FAIL'
          }).eq('orderID', failedOrderId);
        }
        break;
        
      default:
        console.log(`Unhandled event type ${event.type}`);
    }

    // Luôn trả về 200 OK để báo cho Stripe biết đã nhận sự kiện thành công
    res.json({received: true});
  },

  /**
   * Hủy đơn hàng và hoàn tiền (Refund) tự động.
   * Được gọi từ orderController.js khi người dùng hủy đơn đã thanh toán.
   * 
   * @param {string} paymentIntentId - ID của giao dịch PaymentIntent đã thành công
   */
  refundOrder: async (paymentIntentId) => {
    try {
      // Gọi API refunds của Stripe
      const refund = await stripe.refunds.create({
        payment_intent: paymentIntentId,
      });
      console.log(`Đã hoàn tiền thành công cho Stripe PaymentIntent: ${paymentIntentId}`);
      return refund;
    } catch (error) {
      console.error('Lỗi khi hoàn tiền qua Stripe:', error);
      throw error; // Quăng lỗi để orderController xử lý
    }
  },
  
  /**
   * Xác nhận thanh toán chủ động từ Client (Fallback).
   * Rất hữu ích khi test ở localhost do Stripe Webhook không thể gọi tới localhost.
   * App gọi hàm này sau khi thanh toán xong để server check lại trạng thái trên Stripe.
   * 
   * @route POST /api/stripe/confirm-payment
   */
  confirmPayment: async (req, res) => {
    const { orderId, paymentIntentId } = req.body;
    console.log(`[Stripe] Bắt đầu xác nhận thanh toán cho Order: ${orderId}, PI: ${paymentIntentId}`);

    if (!orderId || !paymentIntentId) {
      return res.status(400).json({ message: 'Thiếu orderId hoặc paymentIntentId' });
    }

    try {
      // 1. Kiểm tra trạng thái hiện tại của PaymentIntent trực tiếp trên Stripe
      const paymentIntent = await stripe.paymentIntents.retrieve(paymentIntentId);
      console.log(`[Stripe] Trạng thái PaymentIntent trên Stripe: ${paymentIntent.status}`);

      if (paymentIntent.status === 'succeeded') {
        // 2. Bảo mật: Xác minh metadata có khớp với đơn hàng không
        if (paymentIntent.metadata.orderId !== orderId.toString()) {
          return res.status(400).json({ message: 'Mã đơn hàng không khớp với giao dịch Stripe' });
        }

        // 3. Cập nhật trạng thái vào DB
        const { data, error } = await supabase.from('Order').update({ 
          status: 'Đang xử lý',
          vnpTransactionNo: paymentIntent.id,
          vnpResponseCode: 'STRIPE_00'
        }).eq('orderID', orderId).select().single();

        if (error) throw error;
        console.log(`[Stripe] Cập nhật trạng thái Đang xử lý thành công cho Order: ${orderId}`);

        return res.status(200).json({ message: 'Thanh toán đã được xác nhận thành công!', order: data });
      } else {
        return res.status(400).json({ message: `Thanh toán chưa thành công. Trạng thái hiện tại: ${paymentIntent.status}` });
      }
    } catch (error) {
      console.error('Error confirming payment:', error);
      res.status(500).json({ error: error.message });
    }
  }
};

module.exports = stripeController;
