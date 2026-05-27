/**
 * =====================================================================
 * VNPAY ROUTES - Định tuyến API thanh toán VNPay
 * =====================================================================
 * 
 * Base path: /api/vnpay (được gắn trong index.js)
 * 
 * Danh sách routes:
 *   GET    /api/vnpay/vnpay-return        → Xử lý Return URL (User redirect)
 *   GET    /api/vnpay/vnpay-ipn           → Xử lý Webhook IPN (Server-to-Server)
 *   POST   /api/vnpay/create-token-url    → Tạo link Tokenization lưu thẻ
 *   POST   /api/vnpay/mock-dev-success    → Giả lập thành công (dành cho DEV)
 *   GET    /api/vnpay/saved-cards         → Lấy danh sách thẻ đã lưu
 *   DELETE /api/vnpay/remove-card/:id     → Xóa thẻ đã lưu
 * =====================================================================
 */

const express = require('express');
const router = express.Router();
const vnpayController = require('../controllers/vnpayController');
const authMiddleware = require('../middlewares/authMiddleware');

// Route này nhận redirect từ VNPay (Browser hoặc WebView trên Android)
router.get('/vnpay-return', vnpayController.vnpayReturn);

// Route này nhận thông báo ngầm từ máy chủ VNPay (Server-to-Server)
router.get('/vnpay-ipn', vnpayController.vnpayIpn);

// Route yêu cầu link đăng ký lưu thẻ (Tokenization)
router.post('/create-token-url', vnpayController.handleCreateTokenUrl);

// API Mock để test tự động giao dịch thành công trên Localhost
router.post('/mock-dev-success', vnpayController.mockDevSuccess);

// API Quản lý thẻ đã lưu (yêu cầu đăng nhập)
router.get('/saved-cards', authMiddleware, vnpayController.getSavedCards);
router.delete('/remove-card/:id', authMiddleware, vnpayController.removeCard);

module.exports = router;
