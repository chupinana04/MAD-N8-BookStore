const nodemailer = require('nodemailer');
require('dotenv').config();

const transporter = nodemailer.createTransport({
  service: 'gmail', // Mặc định dùng Gmail
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_APP_PASSWORD,
  },
});

/**
 * Gửi email chứa mã OTP
 * @param {string} toEmail - Email nhận
 * @param {string} otpCode - Mã OTP cần gửi
 * @param {string} subject - Chủ đề email
 */
const sendOtpEmail = async (toEmail, otpCode, subject = 'Xác thực tài khoản Bookstore') => {
  if (!process.env.EMAIL_USER || !process.env.EMAIL_APP_PASSWORD) {
    console.log(`[CẢNH BÁO] Chưa cấu hình EMAIL_USER và EMAIL_APP_PASSWORD trong .env. Mô phỏng gửi tới ${toEmail}: ${otpCode}`);
    return;
  }

  const mailOptions = {
    from: `"Bookstore N8" <${process.env.EMAIL_USER}>`,
    to: toEmail,
    subject: subject,
    html: `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #ddd; border-radius: 8px;">
        <h2 style="color: #2c3e50; text-align: center;">Xác thực tài khoản Bookstore N8</h2>
        <p style="font-size: 16px; color: #555;">Xin chào,</p>
        <p style="font-size: 16px; color: #555;">Mã xác thực (OTP) của bạn là:</p>
        <div style="background-color: #f1f2f6; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; letter-spacing: 4px; border-radius: 4px; color: #e74c3c;">
          ${otpCode}
        </div>
        <p style="font-size: 14px; margin-top: 20px; color: #999;">Mã này có hiệu lực trong vòng 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>
        <p style="font-size: 14px; color: #999;">Trân trọng,<br>Bookstore N8 Team.</p>
      </div>
    `,
  };

  try {
    const info = await transporter.sendMail(mailOptions);
    console.log(`[EMAIL THẬT] Đã gửi thư tới ${toEmail}. ID: ${info.messageId}`);
  } catch (error) {
    console.error(`[LỖI EMAIL] Không thể gửi email tới ${toEmail}:`, error);
  }
};

module.exports = {
  sendOtpEmail,
};
