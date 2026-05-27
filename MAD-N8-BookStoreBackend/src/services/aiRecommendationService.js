const Groq = require('groq-sdk');

const REQUEST_TIMEOUT_MS = 2500;
const DEFAULT_MODEL = process.env.GROQ_RECOMMENDATION_MODEL || 'llama-3.1-8b-instant';
const groq = process.env.GROQ_API_KEY ? new Groq({ apiKey: process.env.GROQ_API_KEY }) : null;

const withTimeout = async (task, timeoutMs = REQUEST_TIMEOUT_MS) => {
  let timeoutHandle;

  try {
    return await Promise.race([
      task,
      new Promise((_, reject) => {
        timeoutHandle = setTimeout(() => reject(new Error('AI recommendation timeout')), timeoutMs);
        if (typeof timeoutHandle.unref === 'function') {
          timeoutHandle.unref();
        }
      }),
    ]);
  } finally {
    if (timeoutHandle) {
      clearTimeout(timeoutHandle);
    }
  }
};

const parseIdArray = (text = '', candidateIds = new Set()) => {
  if (!text) return [];

  const cleaned = text.replace(/```json|```/gi, '').trim();

  try {
    const parsed = JSON.parse(cleaned);
    if (Array.isArray(parsed)) {
      return parsed
        .map((value) => Number(value))
        .filter((value) => Number.isInteger(value) && candidateIds.has(value));
    }
  } catch (_) {
  }

  const matched = cleaned.match(/\d+/g) || [];
  return matched
    .map((value) => Number(value))
    .filter((value) => Number.isInteger(value) && candidateIds.has(value));
};

const buildBookSummary = (book = {}) => ({
  bookID: book.bookID,
  title: book.title,
  author: book.author || null,
  authors: book.authorNames || [],
  categories: book.categoryNames || [],
  language: book.language || null,
  format: book.format || null,
  groupId: book.idGroup || null,
  price: book.price || 0,
  soldCount: book.soldCount || 0,
  avgRating: book.avgRating || 0,
  heuristicScore: book.similarityScore || 0,
  description: (book.description || '').slice(0, 300),
});

const requestRecommendationIds = async (prompt) => {
  if (!groq) return null;

  const completion = await withTimeout(
    groq.chat.completions.create({
      model: DEFAULT_MODEL,
      temperature: 0.2,
      messages: [
        {
          role: 'system',
          content:
            'You rank bookstore recommendations. Return only a JSON array of numeric book IDs chosen from the provided candidates.',
        },
        {
          role: 'user',
          content: prompt,
        },
      ],
    })
  );

  return completion?.choices?.[0]?.message?.content || '';
};

const findSimilarBooks = async (bookDetail, candidates = []) => {
  if (!groq || !bookDetail || candidates.length < 2) return null;

  const candidateIds = new Set(candidates.map((book) => Number(book.bookID)));
  const prompt = [
    'Rank the candidate books by semantic similarity to the target book.',
    'Prioritize same reading intent, topic, tone, audience, author/category overlap, and physical/price proximity when relevant.',
    'Do not invent IDs. Return up to 12 book IDs as a JSON array only.',
    `Target book: ${JSON.stringify(buildBookSummary(bookDetail))}`,
    `Candidates: ${JSON.stringify(candidates.map(buildBookSummary))}`,
  ].join('\n\n');

  try {
    const raw = await requestRecommendationIds(prompt);
    const ids = parseIdArray(raw, candidateIds);
    return ids.length ? ids.slice(0, 12) : null;
  } catch (_) {
    return null;
  }
};

const recommendForYou = async (userHistoryText, candidates = []) => {
  if (!groq || !userHistoryText || candidates.length < 2) return null;

  const candidateIds = new Set(candidates.map((book) => Number(book.bookID)));
  const prompt = [
    'Rank the candidate books for a personalized "for you" shelf.',
    'Use the user history as the main signal, then balance variety, popularity, and fit.',
    'Do not invent IDs. Return up to 12 book IDs as a JSON array only.',
    `User history: ${userHistoryText}`,
    `Candidates: ${JSON.stringify(candidates.map(buildBookSummary))}`,
  ].join('\n\n');

  try {
    const raw = await requestRecommendationIds(prompt);
    const ids = parseIdArray(raw, candidateIds);
    return ids.length ? ids.slice(0, 12) : null;
  } catch (_) {
    return null;
  }
};

module.exports = {
  recommendForYou,
  findSimilarBooks,
};
