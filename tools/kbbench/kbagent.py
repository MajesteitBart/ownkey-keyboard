"""Host-side client for the on-device benchmark agent (tools/kbbench/agent)."""
import base64
import json
import os
import secrets
import socket
import subprocess
import time

ADB = os.environ.get("ADB", "adb")
PKG = "dev.ownkey.kbagent"

IME_IDS = {
    "gboard": "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
    "ownkey": "nl.bartvandermeeren.ownkey.beta/dev.patrickgold.florisboard.FlorisImeService",
    "ownkey-debug": "nl.bartvandermeeren.ownkey.debug/dev.patrickgold.florisboard.FlorisImeService",
    "samsung": "com.samsung.android.honeyboard/.service.HoneyBoardService",
}


def find_serial():
    out = subprocess.run([ADB, "devices", "-l"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        if "model:SM_S928B" in line:
            return line.split()[0]
    raise RuntimeError("S24 not connected")


class Agent:
    def __init__(self, serial=None, port=7788, nolearn=True):
        self.serial = serial or find_serial()
        self.port = port
        self.nolearn = nolearn
        self.proc = None
        self.sock = None
        self.rf = None
        # The agent injects input and reads the screen: every run gets its own token, sent first on each connection.
        self.token = secrets.token_hex(16)

    def adb(self, *args, check=True):
        return subprocess.run([ADB, "-s", self.serial, *args], capture_output=True, text=True, check=check).stdout

    def start(self):
        self.adb("shell", "am", "force-stop", PKG, check=False)
        self.adb("forward", f"tcp:{self.port}", f"tcp:{self.port}")
        self.proc = subprocess.Popen(
            [ADB, "-s", self.serial, "shell", "am", "instrument", "-w", "-e", "port", str(self.port),
             "-e", "token", self.token,
             "-e", "nolearn", "true" if self.nolearn else "false", f"{PKG}/.Agent"],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        deadline = time.time() + 20
        last = None
        while time.time() < deadline:
            try:
                self.sock = socket.create_connection(("127.0.0.1", self.port), timeout=2)
                self.sock.settimeout(60)
                self.sock.sendall((self.token + "\n").encode("utf-8"))
                self.rf = self.sock.makefile("r", encoding="utf-8")
                self.call("ping")
                return self
            except Exception as e:  # agent not up yet
                last = e
                if self.sock:
                    self.sock.close()
                    self.sock = None
                time.sleep(0.5)
        raise RuntimeError(f"agent did not start: {last}")

    def call(self, op, **kw):
        kw["op"] = op
        self.sock.sendall((json.dumps(kw) + "\n").encode("utf-8"))
        line = self.rf.readline()
        if not line:
            raise RuntimeError("agent connection closed")
        r = json.loads(line)
        if not r.get("ok"):
            raise RuntimeError(f"agent error for {op}: {r.get('error')}")
        return r

    # Convenience wrappers
    def tap(self, x, y, hold=35):
        self.call("tap", x=x, y=y, hold=hold)

    def taps(self, pts, hold=35, gap=60):
        self.call("taps", pts=[[float(x), float(y)] for x, y in pts], hold=hold, gap=gap)

    def text(self):
        return self.call("text")["text"]

    def reset(self, text="", settle=300):
        return self.call("reset", text=text, settle=settle)["text"]

    def ime_nodes(self, all=False):
        return self.call("ime", all=all)["nodes"]

    def windows(self):
        return self.call("windows")["windows"]

    def shot(self, box=None, path=None):
        kw = {}
        if box:
            x, y, w, h = box
            kw = dict(x=x, y=y, w=w, h=h)
        png = base64.b64decode(self.call("shot", **kw)["png"])
        if path:
            with open(path, "wb") as f:
                f.write(png)
        return png

    def set_ime(self, name):
        ime = IME_IDS.get(name, name)
        self.adb("shell", "ime", "set", ime)
        time.sleep(0.8)
        self.reset()
        time.sleep(0.8)

    def close(self):
        try:
            if self.sock:
                try:
                    self.call("quit")
                except Exception:
                    pass
                self.sock.close()
        finally:
            if self.proc:
                self.proc.terminate()
            self.adb("forward", "--remove", f"tcp:{self.port}", check=False)
            self.adb("shell", "am", "force-stop", PKG, check=False)
