"""Minimal seekable read-only HTTP file (Range requests) so pyarrow can read selected parquet row groups."""

from __future__ import annotations

import io
import time
import urllib.request


class HttpRangeFile(io.RawIOBase):
    def __init__(self, url: str, block: int = 1 << 18):
        super().__init__()
        self.url = url
        self.pos = 0
        self.block = block
        self._cache_start = -1
        self._cache = b""
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req, timeout=60) as r:
            self.size = int(r.headers["Content-Length"])
        self.bytes_fetched = 0

    def readable(self) -> bool:
        return True

    def seekable(self) -> bool:
        return True

    def tell(self) -> int:
        return self.pos

    def seek(self, offset: int, whence: int = 0) -> int:
        if whence == 0:
            self.pos = offset
        elif whence == 1:
            self.pos += offset
        else:
            self.pos = self.size + offset
        return self.pos

    def _fetch(self, start: int, end: int) -> bytes:
        for attempt in range(6):
            try:
                req = urllib.request.Request(self.url, headers={"Range": f"bytes={start}-{end - 1}"})
                with urllib.request.urlopen(req, timeout=120) as r:
                    data = r.read()
                self.bytes_fetched += len(data)
                return data
            except Exception:  # network hiccup: retry with backoff
                if attempt == 5:
                    raise
                time.sleep(2 ** attempt)
        raise RuntimeError("unreachable")

    def read(self, n: int = -1) -> bytes:
        if n is None or n < 0:
            n = self.size - self.pos
        n = min(n, self.size - self.pos)
        if n <= 0:
            return b""
        start, end = self.pos, self.pos + n
        cs = self._cache_start
        if cs >= 0 and start >= cs and end <= cs + len(self._cache):
            data = self._cache[start - cs:end - cs]
        elif n >= self.block:
            data = self._fetch(start, end)
        else:
            fend = min(self.size, start + self.block)
            self._cache = self._fetch(start, fend)
            self._cache_start = start
            data = self._cache[:n]
        self.pos = end
        return data

    def readinto(self, b) -> int:
        data = self.read(len(b))
        b[:len(data)] = data
        return len(data)
