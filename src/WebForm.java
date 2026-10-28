import java.util.Vector;

class WebForm {
  public String action;
  
  public Vector inputs = new Vector();
  
  static class FormInput {
    String name;
    
    String type;
    
    String value;
    
    String displayLabel;
    
    int yPosition;
    
    int elementIndex;
  }
  
  public void addInput(String name, String type, String value, String label) {
    FormInput in = new FormInput();
    in.name = name;
    in.type = type;
    in.value = value;
    in.displayLabel = label;
    this.inputs.addElement(in);
  }
}
