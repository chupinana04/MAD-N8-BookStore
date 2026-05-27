const supabase = require('../config/supabase');

const DEFAULT_PAYMENT_METHODS = [
  { paymentMethod: 'Thanh toán khi nhận hàng (COD)', status: 'Hoạt động' },
  { paymentMethod: 'Chuyển khoản ngân hàng', status: 'Hoạt động' },
];

// Loại bỏ các hàm kiểm tra dài dòng, tin tưởng vào cột status trong DB
const resolvePaymentStatus = (paymentMethod = '') => 'Chưa hỗ trợ'; // Fallback nếu cần

const sortPayments = (payments = []) => {
  return [...payments].sort((left, right) => (left.paymentID || 0) - (right.paymentID || 0));
};

const ensureDefaultPaymentMethods = async (customerId, existingPayments = []) => {
  const existingNames = new Set(existingPayments.map(p => p.paymentMethod));

  const missingPayload = DEFAULT_PAYMENT_METHODS
    .filter(item => !existingNames.has(item.paymentMethod))
    .map(item => ({
      idCustomer: customerId,
      paymentMethod: item.paymentMethod,
      status: item.status,
    }));

  if (missingPayload.length === 0) return existingPayments;

  const { data, error } = await supabase.from('Payment').insert(missingPayload).select('*');
  if (error) throw error;
  return [...existingPayments, ...(data || [])];
};

// Đã loại bỏ decoratePayments vì không cần ghi đè status nữa

const profileController = {
  getProfile: async (req, res) => {
    const { customerId } = req.query;

    try {
      const { data, error } = await supabase
        .from('Customer')
        .select('customerID, fullName, email, phoneNumber, joinDay')
        .eq('customerID', customerId)
        .single();

      if (error) throw error;
      res.status(200).json(data);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  updateProfile: async (req, res) => {
    const { customerId, fullName, phoneNumber } = req.body;

    try {
      const { data, error } = await supabase
        .from('Customer')
        .update({ fullName, phoneNumber })
        .eq('customerID', customerId)
        .select('customerID, fullName, email, phoneNumber')
        .single();

      if (error) throw error;
      res.status(200).json({ message: 'Cập nhật hồ sơ thành công!', user: data });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getAddresses: async (req, res) => {
    const { customerId } = req.query;

    try {
      const { data, error } = await supabase
        .from('Address')
        .select('*')
        .eq('idCustomer', customerId)
        .order('addressID', { ascending: false });

      if (error) throw error;
      res.status(200).json(data);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  addAddress: async (req, res) => {
    const { customerId, receiverName, addressString } = req.body;

    try {
      const { data, error } = await supabase
        .from('Address')
        .insert([{ idCustomer: customerId, receiverName, addressString }])
        .select()
        .single();

      if (error) throw error;
      res.status(201).json({ message: 'Thêm địa chỉ thành công!', address: data });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  updateAddress: async (req, res) => {
    const { addressId } = req.params;
    const { receiverName, addressString } = req.body;

    try {
      const { data, error } = await supabase
        .from('Address')
        .update({ receiverName, addressString })
        .eq('addressID', addressId)
        .select()
        .single();

      if (error) throw error;
      res.status(200).json({ message: 'Đã cập nhật địa chỉ', address: data });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  deleteAddress: async (req, res) => {
    const { addressId } = req.params;

    try {
      const { error } = await supabase.from('Address').delete().eq('addressID', addressId);
      if (error) throw error;
      res.status(200).json({ message: 'Đã xóa địa chỉ.' });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getPaymentMethods: async (req, res) => {
    const { customerId } = req.query;

    try {
      const { data, error } = await supabase
        .from('Payment')
        .select('*')
        .eq('idCustomer', customerId)
        .order('paymentID', { ascending: true });

      if (error) throw error;

      const allPayments = await ensureDefaultPaymentMethods(customerId, data || []);
      
      // Lấy toàn bộ phương thức có trạng thái 'Hoạt động' trực tiếp từ DB
      const activePayments = allPayments.filter(p => p.status === 'Hoạt động');
      
      res.status(200).json(sortPayments(activePayments));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  addPaymentMethod: async (req, res) => {
    const { customerId, paymentMethod, status } = req.body;
    const finalStatus = status || 'Chưa hỗ trợ';

    try {
      const { data, error } = await supabase
        .from('Payment')
        .insert([{ idCustomer: customerId, paymentMethod, status: finalStatus }])
        .select()
        .single();

      if (error) throw error;
      res.status(201).json({
        message: 'Thêm phương thức thanh toán thành công!',
        payment: { ...data, status },
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  updatePaymentMethod: async (req, res) => {
    const { paymentId } = req.params;
    const { paymentMethod } = req.body;
    const status = resolvePaymentStatus(paymentMethod);

    try {
      const { data, error } = await supabase
        .from('Payment')
        .update({ paymentMethod, status })
        .eq('paymentID', paymentId)
        .select()
        .single();

      if (error) throw error;
      res.status(200).json({
        message: 'Cập nhật phương thức thanh toán thành công',
        payment: { ...data, status },
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  deletePaymentMethod: async (req, res) => {
    const { paymentId } = req.params;

    try {
      const { error } = await supabase.from('Payment').delete().eq('paymentID', paymentId);
      if (error) throw error;
      res.status(200).json({ message: 'Đã xóa phương thức thanh toán.' });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },
};

module.exports = profileController;
