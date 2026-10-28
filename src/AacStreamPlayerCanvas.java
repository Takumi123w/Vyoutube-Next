import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.VolumeControl;
import java.io.*;
import java.util.Vector;

public class AacStreamPlayerCanvas extends Canvas implements Runnable, CommandListener {
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
    private byte[] frameBuffer = new byte[32768];
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
    private AacStreamPlayer audioPlayer;
    private int frameIndex = 0;
    private boolean firstFrameLogged = false;
    private static final int TOLERANCE_MS = 100;
    private static final int PREBUFFER_FRAMES = 5;
    private final Vector prebuf = new Vector();
    private boolean audioGo = false;
    private final byte[] rbuf = new byte[4096];
    private int rpos = 0, rlen = 0;
    private boolean uiVisible = false;
    private long uiShownAt = 0;
    private static final int UI_TIMEOUT = 4000;
    private int uiRow = 1;
    private int uiCol = 0;
    private volatile boolean paused = false;
    private boolean rotated = false;
    private Image rawImage;
    private final int fps;
    private int dotX = 0;
    private volatile long lastFrameAt = 0;
    private volatile boolean fetchHeld = false;
    private volatile long lastShownAt = 0;
    private volatile boolean stallPaused = false;
    private volatile boolean inFrame = false;
    private final Object stallLock = new Object();
    private volatile boolean lagStall = false;
private volatile long syncWaitSince = 0;
private static final int LAG_PAUSE_MS = 600;
private volatile int shownCount = 0;
private int winCount = 0, prevCnt = -1, sameWindows = 0;
private long winStart = 0;
private static final int LOW_FPS = 8;
private static final int FREEZE_SECONDS = 5;
private final String subUrl;
private long[] subS = new long[0], subE = new long[0];
private String[] subT = new String[0];
private volatile int subN = 0;
private int subI = 0;
private boolean subOn = true;
private String subWhy = "", subLogged = "";
private long subNow = -1;
private final Font subF = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
private String wrapSrc = null;
private int wrapW = -1;
private final Vector wrapLines = new Vector();
private static final boolean SUB_DEBUG = false;
private Image splashImage;

    public AacStreamPlayerCanvas(Midlet midlet, String jobId, boolean hf) {
    try {
            splashImage = Image.createImage("/splash.png");
        } catch (Exception e) {
            splashImage = null;
        }
    this.midlet = midlet;
    this.fps = hf ? 23 : 10;
this.streamUrl = Midlet.mainurl + Midlet.phpdlp + "?action=stream&job=" + jobId;
this.audioUrl  = Midlet.mainurl + Midlet.phpdlp + "?action=audio&job=" + jobId;
this.subUrl    = Midlet.mainurl + Midlet.phpdlp + "?action=sub&job=" + jobId;
        addCommand(backCmd);
        addCommand(selectCmd);
        addCommand(logsCmd);
        setCommandListener(this);
        Log.d("start aac job " + jobId);

        audioPlayer = new AacStreamPlayer(audioUrl, new AudioStreamPlayer.ReadyListener() {
            public void onAudioReady() { setAudioStatus("Audio ready"); }
            public void onAudioFailed(String reason) {
                Log.d("audio FAIL: " + reason);
                setAudioStatus("No audio");
            }
        });
        audioPlayer.start();
        startStreaming();
        startTicker();
        loadSubs();
    }
    
private void loadSubs() {
    new Thread(new Runnable() {
        public void run() {
            subWhy = "loading subs";
            for (int t = 0; t < 25 && running; t++) {
                HttpConnection c = null;
                InputStream in = null;
                try {
                    c = (HttpConnection) Connector.open(subUrl);
                    int code = c.getResponseCode();
Log.d("subs try " + (t + 1) + " HTTP " + code);
if (code != HttpConnection.HTTP_OK) {
    try {
        in = c.openInputStream();
        byte[] eb = new byte[300];
        int en = in.read(eb);
        if (en > 0) Log.d("subs body: " + new String(eb, 0, en));
    } catch (Exception e) {}
}
if (code == HttpConnection.HTTP_OK) {
                        in = c.openInputStream();
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] b = new byte[1024];
                        int n;
                        while ((n = in.read(b)) != -1) bo.write(b, 0, n);
                        String txt = new String(bo.toByteArray(), "UTF-8");
                        Log.d("subs bytes " + bo.size());
                        parseSubs(txt);
                        Log.d("subs parsed " + subN);
                        if (subN > 0) Log.d("sub[0] " + subS[0] + "-" + subE[0] + " " + subT[0]);
                        return;
                    }
                } catch (Exception e) {
                    Log.d("subs ERR " + e);
                } finally {
                    try { if (in != null) in.close(); } catch (IOException e) {}
                    try { if (c != null) c.close(); } catch (IOException e) {}
                }
                try { Thread.sleep(4000); } catch (InterruptedException e) {}
            }
            subWhy = "subs not available";
            Log.d("subs gave up");
        }
    }).start();
}

private long parseTime(String t) {
    t = t.trim();
    int c1 = t.indexOf(':'), c2 = t.indexOf(':', c1 + 1);
    int d = t.indexOf(',');
    if (d < 0) d = t.indexOf('.');
    long h = Long.parseLong(t.substring(0, c1));
    long m = Long.parseLong(t.substring(c1 + 1, c2));
    long s = Long.parseLong(t.substring(c2 + 1, d));
    String ms = t.substring(d + 1);
    while (ms.length() < 3) ms += "0";
    ms = ms.substring(0, 3);
    return ((h * 60 + m) * 60 + s) * 1000 + Long.parseLong(ms);
}

private String stripTags(String s) {
    StringBuffer sb = new StringBuffer();
    boolean in = false;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '<') in = true;
        else if (c == '>') in = false;
        else if (!in) sb.append(c);
    }
    return sb.toString();
}

private void parseSubs(String s) {
    Vector lines = new Vector();
    int p = 0;
    while (p < s.length()) {
        int e = s.indexOf('\n', p);
        if (e < 0) e = s.length();
        lines.addElement(s.substring(p, e).trim());
        p = e + 1;
    }
    int n = lines.size();
    long[] ss = new long[n], ee = new long[n];
    String[] tt = new String[n];
    int k = 0;
    String prev = "";
    for (int i = 0; i < n; i++) {
        String ln = (String) lines.elementAt(i);
        int a = ln.indexOf("-->");
        if (a < 0) continue;
        try {
            long st = parseTime(ln.substring(0, a));
            String rest = ln.substring(a + 3).trim();
            int sp = rest.indexOf(' ');
            if (sp > 0) rest = rest.substring(0, sp);
            long en = parseTime(rest);

            String last = "";
            int j = i + 1;
            while (j < n) {
                String t = (String) lines.elementAt(j);
                if (t.indexOf("-->") >= 0) break;
                if (j + 1 < n && ((String) lines.elementAt(j + 1)).indexOf("-->") >= 0) break;
                t = stripTags(t).trim();
                if (t.length() > 0) last = t;
                j++;
            }
            if (last.length() == 0) continue;
            if (last.equals(prev) && en - st < 120) continue;
            prev = last;
            ss[k] = st; ee[k] = en; tt[k] = last; k++;
        } catch (Exception x) {}
    }
    subS = ss; subE = ee; subT = tt; subI = 0;
    subN = k;
}

private String curSub() {
    int n = subN;
    subNow = -1;
    if (n == 0) { if (subWhy.length() == 0 || subWhy.startsWith("gap") || subWhy.startsWith("audio")) subWhy = "no subs loaded"; return null; }
    if (audioPlayer == null) { subWhy = "no audio player"; return null; }
    long t = audioPlayer.getPositionMs();
    subNow = t;
    if (t < 0) { subWhy = "audio pos<0"; return null; }
    int i = subI;
    if (i >= n || subS[i] > t) i = 0;
    for (; i < n; i++) {
        if (subS[i] > t) break;
        if (t < subE[i]) { subI = i; subWhy = "showing"; return subT[i]; }
    }
    subWhy = "Gap sub by TK";
    return null;
}

private void drawSub(Graphics g) {
    String s = subOn ? curSub() : null;
    if (SUB_DEBUG) {
        String why = subOn ? subWhy : "Sub off";
        if (!why.equals(subLogged)) {
            subLogged = why;
            Log.d("sub: " + why + " t=" + subNow + " n=" + subN);
        }
    }
    if (s == null) return;

    g.setFont(subF);
    int w = getWidth() - 8, lh = subF.getHeight();

    if (s != wrapSrc || w != wrapW) {
        wrapSrc = s;
        wrapW = w;
        wrapLines.removeAllElements();
        StringBuffer cur = new StringBuffer();
        int p = 0;
        while (p < s.length()) {
            int e = s.indexOf(' ', p);
            if (e < 0) e = s.length();
            String word = s.substring(p, e);
            p = e + 1;
            if (cur.length() == 0) {
                cur.append(word);
            } else {
                String ts = cur.toString() + " " + word;
                if (subF.stringWidth(ts) > w) {
                    wrapLines.addElement(cur.toString());
                    cur.setLength(0);
                    cur.append(word);
                } else {
                    cur.append(' ').append(word);
                }
            }
        }
        if (cur.length() > 0) wrapLines.addElement(cur.toString());
    }

    int total = wrapLines.size();
    int n = Math.min(total, 3);
    int y = getHeight() - (uiVisible ? bottomBarH() : 0) - 4 - n * lh;
    int x = getWidth() / 2;
    for (int i = 0; i < n; i++) {
        String l = (String) wrapLines.elementAt(total - n + i);
        g.setColor(0x000000);
        g.drawString(l, x + 1, y + i * lh + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(0xFFFFFF);
        g.drawString(l, x, y + i * lh, Graphics.TOP | Graphics.HCENTER);
    }
}

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
    else if (uiCol == 2) { subOn = !subOn; repaint(); }
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
                    display.setCurrent(AacStreamPlayerCanvas.this);
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
    
private void checkStall() {
    if (paused || lastFrameAt == 0 || inFrame) return;
    if (System.currentTimeMillis() - lastFrameAt > 1000) stallOn(false);
}

private void checkFps() {
    long now = System.currentTimeMillis();
    if (winStart == 0) { winStart = now; winCount = shownCount; return; }
    if (now - winStart < 1000) return;
    int cnt = shownCount - winCount;
    winCount = shownCount;
    winStart = now;

    if (!audioGo || paused || stallPaused || lastShownAt == 0) {
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
        long target = (long) frameIndex * 1000L / fps;
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
            long frameMs = 1000L / fps;
            long elapsed = System.currentTimeMillis() - lastFrameTime;
            if (elapsed < frameMs) {
                try { Thread.sleep(frameMs - elapsed); } catch (InterruptedException e) {}
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

private void updateImage(Image img) {
    if (!running) return;
    lastShownAt = System.currentTimeMillis();
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
        int bw = (w - 5 * m) / 4;
Font bf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
g.setFont(bf);
String[] lb = { paused ? "Play" : "Pause", "Rotate", "Sub", "Back" };
for (int i = 0; i < 4; i++) {
    int bx = m + i * (bw + m);
    g.setColor(uiRow == 1 && uiCol == i ? 0xCC0000 : 0x333333);
    g.fillRoundRect(bx, by, bw, bh, 6, 6);
    g.setColor(i == 2 && !subOn ? 0x777777 : 0xFFFFFF);
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
        uiCol = Math.min(3, x * 4 / getWidth());
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
    uiCol = Math.min(3, uiCol + 1);
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
drawSub(g);
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
midlet.keepLight();
//checkFps();
long now = System.currentTimeMillis();
                if (now - spLastT >= 1000) {
                    long rx = Log.rx();
                    if (spLastT != 0) speedKB = (int) ((rx - spLastRx) * 1000 / (now - spLastT) / 1024);
                    spLastRx = rx;
                    spLastT = now;
                }
                if (uiVisible || paused || stallPaused || subN == 0) repaint();
                try { Thread.sleep(250); } catch (InterruptedException e) {}
            }
        }
    }).start();
}
}

class AacStreamPlayer implements Runnable, PlayerListener {
    private static final String MIME_TYPE = "audio/aac";
    private static final int[] RATES = { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350 };
    private static final int[] CHUNK_PLAN = { 11, 43, 129, 323 };
    private static final int MAX_QUEUED = 8;

    private static class Chunk {
        final byte[] data; final int frames; final long ms; final int seq;
        Chunk(byte[] d, int f, long m, int s) { data = d; frames = f; ms = m; seq = s; }
    }
    
    private volatile boolean fetchHold = false;
private final Object holdLock = new Object();

public void holdFetch(boolean h) {
    synchronized (holdLock) {
        fetchHold = h;
        if (!h) holdLock.notifyAll();
    }
}
    private final String audioUrl;
    private final AudioStreamPlayer.ReadyListener listener;
    private HttpConnection conn;
    private InputStream is;
    private volatile boolean running = true;
    private volatile boolean eof = false;
    private volatile boolean failed = false;
    private boolean readyNotified = false;

    private final Object queueLock = new Object();
    private final Vector queue = new Vector();
    private final Object endLock = new Object();
    private boolean ended = false;
    private volatile Player current;
    private volatile int volume = -1;
    private boolean volumeDirty = false;

    private final Object posLock = new Object();
    private long posWall = 0, posBase = 0, posLen = 0;

    private final Object goLock = new Object();
    private volatile boolean go = false;

    private volatile boolean paused = false;
    private volatile long shift = 0;
    private long pausedAt = 0;
    private int expectSeq = 0;

    AacStreamPlayer(String audioUrl, AudioStreamPlayer.ReadyListener listener) {
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
        closeQuietly();
    }

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

    public long getPositionMs() {
        synchronized (posLock) {
            if (posWall == 0 || !running) return -1;
            long now = paused ? pausedAt : System.currentTimeMillis();
            long in = now - posWall;
            if (in > posLen) in = posLen;
            return posBase + in;
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

    public synchronized boolean isSettled() { return readyNotified; }

    private void setPos(long wall, long base, long len) {
        synchronized (posLock) { posWall = wall; posBase = base; posLen = len; }
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
            Log.d("aac connect");
            conn = (HttpConnection) Connector.open(audioUrl, Connector.READ);
            conn.setRequestMethod(HttpConnection.GET);
            int status = conn.getResponseCode();
            Log.d("aac HTTP " + status);
            if (status != HttpConnection.HTTP_OK) { fail("HTTP " + status); return; }
            is = conn.openInputStream();

            byte[] hdr = new byte[7];
            byte[] buf = new byte[2048];
            ByteArrayOutputStream out = new ByteArrayOutputStream(9000);
            int frames = 0, planIdx = 0, limit = CHUNK_PLAN[0], seq = 0, rate = 22050;
            long samples = 0;

            while (running) {
    synchronized (holdLock) {
        while (running && fetchHold) {
            try { holdLock.wait(); } catch (InterruptedException e) {}
        }
    }
    if (!running) break;
    if (!readFully(is, hdr, 0, 7)) break;
                if ((hdr[0] & 0xFF) != 0xFF || (hdr[1] & 0xF0) != 0xF0) {
                    Log.d("aac sync lost");
                    if (seq == 0 && frames == 0) { fail("Not AAC ADTS"); return; }
                    break;
                }
                int ri = (hdr[2] >> 2) & 0x0F;
                if (ri < RATES.length) rate = RATES[ri];
                int len = ((hdr[3] & 3) << 11) | ((hdr[4] & 0xFF) << 3) | ((hdr[5] & 0xFF) >> 5);
                if (len < 7) break;
                if (len > buf.length) buf = new byte[len];
                System.arraycopy(hdr, 0, buf, 0, 7);
                if (len > 7 && !readFully(is, buf, 7, len - 7)) break;
                out.write(buf, 0, len);
                frames++;
                samples += 1024;
                if (frames >= limit) {
                    Log.d("aac chunk queued, " + frames + "f");
                    if (!enqueue(new Chunk(out.toByteArray(), frames, samples * 1000L / rate, seq++))) return;
                    out.reset();
                    frames = 0;
                    samples = 0;
                    if (planIdx < CHUNK_PLAN.length - 1) planIdx++;
                    limit = CHUNK_PLAN[planIdx];
                }
            }
            if (frames > 0 && running) {
                Log.d("aac last chunk, " + frames + "f");
                enqueue(new Chunk(out.toByteArray(), frames, samples * 1000L / rate, seq++));
            }
        } catch (Exception e) {
            Log.d("aac dl ERR " + e);
            if (!isReady()) fail("Download: " + e);
        } finally {
            eof = true;
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
            checkSeq(curChunk);
            Player cur = prepareRetry(curChunk);
            if (cur == null) { fail("Cannot decode AAC"); return; }
            Log.d("aac chunk 0 prepared");
            notifyReady();

            synchronized (goLock) {
                while (running && !go) {
                    try { goLock.wait(); } catch (InterruptedException e) {}
                }
            }
            if (!running) { closePlayer(cur); return; }

            long mediaMs = 0;
            long lenMs = curChunk.ms;
            synchronized (endLock) { ended = false; }
            current = cur;
            applyVolume(cur);
            cur.start();
            long wallStart = System.currentTimeMillis();
            setPos(wallStart, mediaMs, lenMs);
            long sh0 = shift;

            while (running) {
                Chunk nextChunk = dequeue();
                if (!running) break;
                if (nextChunk == null) {
                    long rem = wallStart + lenMs + 1500L - System.currentTimeMillis();
                    if (rem > 0) waitEnd(rem);
                    Log.d("aac end of stream");
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
                lenMs = nextChunk.ms;
                sh0 = shift;
                wallStart = System.currentTimeMillis();
                if (next == null) {
                    Log.d("aac chunk " + nextChunk.seq + " LOST");
                    setPos(wallStart, mediaMs, lenMs);
                    continue;
                }
                synchronized (endLock) { ended = false; }
                current = next;
                applyVolume(next);
                next.start();
                wallStart = System.currentTimeMillis();
                setPos(wallStart, mediaMs, lenMs);
                cur = next;
            }
        } catch (Exception e) {
            if (running) fail("Exception: " + e);
        }
    }

    private void checkSeq(Chunk c) {
        if (c.seq != expectSeq) Log.d("aac SEQ ERROR expected " + expectSeq + " got " + c.seq);
        expectSeq = c.seq + 1;
    }

    private Player prepareRetry(Chunk c) {
        for (int i = 0; i < 3 && running; i++) {
            try { return prepare(c); }
            catch (Exception e) {
                Log.d("aac prepare chunk " + c.seq + " try " + (i + 1) + " failed: " + e);
                try { Thread.sleep(100); } catch (InterruptedException ie) {}
            }
        }
        return null;
    }

    private Player prepare(Chunk c) throws IOException, MediaException {
        Player p = Manager.createPlayer(new ByteArrayInputStream(c.data), MIME_TYPE);
        p.addPlayerListener(this);
        p.realize();
        p.prefetch();
        applyVolume(p);
        return p;
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

    public void playerUpdate(Player p, String event, Object data) {
        if (p != current) return;
        if (PlayerListener.END_OF_MEDIA.equals(event) || PlayerListener.ERROR.equals(event)) {
            synchronized (endLock) { ended = true; endLock.notifyAll(); }
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
        Log.d("aac fail: " + reason);
        closeQuietly();
        synchronized (this) {
            if (readyNotified) return;
            readyNotified = true;
        }
        if (listener != null) listener.onAudioFailed(reason);
    }
}