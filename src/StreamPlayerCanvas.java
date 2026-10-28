import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.VolumeControl;
import java.io.*;
import java.util.Vector;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.media.control.VideoControl;

class Log {
    private static final long T0 = System.currentTimeMillis();
    private static final int MAX = 150;
    private static final Vector lines = new Vector();
    private static long rxBytes = 0;
static synchronized void rx(int n) { rxBytes += n; }
static synchronized long rx() { return rxBytes; }

    static void d(String s) {
        String m = (System.currentTimeMillis() - T0) + "ms " + s;
        System.out.println(m);
        synchronized (lines) {
            lines.addElement(m);
            while (lines.size() > MAX) lines.removeElementAt(0);
        }
    }

    static String dump() {
        StringBuffer sb = new StringBuffer();
        synchronized (lines) {
            for (int i = 0; i < lines.size(); i++) {
                sb.append((String) lines.elementAt(i)).append('\n');
            }
        }
        if (sb.length() == 0) sb.append("(empty)");
        return sb.toString();
    }
}

public class StreamPlayerCanvas extends Canvas implements Runnable, CommandListener {
    private final Midlet midlet;
    private final String streamUrl;
    private final String audioUrl;
    private int[] srcBuf, outBuf, xmap;
    private int mapSw = -1, mapW = -1;
    private boolean sliding = false;
    private static final int SL_X = 55;
    private int topBarH()    { return getHeight() < 220 ? 20 : 35; }
    private int bottomBarH() { return getHeight() < 220 ? 50 : 76; }
    private int rowH()       { return getHeight() < 220 ? 21 : 32; }
    private int btnH()       { return getHeight() < 220 ? 20 : 26; }
    private int rowY()       { return getHeight() - bottomBarH() + (getHeight() < 220 ? 2 : 4); }
    private int btnY()       { return getHeight() - bottomBarH() + (getHeight() < 220 ? 26 : 40); }
    private Image currentImage;
    private volatile boolean running = true;
    private HttpConnection conn;
    private InputStream is;
    private byte[] frameBuffer = new byte[16384];
    private final Object paintLock = new Object();
    private boolean started = false;
    private int frameCount = 0;
    private long fpsWindowStart = 0;
    private int lastFps = 0;
    private long lastFrameTime = 0;
    private String audioStatus = "Loading audio...";
    private Command backCmd = new Command("Back", Command.BACK, 1);
    private Command selectCmd = new Command("Select", Command.OK, 1);
    private Command logsCmd = new Command("Logs", Command.SCREEN, 2);
    private AudioStreamPlayer audioPlayer;
    private int frameIndex = 0;
    private boolean firstFrameLogged = false;
    private static final int FRAME_MS = 100;
    private static final int TOLERANCE_MS = 100;
    private static final int BTN = 32;
    private static final int PAD = 6;
    private boolean dragOk = false;
    private static final int PREBUFFER_FRAMES = 5;
    private final Vector prebuf = new Vector();
    private boolean audioGo = false;
    private int touchY = -1;
    private int touchVol = -1;
    private final byte[] rbuf = new byte[4096];
    private int rpos = 0, rlen = 0;
    private boolean uiVisible = false;
    private long uiShownAt = 0;
    private static final int CORNER = 40;
    private static final int UI_TIMEOUT = 4000;
    private int uiRow = 1;
    private volatile boolean paused = false;
    private boolean rotated = false;
    private Image rawImage;
    private int dotX = 0;
    private volatile long lastFrameAt = 0;
private volatile boolean fetchHeld = false;
private volatile boolean stallPaused = false;
private volatile boolean inFrame = false;
private volatile boolean lagStall = false;
private volatile long syncWaitSince = 0;
private static final int LAG_PAUSE_MS = 600;
private final Object stallLock = new Object();
private volatile int shownCount = 0;
private int winCount = 0, prevCnt = -1, sameWindows = 0;
private long winStart = 0;
private static final int LOW_FPS = 8;
private static final int FREEZE_SECONDS = 5;
private final String rateUrl;
private static final int[] LV_KBPS = { 20, 31, 55, 80, 115, 160 };
private int curLv = 2, lowTicks = 0, goodTicks = 0;
private long lastLvChange = 0;
private Image splashImage;

private void toggleRotate() {
    rotated = !rotated;
    Image r = rawImage;
    if (r != null) {
        try {
            Image f = fitWidth(r);
            synchronized (paintLock) { currentImage = f; }
        } catch (Throwable t) {}
    }
    repaint();
}

private void setLevel(final int lv) {
    if (lv < 0 || lv >= LV_KBPS.length || lv == curLv) return;
    curLv = lv;
    lowTicks = 0;
    goodTicks = 0;
    lastLvChange = System.currentTimeMillis();
    Log.d("auto rate -> " + lv);
    new Thread(new Runnable() {
        public void run() {
            HttpConnection c = null;
            try {
                c = (HttpConnection) Connector.open(rateUrl + "&l=" + lv);
                c.getResponseCode();
            } catch (Exception e) {
            } finally {
                try { if (c != null) c.close(); } catch (IOException e) {}
            }
        }
    }).start();
}

private void autoRate() {
    if (paused || !audioGo) return;
    if (System.currentTimeMillis() - lastLvChange < 5000) return;
    int expect = (LV_KBPS[curLv] + 8) / 8;
    if (stallPaused || speedKB * 10 < expect * 8) { lowTicks++; goodTicks = 0; }
    else if (speedKB * 10 >= expect * 9) { goodTicks++; lowTicks = 0; }
    else { lowTicks = 0; }
    if (lowTicks >= 3) setLevel(curLv - 1);
    else if (goodTicks >= 15) setLevel(curLv + 1);
}

    public StreamPlayerCanvas(Midlet midlet, String jobId) {
        try {
            splashImage = Image.createImage("/splash.png");
        } catch (Exception e) {
            splashImage = null;
        }
        this.midlet = midlet;
this.streamUrl = Midlet.mainurl + Midlet.phpdlp + "?action=stream&job=" + jobId;
this.audioUrl  = Midlet.mainurl + Midlet.phpdlp + "?action=audio&job=" + jobId;
this.rateUrl   = Midlet.mainurl + Midlet.phpdlp + "?action=rate&job=" + jobId;
addCommand(backCmd);
        addCommand(selectCmd);
        addCommand(logsCmd);
        setCommandListener(this);
        Log.d("start job " + jobId);

        audioPlayer = new AudioStreamPlayer(audioUrl, new AudioStreamPlayer.ReadyListener() {
            public void onAudioReady() { setAudioStatus("Audio ready"); }
            public void onAudioFailed(String reason) {
                Log.d("audio FAIL: " + reason);
                setAudioStatus("No audio");
            }
        });
        audioPlayer.start();
        startStreaming();
        startTicker();
    }
    
private int uiCol = 0;

private void togglePause() {
    paused = !paused;
    if (audioPlayer != null) {
        if (paused) audioPlayer.pause(); else if (!stallPaused) audioPlayer.resume();
    }
    repaint();
}

private void goBack() {
    stopAll();
    midlet.showDetails();
}

private void activate() {
    if (uiCol == 0) togglePause();
    else if (uiCol == 1) toggleRotate();
    else goBack();
}

public void commandAction(Command c, Displayable d) {
    if (c == backCmd) {
        goBack();
    } else if (c == logsCmd) {
        showLogs();
    } else if (c == selectCmd) {
        if (!uiVisible) {
            uiVisible = true;
            uiRow = 1;
            uiCol = 0;
            uiShownAt = System.currentTimeMillis();
            repaint();
        } else {
            uiShownAt = System.currentTimeMillis();
            if (uiRow == 1) activate();
            else repaint();
        }
    }
}

    private void showLogs() {
        final Display display = Display.getDisplay(midlet);
        final Command closeCmd = new Command("Close", Command.BACK, 1);
        final Command refreshCmd = new Command("Refresh", Command.SCREEN, 2);
        final Form f = new Form("Logs");
        f.append(Log.dump());
        f.addCommand(closeCmd);
        f.addCommand(refreshCmd);
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c == refreshCmd) {
                    f.deleteAll();
                    f.append(Log.dump());
                } else {
                    display.setCurrent(StreamPlayerCanvas.this);
                }
            }
        });
        display.setCurrent(f);
    }

    public void stopAll() {
        running = false;
        closeQuietly();
        if (audioPlayer != null) audioPlayer.stop();
    }

private void setAudioStatus(String status) {
    this.audioStatus = status;
    repaint();
}

    private synchronized void startStreaming() {
        if (started) return;
        started = true;
        new Thread(this).start();
    }

    private void closeQuietly() {
        try { if (is != null) is.close(); } catch (IOException e) {}
        try { if (conn != null) conn.close(); } catch (IOException e) {}
    }

    private void pause(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {}
    }

    public void run() {
        while (running) {
            try {
                Log.d("video connect");
                conn = (HttpConnection) Connector.open(streamUrl, Connector.READ);
                conn.setRequestMethod(HttpConnection.GET);
                int code = conn.getResponseCode();
                Log.d("video HTTP " + code);
                if (code != HttpConnection.HTTP_OK) {
                    closeQuietly();
                    pause(1000);
                    continue;
                }
                String boundary = extractBoundary(conn.getHeaderField("Content-Type"));
                if (boundary == null) {
                    Log.d("video no boundary");
                    closeQuietly();
                    pause(1000);
                    continue;
                }
                is = conn.openInputStream();
                frameIndex = 0;
                rpos = 0; rlen = 0;
                readMultipartStream(is, boundary);
                if (!audioGo && prebuf.size() > 0) { waitAudioReady(); releaseAudioAndDrain(); }
            } catch (Exception e) {
                Log.d("video ERR " + e);
                e.printStackTrace();
                pause(1000);
            } finally {
                closeQuietly();
            }
        }
    }

    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        int idx = contentType.indexOf("boundary=");
        if (idx == -1) return null;
        String b = contentType.substring(idx + "boundary=".length()).trim();
        int semi = b.indexOf(';');
        if (semi != -1) b = b.substring(0, semi).trim();
        if (b.startsWith("\"") && b.endsWith("\"")) b = b.substring(1, b.length() - 1);
        return b;
    }

private int readByte(InputStream in) throws IOException {
    if (rpos >= rlen) {
        rlen = in.read(rbuf, 0, rbuf.length);
        rpos = 0;
        if (rlen <= 0) { rlen = 0; return -1; }
        Log.rx(rlen);
    }
    return rbuf[rpos++] & 0xFF;
}

private int readBlock(InputStream in, byte[] b, int off, int len) throws IOException {
    if (rpos < rlen) {
        int n = Math.min(len, rlen - rpos);
        System.arraycopy(rbuf, rpos, b, off, n);
        rpos += n;
        return n;
    }
    int n = in.read(b, off, len);
    if (n > 0) Log.rx(n);
    return n;
}
    
private void waitAudioReady() {
    long t0 = System.currentTimeMillis();
    while (running && audioPlayer != null && !audioPlayer.isSettled()
            && System.currentTimeMillis() - t0 < 15000) {
        pause(50);
    }
}

    private void readMultipartStream(InputStream in, String boundary) throws IOException {
        String delimiter = "--" + boundary;
        while (running) {
            String line = readLine(in);
            if (line == null) return;
            if (line.equals(delimiter + "--")) return;
            if (!line.startsWith(delimiter)) continue;

            int contentLength = -1;
            String headerLine;
            while ((headerLine = readLine(in)) != null && headerLine.length() > 0) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(headerLine.substring(headerLine.indexOf(':') + 1).trim()); }
                    catch (NumberFormatException nfe) { contentLength = -1; }
                }
            }
            if (contentLength <= 0) continue;
            if (contentLength > frameBuffer.length) frameBuffer = new byte[contentLength + 4096];
            int offset = 0, read;
            while (offset < contentLength
                    && (read = readBlock(in, frameBuffer, offset, contentLength - offset)) != -1) {
                offset += read;
            }
            if (offset < contentLength) return;
            readLine(in);

            if (!firstFrameLogged) {
                firstFrameLogged = true;
                Log.d("first video frame (" + contentLength + "B)");
            }

if (!audioGo) {
    byte[] copy = new byte[contentLength];
    System.arraycopy(frameBuffer, 0, copy, 0, contentLength);
    prebuf.addElement(copy);
    if (prebuf.size() >= PREBUFFER_FRAMES) {
        waitAudioReady();
        releaseAudioAndDrain();
    }
    continue;
}
            showFrame(frameBuffer, contentLength);
        }
    }

    private void showPreview(byte[] data) {
        try { updateImage(Image.createImage(data, 0, data.length)); }
        catch (Exception e) {}
    }

private void checkStall() {
    if (paused || lastFrameAt == 0 || inFrame) return;
    if (System.currentTimeMillis() - lastFrameAt > 1000) stallOn(false);
}

private void stallOn(boolean lag) {
    synchronized (stallLock) {
        if (stallPaused || paused || audioPlayer == null) return;
        if (!lag && inFrame) return;
        stallPaused = true;
        lagStall = lag;
        fetchHeld = true;
        audioPlayer.holdFetch(true);
        audioPlayer.pause();
    }
    repaint();
}

private void stallOff() {
    synchronized (stallLock) {
        if (!stallPaused) return;
        stallPaused = false;
        lagStall = false;
        fetchHeld = false;
        if (audioPlayer != null) {
            audioPlayer.holdFetch(false);
            if (!paused) audioPlayer.resume();
        }
    }
    repaint();
}

private void showFrame(byte[] data, int len) {
    inFrame = true;
    try {
        if (stallPaused && !lagStall) stallOff();
        boolean noFrameYet;
        synchronized (paintLock) { noFrameYet = (currentImage == null); }
        boolean onTime = syncFrame();
        if (!onTime && !noFrameYet) return;
        Image img;
        try { img = Image.createImage(data, 0, len); }
        catch (Exception e) { return; }
        updateImage(img);
    } finally {
        lastFrameAt = System.currentTimeMillis();
        inFrame = false;
    }
}

    private void releaseAudioAndDrain() {
        audioGo = true;
        Log.d("video prebuffer done -> release audio");
        if (audioPlayer != null) audioPlayer.startPlayback();
        for (int i = 0; i < prebuf.size() && running; i++) {
            byte[] f = (byte[]) prebuf.elementAt(i);
            showFrame(f, f.length);
        }
        prebuf.removeAllElements();
    }

    private boolean hasBuffered() {
        try { return rpos < rlen || (is != null && is.available() > 0); }
        catch (IOException e) { return false; }
    }

    private boolean syncFrame() {
while (paused && running) {
    try { Thread.sleep(50); } catch (InterruptedException e) {}
}
        long target = (long) frameIndex * FRAME_MS;
        frameIndex++;

        long audio = (audioPlayer != null) ? audioPlayer.getPositionMs() : -1;
        if (audio < 0 && audioPlayer != null) {
            long t0 = System.currentTimeMillis();
            while (audio < 0 && running && audioPlayer.isWaiting()
                    && System.currentTimeMillis() - t0 < 8000) {
                try { Thread.sleep(20); } catch (InterruptedException e) {}
                audio = audioPlayer.getPositionMs();
            }
        }
        if (audio < 0) {
            long elapsed = System.currentTimeMillis() - lastFrameTime;
            if (elapsed < FRAME_MS) {
                try { Thread.sleep(FRAME_MS - elapsed); } catch (InterruptedException e) {}
            }
            lastFrameTime = System.currentTimeMillis();
            return true;
        }

            long diff = target - audio;

    if (stallPaused && lagStall) {
        if (diff >= 0) stallOff();
        else return true;
    } else if (diff < -LAG_PAUSE_MS && diff > -5000) {
        stallOn(true);
        if (stallPaused) return true;
    }

    if (diff < -TOLERANCE_MS) return !hasBuffered();
    syncWaitSince = System.currentTimeMillis();
    while (diff > 0 && running && !stallPaused) {
        try { Thread.sleep(Math.min(diff, 50)); } catch (InterruptedException e) {}
        audio = audioPlayer.getPositionMs();
        if (audio < 0) break;
        diff = target - audio;
    }
    syncWaitSince = 0;
    return true;
}
    
private void drawWaiting(Graphics g) {
    Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
    String s = "buffering...";
    int w = f.stringWidth(s) + 16, h = f.getHeight() + 8;
    int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
    g.setColor(0x1A1A1A);
    g.fillRoundRect(x, y, w, h, 8, 8);
    g.setColor(0xFFFFFF);
    g.setFont(f);
    g.drawString(s, getWidth() / 2, y + 4, Graphics.TOP | Graphics.HCENTER);
}

private Image fitWidth(Image src) {
    int w = rotated ? getHeight() : getWidth();
    int sw = src.getWidth(), sh = src.getHeight();
    if (sw == w) return src;
    int nh = sh * w / sw;
    if (srcBuf == null || srcBuf.length < sw * sh) srcBuf = new int[sw * sh];
    if (outBuf == null || outBuf.length < w * nh) outBuf = new int[w * nh];
    if (mapSw != sw || mapW != w) {
        xmap = new int[w];
        for (int x = 0; x < w; x++) xmap[x] = x * sw / w;
        mapSw = sw;
        mapW = w;
    }
    src.getRGB(srcBuf, 0, sw, 0, 0, sw, sh);
    int lastSy = -1;
    for (int y = 0; y < nh; y++) {
        int sy = y * sh / nh;
        int o = y * w;
        if (sy == lastSy) {
            System.arraycopy(outBuf, o - w, outBuf, o, w);
        } else {
            int so = sy * sw;
            for (int x = 0; x < w; x++) outBuf[o + x] = srcBuf[so + xmap[x]];
            lastSy = sy;
        }
    }
    return Image.createRGBImage(outBuf, w, nh, false);
}

private void checkFps() {
    long now = System.currentTimeMillis();
    if (winStart == 0) { winStart = now; winCount = shownCount; return; }
    if (now - winStart < 1000) return;
    int cnt = shownCount - winCount;
    winCount = shownCount;
    winStart = now;

    if (!audioGo || paused || stallPaused || shownCount == 0) {
        prevCnt = -1;
        sameWindows = 0;
        return;
    }

    if (cnt <= LOW_FPS) {
        if (cnt == prevCnt) sameWindows++;
        else sameWindows = 1;
    } else {
        sameWindows = 0;
    }
    prevCnt = cnt;

    if (sameWindows >= FREEZE_SECONDS) {
        Log.d("freeze detected, fps stuck at " + cnt);
        sameWindows = 0;
        prevCnt = -1;
        stallOn(inFrame);
    }
}

private void updateImage(Image img) {
    if (!running) return;
    shownCount++;
    rawImage = img;
    try { img = fitWidth(img); } catch (Throwable t) {}
    synchronized (paintLock) { currentImage = img; }
    repaint();
    trackFps();
}

    private final byte[] lineBuf = new byte[256];
    private String readLine(InputStream in) throws IOException {
        int count = 0, ch;
        while ((ch = readByte(in)) != -1) {
            if (ch == '\n') break;
            if (ch != '\r' && count < lineBuf.length) lineBuf[count++] = (byte) ch;
        }
        if (count == 0 && ch == -1) return null;
        return new String(lineBuf, 0, count);
    }

    private void trackFps() {
        long now = System.currentTimeMillis();
        if (fpsWindowStart == 0) fpsWindowStart = now;
        frameCount++;
        if (now - fpsWindowStart >= 1000) { lastFps = frameCount; frameCount = 0; fpsWindowStart = now; }
    }

private int hitButton(int x, int y) {
    int by = getHeight() - BTN - PAD;
    if (y < by || y > by + BTN) return 0;
    int plusX = getWidth() - BTN - PAD;
    int minusX = plusX - BTN - PAD;
    if (x >= PAD && x <= PAD + BTN) return 1;
    if (x >= minusX && x <= minusX + BTN) return 2;
    if (x >= plusX && x <= plusX + BTN) return 3;
    return 0;
}

private void drawButtons(Graphics g) {
    int by = getHeight() - BTN - PAD;
    int plusX = getWidth() - BTN - PAD;
    int minusX = plusX - BTN - PAD;
    int[] xs = { PAD, minusX, plusX };
    String[] labels = { "<", "-", "+" };
    for (int i = 0; i < 3; i++) {
        g.setColor(255, 255, 255);
        g.drawRoundRect(xs[i] - 1, by - 1, BTN + 2, BTN + 2, 10, 10);
        g.setColor(0, 0, 0);
        g.drawRoundRect(xs[i], by, BTN, BTN, 10, 10);
        g.drawString(labels[i], xs[i] + BTN / 2, by + (BTN - g.getFont().getHeight()) / 2,
                Graphics.HCENTER | Graphics.TOP);
    }
}

private void drawUI(Graphics g) {
    int w = getWidth(), h = getHeight();
    int tb = topBarH(), bb = bottomBarH(), barY = h - bb;
    Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    g.setFont(f);

    g.setColor(0x1A1A1A);
    g.fillRect(0, 0, w, tb);
    g.setColor(0xCC0000);
    g.drawLine(0, tb, w, tb);
    g.setColor(0xFFFFFF);
    int ty = (tb - f.getHeight()) / 2;
    g.drawString(lastFps + " fps", 10, ty, Graphics.TOP | Graphics.LEFT);
    g.drawString(audioStatus, w - 10, ty, Graphics.TOP | Graphics.RIGHT);

    g.setColor(0x1A1A1A);
    g.fillRect(0, barY, w, bb);
    g.setColor(0xCC0000);
    g.drawLine(0, barY, w, barY);

    int vol = (audioPlayer != null) ? audioPlayer.getVolume() : -1;
    if (vol < 0) vol = 0;
    int ry = rowY();
    int slW = w - 2 * SL_X;
    int sy = ry + (h < 220 ? 10 : 14);
    int tY = ry + (h < 220 ? 4 : 8);
    if (uiRow == 0) {
        g.setColor(0x2A2A2A);
        g.fillRoundRect(5, ry, w - 10, rowH(), 6, 6);
    }
    g.setColor(0xCCCCCC);
    g.drawString("VOL", 10, tY, Graphics.TOP | Graphics.LEFT);
    g.drawString(vol + "%", w - 10, tY, Graphics.TOP | Graphics.RIGHT);
    g.setColor(0x444444);
    g.fillRect(SL_X, sy, slW, 4);
    int fw = vol * slW / 100;
    g.setColor(0xCC0000);
    g.fillRect(SL_X, sy, fw, 4);
    g.setColor(0xFFFFFF);
    g.fillRoundRect(SL_X + fw - 4, sy - 3, 8, 11, 4, 4);

    int m = 6, by = btnY(), bh = btnH();
int bw = (w - 4 * m) / 3;
Font bf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
g.setFont(bf);
String[] lb = { paused ? "Play" : "Pause", "Rotate", "Back" };
for (int i = 0; i < 3; i++) {
    int bx = m + i * (bw + m);
    g.setColor(uiRow == 1 && uiCol == i ? 0xCC0000 : 0x333333);
    g.fillRoundRect(bx, by, bw, bh, 6, 6);
    g.setColor(0xFFFFFF);
    g.drawString(lb[i], bx + bw / 2, by + (bh - bf.getHeight()) / 2,
            Graphics.TOP | Graphics.HCENTER);
}
}

private void setSliderVol(int x) {
    if (audioPlayer == null) return;
    int slW = getWidth() - 2 * SL_X;
    int v = (x - SL_X) * 100 / slW;
    v = Math.max(0, Math.min(100, v));
    audioPlayer.setVolume(v);
    repaint();
}

protected void pointerPressed(int x, int y) {
    sliding = false;
    if (!uiVisible) {
        uiVisible = true;
        uiShownAt = System.currentTimeMillis();
        repaint();
        return;
    }
    uiShownAt = System.currentTimeMillis();
    int ry = rowY();
    if (y >= ry && y <= ry + rowH()) {
        uiRow = 0;
        sliding = true;
        setSliderVol(x);
    } else if (y >= btnY() && y <= btnY() + btnH()) {
        uiRow = 1;
        uiCol = Math.min(2, x * 3 / getWidth());
        activate();
        repaint();
    }
}

protected void pointerDragged(int x, int y) {
    if (!sliding) return;
    uiShownAt = System.currentTimeMillis();
    setSliderVol(x);
}

protected void pointerReleased(int x, int y) {
    sliding = false;
}

protected void keyRepeated(int keyCode) {
    if (keyCode != KEY_POUND) keyPressed(keyCode);
}

protected void keyPressed(int keyCode) {
    if (keyCode == KEY_POUND) {
    uiVisible = !uiVisible;
    uiRow = 1;
    uiCol = 0;
    uiShownAt = System.currentTimeMillis();
    repaint();
    return;
}
    if (!uiVisible) return;
    uiShownAt = System.currentTimeMillis();
    int action = getGameAction(keyCode);
    boolean left  = action == LEFT  || keyCode == KEY_NUM4 || keyCode == -3;
    boolean right = action == RIGHT || keyCode == KEY_NUM6 || keyCode == -4;
    if (action == UP || keyCode == KEY_NUM2 || keyCode == -1) {
        uiRow = 0;
    } else if (action == DOWN || keyCode == KEY_NUM8 || keyCode == -2) {
        uiRow = 1;
    } else if (uiRow == 0 && left) {
        adjustVolume(-10);
    } else if (uiRow == 0 && right) {
        adjustVolume(10);
} else if (uiRow == 1 && left) {
    uiCol = Math.max(0, uiCol - 1);
} else if (uiRow == 1 && right) {
    uiCol = Math.min(2, uiCol + 1);
} else if (uiRow == 1 && (action == FIRE || keyCode == KEY_NUM5 || keyCode == -5)) {
    activate();
    return;
}
    repaint();
}

    private void adjustVolume(int delta) {
        if (audioPlayer != null) {
            int current = audioPlayer.getVolume();
            if (current != -1) {
                int newVol = Math.max(0, Math.min(100, current + delta));
                audioPlayer.setVolume(newVol);
                setAudioStatus("Volume: " + newVol + "%");
            }
        }
    }

protected void paint(Graphics g) {
    Image img;
    synchronized (paintLock) { img = currentImage; }
    if (img == null || rotated || img.getWidth() < getWidth() || img.getHeight() < getHeight()) {
        g.setColor(255, 255, 255);
        g.fillRect(0, 0, getWidth(), getHeight());
    }
    if (img != null) {
        if (rotated) {
            g.drawRegion(img, 0, 0, img.getWidth(), img.getHeight(), Sprite.TRANS_ROT90,
                    getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.VCENTER);
        } else {
            g.drawImage(img, getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.VCENTER);
        }
        } else {
                g.setColor(0, 0, 0);
                g.fillRect(0, 0, getWidth(), getHeight());
                if (splashImage != null) {
                    g.drawImage(splashImage, getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.VCENTER);
                }
            //drawWaiting(g);
        }
    long now = System.currentTimeMillis();
if (img != null && !paused
        && (stallPaused || (syncWaitSince != 0 && now - syncWaitSince > 1000))) {
    drawWaiting(g);
}
    if (uiVisible && System.currentTimeMillis() - uiShownAt > UI_TIMEOUT) uiVisible = false;
    if (uiVisible) drawUI(g);
    else {
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(sf);
        String sp = speedKB + " KB/s";
        int sw = sf.stringWidth(sp) + 6;
        g.setColor(0x000000);
        g.fillRect(getWidth() - sw, 0, sw, sf.getHeight() + 2);
        g.setColor(0xFFFFFF);
        g.drawString(sp, getWidth() - 3, 1, Graphics.TOP | Graphics.RIGHT);
    }

    g.setColor(255, 0, 0);
    g.fillRect(dotX, 0, 2, 2);
    midlet.keepLight();
}

private long spLastRx = 0, spLastT = 0;
private int speedKB = 0;

private void startTicker() {
    new Thread(new Runnable() {
        public void run() {
            while (running) {
                dotX = (dotX + 3) % getWidth();
checkStall();
//checkFps();
long now = System.currentTimeMillis();
                if (now - spLastT >= 1000) {
    long rx = Log.rx();
    if (spLastT != 0) {
        speedKB = (int) ((rx - spLastRx) * 1000 / (now - spLastT) / 1024);
        autoRate();
    }
    spLastRx = rx;
    spLastT = now;
}
                repaint();
                try { Thread.sleep(250); } catch (InterruptedException e) {}
            }
        }
    }).start();
}
}

class AudioStreamPlayer implements Runnable, PlayerListener {
    interface ReadyListener { void onAudioReady(); void onAudioFailed(String reason); }
    public synchronized boolean isSettled() { return readyNotified; }
    private static final String MIME_TYPE = "audio/amr";
    private static final byte[] AMR_HEADER = { '#', '!', 'A', 'M', 'R', '\n' };
    private static final int[] FRAME_SIZE = { 13, 14, 16, 18, 20, 21, 27, 32, 6, 1, 1, 1, 1, 1, 1, 1 };
    private static final int[] CHUNK_PLAN = { 25, 100, 300, 750 };
    private static final int MAX_QUEUED = 8;
    private static final boolean PRELOAD_NEXT = true;
    private volatile boolean failed = false;
    private volatile boolean fetchHold = false;
private final Object holdLock = new Object();

public void holdFetch(boolean h) {
    synchronized (holdLock) {
        fetchHold = h;
        if (!h) holdLock.notifyAll();
    }
}
    private int chunkNo = 0;
    private static class Chunk {
        final byte[] data; final int frames; final int seq;
        Chunk(byte[] d, int f, int s) { data = d; frames = f; seq = s; }
    }

    private final String audioUrl;
    private final ReadyListener listener;
    private HttpConnection conn;
    private InputStream is;
    private volatile boolean running = true;
    private volatile boolean eof = false;
    private boolean readyNotified = false;

    private final Object queueLock = new Object();
    private final Vector queue = new Vector();

    private final Object endLock = new Object();
    private boolean ended = false;
    private volatile Player current;
    private volatile Player pending;
    private volatile Player retired;
    private volatile int volume = -1;
    private boolean volumeDirty = false;

    private final Object posLock = new Object();
    private long posWall = 0;
    private long posBase = 0;
    private long posLen = 0;
    private static final long LEAD_MS = 30;

    private final Object goLock = new Object();
    private volatile boolean go = false;

private volatile boolean paused = false;
private volatile long shift = 0;
private long pausedAt = 0;

public void pause() {
    synchronized (posLock) {
        if (paused || posWall == 0) return;
        paused = true;
        pausedAt = System.currentTimeMillis();
    }
    try { Player p = current; if (p != null) p.stop(); } catch (Exception e) {}
}

public void resume() {
    synchronized (posLock) {
        if (!paused) return;
        long d = System.currentTimeMillis() - pausedAt;
        posWall += d;
        shift += d;
        paused = false;
    }
    try { Player p = current; if (p != null) p.start(); } catch (Exception e) {}
    synchronized (endLock) { endLock.notifyAll(); }
}

    AudioStreamPlayer(String audioUrl, ReadyListener listener) {
        this.audioUrl = audioUrl;
        this.listener = listener;
    }

    public void start() {
        Thread dl = new Thread(new Runnable() { public void run() { download(); } });
        dl.setPriority(Thread.MIN_PRIORITY);
        dl.start();
        new Thread(this).start();
    }

    public void stop() {
        running = false;
        synchronized (queueLock) { queueLock.notifyAll(); }
        synchronized (endLock) { endLock.notifyAll(); }
        synchronized (goLock) { goLock.notifyAll(); }
        synchronized (holdLock) { holdLock.notifyAll(); }
        closePlayer(current);
        closePlayer(pending);
        closePlayer(retired);
        closeQuietly();
    }

    private void setPos(long wall, long base, long len) {
        synchronized (posLock) { posWall = wall; posBase = base; posLen = len; }
    }

public long getPositionMs() {
    synchronized (posLock) {
        if (posWall == 0 || !running) return -1;
        long now = paused ? pausedAt : System.currentTimeMillis();
        long inChunk = now - posWall;
        if (inChunk > posLen) inChunk = posLen;
        return posBase + inChunk;
    }
}

    public void setVolume(int level) {
        volume = level;
        synchronized (endLock) { volumeDirty = true; endLock.notifyAll(); }
    }

    public int getVolume() { return volume; }

    public void startPlayback() {
        synchronized (goLock) { go = true; goLock.notifyAll(); }
    }

    public boolean isWaiting() {
        synchronized (posLock) { return running && !failed && posWall == 0; }
    }

private void applyVolume(Player p) {
    if (p == null) return;
    try {
        VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
        if (vc == null) return;
        if (volume < 0) volume = vc.getLevel();
        else volume = vc.setLevel(volume);
    } catch (Exception e) {}
}

    private void download() {
        try {
            Log.d("audio connect");
            conn = (HttpConnection) Connector.open(audioUrl, Connector.READ);
            conn.setRequestMethod(HttpConnection.GET);
            int status = conn.getResponseCode();
            Log.d("audio HTTP " + status);
            if (status != HttpConnection.HTTP_OK) { fail("HTTP " + status); return; }
            is = conn.openInputStream();

            byte[] hdr = new byte[AMR_HEADER.length];
            if (!readFully(is, hdr, 0, hdr.length)) { fail("Empty audio"); return; }
            Log.d("audio first bytes (header)");
            for (int i = 0; i < hdr.length; i++)
                if (hdr[i] != AMR_HEADER[i]) { fail("Not AMR-NB"); return; }

            byte[] frame = new byte[32];
            ByteArrayOutputStream out = new ByteArrayOutputStream(9000);
            out.write(AMR_HEADER, 0, AMR_HEADER.length);
            int frames = 0;
            int planIdx = 0;
            int limit = CHUNK_PLAN[0];
            int total = 0;
            int seq = 0;
            while (running) {
    synchronized (holdLock) {
        while (running && fetchHold) {
            try { holdLock.wait(); } catch (InterruptedException e) {}
        }
    }
    if (!running) break;
    int h = is.read();
    if (h == -1) break;
                int size = FRAME_SIZE[(h >> 3) & 0x0F];
                frame[0] = (byte) h;
                if (size > 1 && !readFully(is, frame, 1, size - 1)) break;
                Log.rx(size);
                out.write(frame, 0, size);
                if (++frames >= limit) {
                    total += frames;
                    Log.d("audio chunk queued, " + frames + "f (total " + total + "f)");
                    if (!enqueue(new Chunk(out.toByteArray(), frames, seq++))) return;
                    out.reset();
                    out.write(AMR_HEADER, 0, AMR_HEADER.length);
                    frames = 0;
                    if (planIdx < CHUNK_PLAN.length - 1) planIdx++;
                    limit = CHUNK_PLAN[planIdx];
                }
            }
            if (frames > 0 && running) {
                Log.d("audio last chunk, " + frames + "f");
                enqueue(new Chunk(out.toByteArray(), frames, seq++));
            }
        } catch (Exception e) {
            Log.d("audio dl ERR " + e);
            if (!isReady()) fail("Download: " + e);
        } finally {
            eof = true;
            Log.d("audio download end");
            synchronized (queueLock) { queueLock.notifyAll(); }
            closeQuietly();
        }
    }

    private boolean readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int r = in.read(b, off, len);
            if (r == -1) return false;
            off += r; len -= r;
        }
        return true;
    }

    private boolean enqueue(Chunk c) {
        synchronized (queueLock) {
            while (running && queue.size() >= MAX_QUEUED) {
                try { queueLock.wait(); } catch (InterruptedException e) {}
            }
            if (!running) return false;
            queue.addElement(c);
            queueLock.notifyAll();
            return true;
        }
    }

    private Chunk dequeue() {
        synchronized (queueLock) {
            while (running && queue.isEmpty() && !eof) {
                try { queueLock.wait(); } catch (InterruptedException e) {}
            }
            if (!running || queue.isEmpty()) return null;
            Chunk c = (Chunk) queue.elementAt(0);
            queue.removeElementAt(0);
            queueLock.notifyAll();
            return c;
        }
    }

    public void run() {
        try {
            Chunk curChunk = dequeue();
            if (curChunk == null) { if (running) fail("No audio data"); return; }
            Log.d("audio prepare chunk 0");
            checkSeq(curChunk);
            Player cur = prepareRetry(curChunk);
            if (cur == null) { fail("Cannot decode first chunk"); return; }
            Log.d("audio chunk 0 prepared");
            notifyReady();

            synchronized (goLock) {
                while (running && !go) {
                    try { goLock.wait(); } catch (InterruptedException e) {}
                }
            }
            if (!running) { closePlayer(cur); return; }

            long mediaMs = 0;
            long lenMs = curChunk.frames * 20L;

            synchronized (endLock) { ended = false; }
            current = cur;
            applyVolume(cur);
            cur.start();
            long wallStart = System.currentTimeMillis();
            setPos(wallStart, mediaMs, lenMs);
            long sh0 = shift;
            Log.d("audio PLAY chunk 0 (" + curChunk.frames + "f)");

while (running) {
    Chunk nextChunk = dequeue();
    if (!running) break;
    if (nextChunk == null) {
        long rem = wallStart + lenMs + 1500L - System.currentTimeMillis();
        if (rem > 0) waitEnd(rem);
        Log.d("audio end of stream");
        closePlayer(cur);
        current = null;
        break;
    }
    checkSeq(nextChunk);
    waitUntil(wallStart + lenMs, sh0);
    if (!running) break;
    closePlayer(cur);
    current = null;
    cur = null;

    Player next = prepareRetry(nextChunk);
    mediaMs += lenMs;
    lenMs = nextChunk.frames * 20L;
    sh0 = shift;
    wallStart = System.currentTimeMillis();
    if (next == null) {
        Log.d("audio chunk " + nextChunk.seq + " LOST (cannot decode)");
        setPos(wallStart, mediaMs, lenMs);
        continue;
    }
    synchronized (endLock) { ended = false; }
    current = next;
    applyVolume(next);
    next.start();
    wallStart = System.currentTimeMillis();
    setPos(wallStart, mediaMs, lenMs);
    chunkNo++;
    Log.d("audio PLAY chunk " + chunkNo + " (" + nextChunk.frames + "f)");
    cur = next;
    curChunk = nextChunk;
}
        } catch (MediaException me) {
            if (running) fail("MediaException: " + me.getMessage());
        } catch (Exception e) {
            if (running) fail("Exception: " + e);
        }
    }

    private int expectSeq = 0;

    private void checkSeq(Chunk c) {
        if (c.seq != expectSeq) Log.d("audio SEQ ERROR expected " + expectSeq + " got " + c.seq);
        expectSeq = c.seq + 1;
    }

    private Player prepareRetry(Chunk c) {
        for (int i = 0; i < 3 && running; i++) {
            try { return prepare(c); }
            catch (Exception e) {
                Log.d("audio prepare chunk " + c.seq + " try " + (i + 1) + " failed: " + e);
                try { Thread.sleep(100); } catch (InterruptedException ie) {}
            }
        }
        return null;
    }

    private void waitUntil(long deadline) {
        while (running) {
            boolean dirty;
            synchronized (endLock) {
                long rem = deadline - System.currentTimeMillis();
                if (rem <= 0 && !volumeDirty) return;
                if (!volumeDirty) {
                    try { endLock.wait(rem); } catch (InterruptedException e) {}
                }
                dirty = volumeDirty;
                volumeDirty = false;
            }
            if (dirty) applyVolume(current);
        }
    }
    
    private void waitUntil(long deadline, long sh0) {
    while (running) {
        boolean dirty;
        synchronized (endLock) {
            long rem = deadline + (shift - sh0) - System.currentTimeMillis();
            if (paused) rem = 200;
            else if (rem <= 0 && !volumeDirty) return;
            if (!volumeDirty) {
                try { endLock.wait(rem); } catch (InterruptedException e) {}
            }
            dirty = volumeDirty;
            volumeDirty = false;
        }
        if (dirty) applyVolume(current);
    }
}

    private Player prepare(Chunk c) throws IOException, MediaException {
        Player p = Manager.createPlayer(new ByteArrayInputStream(c.data), MIME_TYPE);
        p.addPlayerListener(this);
        p.realize();
        p.prefetch();
        applyVolume(p);
        return p;
    }

    public void playerUpdate(Player p, String event, Object data) {
        if (p != current) return;
        if (PlayerListener.END_OF_MEDIA.equals(event) || PlayerListener.ERROR.equals(event)) {
            synchronized (endLock) { ended = true; endLock.notifyAll(); }
        }
    }

    private void waitEnd(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (running) {
            boolean dirty;
            synchronized (endLock) {
                if (ended) break;
                if (!volumeDirty) {
                    long rem = deadline - System.currentTimeMillis();
                    if (rem <= 0) break;
                    try { endLock.wait(rem); } catch (InterruptedException e) {}
                }
                dirty = volumeDirty;
                volumeDirty = false;
            }
            if (dirty) applyVolume(current);
        }
    }

    private void closePlayer(Player p) {
        if (p == null) return;
        try { p.removePlayerListener(this); } catch (Exception e) {}
        try { p.stop(); } catch (Exception e) {}
        try { p.close(); } catch (Exception e) {}
    }

    private synchronized boolean isReady() { return readyNotified; }

    private synchronized void notifyReady() {
        if (readyNotified) return;
        readyNotified = true;
        if (listener != null) listener.onAudioReady();
    }

    private void closeQuietly() {
        try { if (is != null) is.close(); } catch (IOException e) {}
        try { if (conn != null) conn.close(); } catch (IOException e) {}
    }

    private void fail(String reason) {
        failed = true;
        Log.d("audio fail: " + reason);
        closeQuietly();
        synchronized (this) {
            if (readyNotified) return;
            readyNotified = true;
        }
        if (listener != null) listener.onAudioFailed(reason);
    }
}