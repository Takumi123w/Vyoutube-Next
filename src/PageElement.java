import javax.microedition.lcdui.Image;

class PageElement {
  static final int TYPE_TEXT = 0;
  
  static final int TYPE_LINK = 1;
  
  static final int TYPE_IMAGE = 2;
  
  static final int TYPE_FORM = 3;
  
  static final int TYPE_SEPARATOR = 4;
  
  static final int TYPE_NEWLINE = 5;
  
  static final int TYPE_FORM_INPUT = 6;
  
  static final int TYPE_FORM_BUTTON = 7;
  
  static final int TYPE_HEADER = 8;
  
  static final int TYPE_UPLOAD_BUTTON = 9;
  
  int type;
  
  String text;
  
  String url;
  
  Image image;
  
  WebForm form;
  
  WebForm.FormInput formInput;
  
  int yPos;
  
  int height;
  
  PageElement(int type) {
    this.type = type;
    this.height = 0;
  }
}
