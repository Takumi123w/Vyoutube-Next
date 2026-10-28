import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.media.control.VideoControl;

class VideoCanvas extends Canvas implements CommandListener {
    private Midlet midlet;
    private Command selectCommand = new Command("Select", Command.ITEM, 1);
    private boolean showUI = false;
    private int uiSelectedRow = 2; 
    private int uiSelectedIndex = 0;
    private int maxVolume = 100;
    private boolean maxVolumeChecked = false;
    private int dragRow = -1;
    
public VideoCanvas(final Midlet midlet) {
    this.midlet = midlet;
    this.setFullScreenMode(true);

    new Thread(new Runnable() {
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                    if (showUI && midlet.player != null) {
                        repaint();
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
    }).start();
}

    public void updateVideoDisplay(boolean uiVisible) {
        try {
            if (midlet.player != null) {

                VideoControl vc = (VideoControl) midlet.player.getControl("VideoControl");
                if (vc != null) {
                    int canvasW = getWidth();
                    int canvasH = getHeight();

                    int topBarH = (canvasH < 220) ? 20 : 35;
                    int bottomBarH = (canvasH < 220) ? 75 : 110;

                    int availW = canvasW;
                    int availH = canvasH;
                    int startY = 0;

                    if (uiVisible) {
                        availH = canvasH - topBarH - bottomBarH;
                        startY = topBarH;
                    }

                    int targetW = availW;
                    int targetH = (availW * 9) / 16;

                    if (targetH > availH) {
                        targetH = availH;
                        targetW = (availH * 16) / 9;
                    }

                    int x = (availW - targetW) / 2;
                    int y = startY + (availH - targetH) / 2;

                    vc.setDisplayLocation(x, y);
                    vc.setDisplaySize(targetW, targetH);
                }
            }
        } catch (Exception e) {
            System.err.println("[PLAYER] Dynamic resize failed: " + e.getMessage());
        }
    }
    
protected void showNotify() {
    try { Thread.sleep(200); } catch (Exception e) {}
    updateVideoDisplay(showUI);
    repaint();
}

    public void resetUI() {
        this.showUI = false;
        this.uiSelectedRow = 2;
        this.uiSelectedIndex = 0;
        this.maxVolume = 100;
        this.maxVolumeChecked = false;
        removeCommand(selectCommand);
        updateVideoDisplay(false); 

    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);

        if (midlet.player == null) {
            g.setColor(0xFFFFFF);
            g.drawString("Loading...", w / 2, h / 2, Graphics.HCENTER | Graphics.TOP);
        } else if (showUI) {

            int topBarH = (h < 220) ? 20 : 35;
            int bottomBarH = (h < 220) ? 75 : 110;
            int barY = h - bottomBarH;

            int row0_Y = (h < 220) ? barY + 2 : barY + 4;
            int row1_Y = (h < 220) ? barY + 25 : barY + 40;
            int row2_Y = (h < 220) ? barY + 49 : barY + 76;

            int rowH = (h < 220) ? 21 : 32;
            int btnH = (h < 220) ? 20 : 26;

            g.setColor(0x1A1A1A);
            g.fillRect(0, 0, w, topBarH);
            g.setColor(0xCC0000);
            g.drawLine(0, topBarH, w, topBarH);

            g.setColor(0xFFFFFF);
            String title = midlet.selectedVideoTitle;
            if (title != null) {
                if (title.length() > 25) title = title.substring(0, 22) + "...";
                g.drawString(title, 10, (topBarH - Font.getDefaultFont().getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
            }

            g.setColor(0x1A1A1A); 
            g.fillRect(0, barY, w, bottomBarH);
            g.setColor(0xCC0000);
            g.drawLine(0, barY, w, barY);

            long curTime = 0;
            long duration = 1;
            int volLevel = 50; 
            try {
                if (midlet.player != null) {
                    curTime = midlet.player.getMediaTime();
                    duration = midlet.player.getDuration();
                    VolumeControl vol = (VolumeControl) midlet.player.getControl("VolumeControl");
                    if (vol != null) {
                        volLevel = vol.getLevel();

                        if (!maxVolumeChecked) {
                            int testedLevel = vol.setLevel(200);
                            if (testedLevel > 100) {
                                maxVolume = testedLevel;
                            } else {
                                maxVolume = 100;
                            }
                            vol.setLevel(volLevel);
                            maxVolumeChecked = true;
                        }
                    }
                }
            } catch (Exception e) {}
            if (duration <= 0) duration = 1;

            if (uiSelectedRow == 0) {
                g.setColor(0x2A2A2A);
                g.fillRoundRect(5, row0_Y, w - 10, rowH, 6, 6);
            }

            int progressX = 55;
            int progressW = w - 110;
            int progressY = (h < 220) ? row0_Y + 10 : row0_Y + 14;
            int progressH = 4;
            int textY0 = (h < 220) ? row0_Y + 4 : row0_Y + 8;

            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            g.setColor(0xCCCCCC);
            g.drawString(formatTime(curTime), 10, textY0, Graphics.TOP | Graphics.LEFT);
            g.drawString(formatTime(duration), w - 10, textY0, Graphics.TOP | Graphics.RIGHT);

            g.setColor(0x444444);
            g.fillRect(progressX, progressY, progressW, progressH);

            int fillW = (int) ((curTime * progressW) / duration);
            if (fillW > progressW) fillW = progressW;
            if (fillW < 0) fillW = 0;
            g.setColor(0xCC0000);
            g.fillRect(progressX, progressY, fillW, progressH);

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

            int volX = 55;
            int volW = w - 110;
            int volY = (h < 220) ? row1_Y + 10 : row1_Y + 14;
            int volH = 4;
            int textY1 = (h < 220) ? row1_Y + 4 : row1_Y + 8;

            g.setColor(0xCCCCCC);
            g.drawString("VOL", 10, textY1, Graphics.TOP | Graphics.LEFT);
            g.drawString(volLevel + "%", w - 10, textY1, Graphics.TOP | Graphics.RIGHT);

            g.setColor(0x444444);
            g.fillRect(volX, volY, volW, volH);

            int volFillW = (volLevel * volW) / maxVolume;
            if (volFillW > volW) volFillW = volW;
            if (volFillW < 0) volFillW = 0;
            g.setColor(0xCC0000);
            g.fillRect(volX, volY, volFillW, volH);

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
            String playPauseText = isPlaying ? "Pause" : "Play";

            int margin = 6;
            int btnW = (w - (margin * 4)) / 3;
            Font btnFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);

            for (int i = 0; i < 3; i++) {
                int btnX = margin + i * (btnW + margin);
                boolean isFocused = (uiSelectedRow == 2 && uiSelectedIndex == i);

                g.setColor(isFocused ? 0xCC0000 : 0x333333);
                g.fillRoundRect(btnX, row2_Y, btnW, btnH, 6, 6);

                g.setColor(0xFFFFFF);
                String label = "";
                if (i == 0) label = playPauseText;
                else if (i == 1) label = "Stop";
                else if (i == 2) label = "Back";

                g.setFont(btnFont);
                int textY2 = row2_Y + (btnH - btnFont.getHeight()) / 2;
                g.drawString(label, btnX + (btnW / 2), textY2, Graphics.TOP | Graphics.HCENTER);
            }
        }
    }

private void toggleUI() {
    showUI = !showUI;
    updateVideoDisplay(showUI);
    repaint();
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
    if (!showUI) {
        toggleUI();
        return;
    }
    boolean small = getHeight() < 220;
    int barY = getHeight() - (small ? 75 : 110);
    int r0 = barY + (small ? 2 : 4);
    int r1 = barY + (small ? 25 : 40);
    int r2 = barY + (small ? 49 : 76);
    int rowH = small ? 21 : 32;
    int btnH = small ? 20 : 26;

    if (y < barY) {
        toggleUI();
        return;
    }
    if (y >= r0 && y <= r0 + rowH) {
        uiSelectedRow = 0;
        dragRow = 0;
        touchSeek(x);
    } else if (y >= r1 && y <= r1 + rowH) {
        uiSelectedRow = 1;
        dragRow = 1;
        touchVolume(x);
    } else if (y >= r2 && y <= r2 + btnH) {
    uiSelectedRow = 2;
    uiSelectedIndex = Math.min(2, x * 3 / getWidth());
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

    if (keyCode == Canvas.KEY_POUND || keyCode == 35 || keyCode == 106) {
        showUI = !showUI;
        updateVideoDisplay(showUI);
        repaint();
        return;
    }

    if (keyCode == -7 || keyCode == KEY_NUM0) { 
        goBack();
        return;
    }

    if (showUI) {
        if (action == Canvas.UP || keyCode == Canvas.KEY_NUM2 || keyCode == 50 || keyCode == -1) {
            uiSelectedRow = (uiSelectedRow - 1 + 3) % 3;
            repaint();
        } else if (action == Canvas.DOWN || keyCode == Canvas.KEY_NUM8 || keyCode == 56 || keyCode == -2) {
            uiSelectedRow = (uiSelectedRow + 1) % 3;
            repaint();
        }

        if (uiSelectedRow == 0) {
            if (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4 || keyCode == 52 || keyCode == -3) {
                seekTime(-5000000);
                repaint();
            } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == 54 || keyCode == -4) {
                seekTime(5000000);
                repaint();
            }
        } else if (uiSelectedRow == 1) {
            if (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4 || keyCode == 52 || keyCode == -3) {
                changeVolume(-10);
                repaint();
            } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == 54 || keyCode == -4) {
                changeVolume(10);
                repaint();
            }
        } else if (uiSelectedRow == 2) {
            if (action == Canvas.LEFT || keyCode == Canvas.KEY_NUM4 || keyCode == 52 || keyCode == -3) {
                uiSelectedIndex = (uiSelectedIndex - 1 + 3) % 3;
                repaint();
            } else if (action == Canvas.RIGHT || keyCode == Canvas.KEY_NUM6 || keyCode == 54 || keyCode == -4) {
                uiSelectedIndex = (uiSelectedIndex + 1) % 3;
                repaint();
            } else if (keyCode == Canvas.KEY_NUM5 || action == Canvas.FIRE || keyCode == -5 || keyCode == 53 || keyCode == 10 || keyCode == -10) {
                handleUiSelection(); 

                repaint();
            }
        }
    }
}

    private void seekTime(long offsetMicroseconds) {
        try {
            if (midlet.player != null) {
                long curTime = midlet.player.getMediaTime();
                long duration = midlet.player.getDuration();
                long newTime = curTime + offsetMicroseconds;
                if (newTime < 0) newTime = 0;
                if (duration != javax.microedition.media.Player.TIME_UNKNOWN && newTime > duration) {
                    newTime = duration;
                }
                midlet.player.setMediaTime(newTime);
            }
        } catch (Exception e) {}
    }

    private void changeVolume(int amount) {
        try {
            if (midlet.player != null) {
                VolumeControl vol = (VolumeControl) midlet.player.getControl("VolumeControl");
                if (vol != null) {
                    int level = vol.getLevel();
                    level += amount;
                    if (level < 0) level = 0;
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
        if (microSeconds == javax.microedition.media.Player.TIME_UNKNOWN || microSeconds < 0) {
            return "--:--";
        }
        long totalSeconds = microSeconds / 1000000;
        long mins = totalSeconds / 60;
        long secs = totalSeconds % 60;
        return (mins < 10 ? "0" : "") + mins + ":" + (secs < 10 ? "0" : "") + secs;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == selectCommand && showUI) {
            if (uiSelectedRow == 2) {
                handleUiSelection();
            }
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
            if (midlet.videoControl != null) {
                midlet.videoControl.setVisible(false);
                midlet.videoControl = null;
            }
        } catch (Exception e) {}
        midlet.showMainCanvas();
    }
}