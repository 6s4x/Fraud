import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'fraudoor-dev-secret-change-in-production';

const ACCOUNTS = [
  { username: 'root', password: 'cipa123', role: 'admin' },
  { username: 'rwijkoper', password: 'cwel123', role: 'user' },
  { username: 'spacja', password: 'dupa123', role: 'user' },
];

export function loginHandler(req, res) {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ error: 'username and password required' });
  }
  const account = ACCOUNTS.find(a => a.username === username && a.password === password);
  if (!account) {
    return res.status(401).json({ error: 'invalid credentials' });
  }
  const token = jwt.sign({ username: account.username, role: account.role }, JWT_SECRET, { expiresIn: '24h' });
  res.json({ token, user: { username: account.username, role: account.role } });
}

export function authMiddleware(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'missing authorization' });
  }
  try {
    const decoded = jwt.verify(header.split(' ')[1], JWT_SECRET);
    req.user = decoded;
    next();
  } catch {
    return res.status(401).json({ error: 'invalid token' });
  }
}

export function adminOnly(req, res, next) {
  if (req.user?.role !== 'admin') {
    return res.status(403).json({ error: 'admin required' });
  }
  next();
}
