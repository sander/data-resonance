#include <FeedbackServo.h>
#include <Servo.h>

//#define FINAL

#ifdef FINAL
#define PRESSURE_L A1
#define PRESSURE_R A0
#define POT_L A3
#define POT_R A2
#define SERVO_L 5
#define SERVO_R 6
#else
#define PRESSURE_L A1
#define PRESSURE_R A1
#define POT_L A2
#define POT_R A3
#define SERVO_L 10
#define SERVO_R 9
#endif

// #define pins with ifdefs for old/new prototype

int pressureL;
int pressureR;

int targetL = 700;
int targetR = 700;

FeedbackServo servoL;
FeedbackServo servoR;

void setup() {
  Serial.begin(115200);
  
  servoL.begin(SERVO_L, POT_L);
  servoL.setMaxDelay(100);
  servoR.begin(SERVO_R, POT_R);
  servoR.setMaxDelay(100);
  
  establish();
}

void loop() {
  update();
  
  servoL.loop();
  servoR.loop();
  
  if (Serial.available() > 0) {
    int nTargetL = Serial.parseInt();
    int nTargetR = Serial.parseInt();
    if (Serial.read() == '\n') {
      targetL = nTargetL;
      targetR = nTargetR;
    }
  } else {
    send();
  }
}

void establish() {
  while (Serial.available() <= 0) {
    Serial.println("?");
    delay(300);
  }
  Serial.println("!");
}

void send() {
  Serial.print('V');
  Serial.print(pressureL);
  Serial.print(',');
  Serial.print(pressureR);
  Serial.print(',');
  Serial.print(servoL.feedback());
  Serial.print(',');
  Serial.print(servoR.feedback());
  Serial.print(',');
  Serial.print(servoL.attached());
  Serial.print(',');
  Serial.print(servoR.attached());
  Serial.println();
}

void update() {
  pressureL = analogRead(PRESSURE_L);
  pressureR = analogRead(PRESSURE_R);
  
  servoL.setMicro(targetL);
  servoR.setMicro(targetR);
}

