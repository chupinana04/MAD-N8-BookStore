/**
 * =====================================================================
 * REVIEW ROUTES - Định tuyến API cho chức năng Đánh giá sản phẩm
 * =====================================================================
 * 
 * Base path: /api/review (được gắn trong index.js)
 * 
 * Danh sách routes:
 *   GET  /api/review/book/:bookId  → Lấy tất cả đánh giá của 1 cuốn sách (public)
 *   POST /api/review               → Gửi/cập nhật đánh giá (yêu cầu đăng nhập)
 * =====================================================================
 */

const express = require('express');
const router = express.Router();
const reviewController = require('../controllers/reviewController');
const authMiddleware = require('../middlewares/authMiddleware');

// Xem danh sách đánh giá theo sách - Không cần đăng nhập (public API)
router.get('/book/:bookId', reviewController.getReviewsByBook);

// Gửi hoặc cập nhật đánh giá - Yêu cầu đăng nhập (authMiddleware xác thực JWT)
router.post('/', authMiddleware, reviewController.addReview);

module.exports = router;
