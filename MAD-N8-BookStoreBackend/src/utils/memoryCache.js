const cacheStore = new Map();
const evictionTimers = new Map();

const clearTimer = (key) => {
  const timer = evictionTimers.get(key);
  if (timer) {
    clearTimeout(timer);
    evictionTimers.delete(key);
  }
};

const deleteKey = (key) => {
  clearTimer(key);
  cacheStore.delete(key);
};

const set = (key, value, ttlMs = 60 * 60 * 1000) => {
  deleteKey(key);

  const expiresAt = Date.now() + ttlMs;
  cacheStore.set(key, { value, expiresAt });

  const timer = setTimeout(() => {
    cacheStore.delete(key);
    evictionTimers.delete(key);
  }, ttlMs);

  if (typeof timer.unref === 'function') {
    timer.unref();
  }

  evictionTimers.set(key, timer);
  return value;
};

const get = (key) => {
  const entry = cacheStore.get(key);
  if (!entry) return null;

  if (entry.expiresAt <= Date.now()) {
    deleteKey(key);
    return null;
  }

  return entry.value;
};

const has = (key) => get(key) !== null;

module.exports = {
  get,
  set,
  has,
  delete: deleteKey,
};
