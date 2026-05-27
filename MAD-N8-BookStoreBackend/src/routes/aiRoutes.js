/**
 * =====================================================================
 * AI ROUTES - Định tuyến API cho chức năng AI (Chatbot & Tìm kiếm)
 * =====================================================================
 * 
 * Base path: /api/ai (được gắn trong index.js)
 * 
 * Danh sách routes:
 *   POST /api/ai/voice-search → Tìm sách bằng giọng nói (upload audio)
 *   POST /api/ai/image-search → Tìm sách bằng hình ảnh (upload image)
 *   POST /api/ai/chatbot      → Nhắn tin với Trợ lý ảo AI
 * =====================================================================
 */

const express = require('express');
const router = express.Router();
const aiController = require('../controllers/aiController');
const { diskUpload, memoryUpload } = require('../middlewares/upload');

// Tìm kiếm bằng giọng nói - Multer lưu file âm thanh vào đĩa (cần file vật lý cho Whisper)
router.post('/voice-search', diskUpload.single('audio'), aiController.searchByVoice);

// Tìm kiếm bằng hình ảnh - Multer lưu file trong RAM (Base64 xử lý nhanh hơn)
router.post('/image-search', memoryUpload.single('image'), aiController.searchByImage);

// Chatbot trợ lý ảo - Không cần upload file, chỉ nhận text qua JSON body
router.post('/chatbot', aiController.chatbot);

module.exports = router;