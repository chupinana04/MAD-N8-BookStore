const express = require('express');
const cors = require('cors');
require('dotenv').config();

const authRoutes = require('./routes/authRoutes');
const bookRoutes = require('./routes/bookRoutes');
const aiRoutes = require('./routes/aiRoutes');
const cartRoutes = require('./routes/cartRoutes');
const orderRoutes = require('./routes/orderRoutes');
const profileRoutes = require('./routes/profileRoutes');
const reviewRoutes = require('./routes/reviewRoutes');
const checkoutDataRoutes = require('./routes/checkoutDataRoutes');
const vnpayRoutes = require('./routes/vnpayRoutes');

const app = express();
app.use(cors());
// Route cho webhook của Stripe phải dùng raw body
const stripeController = require('./controllers/stripeController');
app.post('/api/stripe/webhook', express.raw({type: 'application/json'}), stripeController.stripeWebhook);

app.use(express.json()); // Để server hiểu định dạng JSON
app.use(express.urlencoded({ extended: true })); // Hỗ trợ đọc dữ liệu khi test bằng dạng Form-urlencoded

// Gắn các routes
app.use('/api/auth', authRoutes);
app.use('/api/books', bookRoutes);
app.use('/api/ai', aiRoutes);
app.use('/api/cart', cartRoutes);
app.use('/api/order', orderRoutes);
app.use('/api/profile', profileRoutes);
app.use('/api/review', reviewRoutes);
app.use('/api/checkout-data', checkoutDataRoutes);
app.use('/api/vnpay', vnpayRoutes);
const stripeRoutes = require('./routes/stripeRoutes');
app.use('/api/stripe', stripeRoutes);
const payosRoutes = require('./routes/payosRoutes');
app.use('/api/payos', payosRoutes);

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🚀 Cửa hàng sách Backend đang chạy tại http://localhost:${PORT}`);
});