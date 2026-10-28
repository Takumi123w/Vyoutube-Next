import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

public class CookieBrowser implements CommandListener {
  Display display;
  public Midlet parentMidlet;
  public int pendingDownloadMode = -1;
  BrowserCanvas browserCanvas;
  private String connectionSuffix = "";
  private Form homeForm;
  boolean silent = false;
  private TextBox logsView;
  private TextField urlInput;
  private boolean isWaitingPage = false;
  String pendingConvertUrl = "";
  private boolean isCaptchaPage = false;
  private String currentPageUrl = "";
  private Command showLogsCommand;

  private Command clearLogsCommand;

  CookieManager cookieManager;

  private StringBuffer logBuffer = new StringBuffer();

  private Hashtable imageCache = new Hashtable();

  private String currentBaseUrl = parentMidlet.mainurl;

  private Command selectCommand;

  public CookieBrowser() {
      setupConnection();
    this.cookieManager = new CookieManager(this);
    this.browserCanvas = new BrowserCanvas(this);
    this.homeForm = new Form("MicroTube");
    this.urlInput = new TextField("Server URL:", this.currentBaseUrl, 512, 0);
    this.homeForm.append((Item)this.urlInput);

    this.showLogsCommand = new Command("Logs", Command.SCREEN, 3);
    this.clearLogsCommand = new Command("Clear", Command.SCREEN, 4);
    this.logsView = new TextBox("Debug Logs", "", 4096, 0);
    this.logsView.addCommand(this.clearLogsCommand);
    this.logsView.setCommandListener(this);

    this.homeForm.addCommand(this.showLogsCommand);
    this.selectCommand = new Command("Select", Command.ITEM, 1);
  }

  void showHome() {
    this.display.setCurrent((Displayable)this.homeForm);
  }

  void showLogs() {
    this.logsView.setString(this.logBuffer.toString());
    this.display.setCurrent((Displayable)this.logsView);
  }

public void log(String msg) {
    Calendar cal = Calendar.getInstance();
    String time = cal.get(11) + ":" + cal.get(12) + ":" + cal.get(13);
    String entry = "[" + time + "] " + msg;
    System.out.println(entry);
    if (this.logBuffer.length() > 3000) {
      this.logBuffer.setLength(0);
    }
    this.logBuffer.append(entry).append("\n");
  }

public void saveSession() {
    log("App closed: Saving session...");
    if (this.cookieManager != null)
      this.cookieManager.saveToRMS();
  }

  public void commandAction(Command c, Displayable d) {
    if (c == this.selectCommand) {
            if (this.browserCanvas != null) {
                this.browserCanvas.activateSelected();
            }
        } else if (c == this.showLogsCommand) {
      showLogs();
    } else if (c == this.clearLogsCommand) {
      this.logBuffer.setLength(0);
      this.logsView.setString("");
    }
  }

  String urlEncode(String s) {
    if (s == null)
      return "";
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch == ' ') {
        sb.append('+');
      } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
        sb.append(ch);
      } else {
        String hex = Integer.toHexString(ch & 0xFF).toUpperCase();
        if (hex.length() == 1) {
          sb.append("%0").append(hex);
        } else {
          sb.append('%').append(hex);
        }
      }
    }
    return sb.toString();
  }
  
    private void setupConnection() {
        String platform = System.getProperty("microedition.platform");
        if (platform != null && platform.toLowerCase().indexOf("blackberry") != -1) {
            connectionSuffix = ";interface=wifi";
        }
    }

void fetchUrl(final String url, final String postData) {
    fetchUrl(url, postData, 0);
}

void fetchUrl(final String url, final String postData, final int retryCount) {
    final int MAX_RETRIES = 3;
    final CookieBrowser self = this;
    new Thread(new Runnable() {
      public void run() {
        self.showLoading();
        HttpConnection hc = null;
        InputStream is = null;
        OutputStream os = null;
        try {
          String targetUrl = url;
          if (!url.startsWith("http")) {
            targetUrl = self.resolveUrl(url);
          }
          self.currentPageUrl = targetUrl;
          if (targetUrl.startsWith("https://"))
            targetUrl = "http://" + targetUrl.substring(8);
                    int qi = targetUrl.indexOf('?');
          if (qi != -1 && targetUrl.startsWith(parentMidlet.mainurl)) {
            String qs = targetUrl.substring(qi + 1);
            StringBuffer qb = new StringBuffer(targetUrl.substring(0, qi + 1));
            int p = 0;
            while (p <= qs.length()) {
              int amp = qs.indexOf('&', p);
              if (amp == -1) amp = qs.length();
              String pair = qs.substring(p, amp);
              int eq = pair.indexOf('=');
              if (eq != -1) {
                qb.append(self.encodeAll(self.urlDecode(pair.substring(0, eq)))).append('=').append(self.encodeAll(self.urlDecode(pair.substring(eq + 1))));
              } else {
                qb.append(self.encodeAll(self.urlDecode(pair)));
              }
              if (amp < qs.length()) qb.append('&');
              p = amp + 1;
            }
            targetUrl = qb.toString();
          }
          self.log("HTTP_REQUEST: " + targetUrl);
          hc = (HttpConnection)Connector.open(targetUrl + connectionSuffix, 3, true);
          self.cookieManager.setRequestCookies(hc);
          if (postData != null) {
            hc.setRequestMethod("POST");
            hc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            os = hc.openOutputStream();
            os.write(postData.getBytes());
            os.close();
            os = null;
          }
          int responseCode = hc.getResponseCode();
          self.log("HTTP_RESPONSE: " + responseCode);
          if (responseCode >= 300 && responseCode < 400) {
            String location = hc.getHeaderField("Location");
            self.log("REDIRECT_TO: " + location);
            if (location != null && location.length() > 0) {
              self.cookieManager.storeCookies(hc);
              self.fetchUrl(location, null);
              return;
            }
          }
          if (responseCode == 200) {
            String contentType = hc.getType();
            String currentReqUrl = self.currentPageUrl;
            if ((contentType != null && (contentType.startsWith("video/") || contentType.startsWith("audio/"))) || (currentReqUrl != null && currentReqUrl.indexOf("download=") != -1)) {
              self.log("DETECTED_FILE_DOWNLOAD: " + currentReqUrl);
              final String downloadUrl = currentReqUrl;
              try {
                if (hc != null) hc.close();
              } catch (Exception e) {}
              if (self.parentMidlet != null) {
                self.display.callSerially(new Runnable() {
                  public void run() {
                    self.parentMidlet.showActionChoice(downloadUrl);
                  }
                });
                return;
              }
            }
            String html;
            self.cookieManager.storeCookies(hc);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
              is = hc.openInputStream();
              byte[] buffer = new byte[1024];
              int bytesRead;
              while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
              }
            } finally {
              if (is != null) { try { is.close(); } catch (Exception e) {} }
              if (hc != null) { try { hc.close(); } catch (Exception e) {} }
            }
            byte[] raw = baos.toByteArray();
            byte[] clean = self.remove4ByteUTF8(raw);
            try {
              html = new String(clean, "UTF-8");
            } catch (Exception e) {
              html = new String(clean);
            }
            self.log("RAW_HTML_START:\n" + html + "\nRAW_HTML_END");
self.log("RAW_HTML_START:\n" + html + "\nRAW_HTML_END");

int streamIdx = html.indexOf("action=stream&job=");
if (streamIdx != -1) {
    int jobStart = streamIdx + "action=stream&job=".length();
    int jobEnd = jobStart;
    while (jobEnd < html.length()
            && html.charAt(jobEnd) != '"'
            && html.charAt(jobEnd) != '\''
            && html.charAt(jobEnd) != '&') {
        jobEnd++;
    }
    final String jobId = html.substring(jobStart, jobEnd);
    if (self.parentMidlet != null) {
        self.display.callSerially(new Runnable() {
            public void run() {
                self.parentMidlet.showStreamPlayer(jobId);
            }
        });
        return;
    }
}

if (self.parentMidlet != null && self.parentMidlet.wavMode) {
    int audioIdx = html.indexOf("action=audio&job=");
    if (audioIdx != -1) {
        int jobStart = audioIdx + "action=audio&job=".length();
        int jobEnd = jobStart;
        while (jobEnd < html.length()
                && html.charAt(jobEnd) != '"'
                && html.charAt(jobEnd) != '\''
                && html.charAt(jobEnd) != '&') {
            jobEnd++;
        }
        final String wavJobId = html.substring(jobStart, jobEnd);
        self.display.callSerially(new Runnable() {
            public void run() {
                self.parentMidlet.showWavPlayer(wavJobId);
            }
        });
        return;
    }
}

/*
int chunkIdx = html.indexOf("action=chunk&job=");
if (chunkIdx != -1) {
    int jobStart = chunkIdx + "action=chunk&job=".length();
    int jobEnd = jobStart;
    while (jobEnd < html.length()
            && html.charAt(jobEnd) != '"'
            && html.charAt(jobEnd) != '\''
            && html.charAt(jobEnd) != '&') {
        jobEnd++;
    }
    final String chunkJobId = html.substring(jobStart, jobEnd);
    self.log("CHUNK_3GP: Launching player for job=" + chunkJobId);
    if (self.parentMidlet != null) {
        self.display.callSerially(new Runnable() {
            public void run() {
                self.parentMidlet.showChunkPlayer(chunkJobId);
            }
        });
    }
    return;
}
*/

int vseClassIdx = html.indexOf("class=\"vse\"");
            int refreshTextIdx = html.indexOf("обновить");

            if (vseClassIdx != -1 && refreshTextIdx != -1) {
                int aTagStart = html.lastIndexOf('<', vseClassIdx);

                int hrefStart = html.indexOf("href=\"", aTagStart);
                int aTagEnd = html.indexOf("</a>", refreshTextIdx);

if (hrefStart != -1 && aTagEnd != -1 && hrefStart < vseClassIdx) {
                    hrefStart += 6; 
                    int hrefEnd = html.indexOf("\"", hrefStart);
                    if (hrefEnd != -1) {
                        final String refreshLink = html.substring(hrefStart, hrefEnd);
                        String onScreenButtonHtml = "<br/><form action=\"" + refreshLink + "\" method=\"get\"><input type=\"submit\" value=\"REFRESH\" /></form><br/>";
                        aTagEnd += 4; 
                        html = html.substring(0, aTagEnd) + onScreenButtonHtml + html.substring(aTagEnd);
                    }
                }
            }
if (self.pendingConvertUrl.length() > 0 && html.indexOf("zkod.php") == -1) {
    String nextUrl = self.pendingConvertUrl;
    self.pendingConvertUrl = "";
    self.fetchUrl(nextUrl, null);
    return;
}
            if (self.pendingDownloadMode != -1 && html.indexOf("zkod.php") == -1) {
                String targetFormat = "";
                switch (self.pendingDownloadMode) {
                    case 0: targetFormat = ".3gp"; break; 
                    case 1: targetFormat = "144.3gp"; break;
                    case 2: targetFormat = "144.mp4"; break;
                    case 3: targetFormat = "240.mp4"; break;
                    case 4: targetFormat = ".mp4"; break;
                    case 5: targetFormat = ".mp3"; break;
                }

                String matchedUrl = null;
                String lowerHtml = html.toLowerCase();
                int aIdx = 0;

                while ((aIdx = lowerHtml.indexOf("href=\"", aIdx)) != -1) {
                    aIdx += 6;
                    int endQuote = lowerHtml.indexOf("\"", aIdx);
                    if (endQuote != -1) {
                        String link = html.substring(aIdx, endQuote);
                        String lowerLink = link.toLowerCase();

                        if (lowerLink.indexOf("attach.php") == -1 && lowerLink.indexOf("javascript") == -1) {
                            if (targetFormat.length() > 0 && lowerLink.indexOf(targetFormat) != -1) {
                                if (self.pendingDownloadMode == 4 && (lowerLink.indexOf("144") != -1 || lowerLink.indexOf("240") != -1)) {
                                } else {
                                    matchedUrl = link;
                                    break;
                                }
                            }
                        }
                        aIdx = endQuote;
                    }
                }

                if (matchedUrl == null) {
                    aIdx = 0;
                    while ((aIdx = lowerHtml.indexOf("href=\"", aIdx)) != -1) {
                        aIdx += 6;
                        int endQuote = lowerHtml.indexOf("\"", aIdx);
                        if (endQuote != -1) {
                            String link = html.substring(aIdx, endQuote);
                            String lowerLink = link.toLowerCase();

                            if (lowerLink.indexOf("attach.php") == -1 && lowerLink.indexOf("javascript") == -1) {
                                if (self.pendingDownloadMode == 5 && lowerLink.indexOf(".mp3") != -1) {
                                    matchedUrl = link; break;
                                } else if (self.pendingDownloadMode != 5) {
                                    if (targetFormat.indexOf(".mp4") != -1 && lowerLink.indexOf(".mp4") != -1) {
                                        if (self.pendingDownloadMode == 4 && (lowerLink.indexOf("144") != -1 || lowerLink.indexOf("240") != -1)) {
                                        } else {
                                            matchedUrl = link; break;
                                        }

                                    } else if (targetFormat.indexOf(".3gp") != -1 && lowerLink.indexOf(".3gp") != -1) {
                                        matchedUrl = link; break;
                                    }
                                }
                            }
                            aIdx = endQuote;
                        }
                    }
                }

                if (matchedUrl != null) {
                    self.log("AUTO_DOWNLOAD MATCHED: " + matchedUrl);
                    self.pendingDownloadMode = -1;
                    matchedUrl = self.replace(matchedUrl, "&amp;", "&");
                    final String finalMatchedUrl = matchedUrl;

                    if (self.parentMidlet != null) {
                        self.display.callSerially(new Runnable() {
                            public void run() {
                                self.parentMidlet.showActionChoice(finalMatchedUrl);
                            }
                        });
                        return;
                    }
                }
            }

self.renderPage(html);
          } else if (responseCode == 403 && retryCount < MAX_RETRIES) {
    self.log("403 Forbidden: Retrying " + (retryCount + 1) + "/" + MAX_RETRIES);
    try { 
        Thread.sleep(1000);
    } catch (Exception e) {}
    
    if (is != null) is.close();
    if (hc != null) hc.close();
    
    self.fetchUrl(url, postData, retryCount + 1);
    return;
} 
else {
            self.log("HTTP_ERROR: " + responseCode);
          }
        } catch (Exception e) {
          self.log("EXCEPTION: " + e.toString());
        } finally {
          try {
            if (is != null) is.close();
            if (hc != null) hc.close();
          } catch (Exception e) {}
        }
      }
    }).start();
  }

  private byte[] remove4ByteUTF8(byte[] input) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < input.length; i++) {
      int b = input[i] & 0xFF;
      if (b >= 240 && b <= 247) {
        int skip = 3;
        while (skip > 0 && i + 1 < input.length) {
          int next = input[i + 1] & 0xFF;
          if (next >= 128 && next <= 191) {
            i++;
            skip--;
          }
        }
      } else {
        out.write(b);
      }
    }
    return out.toByteArray();
  }

  private void renderPage(String html) {
    log("RENDER_PAGE: Starting");
isCaptchaPage = html.indexOf("zkod.php") != -1;
isWaitingPage = !isCaptchaPage
             && html.indexOf("class=\"vse\"") != -1
             && html.indexOf("\u043e\u0431\u043d\u043e\u0432\u0438\u0442\u044c") != -1;
    Vector elements = new Vector();
    int tS = html.indexOf("<title>");
    String title = (tS != -1) ? stripTags(html.substring(tS, html.indexOf("</title>", tS))) : "Kumiabook";
    log("PAGE_TITLE: " + title);
    int bodyStart = html.indexOf("<body");
    String content = (bodyStart != -1) ? html.substring(bodyStart) : html;
    log("BODY_START: " + bodyStart);
    int[] pendingImages = { 0 };
    int cursor = 0;
    int elementCount = 0;
    while (cursor < content.length()) {
      int nextTag = content.indexOf('<', cursor);
      if (nextTag == -1) {
        String lastText = stripTags(content.substring(cursor)).trim();
        if (lastText.length() > 0) {
          PageElement elem = new PageElement(0);
          elem.text = lastText;
          elements.addElement(elem);
          elementCount++;
        }
        break;
      }
      String text = stripTags(content.substring(cursor, nextTag)).trim();
      if (text.length() > 0) {
        PageElement elem = new PageElement(0);
        elem.text = text;
        elements.addElement(elem);
        elementCount++;
      }
      String lowerTag = content.substring(nextTag, content.indexOf('>', nextTag) + 1).toLowerCase();
      if (lowerTag.startsWith("<div class=\"header\"") || lowerTag.startsWith("<h1>")) {
        int endTag = lowerTag.startsWith("<h1") ? content.indexOf("</h1>", nextTag) : content.indexOf("</div>", nextTag);
        if (endTag != -1) {
          String headerText = stripTags(content.substring(nextTag, endTag)).trim();
          PageElement elem = new PageElement(8);
          elem.text = headerText;
          elements.addElement(elem);
          cursor = endTag + (lowerTag.startsWith("<h1") ? 5 : 6);
          continue;
        }
      }
      if (lowerTag.startsWith("<a ")) {
        int endA = content.toLowerCase().indexOf("</a>", nextTag);
        if (endA != -1) {
          parseLink(content.substring(nextTag, endA + 4), elements, pendingImages);
          cursor = endA + 4;
          elementCount++;
          continue;
        }
      } else if (lowerTag.startsWith("<form")) {
        int endF = content.toLowerCase().indexOf("</form>", nextTag);
        if (endF != -1) {
          log("FORM_TAG_FOUND at position " + nextTag);
          parseForm(content.substring(nextTag, endF + 7), elements, pendingImages);
          cursor = endF + 7;
          elementCount++;
          continue;
        }
      } else if (lowerTag.startsWith("<img")) {
        int endTag = content.indexOf('>', nextTag);
        if (endTag != -1) {
          log("IMG_TAG_FOUND at position " + nextTag);
          pendingImages[0] = pendingImages[0] + 1;
          parseImageWait(content.substring(nextTag, endTag + 1), elements, pendingImages);
          elementCount++;
          PageElement nl = new PageElement(5);
          elements.addElement(nl);
          elementCount++;
        }
      } else if (lowerTag.startsWith("<div class=\"post\"") || lowerTag.startsWith("<div class='post'")) {
        PageElement elem = new PageElement(4);
        elements.addElement(elem);
        elementCount++;
      } else if (lowerTag.startsWith("<br") || lowerTag.startsWith("<p")) {
        PageElement elem = new PageElement(5);
        elements.addElement(elem);
        elementCount++;
      }
else if (lowerTag.startsWith("<input")) {
    int endTag = content.indexOf('>', nextTag);
    if (endTag != -1) {
        String tagHtml = content.substring(nextTag, endTag + 1);
        String name = getAttr(tagHtml, "name", 0);
        if (name != null) {
            WebForm fakeForm = new WebForm();
            fakeForm.action = this.currentBaseUrl;
            fakeForm.addInput(name, "text", "", name);

            PageElement inputElem = new PageElement(6);
            inputElem.formInput = (WebForm.FormInput)fakeForm.inputs.lastElement();
            inputElem.form = fakeForm;
            elements.addElement(inputElem);
        }
    }
}
      cursor = content.indexOf('>', nextTag) + 1;
    }
    PageElement footer = new PageElement(0);
    elements.addElement(footer);
    elementCount++;
    log("RENDER_COMPLETE: " + elements.size() + " elements.");
    final Vector finalElements = elements;
    final CookieBrowser self = this;
        this.display.callSerially(new Runnable() {
      public void run() {
        if (self.browserCanvas != null) {
          self.browserCanvas.setElements(finalElements);
          self.display.setCurrent((Displayable)self.browserCanvas);
        }
      }
    });
  }

  private String urlDecode(String s) {
    if (s == null)
      return "";
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '%' && i + 2 < s.length()) {
        try {
          int v = Integer.parseInt(s.substring(i + 1, i + 3), 16);
          sb.append((char)v);
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

  private void parseImageWait(final String tag, final Vector elements, final int[] pendingImages) {
    String finalLoadingUrl;
    String src = getAttr(tag, "src", 0);
    if (src == null) {
      pendingImages[0] = pendingImages[0] - 1;
      return;
    }

    final boolean isCaptcha = (src.indexOf("zkod.php") != -1);
    String resolved = resolveUrl(src);
    if (isCaptcha) {
      finalLoadingUrl = resolved;
    } else {
      finalLoadingUrl = "http://wsrv.nl/?url=" + urlEncode(resolved) + "&w=32&h=32&fit=contain&output=jpg";
    }
    String cacheKey = finalLoadingUrl;
    log("FINAL_URL_RAW: " + finalLoadingUrl);
    if (this.imageCache.containsKey(cacheKey)) {
      PageElement pageElement = new PageElement(2);
      pageElement.url = cacheKey;
      pageElement.image = (Image)this.imageCache.get(cacheKey);
      String cachedClass = getAttr(tag, "class", 0);
      pageElement.text = (cachedClass != null && cachedClass.toLowerCase().indexOf("avatar") != -1) ? "avatar" : "image";
      elements.addElement(pageElement);
      pendingImages[0] = pendingImages[0] - 1;
      return;
    }
    final PageElement elem = new PageElement(2);
    elem.url = cacheKey;
    elem.image = null;
    String imgClass = getAttr(tag, "class", 0);
    final boolean isAvatar = (imgClass != null && imgClass.toLowerCase().indexOf("avatar") != -1);
    final boolean isThumb = (imgClass != null && imgClass.toLowerCase().indexOf("img-thumb") != -1);
    elem.text = isAvatar ? "avatar" : "image";
    elements.addElement(elem);
    final String finalSrc = cacheKey;
    final String finalUrl = finalLoadingUrl;
    final CookieBrowser selfRef = this;
    new Thread(new Runnable() {
      public void run() {
        HttpConnection hc = null;
        InputStream is = null;
        try {
          hc = (HttpConnection)Connector.open(finalUrl + connectionSuffix, 1, true);
          selfRef.cookieManager.setRequestCookies(hc);
          hc.setRequestProperty("User-Agent", "Nokia3110c/2.0");
          if (hc.getResponseCode() == 200) {
            is = hc.openInputStream();
            Image rawImg = Image.createImage(is);
            int screenWidth = (selfRef.browserCanvas != null) ? selfRef.browserCanvas.getWidth() : 240;
            if (finalUrl.indexOf("zkod.php") != -1) {
    elem.image = rawImg; 
}else if (isAvatar) {
              if (rawImg.getWidth() != 32 || rawImg.getHeight() != 32)
                rawImg = selfRef.scaleImage(rawImg, 32, 32);
            } else if (isThumb || rawImg.getWidth() > screenWidth - 10) {
              int targetW = screenWidth - 10;
              int targetH = rawImg.getHeight() * targetW / rawImg.getWidth();
              rawImg = selfRef.scaleImage(rawImg, targetW, targetH);
            }
            elem.image = rawImg;
            if (rawImg != null)
              selfRef.imageCache.put(finalSrc, rawImg);
          }
        } catch (Exception e) {
          selfRef.log("IMG_ERR: " + e.getMessage());
        } finally {
          try {
            if (is != null) is.close();
            if (hc != null) hc.close();
          } catch (Exception e) {}
          pendingImages[0] = pendingImages[0] - 1;
          selfRef.display.callSerially(new Runnable() {
            public void run() {
              if (selfRef.browserCanvas != null) {
                selfRef.browserCanvas.refreshLayout();
                selfRef.browserCanvas.repaint();
              }
            }
          });
        }
      }
    }).start();
  }

String resolveUrl(String url) {
    if (url == null) return null;
    url = replace(url, "&amp;", "&");
    if (url.startsWith("http://") || url.startsWith("https://")) return url;
    String base = (currentPageUrl != null && currentPageUrl.indexOf("://") != -1) ? currentPageUrl : parentMidlet.mainurl + "/";
    int q = base.indexOf('?');
    if (q != -1) base = base.substring(0, q);
    int hostStart = base.indexOf("://") + 3;
    if (url.startsWith("/")) {
        int hostEnd = base.indexOf('/', hostStart);
        return (hostEnd != -1 ? base.substring(0, hostEnd) : base) + url;
    }
    int lastSlash = base.lastIndexOf('/');
    if (lastSlash < hostStart) return base + "/" + url;
    return base.substring(0, lastSlash + 1) + url;
}

  private void parseLink(String html, Vector elements, int[] pendingImages) {
    String url = getAttr(html, "href", 0);
    if (url == null)
      return;
    if (url.startsWith("#") || url.toLowerCase().startsWith("javascript"))
      return;
    url = resolveUrl(url);
    if (url == null)
      return;
    
    if (isCaptchaPage) {
        String linkText = stripTags(html).trim().toLowerCase();
        boolean isRefresh = linkText.indexOf("\u043e\u0431\u043d\u043e\u0432\u0438\u0442\u044c") != -1
                         || linkText.indexOf("refresh") != -1
                         || url.toLowerCase().indexOf("obnovit") != -1
                         || url.toLowerCase().indexOf("5217276") != -1
                         || url.toLowerCase().indexOf("dw") != -1;
        if (!isRefresh) return;
    }
if (isWaitingPage) {
    String lowerUrl = url.toLowerCase();
    boolean isRefreshLink = (lowerUrl.indexOf("%26dw") != -1 || lowerUrl.indexOf("&dw") != -1)
                          && lowerUrl.indexOf("type") != -1;
    if (!isRefreshLink) return;
}
    int imgIdx = html.toLowerCase().indexOf("<img");
    if (imgIdx != -1) {
      int imgEnd = html.indexOf('>', imgIdx);
      if (imgEnd != -1) {
        String imgTag = html.substring(imgIdx, imgEnd + 1);
        pendingImages[0] = pendingImages[0] + 1;
        parseImageWait(imgTag, elements, pendingImages);
        return;
      }
    }
    String lowerUrl = url.toLowerCase();
    if (lowerUrl.indexOf(".jpg") != -1 || lowerUrl.indexOf(".png") != -1 || lowerUrl
      .indexOf(".gif") != -1 || lowerUrl.indexOf("wsrv.nl") != -1) {
      pendingImages[0] = pendingImages[0] + 1;
      parseImageWait("<img src=\"" + url + "\">", elements, pendingImages);
      return;
    }
    PageElement elem = new PageElement(1);
    elem.text = stripTags(html).trim();
    if (elem.text.length() == 0)
      elem.text = "Link";
    elem.url = url;
    elements.addElement(elem);
    elements.addElement(new PageElement(5));
  }

  private void parseForm(String formHtml, Vector elements) {
    log("PARSE_FORM: Starting form parse");
    WebForm form = new WebForm();
    String rawAction = getAttr(formHtml, "action", 0);
    if (rawAction == null || rawAction.length() == 0) {
      form.action = this.currentBaseUrl;
    } else {
      form.action = resolveUrl(rawAction);
    }
    PageElement formLabel = new PageElement(3);
    formLabel.form = form;
    elements.addElement(formLabel);
    Vector allInputs = new Vector();
    int inIdx = 0;
    while ((inIdx = formHtml.toLowerCase().indexOf("<input", inIdx)) != -1) {
      String name = getAttr(formHtml, "name", inIdx);
      String type = getAttr(formHtml, "type", inIdx);
      String val = getAttr(formHtml, "value", inIdx);
      if (type != null && type.equalsIgnoreCase("file")) {
        inIdx = formHtml.indexOf('>', inIdx) + 1;
        continue;
      }
      if (type != null && type.equalsIgnoreCase("submit") && (name == null || name.length() == 0))
        name = "submit_btn";
      if (name != null && name.length() > 0) {
        form.addInput(name, (type == null) ? "text" : type, (val == null) ? "" : val, name);
        allInputs.addElement(form.inputs.lastElement());
      }
      inIdx = formHtml.indexOf('>', inIdx) + 1;
    }
    int taIdx = 0;
    while ((taIdx = formHtml.toLowerCase().indexOf("<textarea", taIdx)) != -1) {
      String taName = getAttr(formHtml, "name", taIdx);
      int taEnd = formHtml.toLowerCase().indexOf(">", taIdx);
      int taClose = formHtml.toLowerCase().indexOf("</textarea>", taIdx);
      String taVal = "";
      if (taEnd != -1 && taClose != -1 && taClose > taEnd)
        taVal = formHtml.substring(taEnd + 1, taClose).trim();
      if (taName != null && taName.length() > 0) {
        form.addInput(taName, "text", taVal, taName);
        allInputs.addElement(form.inputs.lastElement());
      }
      taIdx = formHtml.indexOf('>', taIdx) + 1;
    }
    int i;
    for (i = 0; i < allInputs.size(); i++) {
      WebForm.FormInput input = (WebForm.FormInput)allInputs.elementAt(i);
      if (!input.type.equalsIgnoreCase("submit") && !input.type.equalsIgnoreCase("hidden")) {
        PageElement inputElem = new PageElement(6);
        inputElem.formInput = input;
        inputElem.form = form;
        elements.addElement(inputElem);
      }
    }
    for (i = 0; i < allInputs.size(); i++) {
      WebForm.FormInput input = (WebForm.FormInput)allInputs.elementAt(i);
      if (input.type.equalsIgnoreCase("submit")) {
        PageElement btnElem = new PageElement(7);
        btnElem.text = (input.value != null && input.value.length() > 0) ? input.value.toUpperCase() : "SUBMIT";
        btnElem.form = form;
        btnElem.formInput = input;
        elements.addElement(btnElem);
      }
    }
    log("PARSE_FORM_COMPLETE: " + allInputs.size() + " inputs processed");
  }

private void parseForm(String formHtml, Vector elements, int[] pendingImages) {
    log("PARSE_FORM: Starting combined form parse");
    WebForm form = new WebForm();
    String rawAction = getAttr(formHtml, "action", 0);
    if (rawAction == null || rawAction.length() == 0) {
        form.action = this.currentBaseUrl;
    } else {
        form.action = resolveUrl(rawAction);
    }

    PageElement formLabel = new PageElement(3);
    formLabel.form = form;
    elements.addElement(formLabel);

    Vector allInputs = new Vector();
    int cursor = 0;
    String lowerHtml = formHtml.toLowerCase();
    while (cursor < formHtml.length()) {
        int nextTag = lowerHtml.indexOf("<", cursor);
        if (nextTag == -1) break;

        int endTag = formHtml.indexOf(">", nextTag);
        if (endTag == -1) break;

        String tagBody = lowerHtml.substring(nextTag, endTag + 1);

        if (tagBody.startsWith("<img")) {
            log("FORM_IMG_FOUND: Processing captcha/image inside form");
            pendingImages[0] = pendingImages[0] + 1;
            parseImageWait(formHtml.substring(nextTag, endTag + 1), elements, pendingImages);
        } 
        else if (tagBody.startsWith("<input")) {
            String name = getAttr(formHtml, "name", nextTag);
            String type = getAttr(formHtml, "type", nextTag);
            String val = getAttr(formHtml, "value", nextTag);

            if (type != null && type.equalsIgnoreCase("file")) {
            } else {
                if (type != null && type.equalsIgnoreCase("submit") && (name == null || name.length() == 0))
                    name = "submit_btn";

                if (name != null && name.length() > 0) {
                    form.addInput(name, (type == null) ? "text" : type, (val == null) ? "" : val, name);
                    allInputs.addElement(form.inputs.lastElement());
                }
            }
        } 

        else if (tagBody.startsWith("<textarea")) {
            String taName = getAttr(formHtml, "name", nextTag);
            int taClose = lowerHtml.indexOf("</textarea>", nextTag);
            String taVal = "";
            if (taClose != -1 && taClose > endTag) {
                taVal = formHtml.substring(endTag + 1, taClose).trim();
            }
            if (taName != null && taName.length() > 0) {
                form.addInput(taName, "text", taVal, taName);
                allInputs.addElement(form.inputs.lastElement());
            }
            if (taClose != -1) { cursor = taClose + 11; continue; }
        }

        cursor = endTag + 1;
    }

    int i;
    for (i = 0; i < allInputs.size(); i++) {
        WebForm.FormInput input = (WebForm.FormInput)allInputs.elementAt(i);
        if (!input.type.equalsIgnoreCase("submit") && !input.type.equalsIgnoreCase("hidden")) {
            PageElement inputElem = new PageElement(6);
            inputElem.formInput = input;
            inputElem.form = form;
            elements.addElement(inputElem);
        }
    }

    for (i = 0; i < allInputs.size(); i++) {
        WebForm.FormInput input = (WebForm.FormInput)allInputs.elementAt(i);
        if (input.type.equalsIgnoreCase("submit")) {
            PageElement btnElem = new PageElement(7);
            btnElem.text = (input.value != null && input.value.length() > 0) ? input.value.toUpperCase() : "SUBMIT";
            btnElem.form = form;
            btnElem.formInput = input;
            elements.addElement(btnElem);
        }
    }

    log("PARSE_FORM_COMPLETE: " + allInputs.size() + " inputs processed");
}

  private void showLoading() {
    final Form loadingForm = new Form("");
    loadingForm.append((Item)new StringItem(null, "Loading..."));
    this.display.callSerially(new Runnable() {
      public void run() {
        display.setCurrent((Displayable)loadingForm);
      }
    });
  }

  private Image scaleImage(Image src, int w, int h) {
    int srcW = src.getWidth();
    int srcH = src.getHeight();
    log("SCALE_IMAGE: " + srcW + "x" + srcH + " -> " + w + "x" + h);
    Image tmp = Image.createImage(w, h);
    Graphics g = tmp.getGraphics();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        g.setClip(x, y, 1, 1);
        int dx = x * srcW / w;
        int dy = y * srcH / h;
        g.drawImage(src, x - dx, y - dy, 20);
      }
    }
    return Image.createImage(tmp);
  }

  private String getAttr(String html, String attr, int start) {
    int end;
    String find = attr + "=\"";
    int i = html.toLowerCase().indexOf(find, start);
    if (i == -1) {
      find = attr + "='";
      i = html.toLowerCase().indexOf(find, start);
    }
    if (i == -1) {
      find = attr + "=";
      i = html.toLowerCase().indexOf(find, start);
    }
    if (i == -1 || i > html.indexOf('>', start))
      return null;
    int s = i + find.length();
    char quote = html.charAt(s - 1);
    if (quote == '"' || quote == '\'') {
      end = html.indexOf(quote, s);
    } else {
      end = html.indexOf(' ', s);
      int tagEnd = html.indexOf('>', s);
      if (end == -1 || tagEnd < end)
        end = tagEnd;
    }
    return (end == -1) ? null : html.substring(s, end);
  }

  private String stripTags(String t) {
    StringBuffer sb = new StringBuffer();
    boolean in = false;
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (c == '<') {
        in = true;
      } else if (c == '>') {
        in = false;
      } else if (!in) {
        sb.append(c);
      }
    }
    String res = sb.toString();
    res = replace(res, "&amp;",    "&");
    res = replace(res, "&nbsp;",   " ");
    res = replace(res, "&quot;",   "\"");
    res = replace(res, "&apos;",   "'");
    res = replace(res, "&lt;",     "<");
    res = replace(res, "&gt;",     ">");
    res = replace(res, "&lsquo;",  "\u2018");
    res = replace(res, "&rsquo;",  "\u2019");
    res = replace(res, "&mdash;",  "\u2014");
    res = replace(res, "&ndash;",  "\u2013");
    res = replace(res, "&copy;",   "\u00A9");
    res = replace(res, "&reg;",    "\u00AE");
    res = replace(res, "&trade;",  "\u2122");
    res = replace(res, "&middot;", "\u00B7");
    res = replace(res, "&bull;",   "\u2022");
    res = replace(res, "&hellip;", "\u2026");
    res = replace(res, "&euro;",   "\u20AC");
    res = replace(res, "&pound;",  "\u00A3");
    res = replace(res, "&yen;",    "\u00A5");
    res = replace(res, "&cent;",   "\u00A2");
    res = replace(res, "&plusmn;", "\u00B1");
    res = replace(res, "&times;",  "\u00D7");
    res = replace(res, "&divide;", "\u00F7");
    res = replace(res, "&deg;",    "\u00B0");
    res = replace(res, "&permil;", "\u2030");
    res = replace(res, "&laquo;",  "\u00AB");
    res = replace(res, "&raquo;",  "\u00BB");
    res = replace(res, "&#039;",   "'");
    res = decodeNumericEntities(res);
    res = replace(res, "&Aring;",  "\u00C5");
    res = replace(res, "&Agrave;", "\u00C0");
    return res;
  }

  private String decodeNumericEntities(String s) {
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (i < s.length()) {
      if (s.charAt(i) == '&' && i + 2 < s.length() && s.charAt(i + 1) == '#') {
        int j = s.indexOf(';', i);
        if (j != -1)
          try {
            int codePoint;
            String code = s.substring(i + 2, j);
            if (code.charAt(0) == 'x' || code.charAt(0) == 'X') {
              codePoint = Integer.parseInt(code.substring(1), 16);
            } else {
              codePoint = Integer.parseInt(code, 10);
            }
            sb.append((char)codePoint);
            i = j + 1;
            continue;
          } catch (NumberFormatException numberFormatException) {}
      }
      sb.append(s.charAt(i));
      i++;
    }
    return sb.toString();
  }
  
   String encodeAll(String s) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      String hex = Integer.toHexString(s.charAt(i) & 0xFF).toUpperCase();
      sb.append(hex.length() == 1 ? "%0" : "%").append(hex);
    }
    return sb.toString();
  }

  String replace(String s, String f, String r) {
    if (s.indexOf(f) == -1)
      return s;
    StringBuffer sb = new StringBuffer();
    int pos = 0;
    int next;
    while ((next = s.indexOf(f, pos)) != -1) {
      sb.append(s.substring(pos, next)).append(r);
      pos = next + f.length();
    }
    sb.append(s.substring(pos));
    return sb.toString();
  }
}