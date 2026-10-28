import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import javax.microedition.io.HttpConnection;
import javax.microedition.rms.RecordStore;

class CookieManager {
  private Hashtable cookieStore = new Hashtable();
  
  private CookieBrowser parent;
  
  private static final String RS_NAME = "KumiaCookie";
  
  public CookieManager(CookieBrowser parent) {
    this.parent = parent;
    loadFromRMS();
  }
  
  public void storeCookies(HttpConnection conn) throws IOException {
    int index = 0;
    boolean changed = false;
    String headerKey;
    while ((headerKey = conn.getHeaderFieldKey(index)) != null) {
      if (headerKey.equalsIgnoreCase("Set-Cookie")) {
        String cookieHeader = conn.getHeaderField(index);
        if (cookieHeader != null) {
          this.parent.log("COOKIE_RCV: " + cookieHeader);
          parseAndStore(cookieHeader);
          changed = true;
        } 
      } 
      index++;
    } 
    if (changed)
      saveToRMS(); 
  }
  
  private void parseAndStore(String rawCookie) {
    int semi = rawCookie.indexOf(';');
    String pair = (semi == -1) ? rawCookie : rawCookie.substring(0, semi);
    int equal = pair.indexOf('=');
    if (equal > 0) {
      String name = pair.substring(0, equal).trim();
      String value = pair.substring(equal + 1).trim();
      this.cookieStore.put(name, value);
    } 
  }
  
  public void setRequestCookies(HttpConnection conn) throws IOException {
    if (this.cookieStore.isEmpty())
      return; 
    StringBuffer sb = new StringBuffer();
    Enumeration keys = this.cookieStore.keys();
    while (keys.hasMoreElements()) {
      String name = (String)keys.nextElement();
      String value = (String)this.cookieStore.get(name);
      sb.append(name).append("=").append(value);
      if (keys.hasMoreElements())
        sb.append("; "); 
    } 
    conn.setRequestProperty("Cookie", sb.toString());
  }
  
  void saveToRMS() {
    RecordStore rs = null;
    try {
      try {
        RecordStore.deleteRecordStore("KumiaCookie");
      } catch (Exception exception) {}
      rs = RecordStore.openRecordStore("KumiaCookie", true);
      StringBuffer sb = new StringBuffer();
      Enumeration keys = this.cookieStore.keys();
      while (keys.hasMoreElements()) {
        String name = (String)keys.nextElement();
        String value = (String)this.cookieStore.get(name);
        sb.append(name).append(":").append(value);
        if (keys.hasMoreElements())
          sb.append("|"); 
      } 
      byte[] data = sb.toString().getBytes();
      rs.addRecord(data, 0, data.length);
      this.parent.log("RMS: Cookies Saved");
    } catch (Exception e) {
      this.parent.log("RMS_ERR_SAVE: " + e.getMessage());
    } finally {
      if (rs != null)
        try {
          rs.closeRecordStore();
        } catch (Exception exception) {} 
    } 
  }
  
  private void loadFromRMS() {
    RecordStore rs = null;
    try {
      rs = RecordStore.openRecordStore("KumiaCookie", true);
      if (rs.getNumRecords() > 0) {
        byte[] data = rs.getRecord(1);
        String raw = new String(data);
        int start = 0;
        while (start < raw.length()) {
          int pipe = raw.indexOf('|', start);
          String pair = (pipe == -1) ? raw.substring(start) : raw.substring(start, pipe);
          int colon = pair.indexOf(':');
          if (colon > 0) {
            String name = pair.substring(0, colon);
            String value = pair.substring(colon + 1);
            this.cookieStore.put(name, value);
          } 
          if (pipe == -1)
            break; 
          start = pipe + 1;
        } 
        this.parent.log("RMS: Cookies Loaded (" + this.cookieStore.size() + ")");
      } 
    } catch (Exception e) {
      this.parent.log("RMS_ERR_LOAD: " + e.getMessage());
    } finally {
      if (rs != null)
        try {
          rs.closeRecordStore();
        } catch (Exception exception) {} 
    } 
  }
  
  public void clearCookies() {
    this.cookieStore.clear();
    try {
      RecordStore.deleteRecordStore("KumiaCookie");
    } catch (Exception exception) {}
    this.parent.log("RMS: Cookies Cleared");
  }
}