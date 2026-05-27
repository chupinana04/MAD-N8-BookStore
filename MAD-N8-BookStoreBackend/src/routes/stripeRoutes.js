/**
 * =====================================================================
 * STRIPE ROUTES - Định tuyến API thanh toán Stripe
 * =====================================================================
 * 
 * Base path: /api/stripe (được gắn trong index.js)
 * 
 * Danh sách routes:
 *   POST /api/stripe/create-setup-intent   → Tạo SetupIntent lưu thẻ mới
 *   POST /api/stripe/save-card             → Lưu thông tin thẻ vào DB
 *   POST /api/stripe/create-payment-intent → Tạo PaymentIntent thanh toán
 *   POST /api/stripe/confirm-payment       → Xác nhận giao dịch (fallback)
 * 
 * Lưu ý: Route webhook của Stripe yêu cầu raw body nên được định nghĩa
 *        riêng trong file cấu hình gốc (index.js).
 * =====================================================================
 */

const express = require('express');
const router = express.Router();
const stripeController = require('../controllers/stripeController');
const authMiddleware = require('../middlewares/authMiddleware');

// 1. Lưu thẻ: Chuẩn bị luồng SetupIntent
router.post('/create-setup-intent', authMiddleware, stripeController.createSetupIntent);

// 2. Lưu thẻ: Nhận kết quả từ App và ghi vào Database
router.post('/save-card', authMiddleware, stripeController.savePaymentMethod);

// 3. Thanh toán đơn hàng: Chuẩn bị luồng PaymentIntent
router.post('/create-payment-intent', authMiddleware, stripeController.createPaymentIntent);

// 4. Xác nhận thanh toán chủ động (Fallback cho Webhook)
router.post('/confirm-payment', authMiddleware, stripeController.confirmPayment);

// Webhook từ Stripe KHÔNG ĐƯỢC DÙNG router thông thường nếu dùng express.json()
// Chúng ta sẽ đặt route riêng biệt hoặc xử lý raw body ở index.js. 
// Tạm thời export router cho các API REST thông thường.

module.exports = router;
