/**
 * =====================================================================
 * AI CONTROLLER - Chức năng: Trợ lý ảo (Chatbot) & Tìm kiếm AI
 * =====================================================================
 * 
 * Mô tả: Xử lý 3 tính năng AI sử dụng Groq Cloud:
 *   1. Tìm kiếm bằng giọng nói (Voice Search) - Whisper Large V3
 *   2. Tìm kiếm bằng hình ảnh (Image Search) - Llama Vision
 *   3. Chatbot trợ lý ảo (Text Chat) - Llama-3 (Agentic RAG 3 bước)
 * 
 * API gọi ngoài:
 *   - Groq Cloud (groq-sdk): Nền tảng AI inference siêu nhanh
 *     + Model llama-3.1-8b-instant:    Trích xuất từ khóa (Bước 1 Chatbot)
 *     + Model llama-3.3-70b-versatile: Sinh câu trả lời tư vấn (Bước 3 Chatbot)
 *     + Model whisper-large-v3:         Chuyển giọng nói → text (Voice Search)
 *     + Model llama-4-scout-17b:        Nhận diện sách từ ảnh (Image Search)
 * 
 * Bảng CSDL liên quan:
 *   - Book:       Lấy thông tin sách để tư vấn (title, price, description, ...)
 *   - Authors:    Tìm sách theo tên tác giả
 *   - BookAuthor: Bảng trung gian liên kết Book ↔ Authors (N-N)
 * 
 * API Endpoints (định nghĩa trong aiRoutes.js):
 *   - POST /api/ai/voice-search  → searchByVoice()  [upload file âm thanh]
 *   - POST /api/ai/image-search  → searchByImage()  [upload file hình ảnh]
 *   - POST /api/ai/chatbot       → chatbot()         [gửi tin nhắn text]
 * =====================================================================
 */

const Groq = require('groq-sdk');
const supabase = require('../config/supabase');
const fs = require('fs');

// Khởi tạo Groq SDK - Nền tảng chạy các model AI với tốc độ inference cực nhanh
const groq = new Groq({ apiKey: process.env.GROQ_API_KEY });

const aiController = {
  // --------------------------------------------------------
  // 1. TÌM KIẾM BẰNG GIỌNG NÓI (Voice Search via Whisper)
  // --------------------------------------------------------
  /**
   * Chuyển đổi file âm thanh thành text rồi tìm kiếm sách.
   * 
   * Luồng xử lý:
   *   1. Nhận file âm thanh từ client (qua Multer diskUpload middleware)
   *   2. Gửi file cho Whisper Large V3 trên Groq để dịch Tiếng Việt → Text
   *   3. Lấy keyword từ kết quả nhận diện giọng nói
   *   4. Tìm kiếm sách trong CSDL theo keyword (ilike trên cột title)
   *   5. Xóa file tạm trong server, trả kết quả về client
   * 
   * API gọi ngoài: groq.audio.transcriptions.create() - Model whisper-large-v3
   * 
   * @route POST /api/ai/voice-search
   * @middleware diskUpload.single('audio') - Lưu file vào thư mục uploads/
   */
  searchByVoice: async (req, res) => {
    try {
      if (!req.file) throw new Error("Không tìm thấy file âm thanh!");

      let keyword;
      try {
        const stream = fs.createReadStream(req.file.path);
        
        // Gọi Groq API: Sử dụng Whisper Large V3 để dịch giọng nói Tiếng Việt sang Text
        const transcription = await groq.audio.transcriptions.create({
          file: stream,
          model: "whisper-large-v3",      // Model chuyên dịch giọng nói đa ngôn ngữ
          response_format: "json",
          language: "vi",                  // Chỉ định ngôn ngữ Tiếng Việt
          temperature: 0.0,                // Temperature = 0 để kết quả chính xác nhất
        });
        keyword = transcription.text.trim();
        console.log(`[AI Voice Search] Recognized Text: "${keyword}"`);
        
        // Xóa file âm thanh tạm trong server để không chiếm bộ nhớ
        fs.unlinkSync(req.file.path);
      } catch (aiError) {
        // Fallback: Nếu Groq bị rate limit hoặc lỗi, dùng keyword giả lập để demo
        if (req.file && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
        console.log("[AI Fallback] Voice Search Groq chạm ngưỡng. Khởi động tính năng Giả lập.", aiError.message);
        keyword = "Sức mạnh";
      }

      // Tìm kiếm sách trong CSDL Supabase theo keyword (tìm gần đúng trong tiêu đề)
      const { data: books, error } = await supabase
        .from('Book')
        .select('*, BookImages(imageURL)')
        .ilike('title', `%${keyword}%`);

      if (error) throw error;

      // Log kết quả ra Terminal để debug
      if (books && books.length > 0) {
        console.log(`[AI Voice Search] Found ${books.length} books:`);
        books.forEach((b, i) => console.log(`   ${i + 1}. ${b.title}`));
      } else {
        console.log("[AI Voice Search] No books found in database.");
      }

      res.status(200).json({ recognizedText: keyword, results: books || [] });
    } catch (error) {
      if (req.file && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
      res.status(500).json({ error: error.message });
    }
  },

  // --------------------------------------------------------
  // 2. TÌM KIẾM BẰNG HÌNH ẢNH (Image Search via Llama Vision)
  // --------------------------------------------------------
  /**
   * Nhận diện tên sách từ ảnh bìa rồi tìm kiếm trong CSDL.
   * 
   * Luồng xử lý:
   *   1. Nhận file hình ảnh từ client (qua Multer memoryUpload middleware)
   *   2. Chuyển ảnh sang Base64 → Data URL
   *   3. Gửi cho Llama Vision trên Groq để nhận diện tên sách trong ảnh
   *   4. Tìm kiếm sách trong CSDL theo tên sách đã nhận diện
   * 
   * API gọi ngoài: groq.chat.completions.create() - Model llama-4-scout-17b
   * 
   * @route POST /api/ai/image-search
   * @middleware memoryUpload.single('image') - Lưu file trong RAM (Buffer)
   */
  searchByImage: async (req, res) => {
    try {
      if (!req.file) throw new Error("Không tìm thấy file hình ảnh!");

      // Chuyển buffer ảnh thành Base64 Data URL để gửi cho AI Vision
      const base64Image = req.file.buffer.toString('base64');
      const mimeType = req.file.mimetype;
      const dataUrl = `data:${mimeType};base64,${base64Image}`;
      
      // Prompt yêu cầu AI chỉ trả về tên sách, không giải thích thêm
      const prompt = "Bạn là hệ thống nhận diện sách. Hãy đọc tên cuốn sách trong bức ảnh này. Chỉ trả về duy nhất một chuỗi là tên sách, không giải thích gì thêm.";

      let bookTitle;
      try {
        // Gọi Groq API: Sử dụng Llama Vision để nhận diện ảnh bìa sách
        const chatCompletion = await groq.chat.completions.create({
          messages: [
            {
              role: "user",
              content: [
                { type: "text", text: prompt },
                { type: "image_url", image_url: { url: dataUrl } },
              ],
            },
          ],
          model: "meta-llama/llama-4-scout-17b-16e-instruct", // Model Vision của Meta
          temperature: 0.1, // Temperature thấp để ép xuất text chuẩn xác
        });
        
        bookTitle = chatCompletion.choices[0]?.message?.content?.trim() || "";
        bookTitle = bookTitle.replace(/[\n\r".]/g, ''); // Loại bỏ ký tự rác
        console.log(`[AI Image Search] Recognized Book: "${bookTitle}"`);
      } catch (aiError) {
        // Fallback: Nếu AI lỗi, dùng keyword giả lập để demo
        console.log("[AI Fallback] Image Search chạm giới hạn. Kích hoạt Giả lập.", aiError.message);
        bookTitle = "Harry";
      }

      // Tìm kiếm sách trong CSDL theo tên sách đã nhận diện
      const { data: books, error } = await supabase
        .from('Book')
        .select('*, BookImages(imageURL)')
        .ilike('title', `%${bookTitle}%`);

      if (error) throw error;

      // Log kết quả
      if (books && books.length > 0) {
        console.log(`[AI Image Search] Found ${books.length} books:`);
        books.forEach((b, i) => console.log(`   ${i + 1}. ${b.title}`));
      } else {
        console.log("[AI Image Search] No books found in database.");
      }

      res.status(200).json({ ai_detected_title: bookTitle, results: books || [] });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  // --------------------------------------------------------
  // 3. CHATBOT RAG TRỢ LÝ ẢO (Text via Llama Versatile)
  // --------------------------------------------------------
  /**
   * Chatbot tư vấn sách sử dụng kiến trúc Agentic RAG 3 bước.
   * 
   * Kiến trúc RAG (Retrieval-Augmented Generation):
   *   Bước 1: TRÍCH XUẤT TỪ KHÓA - Dùng AI nhỏ (8B) phân tích câu hỏi → keyword
   *   Bước 2: TÌM KIẾM ĐA TẦNG  - Tìm sách trong CSDL theo keyword (Tác giả → Tên sách → Fallback)
   *   Bước 3: SINH CÂU TRẢ LỜI   - Dùng AI lớn (70B) tư vấn dựa trên dữ liệu sách thực
   * 
   * Đặc điểm:
   *   - Không bịa thông tin: AI chỉ tư vấn sách có trong kho (CSDL)
   *   - Tìm kiếm thông minh: Ưu tiên tìm theo Tác giả trước, sau đó Tên sách
   *   - Trả lời bằng Tiếng Việt, có emoji, ngắn gọn
   *   - Có fallback khi Groq quá tải (rate limit)
   * 
   * API gọi ngoài:
   *   - groq.chat.completions.create() với model llama-3.1-8b-instant (Bước 1)
   *   - groq.chat.completions.create() với model llama-3.3-70b-versatile (Bước 3)
   * 
   * @route POST /api/ai/chatbot
   * @body {string} userMessage - Tin nhắn của người dùng
   */
  chatbot: async (req, res) => {
    const { userMessage } = req.body;
    
    try {
      // ====== BƯỚC 1: TRÍCH XUẤT TỪ KHÓA BẰNG AI NHỎ (Agentic RAG) ======
      // Dùng model 8B siêu nhanh để phân tích câu hỏi → trích 1 từ khóa cốt lõi
      let keyword = "";
      try {
        const keywordPrompt = `Người dùng nói: "${userMessage}". Hãy trích xuất đúng 1 từ khóa cốt lõi nhất (Ví dụ: tên sách hoặc thể loại như 'Thiếu nhi', 'Kinh doanh') để tìm kiếm trong kho. Chú ý: TUYỆT ĐỐI KHÔNG giải thích, CHỈ in ra đúng từ khóa đó. Nếu User chỉ chào hỏi bâng quơ, hãy trả về đúng chữ "ALL".`;
        
        // Gọi Groq API: Model llama-3.1-8b-instant (siêu nhanh, chuyên tác vụ logic)
        const keywordCompletion = await groq.chat.completions.create({
          messages: [{ role: "user", content: keywordPrompt }],
          model: "llama-3.1-8b-instant",
          temperature: 0.1,
        });
        keyword = keywordCompletion.choices[0]?.message?.content?.trim() || "ALL";
        keyword = keyword.replace(/['"]/g, ''); // Loại bỏ ngoặc kép nếu AI lỡ sinh ra
      } catch (err) {
        // Fallback: Nếu Groq lỗi → mặc định "ALL" (lấy sách ngẫu nhiên)
        keyword = "ALL";
      }

      // ====== BƯỚC 2: TÌM KIẾM SÁCH TRONG CSDL (Multi-level Search) ======
      let contextBooks = [];
      if (keyword === "ALL" || keyword.length < 2) {
        // Tình huống chào hỏi → Lấy 5 cuốn sách mới nhất làm ngữ cảnh
        const { data } = await supabase.from('Book').select('*').limit(5);
        contextBooks = data || [];
      } else {
        // ƯU TIÊN 1: Tìm theo Tên Tác Giả trong bảng Authors
        const { data: authors } = await supabase.from('Authors').select('authorID').ilike('authorName', `%${keyword}%`).limit(1);
        if (authors && authors.length > 0) {
          // Tìm thấy tác giả → Lấy sách qua bảng trung gian BookAuthor
          const { data: abcData } = await supabase.from('BookAuthor').select('idBook').eq('idAuthor', authors[0].authorID).limit(5);
          if (abcData && abcData.length > 0) {
            const bookIds = abcData.map(b => b.idBook);
            const { data } = await supabase.from('Book').select('*').in('bookID', bookIds);
            contextBooks = data || [];
          }
        }
        
        // ƯU TIÊN 2: Nếu không tìm thấy tác giả → Tìm theo Tên Sách
        if (contextBooks.length === 0) {
          const { data } = await supabase.from('Book').select('*').ilike('title', `%${keyword}%`).limit(5);
          contextBooks = data || [];
        }
        
        // FALLBACK: Không tìm thấy gì → Lấy sách ngẫu nhiên làm vốn chữa cháy
        if (contextBooks.length === 0) {
          const { data: fallback } = await supabase.from('Book').select('*').limit(5);
          contextBooks = fallback || [];
        }
      }
      
      // ====== TIỀN XỬ LÝ: Chuẩn bị dữ liệu sách cho AI ======
      // Chuyển dữ liệu sách từ CSDL thành format sạch, dễ hiểu cho AI
      const cleanedBooks = contextBooks.map(b => ({
         "Tên_sách": b.title,
         // Nhân tỷ giá 100,000 và gắn định dạng VNĐ chuẩn Việt Nam
         "Giá_bán_thực_tế": b.price ? (b.price * 100000).toLocaleString('vi-VN') + " VNĐ" : "Chưa có giá",
         // Cắt ngắn mô tả (max 300 ký tự) để tránh vượt giới hạn token AI
         "Giới_thiệu_ngắn": b.description ? b.description.substring(0, 300) + "..." : "Không có", 
         // Gộp thông số vật lý vào 1 chuỗi để AI dễ so sánh
         "Thông_số_vật_lý": `Kích thước: ${b.width || 0} x ${b.height || 0} cm | Dày: ${b.thickness || 0} cm | Nặng: ${b.weight || 0} gram | Số trang: ${b.totalPage || 0} trang | Loại bìa: ${b.format || 'Bìa mềm'}`
      }));

      const booksString = JSON.stringify(cleanedBooks);

      // ====== BƯỚC 3: SINH CÂU TRẢ LỜI BẰNG AI LỚN (Llama-3 70B) ======
      // System prompt chuyên biệt: đóng vai nhân viên tư vấn bán sách
      const prompt = `
        BỐI CẢNH: Bạn là một nhân viên tư vấn bán sách thông thái của hệ thống BookStore trực tuyến.
        KHO SÁCH ĐƯỢC CHỈ ĐỊNH ĐỂ TƯ VẤN HÔM NAY: 
        ${booksString}
        
        NHIỆM VỤ CỦA BẠN: 
        1. Trả lời câu hỏi của khách hàng: "${userMessage}"
        2. Dựa vào tâm trạng khách, hãy lôi thông số "Giá_bán_thực_tế" hoặc "Thông_số_vật_lý" của cuốn sách trong kho được cung cấp ở trên ra để thuyết phục khách hàng.
        3. TUYỆT ĐỐI KHÔNG bịa ra sách hoặc giá tiền không nằm trong danh sách. Nếu kho không có sách đúng ý khách, hãy thành thật xin lỗi và gợi ý sách có trong list!
        4. Trả lời ngắn gọn, xuống dòng rõ ràng, sử dụng emoji cho sinh động.
      `;

      let reply;
      try {
        // Gọi Groq API: Model llama-3.3-70b-versatile (mạnh nhất, suy luận tốt tiếng Việt)
        const chatCompletion = await groq.chat.completions.create({
          messages: [{ role: "user", content: prompt }],
          model: "llama-3.3-70b-versatile",
          temperature: 0.7, // Temperature 0.7 cho câu trả lời tự nhiên nhưng vẫn chính xác
        });
        reply = chatCompletion.choices[0]?.message?.content || "";
      } catch (aiError) {
        // Fallback: Khi Groq bị rate limit hoặc quá tải
        console.log("[AI Fallback] Chatbot chập mạch. Bật Fallback.", aiError.message);
        reply = "🤖 [Chế độ Tự Động] Xin chào! Hiện tại hệ thống Llama AI của Cửa hàng đang có quá nhiều khách truy cập cùng lúc. Bạn châm chước nhé!";
      }

      res.status(200).json({ reply: reply });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  }
};

module.exports = aiController;
