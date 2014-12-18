import processing.serial.*;

Serial port;
boolean established = false;

int targetL;
int targetR;

int pressureL;
int pressureR;
int motorL;
int motorR;
int attachedL;
int attachedR;

void setup() {
  size(800, 800);
  port = new Serial(this, "/dev/tty.usbmodem1421", 115200);
  port.bufferUntil('\n');
  port.clear();
}

void draw() {
  background(0);
  fill(255);
  String s = "established: " + established + "\n"
    + "target: " + targetL + ", " + targetR + "\n\n"
    + "pressure: " + pressureL + ", " + pressureR + "\n"
    + "motor: " + motorL + ", " + motorR + "\n"
    + "attached: " + attachedL + ", " + attachedR + "\n";
  textAlign(LEFT, TOP);
  text(s, 0, 0);
}

void serialEvent(Serial port) {
  String val = port.readStringUntil('\n');
  if (val != null) {
    val = trim(val);
    if (!established) {
      if (val.equals("?")) {
        port.clear();
        port.write("!\n");
        println("establishing");
      } else if (val.equals("!")) {
        established = true;
        println("established");
      } else {
        println("unknown establishing command: " + val);
      }
    } else {
      if (val.charAt(0) == 'V') {
        int[] vals = int(split(val.substring(1), ','));
        if (vals.length == 6) {
          pressureL = vals[0];
          pressureR = vals[1];
          motorL = vals[2];
          motorR = vals[3];
          attachedL = vals[4];
          attachedR = vals[5];
        }
      } else {
        println("unknown command: " + val);
      }
    }
  }
}

void mouseMoved() {
  //targetL = int(map(mouseX, 0, width, -1600, 1600));
  //targetR = int(map(mouseY, 0, height, -1600, 1600));
  targetL = int(map(mouseX, 0, width, 112, 149));
  targetR = int(map(mouseY, 0, height, 38, 78));
  if (established) {
    send();
  }
}

void send() {
  String msg = "" + targetL + "," + targetR + "\n";
  println("sending: " + msg);
  port.write(msg);
}
