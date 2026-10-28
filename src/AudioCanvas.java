import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.media.control.VolumeControl;

class AudioCanvas extends Canvas implements CommandListener {
    private Midlet midlet;
    private Command selectCommand = new Command("Select", Command.ITEM, 1);
    private boolean showUI = true;
    private int uiSelectedRow = 2;
    private int uiSelectedIndex = 0;
    private int maxVolume = 100;
    private boolean maxVolumeChecked = false;
    private int noteAnim = 0;
    private static final String[] NOTES = { "1", "2", "3", "4" };
    private int dragRow = -1;

    public AudioCanvas(final Midlet midlet) {
        this.midlet = midlet;
        this.setFullScreenMode(true);
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                        if (midlet.player != null) {
                            noteAnim = (noteAnim + 1) % NOTES.length;
                            repaint();
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        }).start();
    }

    public void resetUI() {
        this.showUI = true;
        this.uiSelectedRow = 2;
        this.uiSelectedIndex = 0;
        this.maxVolume = 100;
        this.maxVolumeChecked = false;
        this.noteAnim = 0;
        removeCommand(selectCommand);
    }

    protected void showNotify() {
        try { Thread.sleep(200); } catch (Exception e) {}
        repaint();
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(0x0D0D0D);
        g.fillRect(0, 0, w, h);

        if (midlet.player == null) {
            g.setColor(0xFFFFFF);
            g.setFont(Font.getDefaultFont());
            g.drawString("Loading...", w / 2, h / 2, Graphics.HCENTER | Graphics.TOP);
            return;
        }
        boolean small   = (h < 220);
        int topBarH     = small ? 20 : 35;
        int bottomBarH  = small ? 75 : 110;
        int barY        = h - bottomBarH;
        int artAreaH    = barY - topBarH;

        int row0_Y = small ? barY + 2  : barY + 4;
        int row1_Y = small ? barY + 25 : barY + 40;
        int row2_Y = small ? barY + 49 : barY + 76;
        int rowH   = small ? 21 : 32;
        int btnH   = small ? 20 : 26;
        g.setColor(0x1A1A1A);
        g.fillRect(0, 0, w, topBarH);
        g.setColor(0xCC0000);
        g.drawLine(0, topBarH, w, topBarH);

        g.setColor(0xFFFFFF);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        String title = midlet.selectedVideoTitle;
        if (title == null || title.length() == 0) title = "Unknown Track";
        if (title.length() > 25) title = title.substring(0, 22) + "...";
        g.drawString(title, 10, (topBarH - Font.getDefaultFont().getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
        g.setColor(0xCC0000);
        g.drawString(NOTES[noteAnim], w - 10, (topBarH - Font.getDefaultFont().getHeight()) / 2,
                Graphics.TOP | Graphics.RIGHT);
        drawArtArea(g, w, topBarH, artAreaH, small);
        g.setColor(0x1A1A1A);
        g.fillRect(0, barY, w, bottomBarH);
        g.setColor(0xCC0000);
        g.drawLine(0, barY, w, barY);
        long curTime = 0, duration = 1;
        int volLevel = 50;
        try {
            if (midlet.player != null) {
                curTime  = midlet.player.getMediaTime();
                duration = midlet.player.getDuration();
                VolumeControl vol = (VolumeControl) midlet.player.getControl("VolumeControl");
                if (vol != null) {
                    volLevel = vol.getLevel();
                    if (!maxVolumeChecked) {
                        int tested = vol.setLevel(200);
                        maxVolume = (tested > 100) ? tested : 100;
                        vol.setLevel(volLevel);
                        maxVolumeChecked = true;
                    }
                }
            }
        } catch (Exception e) {}
        if (duration <= 0) duration = 1;

        Font smallFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(smallFont);
        if (uiSelectedRow == 0) {
            g.setColor(0x2A2A2A);
            g.fillRoundRect(5, row0_Y, w - 10, rowH, 6, 6);
        }

        int progressX = 55;
        int progressW = w - 110;
        int progressY = small ? row0_Y + 10 : row0_Y + 14;
        int textY0    = small ? row0_Y + 4  : row0_Y + 8;

        g.setColor(0xCCCCCC);
        g.drawString(formatTime(curTime),  10,     textY0, Graphics.TOP | Graphics.LEFT);
        g.drawString(formatTime(duration), w - 10, textY0, Graphics.TOP | Graphics.RIGHT);

        g.setColor(0x444444);
        g.fillRect(progressX, progressY, progressW, 4);

        int fillW = (int)((curTime * progressW) / duration);
        fillW = Math.max(0, Math.min(fillW, progressW));
        g.setColor(0xCC0000);
        g.fillRect(progressX, progressY, fillW, 4);

        if (uiSelectedRow == 0) {
            g.setColor(0xFFFFFF);
            g.fillRoundRect(progressX + fillW - 4, progressY - 3, 8, 11, 4, 4);
        } else {
            g.setColor(0x999999);
            g.fillRoundRect(progressX + fillW - 3, progressY - 1, 6, 7, 2, 2);
        }
        if (uiSelectedRow == 1) {
            g.setColor(0x2A2A2A);
            g.fillRoundRect(5, row1_Y, w - 10, rowH, 6, 6);
        }

        int volX   = 55;
        int volW   = w - 110;
        int volY   = small ? row1_Y + 10 : row1_Y + 14;
        int textY1 = small ? row1_Y + 4  : row1_Y + 8;

        g.setColor(0xCCCCCC);
        g.drawString("VOL",         10,     textY1, Graphics.TOP | Graphics.LEFT);
        g.drawString(volLevel + "%", w - 10, textY1, Graphics.TOP | Graphics.RIGHT);

        g.setColor(0x444444);
        g.fillRect(volX, volY, volW, 4);

        int volFillW = (volLevel * volW) / maxVolume;
        volFillW = Math.max(0, Math.min(volFillW, volW));
        g.setColor(0xCC0000);
        g.fillRect(volX, volY, volFillW, 4);

        if (uiSelectedRow == 1) {
            g.setColor(0xFFFFFF);
            g.fillRoundRect(volX + volFillW - 4, volY - 3, 8, 11, 4, 4);
        } else {
            g.setColor(0x999999);
            g.fillRoundRect(volX + volFillW - 3, volY - 1, 6, 7, 2, 2);
        }
        boolean isPlaying = false;
        try {
            if (midlet.player != null) {
                isPlaying = (midlet.player.getState() == javax.microedition.media.Player.STARTED);
            }
        } catch (Exception e) {}

        int margin = 6;
        int btnW   = (w - (margin * 4)) / 3;
        Font btnFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);

        String[] labels = { isPlaying ? "Pause" : "Play", "Stop", "Back" };
        for (int i = 0; i < 3; i++) {
            int btnX      = margin + i * (btnW + margin);
            boolean focus = (uiSelectedRow == 2 && uiSelectedIndex == i);
            g.setColor(focus ? 0xCC0000 : 0x333333);
            g.fillRoundRect(btnX, row2_Y, btnW, btnH, 6, 6);
            g.setColor(0xFFFFFF);
            g.setFont(btnFont);
            int textY2 = row2_Y + (btnH - btnFont.getHeight()) / 2;
            g.drawString(labels[i], btnX + (btnW / 2), textY2, Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void drawArtArea(Graphics g, int w, int topBarH, int artAreaH, boolean small) {
        int centerX = w / 2;
        int centerY = topBarH + artAreaH / 2;
        g.setColor(0x141414);
        g.fillRect(0, topBarH, w, artAreaH);
        g.setColor(0x1A1A1A);
        for (int y = topBarH + 10; y < topBarH + artAreaH; y += 12) {
            g.drawLine(0, y, w, y);
        }

        int barCount = small ? 9 : 13;
        int barSpacing = 4;
        int totalBarsW = (barCount * (barSpacing + 4));
        int barStartX  = centerX - totalBarsW / 2;
        int maxBarH    = (artAreaH / 2) - 10;
        int barBaseY   = centerY + (small ? 10 : 18);
        int[] barHeights = { 40, 70, 55, 90, 65, 100, 75, 85, 60, 80, 45, 95, 50 };

        for (int i = 0; i < barCount; i++) {
            int phase = (i + noteAnim) % barHeights.length;
            int bh    = (barHeights[phase] * maxBarH) / 100;
            int bx    = barStartX + i * (barSpacing + 4);
            g.setColor(0x660000);
            g.fillRect(bx, barBaseY - bh, 4, bh / 2);
            g.setColor(0xCC0000);
            g.fillRect(bx, barBaseY - bh / 2, 4, bh / 2);
        }

        int iconR = small ? 14 : 20;
        g.setColor(0x222222);
        g.fillArc(centerX - iconR, centerY - iconR - (small ? 20 : 30),
                iconR * 2, iconR * 2, 0, 360);
        g.setColor(0xCC0000);
        g.drawArc(centerX - iconR, centerY - iconR - (small ? 20 : 30),
                iconR * 2, iconR * 2, 0, 360);
        g.setColor(0x777777);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        g.drawString("MP3 Audio", centerX,
                centerY + (small ? 14 : 24), Graphics.TOP | Graphics.HCENTER);
    }

private void touchSeek(int x) {
    try {
        if (midlet.player == null) return;
        long duration = midlet.player.getDuration();
        if (duration <= 0 || duration == javax.microedition.media.Player.TIME_UNKNOWN) return;
        int pw = getWidth() - 110;
        long t = (long) (x - 55) * duration / pw;
        if (t < 0) t = 0;
        if (t > duration) t = duration;
        midlet.player.setMediaTime(t);
    } catch (Exception e) {}
}

private void touchVolume(int x) {
    try {
        if (midlet.player == null) return;
        VolumeControl vol = (VolumeControl) midlet.player.getControl("VolumeControl");
        if (vol == null) return;
        int vw = getWidth() - 110;
        int level = (x - 55) * maxVolume / vw;
        if (level < 0) level = 0;
        if (level > maxVolume) level = maxVolume;
        vol.setLevel(level);
    } catch (Exception e) {}
}

protected void pointerPressed(int x, int y) {
    dragRow = -1;
    boolean small = getHeight() < 220;
    int barY = getHeight() - (small ? 75 : 110);
    int r0 = barY + (small ? 2 : 4);
    int r1 = barY + (small ? 25 : 40);
    int r2 = barY + (small ? 49 : 76);
    int rowH = small ? 21 : 32;
    int btnH = small ? 20 : 26;

    if (y >= r0 && y <= r0 + rowH) {
        uiSelectedRow = 0;
        dragRow = 0;
        touchSeek(x);
    } else if (y >= r1 && y <= r1 + rowH) {
        uiSelectedRow = 1;
        dragRow = 1;
        touchVolume(x);
    } else if (y >= r2 && y <= r2 + btnH) {
        int margin = 6;
        int btnW = (getWidth() - margin * 4) / 3;
        int i = (x - margin) / (btnW + margin);
        if (i < 0) i = 0;
        if (i > 2) i = 2;
        uiSelectedRow = 2;
        uiSelectedIndex = i;
        handleUiSelection();
    }
    repaint();
}

protected void pointerDragged(int x, int y) {
    if (dragRow == 1) {
        touchVolume(x);
        repaint();
    }
}

protected void pointerReleased(int x, int y) {
    if (dragRow == 0) {
        touchSeek(x);
        repaint();
    }
    dragRow = -1;
}

    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);

        if (keyCode == -7 || keyCode == KEY_NUM0) {
            goBack();
            return;
        }

        if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == -1) {
            uiSelectedRow = (uiSelectedRow - 1 + 3) % 3;
            repaint();
        } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == -2) {
            uiSelectedRow = (uiSelectedRow + 1) % 3;
            repaint();
        }

        if (uiSelectedRow == 0) {
            if (action == Canvas.LEFT  || keyCode == Canvas.KEY_NUM4 || keyCode == 52 || keyCode == -3) {
                seekTime(-5000000); repaint();
            } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == 54 || keyCode == -4) {
                seekTime(5000000);  repaint();
            }
        } else if (uiSelectedRow == 1) {
            if (action == Canvas.LEFT  || keyCode == Canvas.KEY_NUM4 || keyCode == 52 || keyCode == -3) {
                changeVolume(-10); repaint();
            } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == 54 || keyCode == -4) {
                changeVolume(10);  repaint();
            }
        } else if (uiSelectedRow == 2) {
            if (action == Canvas.LEFT  || keyCode == Canvas.KEY_NUM4 || keyCode == 52 || keyCode == -3) {
                uiSelectedIndex = (uiSelectedIndex - 1 + 3) % 3; repaint();
            } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == 54 || keyCode == -4) {
                uiSelectedIndex = (uiSelectedIndex + 1) % 3; repaint();
            } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE
                    || keyCode == -5 || keyCode == 53 || keyCode == 10 || keyCode == -10) {
                handleUiSelection(); repaint();
            }
        }
    }

    private void seekTime(long offsetMicros) {
        try {
            if (midlet.player != null) {
                long cur      = midlet.player.getMediaTime();
                long duration = midlet.player.getDuration();
                long newTime  = cur + offsetMicros;
                if (newTime < 0) newTime = 0;
                if (duration != javax.microedition.media.Player.TIME_UNKNOWN && newTime > duration)
                    newTime = duration;
                midlet.player.setMediaTime(newTime);
            }
        } catch (Exception e) {}
    }

    private void changeVolume(int amount) {
        try {
            if (midlet.player != null) {
                VolumeControl vol = (VolumeControl) midlet.player.getControl("VolumeControl");
                if (vol != null) {
                    int level = vol.getLevel() + amount;
                    if (level < 0)         level = 0;
                    if (level > maxVolume) level = maxVolume;
                    vol.setLevel(level);
                }
            }
        } catch (Exception e) {}
    }

    private void handleUiSelection() {
        switch (uiSelectedIndex) {
            case 0:
                try {
                    if (midlet.player != null) {
                        if (midlet.player.getState() == javax.microedition.media.Player.STARTED) {
                            midlet.player.stop();
                        } else {
                            midlet.player.start();
                        }
                    }
                } catch (Exception e) {}
                break;
            case 1:
                try {
                    if (midlet.player != null) {
                        midlet.player.stop();
                        midlet.player.setMediaTime(0);
                    }
                } catch (Exception e) {}
                break;
            case 2:
                goBack();
                break;
        }
    }

    private String formatTime(long microSeconds) {
        if (microSeconds == javax.microedition.media.Player.TIME_UNKNOWN || microSeconds < 0)
            return "--:--";
        long totalSeconds = microSeconds / 1000000;
        long mins = totalSeconds / 60;
        long secs = totalSeconds % 60;
        return (mins < 10 ? "0" : "") + mins + ":" + (secs < 10 ? "0" : "") + secs;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == selectCommand) {
            if (uiSelectedRow == 2) handleUiSelection();
            repaint();
        }
    }

    private void goBack() {
        try {
            if (midlet.player != null) {
                midlet.player.stop();
                midlet.player.close();
                midlet.player = null;
            }
        } catch (Exception e) {}
        midlet.showMainCanvas();
    }
}