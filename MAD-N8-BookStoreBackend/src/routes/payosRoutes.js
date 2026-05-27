/**
 * =====================================================================
 * PAYOS ROUTES - Định tuyến API thanh toán VietQR (PayOS)
 * =====================================================================
 * 
 * Base path: /api/payos (được gắn trong index.js)
 * 
 * Danh sách routes:
 *   POST /api/payos/create-payment-link → Tạo link thanh toán QR động
 *   POST /api/payos/webhook             → Xử lý Webhook báo có tiền
 *   GET  /api/payos/success             → Trang redirect thành công
 *   GET  /api/payos/cancel              → Trang redirect bị hủy
 * =====================================================================
 */

const express = require('express');
const router = express.Router();
const payosController = require('../controllers/payosController');
const authMiddleware = require('../middlewares/authMiddleware');

// 1. Tạo link thanh toán QR (Cần đăng nhập qua token)
router.post('/create-payment-link', authMiddleware, payosController.createPaymentLink);

// 2. Webhook nhận thông báo từ ngân hàng (Không dùng authMiddleware vì máy chủ PayOS gọi)
router.post('/webhook', payosController.payosWebhook);

// 3. Các trang redirect tĩnh phục vụ WebView
router.get('/success', payosController.successRedirect);
router.get('/cancel', payosController.cancelRedirect);

module.exports = router;
