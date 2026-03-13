const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 5001;
const HOST = '::';
const BASE_DIR = path.join(__dirname, 'docs/design/prototypes');

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.js':   'application/javascript',
    '.css':  'text/css',
    '.json': 'application/json',
    '.png':  'image/png',
    '.jpg':  'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif':  'image/gif',
    '.svg':  'image/svg+xml',
    '.ico':  'image/x-icon',
    '.woff': 'font/woff',
    '.woff2':'font/woff2',
    '.ttf':  'font/ttf',
    '.eot':  'application/vnd.ms-fontobject',
};

const server = http.createServer((req, res) => {
    const parsedUrl = url.parse(req.url);
    let pathname = decodeURIComponent(parsedUrl.pathname);

    if (pathname === '/') pathname = '/index.html';

    const filePath = path.join(BASE_DIR, pathname);

    if (!filePath.startsWith(BASE_DIR)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    fs.stat(filePath, (statErr, stats) => {
        if (statErr || !stats.isFile()) {
            res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('404 Not Found: ' + pathname);
            return;
        }

        const ext = path.extname(filePath).toLowerCase();
        const contentType = MIME_TYPES[ext] || 'application/octet-stream';

        const isText = contentType.startsWith('text') ||
                       contentType.includes('javascript') ||
                       contentType.includes('json') ||
                       contentType.includes('svg');

        res.writeHead(200, {
            'Content-Type': contentType,
            'Access-Control-Allow-Origin': '*',
        });

        if (isText) {
            fs.readFile(filePath, 'utf8', (err, data) => {
                if (err) { res.end('Error reading file'); return; }
                res.end(data);
            });
        } else {
            fs.createReadStream(filePath).pipe(res);
        }
    });
});

server.listen(PORT, HOST, () => {
    console.log(`Prototype server running at http://localhost:${PORT}/`);
});

server.on('error', (err) => {
    console.error('Server error:', err.message);
    process.exit(1);
});
