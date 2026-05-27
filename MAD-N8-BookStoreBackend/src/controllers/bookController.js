const jwt = require('jsonwebtoken');
const supabase = require('../config/supabase');
const recommendationCache = require('../utils/memoryCache');
const aiRecommendationService = require('../services/aiRecommendationService');

const JWT_SECRET = process.env.JWT_SECRET || 'bookstore_secret_dev';

const normalizeText = (value = '') =>
  value
    .toString()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();

const SOLD_STATUS_KEYS = new Set([
  normalizeText('Đang xử lý'),
  normalizeText('Đang giao'),
  normalizeText('Hoàn tất'),
  normalizeText('Đã thanh toán'),
]);

const extractPrimaryImage = (book) => {
  if (!book.BookImages || book.BookImages.length === 0) return null;
  const primary = book.BookImages.find((image) => image.isPrimary === true);
  return primary ? primary.imageURL : book.BookImages[0].imageURL;
};

const normalizeBookForList = (book, metrics = {}) => {
  const { BookImages, ...bookData } = book;
  return {
    ...bookData,
    price: bookData.price ? bookData.price * 100000 : 0,
    primaryImage: extractPrimaryImage(book),
    avgRating: Number((metrics.avgRating || 0).toFixed(1)),
    reviewCount: metrics.reviewCount || 0,
    soldCount: metrics.soldCount || 0,
  };
};

const getBookMetrics = async (bookIds = []) => {
  if (!bookIds.length) return {};

  const metrics = {};
  bookIds.forEach((bookId) => {
    metrics[bookId] = { avgRating: 0, reviewCount: 0, soldCount: 0 };
  });

  const [reviewResult, orderItemResult] = await Promise.all([
    supabase.from('Review').select('idBook, rating').in('idBook', bookIds),
    supabase.from('OrderItem').select('idBook, quantity, Order(status)').in('idBook', bookIds),
  ]);

  if (!reviewResult.error) {
    (reviewResult.data || []).forEach((review) => {
      const current = metrics[review.idBook] || { avgRating: 0, reviewCount: 0, soldCount: 0 };
      current.avgRating += Number(review.rating || 0);
      current.reviewCount += 1;
      metrics[review.idBook] = current;
    });
  }

  Object.values(metrics).forEach((item) => {
    if (item.reviewCount > 0) {
      item.avgRating /= item.reviewCount;
    }
  });

  if (!orderItemResult.error) {
    (orderItemResult.data || []).forEach((orderItem) => {
      const orderStatusKey = normalizeText(orderItem.Order?.status || '');
      if (!SOLD_STATUS_KEYS.has(orderStatusKey)) return;
      const current = metrics[orderItem.idBook] || { avgRating: 0, reviewCount: 0, soldCount: 0 };
      current.soldCount += Number(orderItem.quantity || 0);
      metrics[orderItem.idBook] = current;
    });
  }

  return metrics;
};

const enrichBooksWithMetrics = async (books = []) => {
  if (!books.length) return [];
  const metrics = await getBookMetrics(books.map((book) => book.bookID));
  return books.map((book) => normalizeBookForList(book, metrics[book.bookID]));
};

const dedupeBooks = (books = []) => {
  const seen = new Set();
  return books.filter((book) => {
    if (!book || seen.has(book.bookID)) return false;
    seen.add(book.bookID);
    return true;
  });
};

const tokenize = (value = '') => normalizeText(value).split(' ').filter(Boolean);

const isSubsequenceMatch = (needle, haystack) => {
  if (!needle || !haystack) return false;
  let index = 0;
  for (const char of haystack) {
    if (char === needle[index]) {
      index += 1;
      if (index === needle.length) return true;
    }
  }
  return false;
};

const scoreTextMatch = (query, source) => {
  const normalizedQuery = normalizeText(query);
  const normalizedSource = normalizeText(source);

  if (!normalizedQuery || !normalizedSource) return 0;
  if (normalizedQuery === normalizedSource) return 160;

  const compactQuery = normalizedQuery.replace(/\s+/g, '');
  const compactSource = normalizedSource.replace(/\s+/g, '');

  if (normalizedSource.includes(normalizedQuery)) {
    return 130 - Math.min(30, normalizedSource.indexOf(normalizedQuery));
  }

  const tokens = tokenize(normalizedQuery);
  const matchedTokens = tokens.filter((token) => normalizedSource.includes(token));

  if (matchedTokens.length === tokens.length && tokens.length > 0) {
    return 110 + matchedTokens.join('').length;
  }

  if (matchedTokens.length > 0) {
    return 70 + matchedTokens.join('').length;
  }

  if (compactSource.startsWith(compactQuery)) {
    return 90;
  }

  if (isSubsequenceMatch(compactQuery, compactSource)) {
    return 45;
  }

  return 0;
};

const orderBooksByIdList = (books = [], orderedIds = []) => {
  if (!books.length) return [];
  if (!orderedIds.length) return dedupeBooks(books);

  const rankMap = new Map(orderedIds.map((bookId, index) => [Number(bookId), index]));
  return dedupeBooks([...books]).sort((left, right) => {
    const leftRank = rankMap.has(left.bookID) ? rankMap.get(left.bookID) : Number.MAX_SAFE_INTEGER;
    const rightRank = rankMap.has(right.bookID) ? rankMap.get(right.bookID) : Number.MAX_SAFE_INTEGER;
    return leftRank - rightRank;
  });
};

const safeNumber = (value, fallback = 0) => {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : fallback;
};

const getBookRelations = async (bookIds = []) => {
  const normalizedIds = [...new Set(bookIds.map((bookId) => safeNumber(bookId)).filter((bookId) => bookId > 0))];
  if (!normalizedIds.length) {
    return {
      authorMap: new Map(),
      categoryMap: new Map(),
    };
  }

  const [bookAuthorResult, bookCategoryResult] = await Promise.all([
    supabase.from('BookAuthor').select('idBook, idAuthor, Authors(authorID, authorName)').in('idBook', normalizedIds),
    supabase.from('BookCategory').select('idBook, idCategory, Categories(categoryID, categoryName)').in('idBook', normalizedIds),
  ]);

  const authorMap = new Map();
  const categoryMap = new Map();

  normalizedIds.forEach((bookId) => {
    authorMap.set(bookId, []);
    categoryMap.set(bookId, []);
  });

  if (!bookAuthorResult.error) {
    (bookAuthorResult.data || []).forEach((item) => {
      const bookId = safeNumber(item.idBook);
      if (!authorMap.has(bookId)) authorMap.set(bookId, []);
      authorMap.get(bookId).push({
        id: safeNumber(item.idAuthor),
        name: item.Authors?.authorName || null,
      });
    });
  }

  if (!bookCategoryResult.error) {
    (bookCategoryResult.data || []).forEach((item) => {
      const bookId = safeNumber(item.idBook);
      if (!categoryMap.has(bookId)) categoryMap.set(bookId, []);
      categoryMap.get(bookId).push({
        id: safeNumber(item.idCategory),
        name: item.Categories?.categoryName || null,
      });
    });
  }

  return { authorMap, categoryMap };
};

const decorateBooksWithRelations = async (books = []) => {
  if (!books.length) return [];

  const { authorMap, categoryMap } = await getBookRelations(books.map((book) => book.bookID));
  return books.map((book) => ({
    ...book,
    authorNames: (authorMap.get(book.bookID) || []).map((item) => item.name).filter(Boolean),
    authorIds: (authorMap.get(book.bookID) || []).map((item) => item.id).filter(Boolean),
    categoryNames: (categoryMap.get(book.bookID) || []).map((item) => item.name).filter(Boolean),
    categoryIds: (categoryMap.get(book.bookID) || []).map((item) => item.id).filter(Boolean),
  }));
};

const extractCustomerIdFromRequest = (req) => {
  const authHeader = req.headers.authorization;
  if (!authHeader) return null;

  try {
    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, JWT_SECRET);
    return safeNumber(decoded.customerID, 0) || null;
  } catch (_) {
    return null;
  }
};

const fetchBooksByIds = async (bookIds = []) => {
  const normalizedIds = [...new Set(bookIds.map((bookId) => safeNumber(bookId)).filter((bookId) => bookId > 0))];
  if (!normalizedIds.length) return [];

  const { data, error } = await supabase
    .from('Book')
    .select('*, BookImages(imageURL, isPrimary)')
    .in('bookID', normalizedIds);

  if (error) throw error;
  return orderBooksByIdList(data || [], normalizedIds);
};

const buildHistoryText = async (customerId) => {
  if (!customerId) return '';

  const { data, error } = await supabase
    .from('ViewHistory')
    .select('viewAt, Book(bookID, title, author, description, language, format)')
    .eq('idCustomer', customerId)
    .order('viewAt', { ascending: false })
    .limit(12);

  if (error || !data?.length) return '';

  return data
    .map((item) => {
      const book = Array.isArray(item.Book) ? item.Book[0] : item.Book;
      if (!book) return null;
      return [
        `bookID=${book.bookID}`,
        `title=${book.title || ''}`,
        `author=${book.author || ''}`,
        `language=${book.language || ''}`,
        `format=${book.format || ''}`,
        `description=${(book.description || '').slice(0, 180)}`,
      ].join(' | ');
    })
    .filter(Boolean)
    .join('\n');
};

const resolveForYouBooks = async (req) => {
  const customerId = extractCustomerIdFromRequest(req);
  const cacheKey = customerId ? `for-you:${customerId}` : null;

  const latestBooksResult = await supabase
    .from('Book')
    .select('*, BookImages(imageURL, isPrimary)')
    .order('createdAt', { ascending: false })
    .limit(36);

  if (latestBooksResult.error) {
    throw latestBooksResult.error;
  }

  const latestBooks = latestBooksResult.data || [];
  if (!latestBooks.length) return [];

  if (cacheKey) {
    const cachedRecommendation = recommendationCache.get(cacheKey);
    if (cachedRecommendation?.ids?.length) {
      try {
        const cachedBooks = await fetchBooksByIds(cachedRecommendation.ids);
        if (cachedBooks.length) {
          return enrichBooksWithMetrics(cachedBooks.slice(0, 12));
        }
      } catch (_) {
        recommendationCache.delete(cacheKey);
      }
    }
  }

  if (!customerId) {
    return enrichBooksWithMetrics(latestBooks.slice(0, 12));
  }

  const historyText = await buildHistoryText(customerId);
  if (!historyText) {
    return enrichBooksWithMetrics(latestBooks.slice(0, 12));
  }

  const metrics = await getBookMetrics(latestBooks.map((book) => book.bookID));
  const aiCandidates = await decorateBooksWithRelations(
    latestBooks.map((book) => ({
      ...book,
      avgRating: metrics[book.bookID]?.avgRating || 0,
      soldCount: metrics[book.bookID]?.soldCount || 0,
    }))
  );

  const aiRecommendedIds = await aiRecommendationService.recommendForYou(historyText, aiCandidates.slice(0, 24));
  if (!aiRecommendedIds?.length) {
    return enrichBooksWithMetrics(latestBooks.slice(0, 12));
  }

  if (cacheKey) {
    recommendationCache.set(cacheKey, {
      ids: aiRecommendedIds,
      source: 'AI personalized recommendations',
    });
  }

  const orderedBooks = orderBooksByIdList(latestBooks, aiRecommendedIds).slice(0, 12);
  return enrichBooksWithMetrics(orderedBooks);
};

const attachCustomerToReviews = async (reviews = []) => {
  if (!reviews.length) return [];

  const customerIds = [...new Set(reviews.map((review) => review.idCustomer).filter(Boolean))];
  if (!customerIds.length) {
    return reviews.map((review) => ({ ...review, Customer: null }));
  }

  const { data: customers, error } = await supabase
    .from('Customer')
    .select('customerID, fullName')
    .in('customerID', customerIds);

  if (error) {
    return reviews.map((review) => ({ ...review, Customer: null }));
  }

  const customerMap = new Map((customers || []).map((customer) => [customer.customerID, customer.fullName]));
  return reviews.map((review) => ({
    ...review,
    Customer: customerMap.has(review.idCustomer)
      ? { fullName: customerMap.get(review.idCustomer) }
      : null,
  }));
};

const computeSimilarityScore = (targetBook, candidateBook) => {
  let score = 0;

  if (targetBook.idGroup && candidateBook.idGroup && targetBook.idGroup === candidateBook.idGroup) {
    score += 140;
  }

  const targetAuthorIds = new Set(targetBook.authorIds || []);
  const candidateAuthorIds = new Set(candidateBook.authorIds || []);
  const sharedAuthorCount = [...candidateAuthorIds].filter((authorId) => targetAuthorIds.has(authorId)).length;
  if (sharedAuthorCount > 0) {
    score += 90 + sharedAuthorCount * 20;
  }

  const targetCategoryIds = new Set(targetBook.categoryIds || []);
  const candidateCategoryIds = new Set(candidateBook.categoryIds || []);
  const sharedCategoryCount = [...candidateCategoryIds].filter((categoryId) => targetCategoryIds.has(categoryId)).length;
  if (sharedCategoryCount > 0) {
    score += 55 + sharedCategoryCount * 12;
  }

  const authorTextScore = scoreTextMatch(targetBook.author || '', candidateBook.author || '');
  if (authorTextScore > 0) {
    score += Math.min(30, Math.round(authorTextScore / 4));
  }

  const titleScore = scoreTextMatch(targetBook.title || '', candidateBook.title || '');
  if (titleScore > 0) {
    score += Math.min(26, Math.round(titleScore / 5));
  }

  const descriptionScore = scoreTextMatch(targetBook.title || '', candidateBook.description || '');
  if (descriptionScore > 0) {
    score += Math.min(18, Math.round(descriptionScore / 7));
  }

  if (normalizeText(targetBook.language || '') && normalizeText(targetBook.language || '') === normalizeText(candidateBook.language || '')) {
    score += 12;
  }

  if (normalizeText(targetBook.format || '') && normalizeText(targetBook.format || '') === normalizeText(candidateBook.format || '')) {
    score += 8;
  }

  const targetPrice = safeNumber(targetBook.price, 0);
  const candidatePrice = safeNumber(candidateBook.price, 0);
  if (targetPrice > 0 && candidatePrice > 0) {
    const ratio = Math.abs(targetPrice - candidatePrice) / Math.max(targetPrice, candidatePrice);
    if (ratio <= 0.15) score += 12;
    else if (ratio <= 0.35) score += 6;
  }

  score += Math.min(14, safeNumber(candidateBook.soldCount, 0));
  score += Math.min(8, Math.round(safeNumber(candidateBook.avgRating, 0)));

  return score;
};

const buildSimilarCandidates = async (bookId, book) => {
  const numericBookId = safeNumber(bookId);
  const targetBook = (await decorateBooksWithRelations([book]))[0] || book;
  const buckets = [];

  if (book.idGroup) {
    const { data, error } = await supabase
      .from('Book')
      .select('*, BookImages(imageURL, isPrimary)')
      .eq('idGroup', book.idGroup)
      .neq('bookID', numericBookId)
      .limit(20);

    if (!error && data?.length) {
      buckets.push({ source: 'same_group', books: data });
    }
  }

  const targetAuthorIds = targetBook.authorIds || [];
  const targetCategoryIds = targetBook.categoryIds || [];

  if (targetAuthorIds.length || targetCategoryIds.length) {
    const [relatedByAuthor, relatedByCategory] = await Promise.all([
      targetAuthorIds.length
        ? supabase.from('BookAuthor').select('idBook').in('idAuthor', targetAuthorIds)
        : Promise.resolve({ data: [], error: null }),
      targetCategoryIds.length
        ? supabase.from('BookCategory').select('idBook').in('idCategory', targetCategoryIds)
        : Promise.resolve({ data: [], error: null }),
    ]);

    const relatedIds = [...new Set([
      ...(relatedByAuthor.error ? [] : (relatedByAuthor.data || []).map((item) => safeNumber(item.idBook))),
      ...(relatedByCategory.error ? [] : (relatedByCategory.data || []).map((item) => safeNumber(item.idBook))),
    ])].filter((candidateId) => candidateId > 0 && candidateId !== numericBookId);

    if (relatedIds.length) {
      const relatedBooks = await fetchBooksByIds(relatedIds.slice(0, 40));
      if (relatedBooks.length) {
        buckets.push({ source: 'shared_author_category', books: relatedBooks });
      }
    }
  }

  const fallbackResult = await supabase
    .from('Book')
    .select('*, BookImages(imageURL, isPrimary)')
    .neq('bookID', numericBookId)
    .order('updatedAt', { ascending: false })
    .limit(24);

  if (!fallbackResult.error && fallbackResult.data?.length) {
    buckets.push({ source: 'recent_fallback', books: fallbackResult.data });
  }

  const decoratedCandidates = await decorateBooksWithRelations(
    dedupeBooks(buckets.flatMap((bucket) => bucket.books || []))
  );

  const bucketSourceByBookId = new Map();
  buckets.forEach((bucket) => {
    (bucket.books || []).forEach((candidate) => {
      if (!bucketSourceByBookId.has(candidate.bookID)) {
        bucketSourceByBookId.set(candidate.bookID, bucket.source);
      }
    });
  });

  const scoredCandidates = decoratedCandidates
    .filter((candidate) => safeNumber(candidate.bookID) !== numericBookId)
    .map((candidate) => ({
      ...candidate,
      candidateBucket: bucketSourceByBookId.get(candidate.bookID) || 'recent_fallback',
      similarityScore: computeSimilarityScore(targetBook, candidate),
    }))
    .sort((left, right) => right.similarityScore - left.similarityScore);

  return {
    targetBook,
    candidates: scoredCandidates,
  };
};

const resolveSmartSimilarBooks = async (bookId, book) => {
  const numericBookId = safeNumber(bookId);
  const cacheKey = `similar-books:${numericBookId}`;
  const cachedRecommendation = recommendationCache.get(cacheKey);

  if (cachedRecommendation?.ids?.length) {
    try {
      const cachedBooks = await fetchBooksByIds(cachedRecommendation.ids);
      if (cachedBooks.length) {
        return {
          similarSource: cachedRecommendation.source || 'AI semantic cache',
          books: await enrichBooksWithMetrics(cachedBooks.slice(0, 12)),
        };
      }
    } catch (_) {
      recommendationCache.delete(cacheKey);
    }
  }

  const { targetBook, candidates } = await buildSimilarCandidates(numericBookId, book);
  if (!candidates.length) {
    return {
      similarSource: 'Fallback recent books',
      books: [],
    };
  }

  const shortlist = candidates.slice(0, 20);
  const aiRecommendedIds = await aiRecommendationService.findSimilarBooks(targetBook, shortlist);

  if (aiRecommendedIds?.length) {
    recommendationCache.set(cacheKey, {
      ids: aiRecommendedIds,
      source: 'AI semantic ranking + relation fallback',
    });

    const aiRankedBooks = orderBooksByIdList(shortlist, aiRecommendedIds).slice(0, 12);
    return {
      similarSource: 'AI semantic ranking + relation fallback',
      books: await enrichBooksWithMetrics(aiRankedBooks),
    };
  }

  return {
    similarSource: 'Heuristic relation fallback',
    books: await enrichBooksWithMetrics(shortlist.slice(0, 12)),
  };
};

const rerankWithAI = async (targetBook, candidates) => {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey || apiKey === '<YOUR API KEY>' || candidates.length < 2) return null;

  try {
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });
    const prompt = `
Bạn là hệ thống gợi ý sách. Hãy xếp hạng danh sách ứng viên theo mức độ tương tự với sách mục tiêu.
Trả về duy nhất JSON array các bookID theo thứ tự phù hợp nhất đến ít phù hợp hơn.

Sách mục tiêu:
${JSON.stringify({ bookID: targetBook.bookID, title: targetBook.title, description: targetBook.description || '' })}

Ứng viên:
${JSON.stringify(candidates.map((book) => ({ bookID: book.bookID, title: book.title, description: book.description || '' })))}
`;

    const result = await model.generateContent(prompt);
    const text = result.response.text().trim();
    const parsed = JSON.parse(text.replace(/```json|```/g, '').trim());
    if (!Array.isArray(parsed)) return null;

    const rankMap = new Map(parsed.map((bookId, index) => [Number(bookId), index]));
    return [...candidates].sort((a, b) => {
      const aRank = rankMap.has(a.bookID) ? rankMap.get(a.bookID) : Number.MAX_SAFE_INTEGER;
      const bRank = rankMap.has(b.bookID) ? rankMap.get(b.bookID) : Number.MAX_SAFE_INTEGER;
      return aRank - bRank;
    });
  } catch (_) {
    return null;
  }
};

const resolveSimilarBooks = async (bookId, book) => {
  let similarSource = 'Mới cập nhật';
  let candidates = [];

  if (book.idGroup) {
    const { data, error } = await supabase
      .from('Book')
      .select('*, BookImages(imageURL, isPrimary)')
      .eq('idGroup', book.idGroup)
      .neq('bookID', bookId)
      .limit(12);

    if (!error) {
      candidates = data || [];
      if (candidates.length) similarSource = 'Cùng nhóm sách';
    }
  }

  if (candidates.length < 12) {
    const [authorLinks, categoryLinks] = await Promise.all([
      supabase.from('BookAuthor').select('idAuthor').eq('idBook', bookId),
      supabase.from('BookCategory').select('idCategory').eq('idBook', bookId),
    ]);

    const authorIds = authorLinks.error ? [] : (authorLinks.data || []).map((item) => item.idAuthor);
    const categoryIds = categoryLinks.error ? [] : (categoryLinks.data || []).map((item) => item.idCategory);

    const [relatedByAuthor, relatedByCategory] = await Promise.all([
      authorIds.length
        ? supabase.from('BookAuthor').select('idBook').in('idAuthor', authorIds)
        : Promise.resolve({ data: [], error: null }),
      categoryIds.length
        ? supabase.from('BookCategory').select('idBook').in('idCategory', categoryIds)
        : Promise.resolve({ data: [], error: null }),
    ]);

    const relatedIds = dedupeBooks(
      [
        ...(relatedByAuthor.error ? [] : (relatedByAuthor.data || [])),
        ...(relatedByCategory.error ? [] : (relatedByCategory.data || [])),
      ]
        .map((item) => ({ bookID: item.idBook }))
        .filter((item) => item.bookID !== Number(bookId))
    ).map((item) => item.bookID);

    if (relatedIds.length) {
      const { data, error } = await supabase
        .from('Book')
        .select('*, BookImages(imageURL, isPrimary)')
        .in('bookID', relatedIds)
        .neq('bookID', bookId)
        .limit(20);

      if (!error) {
        candidates = dedupeBooks([...candidates, ...(data || [])]);
        if (candidates.length) similarSource = 'Cùng tác giả/thể loại';
      }
    }
  }

  if (!candidates.length) {
    const { data, error } = await supabase
      .from('Book')
      .select('*, BookImages(imageURL, isPrimary)')
      .neq('bookID', bookId)
      .order('updatedAt', { ascending: false })
      .limit(20);

    if (!error) {
      candidates = data || [];
    }
  }

  const reranked = await rerankWithAI(book, candidates);
  if (reranked) {
    similarSource = 'AI + dữ liệu cùng tác giả/thể loại';
    candidates = reranked;
  }

  return {
    similarSource,
    books: await enrichBooksWithMetrics(dedupeBooks(candidates).slice(0, 12)),
  };
};

const recordViewHistory = async (req, bookId) => {
  const customerId = extractCustomerIdFromRequest(req);
  if (!customerId) return;

  try {
    await supabase.from('ViewHistory').insert([
      { viewAt: new Date(), idBook: safeNumber(bookId), idCustomer: customerId },
    ]);
  } catch (_) {}
};

const bookController = {
  getCategories: async (_req, res) => {
    try {
      const { data, error } = await supabase.from('Categories').select('*');
      if (error) throw error;
      res.status(200).json(data);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getBooks: async (req, res) => {
    const { categoryId } = req.query;

    try {
      let query = supabase.from('Book').select('*, BookImages(imageURL, isPrimary)');

      if (categoryId) {
        const { data: bookCategories, error: categoryError } = await supabase
          .from('BookCategory')
          .select('idBook')
          .eq('idCategory', categoryId);

        if (categoryError) throw categoryError;
        if (!bookCategories || bookCategories.length === 0) return res.status(200).json([]);

        query = query.in('bookID', bookCategories.map((item) => item.idBook));
      }

      const { data, error } = await query;
      if (error) throw error;

      res.status(200).json(await enrichBooksWithMetrics(data || []));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getBooksForYou: async (req, res) => {
    try {
      res.status(200).json(await resolveForYouBooks(req));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getBookDetail: async (req, res) => {
    const { id } = req.params;
    const numericBookId = safeNumber(id);

    try {
      const { data: book, error: bookError } = await supabase
        .from('Book')
        .select('*, BookImages(imageURL, isPrimary)')
        .eq('bookID', numericBookId)
        .single();

      if (bookError || !book) {
        return res.status(404).json({ message: 'Khong tim thay sach' });
      }

      const [reviewResult, metricsMap, similarResult] = await Promise.all([
        supabase
          .from('Review')
          .select('reviewID, rating, comment, idCustomer')
          .eq('idBook', numericBookId)
          .order('reviewID', { ascending: false }),
        getBookMetrics([book.bookID]),
        resolveSmartSimilarBooks(numericBookId, book),
      ]);

      const reviews = reviewResult.error ? [] : await attachCustomerToReviews(reviewResult.data || []);
      const bookMetrics = metricsMap[book.bookID] || { avgRating: 0, reviewCount: 0, soldCount: 0 };
      await recordViewHistory(req, numericBookId);

      res.status(200).json({
        book: {
          ...book,
          price: book.price ? book.price * 100000 : 0,
          avgRating: Number((bookMetrics.avgRating || 0).toFixed(1)),
          reviewCount: bookMetrics.reviewCount || 0,
          soldCount: bookMetrics.soldCount || 0,
        },
        avgRating: Number((bookMetrics.avgRating || 0).toFixed(1)),
        totalReviews: bookMetrics.reviewCount || 0,
        soldCount: bookMetrics.soldCount || 0,
        top3Reviews: reviews.slice(0, 3),
        similarSource: similarResult.similarSource,
        similarBooks: similarResult.books,
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  searchBooks: async (req, res) => {
    const { q } = req.query;
    if (!q) {
      return res.status(400).json({ message: 'Vui long nhap tu khoa q' });
    }

    try {
      const [authorsResult, categoriesResult, booksResult, bookAuthorResult, bookCategoryResult] = await Promise.all([
        supabase.from('Authors').select('*'),
        supabase.from('Categories').select('categoryID, categoryName'),
        supabase.from('Book').select('*, BookImages(imageURL, isPrimary)'),
        supabase.from('BookAuthor').select('idBook, idAuthor'),
        supabase.from('BookCategory').select('idBook, idCategory'),
      ]);

      if (authorsResult.error) throw authorsResult.error;
      if (categoriesResult.error) throw categoriesResult.error;
      if (booksResult.error) throw booksResult.error;
      if (bookAuthorResult.error) throw bookAuthorResult.error;
      if (bookCategoryResult.error) throw bookCategoryResult.error;

      const authorMatches = (authorsResult.data || [])
        .map((author) => ({
          ...author,
          _score: Math.max(
            scoreTextMatch(q, author.authorName),
            scoreTextMatch(q, author.biography || '')
          ),
        }))
        .filter((author) => author._score > 0)
        .sort((left, right) => right._score - left._score);

      const categoryScores = new Map(
        (categoriesResult.data || [])
          .map((category) => [category.categoryID, scoreTextMatch(q, category.categoryName)])
          .filter(([, score]) => score > 0)
      );

      const authorScoreMap = new Map(authorMatches.map((author) => [author.authorID, author._score]));
      const bookAuthorScoreMap = new Map();
      const bookCategoryScoreMap = new Map();

      (bookAuthorResult.data || []).forEach((link) => {
        const score = authorScoreMap.get(link.idAuthor) || 0;
        if (score > 0) {
          bookAuthorScoreMap.set(link.idBook, Math.max(bookAuthorScoreMap.get(link.idBook) || 0, score));
        }
      });

      (bookCategoryResult.data || []).forEach((link) => {
        const score = categoryScores.get(link.idCategory) || 0;
        if (score > 0) {
          bookCategoryScoreMap.set(link.idBook, Math.max(bookCategoryScoreMap.get(link.idBook) || 0, score));
        }
      });

      const matchedBooks = (booksResult.data || [])
        .map((book) => {
          const titleScore = scoreTextMatch(q, book.title);
          const authorTextScore = scoreTextMatch(q, book.author || '');
          const relationAuthorScore = bookAuthorScoreMap.get(book.bookID) || 0;
          const categoryScore = bookCategoryScoreMap.get(book.bookID) || 0;

          return {
            ...book,
            _score: Math.max(
              titleScore,
              authorTextScore > 0 ? authorTextScore + 10 : 0,
              relationAuthorScore > 0 ? relationAuthorScore + 12 : 0,
              categoryScore > 0 ? categoryScore + 6 : 0
            ),
          };
        })
        .filter((book) => book._score > 0)
        .sort((left, right) => right._score - left._score);

      const books = await enrichBooksWithMetrics(matchedBooks.map(({ _score, ...book }) => book));

      res.status(200).json({
        authorMatches: authorMatches.slice(0, 12).map(({ _score, ...author }) => author),
        data: books,
      });
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getAuthorDetail: async (req, res) => {
    const { id } = req.params;
    try {
      const { data, error } = await supabase.from('Authors').select('*').eq('authorID', id).single();
      if (error) throw error;
      res.status(200).json(data);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getBooksByAuthor: async (req, res) => {
    const { id } = req.params;
    try {
      const { data: authorBooks, error: authorBooksError } = await supabase
        .from('BookAuthor')
        .select('idBook')
        .eq('idAuthor', id);

      if (authorBooksError) throw authorBooksError;
      if (!authorBooks || authorBooks.length === 0) return res.status(200).json([]);

      const { data, error } = await supabase
        .from('Book')
        .select('*, BookImages(imageURL, isPrimary)')
        .in('bookID', authorBooks.map((item) => item.idBook));

      if (error) throw error;
      res.status(200).json(await enrichBooksWithMetrics(data || []));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },

  getBooksByPublisher: async (req, res) => {
    const { id } = req.params;
    try {
      const { data: publisherBooks, error: publisherBooksError } = await supabase
        .from('BookPublisher')
        .select('idBook')
        .eq('idPublisher', id);

      if (publisherBooksError) throw publisherBooksError;
      if (!publisherBooks || publisherBooks.length === 0) return res.status(200).json([]);

      const { data, error } = await supabase
        .from('Book')
        .select('*, BookImages(imageURL, isPrimary)')
        .in('bookID', publisherBooks.map((item) => item.idBook));

      if (error) throw error;
      res.status(200).json(await enrichBooksWithMetrics(data || []));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  },
};

module.exports = bookController;
