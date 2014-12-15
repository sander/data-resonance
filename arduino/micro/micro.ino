#include <FeedbackServo.h>
#include <Servo.h>

#define DEBUG false
#define DOWN 80
#define UP 130
#define DELAY 30

FeedbackServo servo;

void setup() {
  Serial.begin(115200);
  
  servo.begin(5, A5);
  //servo[1].setReversed(true);

  servo.set(UP);
}

void loop() {
  /*
  read_input();
  adjust_motors_to_pressure();
  if (update_state()) send_state();
  */
  for (int st = UP; st > DOWN; st--) {
    servo.set(st);
    servo.loop();
    delay(DELAY);
  }
  delay(1000);
  for (int st = DOWN; st < UP; st++) {
    servo.set(st);
    servo.loop();
    delay(DELAY);
  }
  delay(1000);
}

void read_input() {
  /*
  while (Serial.available() > 0) {
    int val = Serial.read();
    if (val == 255) { read_index = 0; }
    else { servo[read_index++].adjust(val - 127); }
  }
  */
}

void write(byte b) { DEBUG ? Serial.println(b) : Serial.write(b); }

