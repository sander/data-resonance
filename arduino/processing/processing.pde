import processing.serial.*;

Serial port;
int[] vals = new int[3];
int i;

void setup() {
  port = new Serial(this, "/dev/tty.usbmodem1411", 115200);
}

void draw() {
}

void serialEvent(Serial which) {
  int in = which.read();
  if (in == 0) i = 0;
  else vals[i++] = in;
  println(vals);
}
