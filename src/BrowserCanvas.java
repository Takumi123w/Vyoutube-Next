import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.TextField;

class BrowserCanvas extends Canvas implements CommandListener {
  private CookieBrowser parent;
  private int lastTapIndex = -1;
  private int lastPointerY;
  private Vector elements = new Vector();
  private int lineHeight, margin = 3, cachedHeaderHeight = 0, lastTapX = 0;
  private int scrollY = 0;
  private long lastTapTime = 0;
  private int totalHeight = 0;
  private int selectedIndex = -1;
  private int canvasWidth;
  private int canvasHeight;
  private Command backCommand;
  private Command logsCommand;
  private Command selectCommand;
  private Font normalFont;
  private Font boldFont;
  private Font headerFont;

  public BrowserCanvas(CookieBrowser parent) {
    this.parent = parent;
    this.canvasWidth = getWidth();
    this.canvasHeight = getHeight();
    this.normalFont = Font.getFont(0, 0, 8);
    this.boldFont = Font.getFont(0, 1, 8);
    this.headerFont = Font.getFont(0, 1, 16);
    this.lineHeight = this.normalFont.getHeight() + 2;
    this.backCommand = new Command("Back", Command.BACK, 1);
    this.selectCommand = new Command("Select", Command.OK, 1);
    addCommand(this.backCommand);
    addCommand(this.selectCommand);
    setCommandListener(this);
  }

  public Vector getElements() {
    return this.elements;
  }

  void activateSelected() {
    if (this.selectedIndex < 0 || this.selectedIndex >= this.elements.size())
      return;
    PageElement elem = (PageElement)this.elements.elementAt(this.selectedIndex);
    if (elem.type == 1) {
      this.parent.fetchUrl(elem.url, null);
    } else if (elem.type == 6) {
      editFormInput(elem);
    } else if (elem.type == 7) {
      submitForm(elem);
    }
  }

  public void commandAction(Command c, Displayable d) {
    if (c == this.selectCommand)
      activateSelected();
    else if (c == this.backCommand)
      this.parent.parentMidlet.showMainCanvas();
  }

  public void setElements(Vector elements) {
    this.elements = elements;
    this.scrollY = 0;
    this.selectedIndex = findFirstInteractive();
    calculateLayout();
    repaint();
  }

  public void refreshLayout() {
    calculateLayout();
    this.scrollY = Math.max(0, Math.min(this.scrollY,
          Math.max(0, this.totalHeight - this.canvasHeight)));
  }

  private void calculateLayout() {
    int y = this.margin;
    for (int i = 0; i < this.elements.size(); i++) {
      PageElement elem = (PageElement)this.elements.elementAt(i);
      elem.yPos = y;
      switch (elem.type) {
        case 0:
          elem.height = calculateTextHeight(elem.text, this.canvasWidth - 2 * this.margin);
          break;
        case 1:
          elem.height = calculateTextHeight(elem.text, this.canvasWidth - 2 * this.margin);
          break;
        case 8:
          Vector headerLines = wrapText(elem.text, this.canvasWidth - 2 * this.margin, this.headerFont);
          elem.height = headerLines.size() * this.headerFont.getHeight() + 10;
          break;
        case 2:
          if (elem.image != null) {
            elem.height = elem.image.getHeight() + 3;
            break;
          }
          boolean isAvatar = (elem.text != null && elem.text.equals("avatar"));
          elem.height = isAvatar ? 36 : 68;
          break;
        case 3:
          elem.height = this.lineHeight;
          break;
        case 6:
          elem.height = this.lineHeight + 4;
          break;
        case 7:
          elem.height = this.lineHeight + 6;
          break;
        case 4:
          elem.height = 6;
          break;
        case 5:
          elem.height = this.lineHeight / 2;
          break;
      }
      y += elem.height;
    }
    this.totalHeight = y;
  }

  private int calculateTextHeight(String text, int width) {
    Vector lines = wrapText(text, width, this.normalFont);
    return lines.size() * this.lineHeight;
  }

  private Vector wrapText(String text, int maxWidth, Font font) {
    Vector lines = new Vector();
    if (text == null || text.length() == 0) {
      lines.addElement("");
      return lines;
    }
    StringBuffer currentLine = new StringBuffer();
    int start = 0;
    while (start < text.length()) {
      int end = text.indexOf(' ', start);
      String word = (end == -1) ? text.substring(start) : text.substring(start, end + 1);
      String testLine = currentLine.toString() + word;
      if (font.stringWidth(testLine) <= maxWidth) {
        currentLine.append(word);
      } else if (currentLine.length() > 0) {
        lines.addElement(currentLine.toString());
        currentLine = new StringBuffer(word);
      } else {
        lines.addElement(word);
        currentLine = new StringBuffer();
      }
      if (end == -1)
        break;
      start = end + 1;
    }
    if (currentLine.length() > 0)
      lines.addElement(currentLine.toString());
    if (lines.isEmpty())
      lines.addElement("");
    return lines;
  }

  private Vector wrapText(String text, int maxWidth) {
    return wrapText(text, maxWidth, this.normalFont);
  }

  protected void paint(Graphics g) {
    g.setColor(255, 255, 255);
    g.fillRect(0, 0, this.canvasWidth, this.canvasHeight);
    int headerHeight = 0;
    PageElement headerElem = null;
    int i;
    for (i = 0; i < this.elements.size(); i++) {
      PageElement e = (PageElement)this.elements.elementAt(i);
      if (e.type == 8) {
        headerElem = e;
        headerHeight = e.height;
        break;
      }
    }
    for (i = 0; i < this.elements.size(); i++) {
      PageElement elem = (PageElement)this.elements.elementAt(i);
      if (elem.type != 8) {
        int drawY = elem.yPos - this.scrollY + headerHeight;
        if (drawY + elem.height >= headerHeight && drawY <= this.canvasHeight) {
          if (i == this.selectedIndex && isInteractive(elem)) {
            g.setColor(255, 200, 200);
            g.fillRect(0, drawY, this.canvasWidth, elem.height);
          }
          switch (elem.type) {
            case 0:
              drawText(g, elem.text, this.margin, drawY, this.canvasWidth - 2 * this.margin, false);
              break;
            case 1:
              int linkX = this.margin;
              if (elem.url != null && elem.url.indexOf("view=profile") != -1)
                linkX = 0;
              drawText(g, elem.text, linkX, drawY, this.canvasWidth - linkX - this.margin, true);
              break;
            case 2:
              drawImage(g, elem, this.margin, drawY);
              break;
            case 6:
              drawFormInput(g, elem, this.margin, drawY, (i == this.selectedIndex));
              break;
            case 7:
              drawFormButton(g, elem, this.margin, drawY, (i == this.selectedIndex));
              break;
            case 4:
              drawSeparator(g, drawY);
              break;
          }
        }
      }
    }
    if (headerElem != null) {
      g.setColor(59, 89, 152);
      g.fillRect(0, 0, this.canvasWidth, headerHeight);
      drawText(g, headerElem.text, this.margin, 5, this.canvasWidth - 2 * this.margin, false, this.headerFont);
    }
    drawScrollbar(g);
    drawStatus(g);
  }

  private void drawText(Graphics g, String text, int x, int y, int maxWidth, boolean isLink) {
    drawText(g, text, x, y, maxWidth, isLink, (Font)null);
  }

  private void drawText(Graphics g, String text, int x, int y, int maxWidth, boolean isLink, Font customFont) {
    Font activeFont = (customFont != null) ? customFont : (isLink ? this.boldFont : this.normalFont);
    Vector lines = wrapText(text, maxWidth, activeFont);
    g.setFont(activeFont);
    for (int i = 0; i < lines.size(); i++) {
      String line = (String)lines.elementAt(i);
      if (customFont == this.headerFont) {
        g.setColor(255, 255, 255);
      } else if (isLink) {
        g.setColor(0, 0, 255);
      } else {
        g.setColor(0, 0, 0);
      }
      g.drawString(line, x, y + i * activeFont.getHeight(), 20);
    }
  }

  private void drawImage(Graphics g, PageElement elem, int x, int y) {
    if (elem.image != null) {
      int imgWidth = elem.image.getWidth();
      int imgHeight = elem.image.getHeight();
      g.drawImage(elem.image, x, y + 2, 20);
      g.setColor(100, 100, 100);
      g.drawRect(x - 1, y + 1, imgWidth + 1, imgHeight + 1);
    } else {
      boolean isAvatar = (elem.text != null && elem.text.equals("avatar"));
      int boxSize = isAvatar ? 32 : 64;
      g.setColor(50, 50, 50);
      g.fillRect(x, y + 2, boxSize, boxSize);
      g.setColor(100, 100, 100);
      g.drawRect(x, y + 2, boxSize, boxSize);
      if (!isAvatar) {
        g.setColor(150, 150, 150);
        g.setFont(this.normalFont);
        g.drawString("...", x + boxSize / 2 - 6, y + boxSize / 2 - 3, 20);
      }
    }
  }

  private void drawFormInput(Graphics g, PageElement elem, int x, int y, boolean selected) {
    g.setFont(this.normalFont);
    g.setColor(0, 0, 0);
    if (elem.formInput == null) {
      g.drawString("ERROR: NULL INPUT", x, y, 20);
      return;
    }
    WebForm.FormInput input = elem.formInput;
    String displayText = input.displayLabel + ": " + ((input.value != null) ? input.value : "");
    if (input.type.equalsIgnoreCase("password") && input.value != null)
      displayText = input.displayLabel + ": " + getPasswordMask(input.value.length());
    g.drawString(displayText, x, y, 20);
    g.setColor(100, 100, 100);
    g.drawRect(x, y + this.lineHeight - 2, this.canvasWidth - 2 * this.margin, 1);
    if (selected) {
      g.setColor(0, 150, 0);
      g.drawString("<EDIT>", this.canvasWidth - this.margin - this.normalFont.stringWidth("<EDIT>"), y, 20);
    }
  }
  
public void pressButtonByText(String text) {
    for (int i = 0; i < elements.size(); i++) {
        PageElement e = (PageElement) elements.elementAt(i);
        if (e.type == 7 && e.text != null && e.text.equals(text)) {
            selectedIndex = i;
            activateSelected();
            break;
        }
    }
}

  private void drawFormButton(Graphics g, PageElement elem, int x, int y, boolean selected) {
    g.setFont(this.boldFont);
    if (elem.text == null)
      elem.text = "SUBMIT";
    String label = elem.text.toUpperCase();
    int buttonWidth = this.canvasWidth - 2 * this.margin;
    int buttonHeight = this.lineHeight + 4;
    if (label.indexOf("REGISTER") != -1 || label.indexOf("DAFTAR") != -1 || label.indexOf("CREATE") != -1) {
      if (selected) {
        g.setColor(255, 0, 0);
      } else {
        g.setColor(0, 136, 0);
      }
    } else if (selected) {
      g.setColor(200, 0, 0);
    } else {
      g.setColor(0, 100, 200);
    }
    g.fillRoundRect(x, y, buttonWidth, buttonHeight, 6, 6);
    g.setColor(255, 255, 255);
    int textWidth = this.boldFont.stringWidth(label);
    g.drawString(label, x + (buttonWidth - textWidth) / 2, y + 3, 20);
  }

  private String getPasswordMask(int length) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < length; i++) {
      sb.append('*');
    }
    return sb.toString();
  }

  private void drawSeparator(Graphics g, int y) {
    g.setColor(150, 150, 150);
    g.drawLine(this.margin, y + 3, this.canvasWidth - this.margin, y + 3);
  }

  private void drawScrollbar(Graphics g) {
    if (this.totalHeight <= this.canvasHeight)
      return;
    int barHeight = this.canvasHeight * this.canvasHeight / this.totalHeight;
    int barY = this.scrollY * this.canvasHeight / this.totalHeight;
    g.setColor(200, 200, 200);
    g.fillRect(this.canvasWidth - 4, 0, 4, this.canvasHeight);
    g.setColor(100, 100, 100);
    g.fillRect(this.canvasWidth - 4, barY, 4, barHeight);
  }

  private void drawStatus(Graphics g) {}

private void updateSelectionByPosition() {
    if (this.elements.isEmpty()) return;

    int screenMiddle = this.scrollY + (this.canvasHeight / 2);

    for (int i = 0; i < this.elements.size(); i++) {
        PageElement elem = (PageElement)this.elements.elementAt(i);

        if (isInteractive(elem)) {

            if (elem.yPos <= screenMiddle && (elem.yPos + elem.height) >= screenMiddle) {
                this.selectedIndex = i;
                return;
            }

            if (elem.yPos > screenMiddle - 20) {
                this.selectedIndex = i;
                return;
            }
        }
    }
}

protected void pointerPressed(int px, int py) {
        lastPointerY = py;
        lastTapX = px;

        int tappedIndex = -1;

        int virtualY = py + scrollY - cachedHeaderHeight;
        for (int i = 0; i < elements.size(); i++) {
            PageElement elem = (PageElement) elements.elementAt(i);
            if (virtualY >= elem.yPos && virtualY < elem.yPos + elem.height) {
                if (isInteractive(elem)) {
                    selectedIndex = i;
                    tappedIndex = i;
                    repaint();
                }
                break;
            }
        }

        long currentTime = System.currentTimeMillis();
        if (tappedIndex != -1 && tappedIndex == lastTapIndex
                && currentTime - lastTapTime < 400) {
            activateSelected();
            lastTapTime = 0;
        } else {
            lastTapTime = currentTime;
        }
        lastTapIndex = tappedIndex;
    }

    protected void pointerDragged(int px, int py) {

        int deltaY = lastPointerY - py;
        scrollY += deltaY;

        int maxScroll = Math.max(0, totalHeight - (canvasHeight - cachedHeaderHeight));
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;

        lastPointerY = py;
        repaint();
    }

    protected void pointerReleased(int px, int py) {

        repaint();
    }

protected void keyPressed(int keyCode) {
    int action = getGameAction(keyCode);
    int scrollStep = 20; 
    int maxScroll = Math.max(0, this.totalHeight - this.canvasHeight + 50);

    int oldScroll = this.scrollY;
    boolean atEdge = false; 

    if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == -1) {
        this.scrollY = Math.max(0, this.scrollY - scrollStep);
        if (oldScroll == 0 && this.scrollY == 0) {
            moveSelection(-1);
            atEdge = true; 

        }
    } 
    else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == -2) {
        this.scrollY = Math.min(maxScroll, this.scrollY + scrollStep);
        if (oldScroll == maxScroll && this.scrollY == maxScroll) {
            moveSelection(1);
            atEdge = true; 

        }
    } 
    else if (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4 || keyCode == -3) {
        this.scrollY = Math.max(0, this.scrollY - this.canvasHeight / 2);
    } 
    else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == -4) {
        this.scrollY = Math.min(maxScroll, this.scrollY + this.canvasHeight / 2);
    }
    else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 103 || keyCode == -10 || keyCode == 10) {
        activateSelected();
        atEdge = true; 

    } 

    if (!atEdge) {
        updateSelectionByPosition();
    }

    repaint();
}

private void moveSelection(int direction) {
    if (this.elements.isEmpty())
      return;
    int newIndex = this.selectedIndex;
    int maxScroll = Math.max(0, this.totalHeight - this.canvasHeight + 50);

    do {
      newIndex += direction;
      if (newIndex < 0)
        newIndex = this.elements.size() - 1;
      if (newIndex >= this.elements.size())
        newIndex = 0;

      PageElement elem = (PageElement)this.elements.elementAt(newIndex);
      if (isInteractive(elem)) {
        this.selectedIndex = newIndex;

        if (this.scrollY > 0 && this.scrollY < maxScroll) {
            ensureVisible(this.selectedIndex);
        }

        repaint();
        break;
      }
    } while (newIndex != this.selectedIndex);
  }

private void ensureVisible(int index) {
    if (index < 0 || index >= this.elements.size()) return;

    PageElement elem = (PageElement)this.elements.elementAt(index);
    int headerHeight = 0;

    for (int i = 0; i < this.elements.size(); i++) {
        PageElement e = (PageElement)this.elements.elementAt(i);
        if (e.type == 8) { headerHeight = e.height; break; }
    }

    int targetScroll = elem.yPos - (this.canvasHeight / 2) + (elem.height / 2);
    int maxScroll = Math.max(0, this.totalHeight - this.canvasHeight + 50);

    if (this.scrollY > 0 && this.scrollY < maxScroll) {
        this.scrollY = Math.max(0, Math.min(maxScroll, targetScroll));
    }
}

  private boolean isInteractive(PageElement elem) {
    return (elem.type == 1 || elem.type == 6 || elem.type == 7);
  }

  private int findFirstInteractive() {
    for (int i = 0; i < this.elements.size(); i++) {
      PageElement elem = (PageElement)this.elements.elementAt(i);
      if (isInteractive(elem))
        return i;
    }
    return -1;
  }

  private void editFormInput(PageElement elem) {
    int constraints;
    WebForm.FormInput input = elem.formInput;
    if (input == null)
      return;
    if (input.type.equalsIgnoreCase("password")) {
      constraints = 65536;
    } else if (input.type.equalsIgnoreCase("number")) {
      constraints = 2;
    } else {
      constraints = 0;
    }
    int maxSize = 256;
    if (input.name != null) {
      String n = input.name.toLowerCase();
      if (n.indexOf("message") != -1 || n.indexOf("content") != -1 || n
        .indexOf("text") != -1 || n.indexOf("body") != -1 || n
        .indexOf("post") != -1 || n.indexOf("comment") != -1)
        maxSize = 1024;
    }
    String title = (input.displayLabel != null && input.displayLabel.length() > 0) ? input.displayLabel : ((input.name != null) ? input.name : "Input");
    Form editForm = new Form(title);
    final TextField tf = new TextField(title, (input.value != null) ? input.value : "", maxSize, constraints);
    editForm.append((Item)tf);
    final Command doneCmd = new Command("Done", 4, 1);
    Command cancelCmd = new Command("Cancel", 3, 2);
    editForm.addCommand(doneCmd);
    editForm.addCommand(cancelCmd);
    final WebForm.FormInput finalInput = input;
    final BrowserCanvas self = this;
    editForm.setCommandListener(new CommandListener() {
      public void commandAction(Command c, Displayable d) {
        if (c == doneCmd) {
          finalInput.value = tf.getString();
          self.parent.log("INPUT_SET: " + finalInput.name + "=" + finalInput.value);
        }
        self.parent.display.setCurrent((Displayable)self);
        self.repaint();
      }
    });
    this.parent.display.setCurrent((Displayable)editForm);
  }

  private void submitForm(PageElement buttonElem) {
    WebForm form = buttonElem.form;
    WebForm.FormInput clickedButton = buttonElem.formInput;
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < form.inputs.size(); i++) {
      WebForm.FormInput input = (WebForm.FormInput)form.inputs.elementAt(i);
      if (input.type.equalsIgnoreCase("submit")) {
        if (input == clickedButton && !input.name.equals("submit_btn")) {
          String value = (input.value != null) ? input.value : "";
          if (sb.length() > 0)
            sb.append("&");
          sb.append(this.parent.urlEncode(input.name)).append("=").append(this.parent.urlEncode(value));
        }
      } else if (!input.type.equalsIgnoreCase("file")) {
        String value = (input.value != null) ? input.value : "";
        if (sb.length() > 0)
          sb.append("&");
        sb.append(this.parent.urlEncode(input.name)).append("=").append(this.parent.urlEncode(value));
      }
    }
    this.parent.fetchUrl(form.action, sb.toString());
  }
}