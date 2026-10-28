import javax.microedition.media.Manager;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.media.Player;
import javax.microedition.media.control.VideoControl;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

    public class Midlet extends MIDlet implements CommandListener {
private Vector customNames = new Vector();
private Vector customUrls = new Vector();
private Vector customPhps = new Vector();
private int editingCustom = -1;

private List sourceList;
private Form customForm;
private TextField customNameField, customUrlField, customPhpField;
private Command sourceBack = new Command("Back", Command.BACK, 1);
private Command sourceEdit = new Command("Edit", Command.ITEM, 2);
private Command sourceDelete = new Command("Delete", Command.ITEM, 3);
    private String currentQuery = "Trending";
    private CookieBrowser cookieBrowser;
    private Display display;
    private MainCanvas canvasUI;
    private static final String PC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private TextBox searchInputBox;
    private Command startSearchCommand;
    private Command cancelSearchCommand;
    Player player;
    VideoControl videoControl;
    private AudioCanvas audioCanvas;
    private Vector videoTitles = new Vector();
    private Vector videoIds = new Vector();
    private String selectedPath = null;
    String selectedVideoTitle = "";
    private String selectedVideoId = "";
    public String capturedMediaUrl = "";
    public Image selectedThumbnail = null;
    private Image headerLogo = null;
    private int downloadProgress = 0;
    private long downloadTotal = 0;
    private long downloadReceived = 0;
    private String playerUrl = "";
    private String playerType = "";
    private VideoCanvas videoCanvas;
    private boolean startupPick = false;
    private String customUrl = "http://";
private Command customOk = new Command("OK", Command.OK, 1);
private Command customCancel = new Command("Cancel", Command.BACK, 2);
public static String mainurl = "http://example.com";
public static String phpdlp = "/ytdlp4.php";
private String baseUrl = mainurl + phpdlp + "?action=search&query=";
private static final String[][] SOURCES = {
    {"Your self host", "http://example.com", "/ytdlp.php"},
};
private int sourceIndex = 0;
    private String connectionSuffix = "";
    private Form browserForm;
    private StringItem browserText;
    private Command backFromBrowserCommand;
    public static final int STATE_DOWNLOAD = 3;
    private int currentPage = 1;
    private String currentSearchUrl = "trending";
    private int loadFrame = 0;
    private Image iconHome, iconShorts, iconMenu, iconLibrary;
    Image iconHomeHover, iconShortsHover, iconMenuHover, iconLibraryHover;
    private boolean isSplashShown = false;
    private Vector libraryTitles = new Vector();
    private Vector libraryIds = new Vector();
    private LibraryStore libraryStore = new LibraryStore();
    private Vector libraryPaths = new Vector();
    private StreamPlayerCanvas streamPlayerCanvas;
    private AacStreamPlayerCanvas aacStreamCanvas;
    //private ChunkPlayerCanvas chunkCanvas;
    public boolean aacMode = false;
    public boolean aacHf = false;
    private Vector subscribeTitles = new Vector();
private Vector subscribeUrls = new Vector();
private SubscribeStore subscribeStore = new SubscribeStore();
private java.util.Hashtable channelThumbs = new java.util.Hashtable();

public void addSubscription(String title, String url) {
    for (int i = 0; i < subscribeUrls.size(); i++) {
        if (url.equals(subscribeUrls.elementAt(i))) return;
    }
    subscribeTitles.addElement(title);
    subscribeUrls.addElement(url);
    subscribeStore.add(title, url);
}

/*
public void showChunkPlayer(String jobId) {
    if (chunkCanvas != null) { chunkCanvas.stopAll(); chunkCanvas = null; }
    if (streamPlayerCanvas != null) { streamPlayerCanvas.stopAll(); streamPlayerCanvas = null; }
    if (aacStreamCanvas != null) { aacStreamCanvas.stopAll(); aacStreamCanvas = null; }
    chunkCanvas = new ChunkPlayerCanvas(this, jobId);
    display.setCurrent(chunkCanvas);
}
*/

public void applySource(int i) {
    int n = SOURCES.length;
    if (i < 0 || i >= n + customUrls.size()) i = 0;
    sourceIndex = i;
    if (i >= n) {
        mainurl = (String) customUrls.elementAt(i - n);
        phpdlp = (String) customPhps.elementAt(i - n);
    } else {
        mainurl = SOURCES[i][1];
        phpdlp = SOURCES[i][2];
    }
    baseUrl = mainurl + phpdlp + "?action=search&query=";
}

public String sourceName(int i) {
    int n = SOURCES.length;
    if (i < n) return SOURCES[i][0];
    int c = i - n;
    return c < customNames.size() ? (String) customNames.elementAt(c) : "Custom";
}

public void showSourceList() {
    sourceList = new List("Select Source", List.IMPLICIT);
    int n = SOURCES.length;
    for (int i = 0; i < n; i++) {
        sourceList.append(SOURCES[i][0] + (i == sourceIndex ? " *" : ""), null);
    }
    for (int i = 0; i < customNames.size(); i++) {
        sourceList.append((String) customNames.elementAt(i) + (n + i == sourceIndex ? " *" : ""), null);
    }
    sourceList.append("+ Add Custom", null);
    sourceList.addCommand(sourceBack);
    sourceList.addCommand(sourceEdit);
    sourceList.addCommand(sourceDelete);
    sourceList.setCommandListener(this);
    display.setCurrent(sourceList);
}

public void openCustomEditor(int customIdx) {
    editingCustom = customIdx;
    String nm = "", u = "http://", p = "/ytdlp.php";
    if (customIdx >= 0) {
        nm = (String) customNames.elementAt(customIdx);
        u = (String) customUrls.elementAt(customIdx);
        p = (String) customPhps.elementAt(customIdx);
    }
    customForm = new Form(customIdx >= 0 ? "Edit Source" : "Add Source");
    customNameField = new TextField("Name", nm, 40, TextField.ANY);
    customUrlField = new TextField("Server URL", u, 200, TextField.URL);
    customPhpField = new TextField("PHP file", p, 100, TextField.ANY);
    customForm.append(customNameField);
    customForm.append(customUrlField);
    customForm.append(customPhpField);
    customForm.addCommand(customOk);
    customForm.addCommand(customCancel);
    customForm.setCommandListener(this);
    display.setCurrent(customForm);
}

public void selectSource(int i) {
    startupPick = false;
    applySource(i);
    libraryStore.saveSource(i);
    channelThumbs.clear();
    startSearchThread("trending");
}

private void loadChannelThumbs(final Vector ids, final boolean raw) {
    new Thread(new Runnable() {
        public void run() {
            for (int i = 0; i < ids.size(); i++) {
                String id = (String) ids.elementAt(i);
                if (!raw && !id.startsWith("CHANNEL:")) continue;
                String key = raw ? "CHANNEL:" + id : id;
                String u = raw ? id : id.substring(8);
                if (channelThumbs.containsKey(key)) continue;
                for (int attempt = 0; attempt < 2; attempt++) {
                    HttpConnection hc = null; InputStream is = null;
                    try {
                        hc = (HttpConnection) Connector.open(mainurl + phpdlp + "?action=chthumb&s=40&u=" + u + connectionSuffix);
                        if (hc.getResponseCode() != 200) continue;
                        is = hc.openInputStream();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024]; int n;
                        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                        channelThumbs.put(key, Image.createImage(baos.toByteArray(), 0, baos.size()));
                        canvasUI.repaint();
                        break;
                    } catch (Exception e) {
                    } finally {
                        try { if (is != null) is.close(); if (hc != null) hc.close(); } catch (Exception e) {}
                    }
                }
            }
        }
    }).start();
}

public boolean isSubscribed(String url) {
    for (int i = 0; i < subscribeUrls.size(); i++) {
        if (url.equals(subscribeUrls.elementAt(i))) return true;
    }
    return false;
}

public void removeSubscription(String url) {
    for (int i = 0; i < subscribeUrls.size(); i++) {
        if (url.equals(subscribeUrls.elementAt(i))) {
            subscribeUrls.removeElementAt(i);
            subscribeTitles.removeElementAt(i);
            break;
        }
    }
    subscribeStore.remove(url);
}

private int nokia = -1;

void keepLight() {
    if (nokia == -1) {
        String p = System.getProperty("microedition.platform");
        nokia = (p != null && p.toLowerCase().indexOf("nokia") != -1) ? 1 : 0;
    }
    if (nokia == 1) {
        try { display.flashBacklight(1000); } catch (Throwable t) {}
    }
}

public void startSubFeedThread() {
    if (subscribeUrls.size() == 0) {
        videoTitles.removeAllElements();
        videoIds.removeAllElements();
        videoTitles.addElement("No subscriptions found");
        videoIds.addElement("");
        canvasUI.setLoading(false);
        return;
    }
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < subscribeUrls.size(); i++) {
        if (i > 0) sb.append(",");
        sb.append((String) subscribeUrls.elementAt(i));
    }
    canvasUI.isLibraryView = false;
    canvasUI.isSubscriptionView = false; 
    videoTitles.removeAllElements();
    videoIds.removeAllElements();
    currentPage = 1;
    canvasUI.setLoading(true);

    final String channelsParam = sb.toString();
    new Thread(new Runnable() {
        public void run() {
            String url = mainurl + phpdlp + "?action=subfeed&channels=" + cookieBrowser.urlEncode(channelsParam);
            performSearch(url);
        }
    }).start();
}
    
public class SubscribeStore {
    private static final String STORE_NAME = "Vyoutubesub";

    public void load(Vector titles, Vector urls) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            while (re.hasNextElement()) {
                try {
                    String record = new String(re.nextRecord());
                    int sep = record.indexOf('|');
                    if (sep > 0) {
                        titles.addElement(record.substring(0, sep));
                        urls.addElement(record.substring(sep + 1));
                    }
                } catch (Exception ignored) {}
            }
            re.destroy();
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    public void add(String title, String url) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            String record = title + "|" + url;
            byte[] b = record.getBytes();
            rs.addRecord(b, 0, b.length);
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }
    
    public void remove(String url) {
    RecordStore rs = null;
    try {
        rs = RecordStore.openRecordStore(STORE_NAME, true);
        RecordEnumeration re = rs.enumerateRecords(null, null, false);
        while (re.hasNextElement()) {
            int id = re.nextRecordId();
            try {
                String record = new String(rs.getRecord(id));
                int sep = record.indexOf('|');
                if (sep > 0 && record.substring(sep + 1).equals(url)) {
                    rs.deleteRecord(id);
                    break;
                }
            } catch (Exception ignored) {}
        }
        re.destroy();
    } catch (Exception e) {
    } finally {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
    }
}
}
    
public void showStreamPlayer(String jobId) {
    if (streamPlayerCanvas != null) {
        streamPlayerCanvas.stopAll();
        streamPlayerCanvas = null;
    }
    if (aacStreamCanvas != null) {
        aacStreamCanvas.stopAll();
        aacStreamCanvas = null;
    }
    if (aacMode) {
    aacStreamCanvas = new AacStreamPlayerCanvas(this, jobId, aacHf);
    display.setCurrent(aacStreamCanvas);
} else {
        streamPlayerCanvas = new StreamPlayerCanvas(this, jobId);
        display.setCurrent(streamPlayerCanvas);
    }
}

    //private WavStreamCanvas wavStreamCanvas;
    public boolean wavMode = false;

public void showWavPlayer(String jobId) {
    
}
    
        public Midlet() {
        display = Display.getDisplay(this);
        cookieBrowser = new CookieBrowser();
        cookieBrowser.display = display;
        cookieBrowser.parentMidlet = this;
        libraryStore.load(libraryTitles, libraryIds, libraryPaths);
        subscribeStore.load(subscribeTitles, subscribeUrls);
        selectedPath = libraryStore.loadPath();
libraryStore.loadCustoms(customNames, customUrls, customPhps);
applySource(libraryStore.loadSource());
        setupConnection();
        loadResources();
        audioCanvas = new AudioCanvas(this);
        canvasUI = new MainCanvas(this);
        browserForm = new Form("Built-in Browser");
        browserText = new StringItem(null, "Loading...");
        backFromBrowserCommand = new Command("Back", Command.BACK, 1);
        
        browserForm.append(browserText);
        browserForm.addCommand(backFromBrowserCommand);
        browserForm.setCommandListener(this);
        searchInputBox = new TextBox("Enter Search", "", 64, TextField.ANY);
        startSearchCommand = new Command("Search", Command.OK, 1);
        cancelSearchCommand = new Command("Cancel", Command.BACK, 2);
        searchInputBox.addCommand(startSearchCommand);
        searchInputBox.setCommandListener(this);
        videoCanvas = new VideoCanvas(this);
        startSearchThread("trending");
    }
    
class SplashCanvas extends Canvas {
        private Image splashLogo;
        private String appVersion;

        public SplashCanvas() {
            setFullScreenMode(true);
            try {
                splashLogo = Image.createImage("/splash.png"); 
            } catch (IOException e) {
                System.out.println("Ta ku ru Ka Ga MiSplash logo not found");
            }
            
            appVersion = getAppProperty("MIDlet-Version");
            
            if (appVersion == null) {
                appVersion = "Unknown"; 
            }
        }

        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();

            g.setColor(0x121212);
            g.fillRect(0, 0, w, h);

            int textYPosition;
            if (splashLogo != null) {
                g.drawImage(splashLogo, w / 2, h / 2, Graphics.HCENTER | Graphics.VCENTER);
                
                textYPosition = (h / 2) + (splashLogo.getHeight() / 2) + 20; 
            } else {
                g.setColor(0xFFFFFF);
                g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
                g.drawString("Loading...", w / 2, h / 2, Graphics.TOP | Graphics.HCENTER);
                textYPosition = (h / 2) + 20;
            }

            g.setColor(0x888888);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.drawString("v" + appVersion, w / 2, textYPosition, Graphics.TOP | Graphics.HCENTER);
        }
    }

public void startChannelThread(final String channelUrl, final String channelTitle) {
    canvasUI.isLibraryView = false;
    canvasUI.isSubscriptionView = false;
    videoTitles.removeAllElements();
    videoIds.removeAllElements();
    currentPage = 1;
    canvasUI.setLoading(true);

    new Thread(new Runnable() {
        public void run() {
            if (channelTitle != null && channelTitle.length() > 0) {
    if (isSubscribed(channelUrl)) {
        videoTitles.addElement("[-] Unsubscribe");
        videoIds.addElement("UNSUB_ACTION:" + channelUrl);
    } else {
        videoTitles.addElement("[+] Subscribe");
        videoIds.addElement("SUB_ACTION:" + channelUrl + "|" + channelTitle);
    }
}
            String url = mainurl + phpdlp + "?action=channel&channel=" + channelUrl;
            performSearch(url);
        }
    }).start();
}
    
private void loadResources() {
    try {
        headerLogo = Image.createImage("/logo.png");
        iconHome = Image.createImage("/home.png");
        iconShorts = Image.createImage("/library.png");
        iconLibrary = Image.createImage("/dl.png");
        iconMenu = Image.createImage("/menu.png");
        iconHomeHover = Image.createImage("/home_hover.png");
        iconShortsHover = Image.createImage("/library_hover.png");
        iconLibraryHover = Image.createImage("/dl_hover.png");
        iconMenuHover = Image.createImage("/menu_hover.png");
    } catch (IOException e) {
        System.out.println("Ta ku ru Ka Ga MiIcons not found");
    }
}

public void showMainCanvas() {
    if (libraryStore.hasSource()) {
        startupPick = false;
        display.setCurrent(canvasUI);
        canvasUI.setState(MainCanvas.STATE_LIST);
    } else {
        startupPick = true;
        showSourceList();
    }
}

public void showActionChoice(String url) {
    capturedMediaUrl = url;
    canvasUI.actionChoiceSelectedIndex = 0;
    canvasUI.actionChoiceScrollY = 0;
    canvasUI.setState(MainCanvas.STATE_ACTION_CHOICE);
    display.setCurrent(canvasUI);
}

public void saveMediaToFile(final String url) {
    saveMediaToFile(url, false);
}

public void saveMediaToFile(final String url, final boolean isTemporary) {
    String tmp = url;
    if (tmp.indexOf("relay?u=") != -1) tmp = tmp.substring(tmp.indexOf("relay?u=") + 8);
    tmp = decodeURL(tmp);
    if (tmp.toLowerCase().startsWith("https://")) tmp = "http://" + tmp.substring(8);
    final String cleanUrl = tmp.trim();
    String lowerUrl = cleanUrl.toLowerCase();

    final String ext;
    if (lowerUrl.indexOf("file=media.mp3") != -1) {
        ext = ".mp3";
    } else if (lowerUrl.indexOf("file=media.3gp") != -1) {
        ext = ".3gp";
    } else if (lowerUrl.indexOf("file=media.mp4") != -1) {
        ext = ".mp4";
    } else {
        boolean isAudio = lowerUrl.endsWith(".mp3") || lowerUrl.indexOf(".mp3?") != -1 || lowerUrl.indexOf(".mp3&") != -1;
        ext = isAudio ? ".mp3" : (lowerUrl.indexOf(".3gp") != -1 ? ".3gp" : ".mp4");
    }

if (selectedPath == null) {
        display.setCurrent(new FileSelector(cookieBrowser, new Runnable() {
            public void run() {
                saveMediaToFile(url, isTemporary);
            }
        }));
        return;
    }
    final String root = selectedPath;
    final String savePath = isTemporary
        ? root + (ext.equals(".mp3") ? "temp_audio" : "temp_video") + ext
        : root + selectedVideoId + ext;

    downloadProgress = 0; downloadTotal = 0; downloadReceived = 0;
    canvasUI.setLoading(true);
    canvasUI.setState(MainCanvas.STATE_DOWNLOAD);

    new Thread(new Runnable() {
        public void run() {
            HttpConnection http = null; InputStream is = null;
            javax.microedition.io.file.FileConnection fc = null; OutputStream os = null;
            boolean success = false;
            try {
                cookieBrowser.log("HTTP_REQUEST (SaveMedia): " + cleanUrl);
                http = (HttpConnection) Connector.open(cleanUrl);
                http.setRequestProperty("User-Agent", PC_USER_AGENT);
                int status = http.getResponseCode();
                cookieBrowser.log("HTTP_RESPONSE (SaveMedia): " + status);
                if (status != 200) throw new IOException("HTTP error " + status);

                downloadTotal = http.getLength();
                cookieBrowser.log("SAVE_MEDIA: savePath=" + savePath + " contentLength=" + downloadTotal);

                is = http.openInputStream();
                fc = (javax.microedition.io.file.FileConnection) Connector.open(savePath);
                if (fc.exists()) fc.delete();
                fc.create();
                os = fc.openOutputStream();

                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    downloadReceived += n;
                    if (downloadTotal > 0) {
                        downloadProgress = (int)((downloadReceived * 100L) / downloadTotal);
                    } else {
                        downloadProgress = 1;
                    }
                    canvasUI.repaint();
                }
                os.flush();
                cookieBrowser.log("SAVE_MEDIA: done, bytes=" + downloadReceived);
                success = (downloadReceived > 0);

            } catch (Exception e) {
                cookieBrowser.log("SAVE_MEDIA_ERROR: " + e.getMessage());
                success = false;
            } finally {
                try { if (os!=null) os.close(); } catch(Exception e){}
                try { if (is!=null) is.close(); } catch(Exception e){}
                try { if (http!=null) http.close(); } catch(Exception e){}
                try { if (fc!=null) fc.close(); } catch(Exception e){}
            }

            canvasUI.setLoading(false);

            if (success) {
    if (!isTemporary) {
        libraryTitles.addElement(selectedVideoTitle);
        libraryIds.addElement(selectedVideoId);
        libraryPaths.addElement(savePath);
        libraryStore.add(selectedVideoTitle, selectedVideoId, savePath);
    }
    canvasUI.setState(MainCanvas.STATE_DETAILS);
    playLocalFile(savePath);
} else {
                try {
                    javax.microedition.io.file.FileConnection cleanupFc =
                        (javax.microedition.io.file.FileConnection) Connector.open(savePath);
                    if (cleanupFc.exists()) cleanupFc.delete();
                    cleanupFc.close();
                } catch (Exception ignored) {}
                canvasUI.setState(MainCanvas.STATE_DETAILS);
            }
        }
    }).start();
}

    private void setupConnection() {
        String platform = System.getProperty("microedition.platform");
        if (platform != null && platform.toLowerCase().indexOf("blackberry") != -1) {
            connectionSuffix = ";interface=wifi";
        }
    }

public void showUrlInBrowser(final String url) {
    browserForm.deleteAll(); 

    browserText.setText("Fetching links...");
    browserForm.append(browserText);
    display.setCurrent(browserForm);

    new Thread(new Runnable() {
        public void run() {
            HttpConnection hc = null;
            InputStream is = null;
            try {
                cookieBrowser.log("HTTP_REQUEST (Browser): " + url);
                hc = (HttpConnection) Connector.open(url + connectionSuffix);
        hc.setRequestProperty("User-Agent", PC_USER_AGENT);
        hc.setRequestMethod("GET");
        int status = hc.getResponseCode();
        cookieBrowser.log("HTTP_RESPONSE (Search): " + status);
        if (status != 200) {
            videoTitles.addElement("Search failed (HTTP " + status + ")");
            videoIds.addElement("");
            return;
        }
        is = hc.openInputStream();
                StringBuffer sb = new StringBuffer();
                int ch;
                while ((ch = is.read()) != -1) {
                    sb.append((char) ch);
                    if (sb.length() > 50000) break; 
                }

                String html = sb.toString();
                System.out.println(html); 

                parseAndDisplayLinks(html);

            } catch (Exception e) {
                browserText.setText("Error: " + e.getMessage());
            } finally {
                try { if(is != null) is.close(); if(hc != null) hc.close(); } catch(Exception e){}
            }
        }
    }).start();
}

private String decodeURL(String s) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '%' && i + 2 < s.length()) {
            try {
                String hex = s.substring(i + 1, i + 3);
                sb.append((char) Integer.parseInt(hex, 16));
                i += 2;
            } catch (Exception e) {
                sb.append(c);
            }
        } else if (c == '+') {
            sb.append(' ');
        } else {
            sb.append(c);
        }
    }
    return sb.toString();
}

private void parseAndDisplayLinks(String html) {
    browserForm.deleteAll();
    String marker = "<a href=\"";
    int index = 0;

    while ((index = html.indexOf(marker, index)) != -1) {
        index += marker.length();
        int endQuote = html.indexOf("\"", index);
        if (endQuote != -1) {
            final String linkUrl = html.substring(index, endQuote);

            int endTag = html.indexOf(">", endQuote) + 1;
            int closeTag = html.indexOf("</a>", endTag);

            if (closeTag != -1) {
                String linkText = stripHtml(html.substring(endTag, closeTag)).trim();

                if (linkText.length() > 0) {

                    StringItem linkItem = new StringItem(null, linkText, Item.HYPERLINK);
                    linkItem.setDefaultCommand(new Command("Open", Command.ITEM, 1));
                    linkItem.setItemCommandListener(new ItemCommandListener() {

public void commandAction(Command c, Item item) {
    String absoluteUrl = linkUrl;
    if (linkUrl.startsWith("/")) {
    absoluteUrl = mainurl + "" + linkUrl;
}

    String lower = absoluteUrl.toLowerCase();
    String decodedTarget = "";
    int relayIdx = lower.indexOf("relay?u=");
    if (relayIdx != -1) {
        decodedTarget = decodeURL(absoluteUrl.substring(relayIdx + 8)).toLowerCase();
    }

    boolean isMedia = decodedTarget.indexOf(".mp4") != -1
                   || decodedTarget.indexOf(".3gp") != -1
                   || decodedTarget.indexOf(".mp3") != -1
                   || lower.indexOf(".mp4") != -1
                   || lower.indexOf(".3gp") != -1
                   || lower.indexOf(".mp3") != -1;

    if (isMedia) {
        display.setCurrent(canvasUI);
        startBuiltInPlayer(absoluteUrl);
    } else {
        showUrlInBrowser(absoluteUrl);
    }
}
                    });
                    browserForm.append(linkItem);
                }
            }
            index = endQuote;
        }
    }

    if (browserForm.size() == 0) {
        browserForm.append(new StringItem(null, "No links found on this page."));
    }
}

private String relayedUrlEncode(String s) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == ':') sb.append("%3A");
        else if (c == '/') sb.append("%2F");
        else if (c == '.') sb.append("."); 

        else sb.append(c);
    }
    return sb.toString();
}

class FileSelector extends List implements CommandListener {
    private CookieBrowser parent;
    private Midlet midlet;
    private String currentPath;
    private Command selectCommand;
    private Command backCommand;
    private Runnable onSelectedCallback;
    public String selectedPath = null;

    public FileSelector(CookieBrowser parent, Runnable onSelectedCallback) {
        super("Select Download Folder", List.IMPLICIT);
        this.parent = parent;
        this.midlet = parent.parentMidlet;
        this.onSelectedCallback = onSelectedCallback;
        this.currentPath = null;
        this.selectCommand = new Command("Open", Command.ITEM, 1);
        this.backCommand = new Command("Back", Command.BACK, 2);
        addCommand(this.selectCommand);
        addCommand(this.backCommand);
        setCommandListener(this);
        loadFolder();
    }

    private void loadFolder() {
        deleteAll();
        try {
            if (this.currentPath == null) {
                setTitle("Select Drive");
                Enumeration roots = FileSystemRegistry.listRoots();
                while (roots.hasMoreElements()) append((String) roots.nextElement(), null);
            } else {
                setTitle("Select Save Folder");
                append("Select This Path", null);

                FileConnection fc = (FileConnection) Connector.open("file:///" + this.currentPath, Connector.READ);
                Enumeration e = fc.list();
                while (e.hasMoreElements()) {
                    String item = (String) e.nextElement();
                    if (item.endsWith("/")) {
                        append(item, null);
                    }
                }
                fc.close();
            }
        } catch (IOException e) {
            this.parent.log("FS_ERROR: " + e.getMessage());
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == this.selectCommand || c == List.SELECT_COMMAND) {
            int selectedIdx = getSelectedIndex();
            if (selectedIdx == -1) return;

            final String selected = getString(selectedIdx);
            final FileSelector fs = this;

            new Thread(new Runnable() {
                public void run() {
                    if (fs.currentPath == null) {
                        fs.currentPath = selected;
                        fs.parent.display.callSerially(new Runnable() { public void run() { fs.loadFolder(); } });
                                        } else if (selected.equals("Select This Path")) {
                        String dirPath = "file:///" + fs.currentPath;
                        if (!dirPath.endsWith("/")) dirPath += "/";

                        fs.selectedPath = dirPath;
                        if (fs.midlet != null) {
                            fs.midlet.selectedPath = dirPath;
                            fs.midlet.libraryStore.savePath(dirPath);
                        }

                        fs.parent.display.callSerially(new Runnable() {
                            public void run() {
                                fs.parent.display.setCurrent(fs.midlet.canvasUI);
                                if (fs.onSelectedCallback != null) {
                                    fs.onSelectedCallback.run();
                                }
                            }
                        });
                    } else if (selected.endsWith("/")) {
                        fs.currentPath = fs.currentPath + selected;
                        fs.parent.display.callSerially(new Runnable() { public void run() { fs.loadFolder(); } });
                    }
                }
            }).start();
        } else if (c == this.backCommand) {
            if (this.currentPath == null || this.currentPath.equals("")) {
                this.parent.display.setCurrent(this.parent.parentMidlet.canvasUI);
                this.parent.parentMidlet.canvasUI.repaint();
            } else {
                String path = this.currentPath.substring(0, this.currentPath.length() - 1);
                int lastSlash = path.lastIndexOf('/');
                this.currentPath = (lastSlash == -1) ? null : path.substring(0, lastSlash + 1);
                loadFolder();
            }
        }
    }
}

private String getWorkableRoot() {
    String[] possibleRoots = { "file:///TFCard:/", "file:///TFCard/", "file:///D:/", "file:///root1/", "file:///d:/", "file:///e:/", "file:///E:/", "file:///C:/predefgallery/predeffilereceived/", "file:///c:/predefgallery/predeffilereceived/", "file:///c:/", "file:///C:/" };
    for (int i = 0; i < possibleRoots.length; i++) {
        javax.microedition.io.file.FileConnection fc = null;
        try {
            fc = (javax.microedition.io.file.FileConnection) Connector.open(possibleRoots[i], Connector.READ);
            if (fc.exists()) {
                fc.close();
                return possibleRoots[i];
            }
            fc.close();
        } catch (Exception e) {}
    }
    return "file:///c:/";
}

public void startBuiltInPlayer(String url) {
    if (url.indexOf("download=") != -1 || url.indexOf("action=download") != -1) {
        saveMediaToFile(url);
        return;
    }
    if (url.indexOf("relay?u=") != -1) {
        url = url.substring(url.indexOf("relay?u=") + 8);
    }
    url = decodeURL(url);
    if (url.toLowerCase().startsWith("https://")) {
        url = "http://" + url.substring(8);
    }
    
    if (url.startsWith("file:///")) {
       playLocalFile(url);
       return;
    }

    final String directUrl = url.trim();

    String lowerUrl = directUrl.toLowerCase();
    final boolean isAudio = lowerUrl.endsWith(".mp3")
                         || lowerUrl.endsWith(".aac")
                         || lowerUrl.endsWith(".wav")
                         || lowerUrl.endsWith(".ogg")
                         || lowerUrl.indexOf(".mp3?") != -1
                         || lowerUrl.indexOf(".mp3&") != -1;

if (selectedPath == null) {
    display.setCurrent(new FileSelector(cookieBrowser, new Runnable() {
        public void run() {
            startBuiltInPlayer(directUrl);
        }
    }));
    return;
}
final String root = selectedPath;
final String localPath = root + (isAudio ? "temp_audio.mp3" : "temp_video.mp4");

    downloadProgress = 0;
    downloadTotal = 0;
    downloadReceived = 0;
    canvasUI.setLoading(true);
    canvasUI.setState(MainCanvas.STATE_DOWNLOAD);

    new Thread(new Runnable() {
        public void run() {
            HttpConnection http = null;
            InputStream is = null;
            javax.microedition.io.file.FileConnection fc = null;
            OutputStream os = null;

            try {
                cookieBrowser.log("HTTP_REQUEST (Player): " + directUrl);
                http = (HttpConnection) Connector.open(directUrl);
                http.setRequestProperty("User-Agent", PC_USER_AGENT);
                int status = http.getResponseCode();
                cookieBrowser.log("HTTP_RESPONSE (Player): " + status);

                if (status != 200) throw new IOException("HTTP " + status);
                downloadTotal = http.getLength();

                is = http.openInputStream();
String[] tempFiles = {
    root + "temp_audio.mp3",
    root + "temp_video.mp4",
    root + "temp_video.3gp"
};
for (int i = 0; i < tempFiles.length; i++) {
    javax.microedition.io.file.FileConnection tmpFc = null;
    try {
        tmpFc = (javax.microedition.io.file.FileConnection) Connector.open(tempFiles[i]);
        if (tmpFc.exists()) tmpFc.delete();
    } catch (Exception ignored) {}
    finally { try { if (tmpFc != null) tmpFc.close(); } catch (Exception ignored) {} }
}

fc = (javax.microedition.io.file.FileConnection) Connector.open(localPath);
fc.create();
                os = fc.openOutputStream();

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    downloadReceived += bytesRead;
                    if (downloadTotal > 0) {
                        downloadProgress = (int)((downloadReceived * 100L) / downloadTotal);
                    }
                    canvasUI.repaint();
                }

                os.close();  os = null;
                is.close();  is = null;
                http.close(); http = null;
                fc.close();  fc = null;
                if (player != null) {
                    player.close();
                    player = null;
                }

                if (isAudio) {
                    try {
                        System.out.println("Ta ku ru Ka Ga Mi[PLAYER] Starting audio: " + localPath);
                        player = Manager.createPlayer(localPath);
                        player.realize();
                        player.prefetch();

                        audioCanvas.resetUI();
                        display.setCurrent(audioCanvas);
                        player.start();

                        canvasUI.setLoading(false);
                        canvasUI.setState(MainCanvas.STATE_LIST);

                    } catch (Exception audioEx) {
                        System.err.println("[PLAYER] Audio failed: " + audioEx.getMessage());
                        try {
                            boolean mustExit = platformRequest(localPath);
                            if (mustExit) exitApp();
                        } catch (Exception e2) {}
                        canvasUI.setLoading(false);
                        canvasUI.setState(MainCanvas.STATE_DETAILS);
                    }

                } else {
                    try {
                        System.out.println("Ta ku ru Ka Ga Mi[PLAYER] Attempting Internal...");
                        player = Manager.createPlayer(localPath);
                        player.realize();
                        videoControl = (VideoControl) player.getControl("VideoControl");

                        if (videoControl != null) {
                            videoCanvas.resetUI();
                            display.setCurrent(videoCanvas);
                            videoControl.initDisplayMode(VideoControl.USE_DIRECT_VIDEO, videoCanvas);
                            videoControl.setVisible(true);
                            player.start();

                            try { Thread.sleep(300); } catch (Exception e) {}
                            videoCanvas.updateVideoDisplay(false);
                            canvasUI.setLoading(false);
                            canvasUI.setState(MainCanvas.STATE_LIST);
                        } else {
                            throw new Exception("No VideoControl");
                        }
                    } catch (Exception internalEx) {
                        System.err.println("[PLAYER] Internal failed, launching External via platformRequest");
                        try {
                            boolean mustExit = platformRequest(localPath);
                            if (mustExit) exitApp();
                        } catch (Exception e2) {}
                        canvasUI.setLoading(false);
                        canvasUI.setState(MainCanvas.STATE_DETAILS);
                    }
                }

            } catch (Exception e) {
                canvasUI.setLoading(false);
                canvasUI.setState(MainCanvas.STATE_DETAILS);
                System.err.println("[LOG] Global Playback Error: " + e.getMessage());
            } finally {
                try { if (os   != null) os.close();   } catch (Exception e) {}
                try { if (is   != null) is.close();   } catch (Exception e) {}
                try { if (http != null) http.close(); } catch (Exception e) {}
                try { if (fc   != null) fc.close();   } catch (Exception e) {}
            }
        }
    }).start();
}


private void playLocalFile(final String localPath) {
    String lower = localPath.toLowerCase();
    final boolean isAudio = lower.endsWith(".mp3") || lower.endsWith(".aac");
    new Thread(new Runnable() {
        public void run() {
            try {
                if (player != null) { player.close(); player = null; }
                if (isAudio) {
                    player = Manager.createPlayer(localPath);
                    player.realize();
                    player.prefetch();
                    audioCanvas.resetUI();
                    display.setCurrent(audioCanvas);
                    player.start();
                } else {
                    player = Manager.createPlayer(localPath);
                    player.realize();
                    videoControl = (VideoControl) player.getControl("VideoControl");
                    if (videoControl != null) {
                        videoCanvas.resetUI();
                        display.setCurrent(videoCanvas);
                        videoControl.initDisplayMode(VideoControl.USE_DIRECT_VIDEO, videoCanvas);
                        videoControl.setVisible(true);
                        player.start();
                        try { Thread.sleep(300); } catch (Exception e) {}
                        videoCanvas.updateVideoDisplay(false);
                    } else {
                        boolean mustExit = platformRequest(localPath);
                        if (mustExit) exitApp();
                    }
                }
            } catch (Exception e) {
                try {
                    boolean mustExit = platformRequest(localPath);
                    if (mustExit) exitApp();
                } catch (Exception e2) {}
            }
        }
    }).start();
}

private String stripHtml(String html) {
    if (html == null) return "";
    StringBuffer sb = new StringBuffer();
    boolean inTag = false;

    for (int i = 0; i < html.length(); i++) {
        char c = html.charAt(i);
        if (c == '<') {
            inTag = true;
        } else if (c == '>') {
            inTag = false;
        } else if (!inTag) {

            sb.append(c);
        }
    }

    String result = sb.toString();
    result = replace(result, "&nbsp;", " ");
    result = replace(result, "&amp;", "&");
    result = decodeHtml(result); 

    return result.trim();
}

    public void startApp() {
        if (!isSplashShown) {
            final SplashCanvas splash = new SplashCanvas();
            display.setCurrent(splash);
            
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                    
                    showMainCanvas(); 
                }
            }).start();
            
            isSplashShown = true;
        } else {
            display.setCurrent(canvasUI);
        }
    }

    public void pauseApp() {}
public void destroyApp(boolean unconditional) {
        if (cookieBrowser != null) {
            cookieBrowser.saveSession();
        }
    }

    public void exitApp() { 
        destroyApp(true);
        notifyDestroyed(); 
    }

public void commandAction(Command c, Displayable d) {
if (d == sourceList) {
    int n = SOURCES.length;
    int idx = sourceList.getSelectedIndex();
    int cIdx = idx - n;
    boolean isCustom = cIdx >= 0 && cIdx < customUrls.size();

    if (c == sourceBack) {
    if (startupPick) {
        startupPick = false;
        display.setCurrent(canvasUI);
        canvasUI.setState(MainCanvas.STATE_LIST);
        startSearchThread("trending");
    } else {
        canvasUI.setState(MainCanvas.STATE_MENU);
        display.setCurrent(canvasUI);
    }
} else if (c == sourceEdit) {
        if (isCustom) openCustomEditor(cIdx);
    } else if (c == sourceDelete) {
        if (isCustom) {
            customNames.removeElementAt(cIdx);
            customUrls.removeElementAt(cIdx);
            customPhps.removeElementAt(cIdx);
            libraryStore.saveCustoms(customNames, customUrls, customPhps);
            if (idx == sourceIndex) {
                display.setCurrent(canvasUI);
                canvasUI.setState(MainCanvas.STATE_LIST);
                selectSource(0);
            } else {
                if (idx < sourceIndex) {
                    sourceIndex--;
                    libraryStore.saveSource(sourceIndex);
                }
                showSourceList();
            }
        }
    } else {
        if (idx < n + customUrls.size()) {
            display.setCurrent(canvasUI);
            canvasUI.setState(MainCanvas.STATE_LIST);
            selectSource(idx);
        } else {
            openCustomEditor(-1);
        }
    }
    return;
}
if (d == customForm) {
    if (c == customOk) {
        String u = customUrlField.getString().trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        String p = customPhpField.getString().trim();
        if (!p.startsWith("/")) p = "/" + p;
        String nm = customNameField.getString().trim();
        if (nm.length() == 0) nm = u;

        int n = SOURCES.length;
        int target;
        if (editingCustom >= 0) {
            customNames.setElementAt(nm, editingCustom);
            customUrls.setElementAt(u, editingCustom);
            customPhps.setElementAt(p, editingCustom);
            target = n + editingCustom;
        } else {
            customNames.addElement(nm);
            customUrls.addElement(u);
            customPhps.addElement(p);
            target = n + customUrls.size() - 1;
        }
        libraryStore.saveCustoms(customNames, customUrls, customPhps);

        if (editingCustom < 0 || target == sourceIndex) {
            display.setCurrent(canvasUI);
            canvasUI.setState(MainCanvas.STATE_LIST);
            selectSource(target);
        } else {
            showSourceList();
        }
    } else {
        showSourceList();
    }
    return;
}
    if (d == browserForm) {
        if (c == backFromBrowserCommand) {
            canvasUI.setState(MainCanvas.STATE_DETAILS); 

            display.setCurrent(canvasUI); 
        }
    }
        if (d == searchInputBox) {
            if (c == startSearchCommand) {
                currentQuery = searchInputBox.getString();
                display.setCurrent(canvasUI);
                startSearchThread(currentQuery);
            } else if (c == cancelSearchCommand) {
                display.setCurrent(canvasUI);
            }
        }
    }

    public void openSearchInput() {
        searchInputBox.setString(currentQuery.equals("Click to search...") ? "" : currentQuery);
        display.setCurrent(searchInputBox);
    }
    
    public void showDetails() {
    canvasUI.setState(MainCanvas.STATE_DETAILS);
    display.setCurrent(canvasUI);
}

    public void prepareVideoDetails(int index) {
        if (index >= 0 && index < videoIds.size()) {
            selectedVideoId = (String) videoIds.elementAt(index);
            selectedVideoTitle = (String) videoTitles.elementAt(index);
            selectedThumbnail = null;

            canvasUI.resetDetailsState();
            canvasUI.setState(MainCanvas.STATE_DETAILS);

            new Thread(new Runnable() {
                public void run() {
                    loadThumbnail(selectedVideoId);
                }
            }).start();
        }
    }

private void startSearchThread(final String query) {
    canvasUI.isLibraryView = false;
    canvasUI.isSubscriptionView = false;
    videoTitles = new Vector();
    videoIds = new Vector(); 
    this.currentPage = 1; 
    this.currentQuery = query;
    videoTitles.removeAllElements();
    videoIds.removeAllElements();
    canvasUI.setLoading(true);

    new Thread(new Runnable() {
        public void run() {
            String url;
            if (query.equalsIgnoreCase("trending")) {
                url = baseUrl + "trending";
            } else {
                url = baseUrl + replace(query, " ", "+");
            }
            performSearch(url);
        }
    }).start();
}

private void performSearch(String url) {
    HttpConnection hc = null;
    InputStream is = null;
    try {
        currentSearchUrl = url; 
        cookieBrowser.log("HTTP_REQUEST (Search): " + url);

        hc = (HttpConnection) Connector.open(url + connectionSuffix);
        hc.setRequestProperty("User-Agent", PC_USER_AGENT);
        hc.setRequestMethod("GET");
        int status = hc.getResponseCode();
        cookieBrowser.log("HTTP_RESPONSE (Search): " + status);
        if (status != 200) {
            videoTitles.addElement("Search failed (HTTP " + status + ")");
            videoIds.addElement("");
            return;
        }
        is = hc.openInputStream();

        StringBuffer sb = new StringBuffer();
        int ch;
        while ((ch = is.read()) != -1) {
            sb.append((char) ch);
        }
        parseHtml(sb.toString());
        loadChannelThumbs(videoIds, false);
    } catch (Exception e) {
        videoTitles.addElement("Error: " + e.getMessage());
        videoIds.addElement("");
    } finally {
        try { if (is != null) is.close(); if (hc != null) hc.close(); } catch (Exception e) {}
        canvasUI.setLoading(false);
    }
}

public void changePage(final boolean next) {
    if (!next && currentPage <= 1) return;

    if (next) currentPage++;
    else currentPage--;

    videoTitles.removeAllElements();
    videoIds.removeAllElements();
    canvasUI.setLoading(true);
    canvasUI.resetScroll();
    new Thread(new Runnable() {
        public void run() {
            String url = "";
            if (currentPage == 1) {
                if (currentQuery.equalsIgnoreCase("")) {
                    url = baseUrl + "";
                } else {
                    url = baseUrl + replace(currentQuery, " ", "+");
                }
            } else {
                url = baseUrl + replace(currentQuery, " ", "+") 
                              + "&page=" + currentPage;
            }

            performSearch(url);
        }
    }).start();
}

private void parseHtml(String html) {
    int index = 0;
    while (index < html.length()) {
        int itemStart = html.indexOf("class=\"video-item\"", index);
        if (itemStart == -1) break;

        int itemEnd = html.indexOf("</div>", itemStart);
        if (itemEnd == -1) itemEnd = html.length();

        String block = html.substring(itemStart, itemEnd);

        String title = "";
        int sStart = block.indexOf("<span>");
        int sEnd = block.indexOf("</span>", sStart);
        if (sStart != -1 && sEnd != -1) {
            title = stripHtml(block.substring(sStart + 6, sEnd)).trim();
        }

        if (block.indexOf("action=channel") != -1) {
            int chIdx = block.indexOf("channel=");
            if (chIdx != -1) {
                int quoteEnd = block.indexOf("\"", chIdx);
                if (quoteEnd != -1) {
                    String chUrl = block.substring(chIdx + 8, quoteEnd);
                    if (title.length() > 0) {
                        videoTitles.addElement(decodeHtml(title));
                        videoIds.addElement("CHANNEL:" + chUrl);
                    }
                }
            }
        } else if (block.indexOf("watch%3Fv%3D") != -1) {
            int vIdx = block.indexOf("watch%3Fv%3D");
            if (vIdx + 12 + 11 <= block.length()) {
                String videoId = block.substring(vIdx + 12, vIdx + 12 + 11);
                if (title.length() > 0) {
                    videoTitles.addElement(decodeHtml(title));
                    videoIds.addElement(videoId);
                }
            }
        } else if (block.indexOf("watch?v=") != -1) {
            int vIdx = block.indexOf("watch?v=");
            if (vIdx + 8 + 11 <= block.length()) {
                String videoId = block.substring(vIdx + 8, vIdx + 8 + 11);
                if (title.length() > 0) {
                    videoTitles.addElement(decodeHtml(title));
                    videoIds.addElement(videoId);
                }
            }
        }

        index = itemEnd;
    }
}

    private int lastIndexOf(String source, String target, int fromIndex) {
        if (fromIndex >= source.length()) {
            fromIndex = source.length() - 1;
        }
        for (int i = fromIndex; i >= 0; i--) {
            if (source.startsWith(target, i)) {
                return i;
            }
        }
        return -1;
    }

    private void loadThumbnail(String videoId) {
        HttpConnection hc = null;
        InputStream is = null;
        try {
            String imgUrl = "http://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
            cookieBrowser.log("HTTP_REQUEST (Thumbnail): " + imgUrl);
            hc = (HttpConnection) Connector.open(imgUrl + connectionSuffix);
            int status = hc.getResponseCode();
            cookieBrowser.log("HTTP_RESPONSE (Thumbnail): " + status);
            is = hc.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            selectedThumbnail = Image.createImage(baos.toByteArray(), 0, baos.size());
            canvasUI.repaint();
        } catch (Exception e) {
        } finally {
            try { if (is != null) is.close(); if (hc != null) hc.close(); } catch (Exception e) {}
        }
    }

    private String decodeHtml(String str) {
        str = replace(str, "&amp;", "&");
        str = replace(str, "&quot;", "\"");
        str = replace(str, "&#39;", "'");
        return str;
    }

    private String replace(String str, String pattern, String repl) {
        StringBuffer result = new StringBuffer();
        int s = 0, e;
        while ((e = str.indexOf(pattern, s)) >= 0) {
            result.append(str.substring(s, e)).append(repl);
            s = e + pattern.length();
        }
        result.append(str.substring(s));
        return result.toString();
    }

    class MainCanvas extends Canvas {
private int[] detailButtonY = new int[5];
public static final int STATE_LIST = 0;
public static final int STATE_DETAILS = 1;
public static final int STATE_PLAYER = 2;
public static final int STATE_DOWNLOAD = 3;
        private int lastTappedIndex = -1;
        private int currentState = STATE_LIST;
        private int touchStartX, touchStartY, touchStartScrollY;
        private boolean isDragging = false;
        private int listSelectedIndex = 0; 
        private int detailsSelectedIndex = 0;
        private int scrollY = 0; 
        private int detailsScrollY = 0; 
        private boolean isLoading = false;
        public static final int STATE_BROWSE = 0;
        private int playerSelectedIndex = 0;  
        public int actionChoiceSelectedIndex = 0;
        private int actionChoiceScrollY = 0;
       private final int ITEM_HEIGHT = 55;
private final int NAV_BAR_HEIGHT = 55;
private final int BOTTOM_BAR_HEIGHT = 32;
private final int BUTTON_HEIGHT = 38;
private final int HEADER_HEIGHT = 30;
        private Midlet midlet;
        private final Font FONT_TITLE = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        private final Font FONT_LABEL = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        private final Font FONT_SMALL = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        public static final int STATE_ACTION_CHOICE = 4;
        private boolean isLibraryView = false;
public boolean isSubscriptionView = false;
private boolean xFocused = false;
public static final int STATE_MENU = 5;
private int menuSelectedIndex = 0;
public static final int STATE_SOURCE = 6;
private int sourceSelectedIndex = 0;

public void showSource() {
    midlet.showSourceList();
}

public void showMenu() {
    menuSelectedIndex = 0;
    setState(STATE_MENU);
}

public void showSubscriptions() {
    isSubscriptionView = true;
    isLibraryView = false;
    scrollY = 0;
    listSelectedIndex = 5;
    midlet.loadChannelThumbs(midlet.subscribeUrls, true);
    repaint();
}
        
        public MainCanvas(Midlet midlet) {
            this.midlet = midlet;
            setFullScreenMode(true);
        }

        public void setState(int state) {
    this.currentState = state;
    this.lastTappedIndex = -1;
    repaint();
}

        public void resetDetailsState() {
    this.detailsSelectedIndex = 0;
}

        public void resetPlayerState() {
            this.playerSelectedIndex = 0;
        }

        public void setLoading(boolean state) {
            this.isLoading = state;
            repaint();
        }
        
protected void pointerPressed(int x, int y) {
    touchStartX = x;
    touchStartY = y;
    if (currentState == STATE_DETAILS) {
        touchStartScrollY = detailsScrollY;
    } else if (currentState == STATE_ACTION_CHOICE) {
        touchStartScrollY = actionChoiceScrollY;
    } else {
        touchStartScrollY = scrollY;
    }
    isDragging = false;
}

protected void pointerDragged(int x, int y) {
    isDragging = true;
    int dy = touchStartY - y;
    if (currentState == STATE_LIST) {
        scrollY = touchStartScrollY + dy;
        if (scrollY < 0) scrollY = 0;
    } else if (currentState == STATE_DETAILS) {
        detailsScrollY = touchStartScrollY + dy;
        if (detailsScrollY < 0) detailsScrollY = 0;
    }
    repaint();
}

private void unsubscribeAt(int i) {
    xFocused = false;
    if (i < 0 || i >= midlet.subscribeUrls.size()) return;
    midlet.removeSubscription((String) midlet.subscribeUrls.elementAt(i));
    if (listSelectedIndex >= midlet.subscribeUrls.size() + 5) listSelectedIndex = 4;
    lastTappedIndex = -1;
    repaint();
}

protected void pointerReleased(int x, int y) {
    if (isDragging) { isDragging = false; return; }
    int h = getHeight(); int w = getWidth();

    if (currentState == STATE_LIST) {
        if (y >= h - BOTTOM_BAR_HEIGHT) {
            int btn = x / (w / 4);
            if (btn == lastTappedIndex) {
                switch (btn) {
                    case 0: midlet.startSearchThread("trending"); break;
                    case 1: showSubscriptions(); break;
                    case 2: showLibrary(); break;
                    case 3: showMenu(); break;
                }
            } else {
                listSelectedIndex = btn;
                lastTappedIndex = btn;
            }
            repaint(); return;
        }

int tappedIndex = -1;
int searchBarTop = HEADER_HEIGHT - scrollY;
if (y >= searchBarTop && y < searchBarTop + NAV_BAR_HEIGHT) {
    tappedIndex = 4;
} else {
    Vector titles = isSubscriptionView ? midlet.subscribeTitles : (isLibraryView ? midlet.libraryTitles : midlet.videoTitles);
    int itemY = HEADER_HEIGHT - scrollY + NAV_BAR_HEIGHT;
    
    if (isSubscriptionView) {
        if (y >= itemY && y < itemY + ITEM_HEIGHT) {
            midlet.startSubFeedThread();
            return;
        }
        itemY += ITEM_HEIGHT;
    }

    for (int i = 0; i < titles.size(); i++) {
        if (y >= itemY && y < itemY + ITEM_HEIGHT) {
            if (isSubscriptionView && x >= w - 40) { unsubscribeAt(i); return; }
            tappedIndex = isSubscriptionView ? i + 6 : i + 5; 
            break;
        }
        itemY += ITEM_HEIGHT;
    }
    if (tappedIndex == -1) {
        int totalN = titles.size();
        int backIdx = totalN + 5;
        int nextIdx = totalN + 6;
        if (y >= itemY && y < itemY + ITEM_HEIGHT) {
            if (midlet.currentPage > 1 && x < w / 2) {
                tappedIndex = backIdx;
            } else {
                tappedIndex = nextIdx;
            }
        }
    }
}

        if (tappedIndex == -1) return;
        if (tappedIndex == lastTappedIndex) {
            listSelectedIndex = tappedIndex;
            keyPressed(Canvas.KEY_NUM5);
        } else {
            listSelectedIndex = tappedIndex;
            lastTappedIndex = tappedIndex;
            updateScroll();
            repaint();
        }

    } else if (currentState == STATE_DETAILS) {
    for (int i = 0; i <= 6; i++) {
        if (detailButtonY[i] > 0 && y >= detailButtonY[i] && y < detailButtonY[i] + BUTTON_HEIGHT) {
            if (i == lastTappedIndex) {
                detailsSelectedIndex = i;
                keyPressed(Canvas.KEY_NUM5);
            } else {
                detailsSelectedIndex = i;
                lastTappedIndex = i;
                repaint();
            }
            return;
        }
    }
} else if (currentState == STATE_PLAYER) {
        int curY = HEADER_HEIGHT + 10 + (w * 9 / 16) + 12 + FONT_TITLE.getHeight() + 4 + FONT_SMALL.getHeight() + 28;
        for (int i = 0; i <= 1; i++) {
            if (y >= curY && y < curY + BUTTON_HEIGHT) {
                if (i == lastTappedIndex) {
                    playerSelectedIndex = i;
                    keyPressed(Canvas.KEY_NUM5);
                } else {
                    playerSelectedIndex = i;
                    lastTappedIndex = i;
                    repaint();
                }
                return;
            }
            curY += BUTTON_HEIGHT + 6;
        }

    } else if (currentState == STATE_ACTION_CHOICE) {
        int curY = HEADER_HEIGHT + 20 - actionChoiceScrollY + FONT_TITLE.getHeight() + 8 + FONT_SMALL.getHeight() + 28;
        for (int i = 0; i <= 2; i++) {
            if (y >= curY && y < curY + BUTTON_HEIGHT) {
                if (i == lastTappedIndex) {
                    actionChoiceSelectedIndex = i;
                    keyPressed(Canvas.KEY_NUM5);
                } else {
                    actionChoiceSelectedIndex = i;
                    lastTappedIndex = i;
                    repaint();
                }
                return;
            }
            curY += BUTTON_HEIGHT + 6;
        }
} else if (currentState == STATE_MENU) {
    int curY = menuTop();
    for (int i = 0; i <= 3; i++) {
        if (y >= curY && y < curY + BUTTON_HEIGHT) {
            if (i == lastTappedIndex) {
                menuAction(i);
            } else {
                menuSelectedIndex = i;
                lastTappedIndex = i;
                repaint();
            }
            return;
        }
        curY += BUTTON_HEIGHT + 6;
    }
} else if (currentState == STATE_SOURCE) {
    int curY = menuTop();
    for (int i = 0; i <= SOURCES.length + 2; i++) {
        if (y >= curY && y < curY + BUTTON_HEIGHT) {
            if (i == lastTappedIndex) {
                sourceAction(i);
            } else {
                sourceSelectedIndex = i;
                lastTappedIndex = i;
                repaint();
            }
            return;
        }
        curY += BUTTON_HEIGHT + 6;
    }
}
}

private void drawDownloadView(Graphics g, int w, int h) {
    g.setClip(0, HEADER_HEIGHT, w, h - HEADER_HEIGHT);
    int centerY = HEADER_HEIGHT + (h - HEADER_HEIGHT) / 2 - 40;

    int prog = midlet.downloadProgress;
    long received = midlet.downloadReceived;
    long total    = midlet.downloadTotal;

    g.setColor(0xFFFFFF);
    g.setFont(FONT_TITLE);
    g.drawString(prog > 0 ? "Downloading..." : "Connecting...",
        w >> 1, centerY, Graphics.TOP | Graphics.HCENTER);

    if (prog > 0) {
        centerY += 24;

        String sizeText = (total > 0)
            ? formatBytes(received) + " / " + formatBytes(total)
            : formatBytes(received);
        g.setColor(0x999999);
        g.setFont(FONT_SMALL);
        g.drawString(sizeText, w >> 1, centerY, Graphics.TOP | Graphics.HCENTER);

        centerY += 22;

        int barX = 20, barW = w - 40, barH = 12;
        g.setColor(0x333333);
        g.fillRoundRect(barX, centerY, barW, barH, 6, 6);

        int fillW = (barW * prog) / 100;
        if (fillW > 0) {
            g.setColor(0xCC0000);
            g.fillRoundRect(barX, centerY, fillW, barH, 6, 6);
        }

        centerY += 20;
        g.setColor(0xFFFFFF);
        g.setFont(FONT_TITLE);
        g.drawString(prog + "%", w >> 1, centerY, Graphics.TOP | Graphics.HCENTER);
    }
}

        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();

    g.setColor(0x121212);
    g.fillRect(0, 0, w, h);

    if (currentState == STATE_LIST) {
        drawListView(g, w, h);
        drawBottomBar(g, w, h);
    }

if (currentState == STATE_LIST) {
    drawListView(g, w, h);
} else if (currentState == STATE_DETAILS) {
    drawDetailsView(g, w, h);
} else if (currentState == STATE_PLAYER) {
    drawPlayerView(g, w, h);
} else if (currentState == STATE_DOWNLOAD) {
    drawDownloadView(g, w, h);
} else if (currentState == STATE_ACTION_CHOICE) {
    drawActionChoiceView(g, w, h);
} else if (currentState == STATE_MENU) {
    drawMenuView(g, w, h);
}
            g.setClip(0, 0, w, HEADER_HEIGHT);
    g.setColor(0x262626);
    g.fillRect(0, 0, w, HEADER_HEIGHT);

            if (headerLogo != null) {
                g.drawImage(headerLogo, 10, HEADER_HEIGHT >> 1, Graphics.VCENTER | Graphics.LEFT);
            } else {
                g.setColor(0xFFFFFF);
                g.setFont(FONT_TITLE);
                g.drawString("YouTube", 10, (HEADER_HEIGHT - FONT_TITLE.getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
            }
        }
        
private int menuTop() {
    return HEADER_HEIGHT + 20 + FONT_TITLE.getHeight() + 14;
}

private void drawMenuView(Graphics g, int w, int h) {
    g.setClip(0, HEADER_HEIGHT, w, h - HEADER_HEIGHT);
    g.setColor(0xFFFFFF);
    g.setFont(FONT_TITLE);
    g.drawString("Menu", w >> 1, HEADER_HEIGHT + 20, Graphics.TOP | Graphics.HCENTER);
    int y = menuTop();
    drawButton(g, 10, y, w - 20, "Set Download Folder", (menuSelectedIndex == 0));
    y += BUTTON_HEIGHT + 6;
    drawButton(g, 10, y, w - 20, "Source: " + midlet.sourceName(midlet.sourceIndex), (menuSelectedIndex == 1));
    y += BUTTON_HEIGHT + 6;
    drawButton(g, 10, y, w - 20, "Exit App", (menuSelectedIndex == 2));
    y += BUTTON_HEIGHT + 6;
    drawButton(g, 10, y, w - 20, "Back", (menuSelectedIndex == 3));
}

private void menuAction(int i) {
    if (i == 0) {
        setState(STATE_LIST);
        display.setCurrent(new FileSelector(cookieBrowser, null));
    } else if (i == 1) {
        showSource();
    } else if (i == 2) {
        midlet.exitApp();
    } else {
        setState(STATE_LIST);
    }
}

private void handleMenuInput(int keyCode, int action) {
    if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
        if (menuSelectedIndex > 0) menuSelectedIndex--;
    } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
        if (menuSelectedIndex < 3) menuSelectedIndex++;
    } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
        menuAction(menuSelectedIndex);
    } else if (keyCode == -8 || keyCode == 8) {
        setState(STATE_LIST);
    }
}

private void drawSourceView(Graphics g, int w, int h) {
    g.setClip(0, HEADER_HEIGHT, w, h - HEADER_HEIGHT);
    g.setColor(0xFFFFFF);
    g.setFont(FONT_TITLE);
    g.drawString("Select Source", w >> 1, HEADER_HEIGHT + 20, Graphics.TOP | Graphics.HCENTER);
    int y = menuTop();
    int n = SOURCES.length;
    for (int i = 0; i < n; i++) {
        drawButton(g, 10, y, w - 20, SOURCES[i][0] + (i == midlet.sourceIndex ? " *" : ""), (sourceSelectedIndex == i));
        y += BUTTON_HEIGHT + 6;
    }
    String cu = midlet.customUrl;
    if (cu.length() > 18) cu = cu.substring(0, 15) + "...";
    drawButton(g, 10, y, w - 20, "Custom: " + cu + (midlet.sourceIndex == n ? " *" : ""), (sourceSelectedIndex == n));
    y += BUTTON_HEIGHT + 6;
    drawButton(g, 10, y, w - 20, "Edit Custom", (sourceSelectedIndex == n + 1));
    y += BUTTON_HEIGHT + 6;
    drawButton(g, 10, y, w - 20, "Back", (sourceSelectedIndex == n + 2));
}

private void sourceAction(int i) {
    int n = SOURCES.length;
    if (i <= n) {
        setState(STATE_LIST);
        midlet.selectSource(i);
    } else if (i == n + 1) {
        midlet.openCustomEditor(-1);
    } else {
        setState(STATE_MENU);
    }
}

private void handleSourceInput(int keyCode, int action) {
    if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
        if (sourceSelectedIndex > 0) sourceSelectedIndex--;
    } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
        if (sourceSelectedIndex < SOURCES.length + 2) sourceSelectedIndex++;
    } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
        sourceAction(sourceSelectedIndex);
    } else if (keyCode == -8 || keyCode == 8) {
        setState(STATE_MENU);
    }
}

private void drawListView(Graphics g, int w, int h) {
    Vector videoTitles = isSubscriptionView ? midlet.subscribeTitles : (isLibraryView ? midlet.libraryTitles : midlet.videoTitles);
    Vector videoIds = isSubscriptionView ? midlet.subscribeUrls : (isLibraryView ? midlet.libraryIds : midlet.videoIds);
    int viewableHeight = h - HEADER_HEIGHT - BOTTOM_BAR_HEIGHT;
    g.setClip(0, HEADER_HEIGHT, w, viewableHeight);
    
if (isLoading) {
        int centerX = w / 2;
        int centerY = HEADER_HEIGHT + (viewableHeight / 2);
        int r = 12;
        
        g.setColor(0x888888);
        g.setFont(FONT_SMALL);
        g.drawString("Loading...", centerX, centerY + 25, Graphics.TOP | Graphics.HCENTER);

        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            int x = centerX + (int) (Math.cos(angle) * r);
            int y = centerY + (int) (Math.sin(angle) * r);
            int diff = (i - loadFrame + 8) % 8;

            if (diff == 0) g.setColor(0xFFFFFF);
            else if (diff == 1) g.setColor(0xBBBBBB);
            else if (diff == 2) g.setColor(0x777777);
            else g.setColor(0x333333);
            g.fillRoundRect(x - 2, y - 2, 5, 5, 2, 2);
        }

        loadFrame = (loadFrame + 1) % 8;
        try { Thread.sleep(90); } catch (Exception e) {}
        repaint();
        return;
    }
    
    int currentY = HEADER_HEIGHT - scrollY;
    boolean isSearchSelected = (listSelectedIndex == 4);
    g.setColor(0x1F1F1F);
    g.fillRect(0, currentY, w, NAV_BAR_HEIGHT);
    g.setColor(0xAAAAAA);
    g.setFont(FONT_SMALL);
    g.drawString("Search YouTube:", 12, currentY + 5, Graphics.TOP | Graphics.LEFT);
    int boxX = 10;
    int boxY = currentY + 24;
    int boxW = w - 20;
    int boxH = 24;
    g.setColor(0x121212);
    g.fillRect(boxX, boxY, boxW, boxH);
    g.setColor(isSearchSelected ? 0xCC0000 : 0x444444);
    g.drawRect(boxX, boxY, boxW, boxH);
    g.setColor(isSearchSelected ? 0xFFFFFF : 0x888888);
    g.setFont(FONT_LABEL);
    String dispQ = (currentQuery == null || currentQuery.length() == 0) ? "Type here..." : currentQuery;
    if (dispQ.length() > 25) dispQ = dispQ.substring(0, 22) + "...";

    g.drawString(dispQ, boxX + 5, boxY + 4, Graphics.TOP | Graphics.LEFT);

    currentY += NAV_BAR_HEIGHT; 

    if (!isLoading) {
        if (isSubscriptionView) {
            boolean isFeedSelected = (listSelectedIndex == 5);
            if (currentY + ITEM_HEIGHT > HEADER_HEIGHT && currentY < h - BOTTOM_BAR_HEIGHT) {
                if (isFeedSelected) {
                    g.setColor(0x333333);
                    g.fillRect(0, currentY, w, ITEM_HEIGHT);
                    g.setColor(0xCC0000);
                    g.fillRect(0, currentY, 3, ITEM_HEIGHT);
                }
                g.setColor(0xFFFFFF);
                g.setFont(FONT_TITLE);
                g.drawString("Lastest Channel Videos", 12, currentY + 12, 0);
                g.setColor(0x888888);
                g.setFont(FONT_SMALL);
                g.drawString("Get latest uploads from any subscribed channels", 12, currentY + 32, 0);
            }
            currentY += ITEM_HEIGHT;
        }

        for (int i = 0; i < videoTitles.size(); i++) {
            int idx = isSubscriptionView ? i + 6 : i + 5; 
            boolean isItemSelected = (listSelectedIndex == idx);

            if (currentY + ITEM_HEIGHT > HEADER_HEIGHT && currentY < h - BOTTOM_BAR_HEIGHT) {
                if (isItemSelected) {
                    g.setColor(0x333333); 

                    g.fillRect(0, currentY, w, ITEM_HEIGHT);
                    g.setColor(0xCC0000); 

                    g.fillRect(0, currentY, 3, ITEM_HEIGHT);
                }
                Image th = (Image) midlet.channelThumbs.get(isSubscriptionView ? "CHANNEL:" + (String) videoIds.elementAt(i) : (String) videoIds.elementAt(i));
                int tx = 12;
                if (th != null) {
                    g.drawImage(th, 8, currentY + (ITEM_HEIGHT - th.getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
                    tx = 8 + th.getWidth() + 6;
                }

                g.setColor(0xFFFFFF);
                g.setFont(FONT_TITLE);
                String t = (String) videoTitles.elementAt(i);
                if (t.length() > 30) t = t.substring(0, 27) + "...";
                g.drawString(t, tx, currentY + 6, 0);

                g.setColor(isItemSelected ? 0xCCCCCC : 0x777777);
                g.setFont(FONT_SMALL);
    String idVal = (String) videoIds.elementAt(i);
    String labelText = isSubscriptionView ? "Subscribed Channel" : (idVal.startsWith("CHANNEL:") ? "View Channel" : "ID: " + idVal);

    g.setColor(isItemSelected ? 0xCCCCCC : 0x777777);
    g.setFont(FONT_SMALL);
    g.drawString(labelText, tx, currentY + 30, 0);
    if (isSubscriptionView) {
    int xs = 24;
    int xx = w - xs - 8;
    int xy = currentY + (ITEM_HEIGHT - xs) / 2;
    g.setColor((isItemSelected && xFocused) ? 0xCC0000 : 0x444444);
    g.fillRoundRect(xx, xy, xs, xs, 6, 6);
    g.setColor(0xFFFFFF);
    g.setFont(FONT_TITLE);
    g.drawString("x", xx + (xs >> 1), xy + (xs - FONT_TITLE.getHeight()) / 2, Graphics.TOP | Graphics.HCENTER);
}
            }
            currentY += ITEM_HEIGHT;
        }

int totalN = (isSubscriptionView ? midlet.subscribeTitles : (isLibraryView ? midlet.libraryTitles : midlet.videoTitles)).size();
int backIdx = totalN + 5;
int nextIdx = totalN + 6;

        g.setColor(0x1F1F1F);
        g.fillRect(0, currentY, w, ITEM_HEIGHT);

        int btnW = (w / 2) - 15;
        int btnH = 30;
        int btnY = currentY + 12;

        if (midlet.currentPage > 1) {
            boolean isBackSelected = (listSelectedIndex == backIdx);
            g.setColor(isBackSelected ? 0xCC0000 : 0x333333);
            g.fillRoundRect(10, btnY, btnW, btnH, 6, 6);
            g.setColor(0xFFFFFF);
            g.drawString("Back", 10 + (btnW/2), btnY + 7, Graphics.TOP | Graphics.HCENTER);
        }

        g.setColor(0x888888);

        boolean isNextSelected = (listSelectedIndex == nextIdx);
        g.setColor(isNextSelected ? 0xCC0000 : 0x333333);
        g.fillRoundRect(w/2 + 5, btnY, btnW, btnH, 6, 6);
        g.setColor(0xFFFFFF);
        g.drawString("Next", w/2 + 5 + (btnW/2), btnY + 7, Graphics.TOP | Graphics.HCENTER);

        currentY += ITEM_HEIGHT;
    }
    g.setClip(0, 0, getWidth(), getHeight()); 

}

private void drawBottomBar(Graphics g, int w, int h) {
    int barY = h - BOTTOM_BAR_HEIGHT;
    g.setClip(0, barY, w, BOTTOM_BAR_HEIGHT);

    g.setColor(0x1A1A1A);
    g.fillRect(0, barY, w, BOTTOM_BAR_HEIGHT);
    g.setColor(0x333333);
    g.drawLine(0, barY, w, barY); 

int btnW = w / 4;
drawNavButton(g, (listSelectedIndex == 0) ? midlet.iconHomeHover : midlet.iconHome, "Home", 0, barY, btnW, h, (listSelectedIndex == 0));
drawNavButton(g, (listSelectedIndex == 1) ? midlet.iconShortsHover : midlet.iconShorts, "Subscribe", btnW, barY, btnW, h, (listSelectedIndex == 1));
drawNavButton(g, (listSelectedIndex == 2) ? midlet.iconLibraryHover : midlet.iconLibrary, "Library", btnW * 2, barY, btnW, h, (listSelectedIndex == 2));
drawNavButton(g, (listSelectedIndex == 3) ? midlet.iconMenuHover : midlet.iconMenu, "Menu", btnW * 3, barY, btnW, h, (listSelectedIndex == 3));
    
    g.setClip(0, 0, getWidth(), getHeight());
}

private void drawNavButton(Graphics g, Image icon, String name, int x, int y, int w, int screenH, boolean selected) {
    if (selected) {
        g.setColor(0x282828);
        g.fillRect(x, y + 1, w, BOTTOM_BAR_HEIGHT - 1);
        g.setColor(0xCC0000);
        g.fillRect(x + 2, screenH - 2, w - 4, 2);
    }

    int centerX = x + (w / 2);
    
    if (icon != null) {
        g.drawImage(icon, centerX, y + 10, Graphics.VCENTER | Graphics.HCENTER);
    }

    g.setFont(FONT_SMALL);
    g.setColor(selected ? 0xFFFFFF : 0x888888);
    g.drawString(name, centerX, y + BOTTOM_BAR_HEIGHT - 2, Graphics.BOTTOM | Graphics.HCENTER);
}

        private String formatBytes(long bytes) {
    if (bytes < 1024)       return bytes + " B";
    if (bytes < 1048576)    return (bytes / 1024) + " KB";
    return (bytes / 1048576) + "." + ((bytes % 1048576) / 104858) + " MB";
}

        private void drawDetailsView(Graphics g, int w, int h) {
    g.setClip(0, HEADER_HEIGHT, w, h - HEADER_HEIGHT);

    int currentY = HEADER_HEIGHT - detailsScrollY;
    int thumbH = (w * 9) / 16;

    g.setColor(0x000000);
    g.fillRect(0, currentY, w, thumbH);
    if (selectedThumbnail != null) {
        g.drawImage(selectedThumbnail, w >> 1, currentY + (thumbH >> 1), Graphics.VCENTER | Graphics.HCENTER);
    }
    currentY += thumbH;

    g.setColor(0x1A1A1A);
    g.fillRect(0, currentY, w, 70);

    currentY += 10;
    g.setColor(0xFFFFFF);
    g.setFont(FONT_TITLE);
    String t = selectedVideoTitle;
    if (t.length() > 50) t = t.substring(0, 47) + "...";
    g.drawString(t, 10, currentY, 0);

    currentY += 22;
    g.setColor(0x999999);
    g.setFont(FONT_SMALL);
    g.drawString("ID: " + selectedVideoId, 10, currentY, 0);

    currentY += 45;

    detailButtonY = new int[7];
    detailButtonY[0] = currentY;
    drawButton(g, 10, currentY, w - 20, "Stream MJPEG + AMR 2G", (detailsSelectedIndex == 0));
    currentY += BUTTON_HEIGHT + 6;
    
    detailButtonY[1] = currentY;
    drawButton(g, 10, currentY, w - 20, "Stream MJPEG + AAC 3G", (detailsSelectedIndex == 1));
    currentY += BUTTON_HEIGHT + 6;

    detailButtonY[2] = currentY;
    drawButton(g, 10, currentY, w - 20, "Stream MJPEG + AAC HF", (detailsSelectedIndex == 2));
    currentY += BUTTON_HEIGHT + 6;

    detailButtonY[3] = currentY;
    drawButton(g, 10, currentY, w - 20, "Stream Chuncked 3GP", (detailsSelectedIndex == 3));
    currentY += BUTTON_HEIGHT + 6;

    detailButtonY[4] = currentY;
    drawButton(g, 10, currentY, w - 20, "[DL] MP4 144p (MPEG)", (detailsSelectedIndex == 4));
    currentY += BUTTON_HEIGHT + 6;

    detailButtonY[5] = currentY;
    drawButton(g, 10, currentY, w - 20, "[DL] MP3 Audio only", (detailsSelectedIndex == 5));
    currentY += BUTTON_HEIGHT + 15;

    detailButtonY[6] = currentY;
    drawButton(g, 10, currentY, w - 20, "Back to Search", (detailsSelectedIndex == 6));
}

        private void drawPlayerView(Graphics g, int w, int h) {
            g.setClip(0, HEADER_HEIGHT, w, h - HEADER_HEIGHT);

            int currentY = HEADER_HEIGHT + 10;

            int thumbH = (w * 9) / 16;
            g.setColor(0x000000);
            g.fillRect(0, currentY, w, thumbH);
            if (selectedThumbnail != null) {
                g.drawImage(selectedThumbnail, w >> 1, currentY + (thumbH >> 1), Graphics.VCENTER | Graphics.HCENTER);
            }

            g.setColor(0xCC0000);
            g.fillRoundRect(w - 60, currentY + thumbH - 26, 52, 22, 6, 6);
            g.setColor(0xFFFFFF);
            g.setFont(FONT_TITLE);
            String badge = playerType.toUpperCase();
            g.drawString(badge, w - 34, currentY + thumbH - 22, Graphics.TOP | Graphics.HCENTER);

            currentY += thumbH + 12;

            g.setColor(0xFFFFFF);
            g.setFont(FONT_TITLE);
            String t = selectedVideoTitle;
            if (t.length() > 50) t = t.substring(0, 47) + "...";
            g.drawString(t, 10, currentY, 0);
            currentY += FONT_TITLE.getHeight() + 4;

            g.setColor(0x888888);
            g.setFont(FONT_SMALL);
            String urlDisplay = playerUrl;
            if (urlDisplay.length() > 38) urlDisplay = urlDisplay.substring(0, 35) + "...";
            g.drawString(urlDisplay, 10, currentY, 0);
            currentY += FONT_SMALL.getHeight() + 16;

            g.setColor(0x333333);
            g.drawLine(10, currentY, w - 10, currentY);
            currentY += 12;

            g.setColor(0x666666);
            g.setFont(FONT_SMALL);
            g.drawString("Choose action:", 10, currentY, 0);
            currentY += FONT_SMALL.getHeight() + 8;

            drawButton(g, 10, currentY, w - 20, "Open in Browser", (playerSelectedIndex == 0));
            currentY += BUTTON_HEIGHT + 6;
            drawButton(g, 10, currentY, w - 20, "Back to Details", (playerSelectedIndex == 1));
        }

        private void drawButton(Graphics g, int x, int y, int w, String text, boolean isSelected) {
            g.setColor(isSelected ? 0xCC0000 : 0x2A2A2A);
            g.fillRoundRect(x, y, w, BUTTON_HEIGHT, 8, 8);
            g.setColor(isSelected ? 0xFFFFFF : 0xCCCCCC);
            g.setFont(FONT_TITLE);
            g.drawString(text, x + (w >> 1), y + 8, Graphics.TOP | Graphics.HCENTER);
        }

        protected void keyPressed(int keyCode) {
            int action = getGameAction(keyCode);
            if (currentState == STATE_LIST) {
                handleListInput(keyCode, action);
            } else if (currentState == STATE_DETAILS) {
                handleDetailsInput(keyCode, action);
            } else if (currentState == STATE_PLAYER) {
                handlePlayerInput(keyCode, action);
            } else if (currentState == STATE_ACTION_CHOICE) {
    handleActionChoiceInput(keyCode, action);
} else if (currentState == STATE_MENU) {
    handleMenuInput(keyCode, action);
}
            repaint();
        }

private void handleListInput(int keyCode, int action) {
    Vector videoTitles = isSubscriptionView ? midlet.subscribeTitles : (isLibraryView ? midlet.libraryTitles : midlet.videoTitles);
    int totalN = videoTitles.size();
int startIdx = isSubscriptionView ? 6 : 5;
int lastItemIdx = isSubscriptionView ? (5 + totalN) : (4 + totalN);
int backIdx = isSubscriptionView ? totalN + 6 : totalN + 5;
int nextIdx = isSubscriptionView ? totalN + 7 : totalN + 6;

if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
    if (listSelectedIndex >= 0 && listSelectedIndex <= 3) {
        listSelectedIndex = (totalN > 0) ? nextIdx : 4;
    } else if (listSelectedIndex == 4) {
        listSelectedIndex = 0;
    } else if (isSubscriptionView && listSelectedIndex == 5) {
        listSelectedIndex = 4;
    } else if (listSelectedIndex == startIdx) {
        listSelectedIndex = isSubscriptionView ? 5 : 4;
    } else if (listSelectedIndex == nextIdx) {
        listSelectedIndex = backIdx;
    } else if (listSelectedIndex == backIdx) {
        listSelectedIndex = lastItemIdx;
    } else {
        listSelectedIndex--;
    }
    updateScroll();
} else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
    if (listSelectedIndex >= 0 && listSelectedIndex <= 3) {
        listSelectedIndex = 4;
    } else if (listSelectedIndex == 4) {
        listSelectedIndex = isSubscriptionView ? 5 : (totalN > 0 ? startIdx : 0);
    } else if (isSubscriptionView && listSelectedIndex == 5) {
        listSelectedIndex = totalN > 0 ? startIdx : backIdx;
    } else if (listSelectedIndex >= startIdx && listSelectedIndex < lastItemIdx) {
        listSelectedIndex++;
    } else if (listSelectedIndex == lastItemIdx) {
        listSelectedIndex = backIdx;
    } else if (listSelectedIndex == backIdx) {
        listSelectedIndex = nextIdx;
    } else if (listSelectedIndex == nextIdx) {
        listSelectedIndex = 0;
    } else {
        listSelectedIndex = 0;
    }
    updateScroll();
} else if (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4 || keyCode == -3 || keyCode == 102 || keyCode == 52) {
        if (xFocused) xFocused = false;
        else if (listSelectedIndex == 1 || listSelectedIndex == 2 || listSelectedIndex == 3) listSelectedIndex--;
        else if (listSelectedIndex == nextIdx && midlet.currentPage > 1) listSelectedIndex = backIdx;
    } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == -4 || keyCode == 104 || keyCode == 54) {
        if (isSubscriptionView && listSelectedIndex >= 5 && listSelectedIndex <= totalN + 4) xFocused = true;
        else if (listSelectedIndex == 0 || listSelectedIndex == 1 || listSelectedIndex == 2) listSelectedIndex++;
        else if (listSelectedIndex == backIdx) listSelectedIndex = nextIdx;
    } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
        switch (listSelectedIndex) {
            case 0: midlet.startSearchThread("trending"); break;
            case 1: showSubscriptions(); break;
            case 2: showLibrary(); break;
            case 3: showMenu(); break;
            case 4: midlet.openSearchInput(); break;
            case 5:
                if (isSubscriptionView) {
                    midlet.startSubFeedThread();
                    return;
                }
            default:
                int subIdx = isSubscriptionView ? listSelectedIndex - 6 : listSelectedIndex - 5;
                if (xFocused) { unsubscribeAt(subIdx); return; }
                if (isSubscriptionView) {
                    if (subIdx >= 0 && subIdx < midlet.subscribeUrls.size()) {
                        String url = (String) midlet.subscribeUrls.elementAt(subIdx);
                        String title = (String) midlet.subscribeTitles.elementAt(subIdx);
                        midlet.startChannelThread(url, title);
                    }
                } else if (isLibraryView) {
                    int libIdx = listSelectedIndex - 5;
                    if (libIdx >= 0 && libIdx < midlet.libraryPaths.size()) {
                        midlet.startBuiltInPlayer((String) midlet.libraryPaths.elementAt(libIdx));
                    }
                } else if (listSelectedIndex == backIdx) {
                    midlet.changePage(false);
                } else if (listSelectedIndex == nextIdx) {
                    midlet.changePage(true);
                } else {
                    int vidIdx = listSelectedIndex - 5;
                    if (vidIdx >= 0 && vidIdx < videoIds.size()) {
                        String id = (String) videoIds.elementAt(vidIdx);
                        if (id != null && id.length() > 0) {
                            if (id.startsWith("UNSUB_ACTION:")) {
                                midlet.removeSubscription(id.substring(13));
                                midlet.canvasUI.showSubscriptions();
                            } else if (id.startsWith("SUB_ACTION:")) {
                                String data = id.substring(11);
                                int pipe = data.indexOf('|');
                                if (pipe > 0) {
                                    midlet.addSubscription(data.substring(pipe + 1), data.substring(0, pipe));
                                    midlet.canvasUI.showSubscriptions();
                                }
                            } else if (id.startsWith("CHANNEL:")) {
                                String title = (String) videoTitles.elementAt(vidIdx);
                                midlet.startChannelThread(id.substring(8), title);
                            } else {
                                midlet.prepareVideoDetails(vidIdx);
                            }
                        }
                    }
                }
                break;
        }
    }
}

private void showLibrary() {
    isLibraryView = true;
    isSubscriptionView = false;
    scrollY = 0;
    listSelectedIndex = 5;
    repaint();
}

        private void handleListInput2(int keyCode, int action) {
            int totalVideoCount = videoTitles.size();
            if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
                if (listSelectedIndex > 1) listSelectedIndex--;
                else if (listSelectedIndex == 1) listSelectedIndex = 0;
                updateScroll();
            } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
                if (listSelectedIndex < 2) listSelectedIndex = 2;
                else if (listSelectedIndex < totalVideoCount + 1) listSelectedIndex++;
                updateScroll();
            } else if (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4 || keyCode == -3 || keyCode == 102 || keyCode == 52) {
                if (listSelectedIndex == 1) listSelectedIndex = 0;
            } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == -4 || keyCode == 104 || keyCode == 54) {
                if (listSelectedIndex == 0) listSelectedIndex = 1;
            } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
                if (listSelectedIndex == 0) openSearchInput();
                else if (listSelectedIndex == 1) exitApp();
                else prepareVideoDetails(listSelectedIndex - 2);
            }
        }

        private void handleDetailsInput(int keyCode, int action) {
            if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
                if (detailsSelectedIndex > 0) {
                    detailsSelectedIndex--;
                    updateDetailsScroll();
                } else {
                    detailsScrollY -= 20;
                    if (detailsScrollY < 0) detailsScrollY = 0;
                }
            } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
                if (detailsSelectedIndex < 6) {
                    detailsSelectedIndex++;
                    updateDetailsScroll();
                } else {
                    detailsScrollY += 20;
                    updateDetailsScroll(); 
                }
            } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
                if (detailsSelectedIndex == 6) {
                    setState(STATE_LIST);
                    return;
                }
if (detailsSelectedIndex == 0) {
    aacMode = false;
    aacHf = false;
    String runUrl = mainurl + phpdlp + "?action=run&url=" + cookieBrowser.urlEncode("https://www.youtube.com/watch?v=" + selectedVideoId) + "&format=mjpeg_amr";
    cookieBrowser.fetchUrl(runUrl, null);
    display.setCurrent(cookieBrowser.browserCanvas);
    return;
} else if (detailsSelectedIndex == 1) {
    aacMode = true;
    aacHf = false;
    String runUrl = mainurl + phpdlp + "?action=run&url=" + cookieBrowser.urlEncode("https://www.youtube.com/watch?v=" + selectedVideoId) + "&format=mjpeg_aac";
    cookieBrowser.fetchUrl(runUrl, null);
    display.setCurrent(cookieBrowser.browserCanvas);
    return;
} else if (detailsSelectedIndex == 2) {
    aacMode = true;
    aacHf = true;
    String runUrl = mainurl + phpdlp + "?action=run&url=" + cookieBrowser.urlEncode("https://www.youtube.com/watch?v=" + selectedVideoId) + "&format=mjpeg_aac_hf";
    cookieBrowser.fetchUrl(runUrl, null);
    display.setCurrent(cookieBrowser.browserCanvas);
    return;
}
                String format = "3gp";
                if (detailsSelectedIndex == 5) format = "mp3";
                //else if (detailsSelectedIndex == 1) format = "mp4_mpeg4";
                else if (detailsSelectedIndex == 3) format = "3gp";
                else if (detailsSelectedIndex == 4) format = "mp4_mpeg4";

                String runUrl = mainurl + phpdlp + "?action=run&url=" + cookieBrowser.urlEncode("https://www.youtube.com/watch?v=" + selectedVideoId) + "&format=" + format;
                cookieBrowser.fetchUrl(runUrl, null);
                display.setCurrent(cookieBrowser.browserCanvas);
            } else if (keyCode == -8 || keyCode == 8) {
                setState(STATE_LIST);
            }
        }

private void handlePlayerInput(int keyCode, int action) {
            if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
                if (playerSelectedIndex > 0) playerSelectedIndex--;
            } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
                if (playerSelectedIndex < 1) playerSelectedIndex++;
            } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
                if (playerSelectedIndex == 0) {

                    try {

                        if (midlet.player != null) {
                            midlet.player.close();
                            midlet.player = null;
                        }
                        cookieBrowser.fetchUrl(playerUrl, null);
                        display.setCurrent(cookieBrowser.browserCanvas);
                    } catch (Exception e) {}
                } else if (playerSelectedIndex == 1) {

                    cleanupInternalPlayer();
                    setState(STATE_DETAILS);
                }
            } else if (keyCode == -8 || keyCode == 8) {

                cleanupInternalPlayer();
                setState(STATE_DETAILS);
            }
        }

        private void cleanupInternalPlayer() {
            try {
                if (midlet.player != null) {
                    midlet.player.stop();
                    midlet.player.close();
                    midlet.player = null;
                }
                if (midlet.videoControl != null) {
                    midlet.videoControl.setVisible(false);
                    midlet.videoControl = null;
                }
            } catch (Exception e) {

            }
        }
        
private void drawActionChoiceView(Graphics g, int w, int h) {
    g.setClip(0, HEADER_HEIGHT, w, h - HEADER_HEIGHT);
    int y = HEADER_HEIGHT + 20 - actionChoiceScrollY;

    g.setColor(0xFFFFFF);
    g.setFont(FONT_TITLE);
    g.drawString("Captured Video Conveted", w>>1, y, Graphics.TOP|Graphics.HCENTER);
    y += FONT_TITLE.getHeight() + 8;

    g.setColor(0x888888);
    g.setFont(FONT_SMALL);
    g.drawString("Option:", w>>1, y, Graphics.TOP|Graphics.HCENTER);
    y += FONT_SMALL.getHeight() + 14;

    g.setColor(0x333333);
    g.drawLine(20, y, w-20, y);
    y += 14;

    drawButton(g, 10, y, w - 20, "Watch", (actionChoiceSelectedIndex == 0));
    y += BUTTON_HEIGHT + 6;
    drawButton(g, 10, y, w - 20, "Save to permanent", (actionChoiceSelectedIndex == 1));
    y += BUTTON_HEIGHT + 6;
    drawButton(g, 10, y, w - 20, "Cancel", (actionChoiceSelectedIndex == 2));
}

        private void handleDetailsInput2(int keyCode, int action) {
            if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
                if (detailsSelectedIndex > 0) {
                    detailsSelectedIndex--;
                    updateDetailsScroll();
                } else {
                    detailsScrollY -= 20;
                    if (detailsScrollY < 0) detailsScrollY = 0;
                }
            } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
                if (detailsSelectedIndex < 6) {
                    detailsSelectedIndex++;
                    updateDetailsScroll();
                } else {
                    detailsScrollY += 20;
                    updateDetailsScroll(); 
                }
} else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
  
            } else if (keyCode == -8 || keyCode == 8) {
                setState(STATE_LIST);
            }
        }

private void handleActionChoiceInput(int keyCode, int action) {
    if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == 116 || keyCode == -1) {
    if (actionChoiceSelectedIndex > 0) {
        actionChoiceSelectedIndex--;
        updateActionChoiceScroll();
    } else {
        actionChoiceScrollY -= 20;
        if (actionChoiceScrollY < 0) actionChoiceScrollY = 0;
    }
} else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == 98 || keyCode == -2) {
        if (actionChoiceSelectedIndex < 2) {
            actionChoiceSelectedIndex++;
            updateActionChoiceScroll();
        }
    } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
        switch (actionChoiceSelectedIndex) {
    case 0: setState(STATE_LIST); midlet.saveMediaToFile(midlet.capturedMediaUrl, true);  break;
    case 1: midlet.saveMediaToFile(midlet.capturedMediaUrl, false); break;
    case 2: setState(STATE_DETAILS); break;
}
    } else if (keyCode == -8 || keyCode == 8) {
        setState(STATE_DETAILS);
    }
}

private void updateScroll() {
    xFocused = false;
    if (listSelectedIndex < 4) { scrollY = 0; return; }

    int viewH = getHeight() - HEADER_HEIGHT - BOTTOM_BAR_HEIGHT;
    int itemTop, itemBottom;
    int totalN = (isSubscriptionView ? midlet.subscribeTitles : (isLibraryView ? midlet.libraryTitles : midlet.videoTitles)).size();

int headerOffset = NAV_BAR_HEIGHT + (isSubscriptionView ? ITEM_HEIGHT : 0);
if (listSelectedIndex == 4) {
    itemTop = 0; itemBottom = NAV_BAR_HEIGHT;
} else if (isSubscriptionView && listSelectedIndex == 5) {
    itemTop = NAV_BAR_HEIGHT; itemBottom = NAV_BAR_HEIGHT + ITEM_HEIGHT;
} else {
    int contentIdx = isSubscriptionView ? (listSelectedIndex - 6) : (listSelectedIndex - 5);
    if (contentIdx >= 0 && contentIdx < totalN) {
        itemTop = headerOffset + (contentIdx * ITEM_HEIGHT);
        itemBottom = itemTop + ITEM_HEIGHT;
    } else {
        itemTop = headerOffset + (totalN * ITEM_HEIGHT);
        itemBottom = itemTop + ITEM_HEIGHT;
    }
}
int totalContentHeight = headerOffset + (totalN * ITEM_HEIGHT) + ITEM_HEIGHT;
int maxScroll = totalContentHeight - viewH;
if (scrollY < 0) scrollY = 0;
if (maxScroll > 0 && scrollY > maxScroll) scrollY = maxScroll;
else if (maxScroll <= 0) scrollY = 0;

    if (itemTop < scrollY) scrollY = itemTop;
    else if (itemBottom > scrollY + viewH) scrollY = itemBottom - viewH;
}

public void resetScroll() {
        this.scrollY = 0;
        this.listSelectedIndex = 3;
    }

private void updateActionChoiceScroll() {
    int viewH = getHeight() - HEADER_HEIGHT;
    int w = getWidth();
    int thumbH = (w * 9) / 16;
    int infoH = 20;
    int buttonAreaY = thumbH + infoH + 45;
    int itemTop = buttonAreaY + (actionChoiceSelectedIndex * (BUTTON_HEIGHT + 6));
    int itemBottom = itemTop + BUTTON_HEIGHT;
    if (itemTop < actionChoiceScrollY) actionChoiceScrollY = itemTop;
    else if (itemBottom > actionChoiceScrollY + viewH) actionChoiceScrollY = itemBottom - viewH;
    int totalContentHeight = buttonAreaY + (6 * (BUTTON_HEIGHT + 6)) + 15 + BUTTON_HEIGHT + 20;
    int maxScroll = totalContentHeight - viewH;
    if (actionChoiceScrollY < 0) actionChoiceScrollY = 0;
    if (maxScroll > 0 && actionChoiceScrollY > maxScroll) actionChoiceScrollY = maxScroll;
}

private void updateDetailsScroll() {
            int viewH = getHeight() - HEADER_HEIGHT;
            int w = getWidth();
            int thumbH = (w * 9) / 16;
            int infoH = 70;
            int buttonAreaY = thumbH + infoH + 45; 
            int itemTop = buttonAreaY + (detailsSelectedIndex * (BUTTON_HEIGHT + 6));
            int itemBottom = itemTop + BUTTON_HEIGHT;
            if (itemTop < detailsScrollY) detailsScrollY = itemTop;
            else if (itemBottom > detailsScrollY + viewH) detailsScrollY = itemBottom - viewH;
            int totalContentHeight = buttonAreaY + (5 * (BUTTON_HEIGHT + 6)) + 15 + BUTTON_HEIGHT + 20;
            int maxScroll = totalContentHeight - viewH;
            if (detailsScrollY < 0) detailsScrollY = 0;
            if (maxScroll > 0 && detailsScrollY > maxScroll) detailsScrollY = maxScroll;
        }
    }

public class LibraryStore {

    private static final String STORE_NAME = "VyoutubSubscribe";

    public void load(java.util.Vector titles, java.util.Vector ids, java.util.Vector paths) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            while (re.hasNextElement()) {
                try {
                    String record = new String(re.nextRecord());
                    if (record.startsWith("PATH|") || record.startsWith("SRC|") || record.startsWith("CUS|")) {
                        continue;
                    }
                    int first = record.indexOf('|');
                    int second = record.indexOf('|', first + 1);
                    if (first > 0 && second > 0) {
                        ids.addElement(record.substring(0, first));
                        titles.addElement(record.substring(first + 1, second));
                        paths.addElement(record.substring(second + 1));
                    }
                } catch (Exception ignored) {}
            }
            re.destroy();
        } catch (Exception e) {
            System.out.println("Ta ku ru Ka Ga Mi[RMS] load error: " + e.getMessage());
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    public String loadPath() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            while (re.hasNextElement()) {
                try {
                    String record = new String(re.nextRecord());
                    if (record.startsWith("PATH|")) {
                        re.destroy();
                        return record.substring(5);
                    }
                } catch (Exception ignored) {}
            }
            re.destroy();
        } catch (Exception e) {}
        finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
        }
        return null;
    }
    
public void loadCustoms(Vector names, Vector urls, Vector phps) {
    RecordStore rs = null;
    try {
        rs = RecordStore.openRecordStore(STORE_NAME, true);
        RecordEnumeration re = rs.enumerateRecords(null, null, false);
        while (re.hasNextElement()) {
            try {
                String record = new String(re.nextRecord());
                if (!record.startsWith("CUS|")) continue;
                String d = record.substring(4);
                int p1 = d.indexOf('|');
                if (p1 <= 0) continue;
                int p2 = d.indexOf('|', p1 + 1);
                if (p2 > 0) {
                    names.addElement(d.substring(0, p1));
                    urls.addElement(d.substring(p1 + 1, p2));
                    phps.addElement(d.substring(p2 + 1));
                } else {
                    String u = d.substring(0, p1);
                    names.addElement(u);
                    urls.addElement(u);
                    phps.addElement(d.substring(p1 + 1));
                }
            } catch (Exception ignored) {}
        }
        re.destroy();
    } catch (Exception e) {}
    finally {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
    }
}

public void saveCustoms(Vector names, Vector urls, Vector phps) {
    RecordStore rs = null;
    try {
        rs = RecordStore.openRecordStore(STORE_NAME, true);
        Vector oldIds = new Vector();
        RecordEnumeration re = rs.enumerateRecords(null, null, false);
        while (re.hasNextElement()) {
            int id = re.nextRecordId();
            try {
                if (new String(rs.getRecord(id)).startsWith("CUS|")) oldIds.addElement(new Integer(id));
            } catch (Exception ignored) {}
        }
        re.destroy();
        for (int i = 0; i < oldIds.size(); i++) {
            rs.deleteRecord(((Integer) oldIds.elementAt(i)).intValue());
        }
        for (int i = 0; i < names.size(); i++) {
            byte[] b = ("CUS|" + names.elementAt(i) + "|" + urls.elementAt(i) + "|" + phps.elementAt(i)).getBytes();
            rs.addRecord(b, 0, b.length);
        }
    } catch (Exception e) {}
    finally {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
    }
}

    public void savePath(String path) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            int pathRecordId = -1;
            while (re.hasNextElement()) {
                int id = re.nextRecordId();
                try {
                    String record = new String(rs.getRecord(id));
                    if (record.startsWith("PATH|")) {
                        pathRecordId = id;
                        break;
                    }
                } catch (Exception ignored) {}
            }
            re.destroy();

            if (pathRecordId != -1) {
                rs.deleteRecord(pathRecordId);
            }
            byte[] b = ("PATH|" + path).getBytes();
            rs.addRecord(b, 0, b.length);
        } catch (Exception e) {}
        finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    public void add(String title, String id, String path) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            while (re.hasNextElement()) {
                try {
                    String record = new String(re.nextRecord());
                    int first = record.indexOf('|');
                    if (first > 0 && record.substring(0, first).equals(id)) {
                        re.destroy();
                        return;
                    }
                } catch (Exception ignored) {}
            }
            re.destroy();
            String record = id + "|" + title + "|" + path;
            byte[] b = record.getBytes();
            rs.addRecord(b, 0, b.length);
        } catch (Exception e) {
            System.out.println("Ta ku ru Ka Ga Mi[RMS] add error: " + e.getMessage());
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

public int loadSource() {
    RecordStore rs = null;
    try {
        rs = RecordStore.openRecordStore(STORE_NAME, true);
        RecordEnumeration re = rs.enumerateRecords(null, null, false);
        while (re.hasNextElement()) {
            String record = new String(re.nextRecord());
            if (record.startsWith("SRC|")) {
                re.destroy();
                return Integer.parseInt(record.substring(4));
            }
        }
        re.destroy();
    } catch (Exception e) {}
    finally {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
    }
    return 0;
}

public void saveSource(int idx) {
    RecordStore rs = null;
    try {
        rs = RecordStore.openRecordStore(STORE_NAME, true);
        RecordEnumeration re = rs.enumerateRecords(null, null, false);
        int oldId = -1;
        while (re.hasNextElement()) {
            int id = re.nextRecordId();
            try {
                if (new String(rs.getRecord(id)).startsWith("SRC|")) { oldId = id; break; }
            } catch (Exception ignored) {}
        }
        re.destroy();
        if (oldId != -1) rs.deleteRecord(oldId);
        byte[] b = ("SRC|" + idx).getBytes();
        rs.addRecord(b, 0, b.length);
    } catch (Exception e) {}
    finally {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
    }
}

public boolean hasSource() {
    RecordStore rs = null;
    try {
        rs = RecordStore.openRecordStore(STORE_NAME, true);
        RecordEnumeration re = rs.enumerateRecords(null, null, false);
        while (re.hasNextElement()) {
            String record = new String(re.nextRecord());
            if (record.startsWith("SRC|")) {
                re.destroy();
                return true;
            }
        }
        re.destroy();
    } catch (Exception e) {}
    finally {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception ignored) {}
    }
    return false;
}
}
}
