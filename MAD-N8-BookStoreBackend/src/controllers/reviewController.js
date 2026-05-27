/**
 * =====================================================================
 * REVIEW CONTROLLER - Chức năng: Đánh giá sản phẩm
 * =====================================================================
 * 
 * Mô tả: Xử lý logic nghiệp vụ cho chức năng Đánh giá sản phẩm.
 *         Cho phép người dùng xem danh sách đánh giá theo sách và
 *         gửi/cập nhật bài đánh giá (rating + comment).
 * 
 * Bảng CSDL liên quan:
 *   - Review:   Lưu bài đánh giá (reviewID, idBook, idCustomer, rating, comment)
 *   - Customer: Lấy tên người đánh giá (customerID, fullName)
 * 
 * API Endpoints (định nghĩa trong reviewRoutes.js):
 *   - GET  /api/review/book/:bookId  → getReviewsByBook()
 *   - POST /api/review               → addReview()  [yêu cầu đăng nhập]
 * =====================================================================
 */

const supabase = require('../config/supabase');

/**
 * Hàm nội bộ: Gắn thông tin khách hàng (fullName) vào từng bài đánh giá.
 * 
 * Lý do: Bảng Review chỉ lưu idCustomer (FK), không lưu tên.
 *        Hàm này query bảng Customer 1 lần duy nhất rồi dùng Map để
 *        lookup O(1) cho từng review, tránh N+1 query.
 * 
 * @param {Array} reviews - Danh sách review từ bảng Review
 * @returns {Array} Danh sách review đã được gắn thêm trường Customer: { fullName }
 */
const attachCustomerToReviews = async (reviews = []) => {
  if (!reviews.length) return [];

  // Lấy danh sách customerID duy nhất (loại bỏ null/undefined và trùng lặp)
  const customerIds = [...new Set(reviews.map((review) => review.idCustomer).filter(Boolean))];
  if (!customerIds.length) {
    return reviews.map((review) => ({ ...review, Customer: null }));
  }

  // Query bảng Customer một lần duy nhất với danh sách ID
  const { data: customers, error } = await supabase
    .from('Customer')
    .select('customerID, fullName')
    .in('customerID', customerIds);

  // Nếu lỗi query, vẫn trả về review nhưng Customer = null
  if (error) {
    return reviews.map((review) => ({ ...review, Customer: null }));
  }

  // Tạo Map<customerID, fullName> để lookup nhanh O(1)
  const customerMap = new Map((customers || []).map((customer) => [customer.customerID, customer.fullName]));

  // Gắn trường Customer vào từng review
  return reviews.map((review) => ({
    ...review,
    Customer: customerMap.has(review.idCustomer)
      ? { fullName: customerMap.get(review.idCustomer) }
      : null,
  }));
};

const reviewController = {
  /**
   * Lấy danh sách đánh giá theo sách.
   * 
   * Luồng xử lý:
   *   1. Nhận bookId từ URL params
   *   2. Query bảng Review theo idBook, sắp xếp mới nhất lên đầu
   *   3. Gắn tên khách hàng vào từng review qua attachCustomerToReviews()
   *   4. Trả về danh sách review đã có tên người đánh giá
   * 
   * @route GET /api/review/book/:bookId
   */
  getReviewsByBook: async (req, res) => {
    const { bookId } = req.params;
    const numericBookId = Number(bookId);

    try {
      // Query bảng Review: lấy các cột cần thiết, sắp xếp theo reviewID giảm dần
      const { data, error } = await supabase
        .from('Review')
        .select('reviewID, rating, comment, idCustomer')
        .eq('idBook', numericBookId)
        .order('reviewID', { ascending: false });

      if (error) throw error;

      // Gắn tên khách hàng rồi trả về cho client
      res.status(200).json(await attachCustomerToReviews(data || []));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  /**
   * Gửi hoặc cập nhật đánh giá sản phẩm.
   * 
   * Quy tắc nghiệp vụ:
   *   - Mỗi khách hàng chỉ được đánh giá 1 lần cho mỗi sách
   *   - Nếu đã đánh giá trước đó → CẬP NHẬT (update) rating + comment
   *   - Nếu chưa đánh giá → TẠO MỚI (insert) bài đánh giá
   *   - Yêu cầu đăng nhập (authMiddleware kiểm tra JWT token)
   * 
   * Luồng xử lý:
   *   1. Kiểm tra đăng nhập (req.user.customerID từ JWT)
   *   2. Tìm review cũ của cùng (idBook, idCustomer)
   *   3. Nếu có → update rating + comment
   *   4. Nếu chưa có → tính reviewID tiếp theo và insert mới
   *   5. Gắn tên khách hàng vào review vừa tạo/cập nhật
   *   6. Trả về HTTP 200 (cập nhật) hoặc 201 (mới tạo)
   * 
   * @route POST /api/review
   * @middleware authMiddleware - Xác thực JWT token
   */
  addReview: async (req, res) => {
    const { bookId, rating, comment } = req.body;
    const customerId = req.user?.customerID; // Lấy từ JWT payload (do authMiddleware giải mã)
    const numericBookId = Number(bookId);

    // Kiểm tra đăng nhập: nếu không có customerID → chưa đăng nhập
    if (!customerId) {
      return res.status(401).json({ message: 'Vui long dang nhap de gui danh gia' });
    }

    try {
      // Bước 1: Kiểm tra xem khách hàng đã đánh giá sách này chưa
      const { data: existingReview, error: existingReviewError } = await supabase
        .from('Review')
        .select('reviewID')
        .eq('idBook', numericBookId)
        .eq('idCustomer', customerId)
        .maybeSingle(); // Trả null nếu không tìm thấy (không throw error)

      if (existingReviewError) throw existingReviewError;

      let review;
      let error;
      let updated = false;

      if (existingReview) {
        // Bước 2a: ĐÃ CÓ review trước đó → Cập nhật rating + comment
        updated = true;
        ({ data: review, error } = await supabase
          .from('Review')
          .update({ rating, comment })
          .eq('reviewID', existingReview.reviewID)
          .select('reviewID, rating, comment, idCustomer')
          .single());
      } else {
        // Bước 2b: CHƯA CÓ review → Tạo mới
        // Tự tính reviewID tiếp theo để tránh lỗi duplicate key nếu sequence CSDL bị lệch
        const { data: maxReview } = await supabase
          .from('Review')
          .select('reviewID')
          .order('reviewID', { ascending: false })
          .limit(1)
          .maybeSingle();
          
        const nextReviewId = (maxReview?.reviewID || 0) + 1;

        // Insert bài đánh giá mới vào bảng Review
        ({ data: review, error } = await supabase
          .from('Review')
          .insert([
            {
              reviewID: nextReviewId,
              idBook: numericBookId,
              idCustomer: customerId,
              rating,
              comment,
            },
          ])
          .select('reviewID, rating, comment, idCustomer')
          .single());
      }

      if (error) throw error;

      // Bước 3: Gắn tên khách hàng vào review vừa tạo/cập nhật
      const [decoratedReview] = await attachCustomerToReviews(review ? [review] : []);

      // Trả về kết quả: HTTP 200 nếu cập nhật, 201 nếu tạo mới
      res.status(updated ? 200 : 201).json({
        message: updated ? 'Da cap nhat danh gia thanh cong' : 'Da gui danh gia thanh cong',
        updated,
        review: decoratedReview || null,
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },
};

module.exports = reviewController;
