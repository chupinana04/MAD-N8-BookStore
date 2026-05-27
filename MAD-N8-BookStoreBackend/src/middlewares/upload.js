const multer = require('multer');

// Dùng diskStorage cho file âm thanh (cần file vật lý có ĐUÔI FILE để Whisper nhận diện)
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, 'uploads/')
  },
  filename: function (req, file, cb) {
    // Giữ nguyên đuôi file gốc (vd: .mp3, .wav)
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9)
    const ext = file.originalname.split('.').pop();
    cb(null, file.fieldname + '-' + uniqueSuffix + '.' + ext)
  }
})
const diskUpload = multer({ storage: storage });

// Dùng memoryStorage cho hình ảnh (Base64 xử lý trên RAM nhanh hơn)
const memoryUpload = multer({ storage: multer.memoryStorage() });

module.exports = { diskUpload, memoryUpload };